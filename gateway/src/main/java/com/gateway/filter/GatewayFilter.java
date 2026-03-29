package com.gateway.filter;

import com.gateway.circuitbreaker.CircuitBreakerManager;
import com.gateway.config.ServiceRegistryConfig;
import com.gateway.model.RequestLog;
import com.gateway.ratelimit.RateLimiterService;
import com.gateway.repository.RequestLogRepository;
import com.gateway.security.JwtUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayFilter implements Filter {

    private final JwtUtil jwtUtil;
    private final RateLimiterService rateLimiter;
    private final ServiceRegistryConfig serviceRegistry;
    private final CircuitBreakerManager circuitBreakerManager;
    private final RequestLogRepository requestLogRepository;
    private final RestTemplate restTemplate;

    private static final Set<String> PUBLIC_PATHS = Set.of("/auth/token", "/actuator/health");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();

        // Pass through public paths
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            chain.doFilter(req, res);
            return;
        }

        // --- Step 1: Assign Correlation ID ---
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-ID"))
                .orElse(UUID.randomUUID().toString());
        response.setHeader("X-Correlation-ID", correlationId);

        // --- Step 2: JWT Authentication ---
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header", correlationId);
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token", correlationId);
            return;
        }

        String userId = jwtUtil.extractUserId(token);

        // --- Step 3: Rate Limiting (user + endpoint) ---
        RateLimiterService.RateLimitResult userLimit = rateLimiter.checkUserLimit(userId);
        if (!userLimit.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(userLimit.limit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            sendError(response, HttpStatus.TOO_MANY_REQUESTS, "User rate limit exceeded", correlationId);
            logRequest(correlationId, request.getMethod(), path, null, 429L, userId, null, "Rate limit: user");
            return;
        }

        RateLimiterService.RateLimitResult endpointLimit = rateLimiter.checkEndpointLimit(path);
        if (!endpointLimit.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(endpointLimit.limit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            sendError(response, HttpStatus.TOO_MANY_REQUESTS, "Endpoint rate limit exceeded", correlationId);
            logRequest(correlationId, request.getMethod(), path, null, 429L, userId, null, "Rate limit: endpoint");
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.min(userLimit.remaining(), endpointLimit.remaining())));

        // --- Step 4: Route to downstream service ---
        var serviceEntry = serviceRegistry.resolveService(path);
        if (serviceEntry == null) {
            sendError(response, HttpStatus.NOT_FOUND, "No downstream service found for path: " + path, correlationId);
            return;
        }

        String serviceName = serviceEntry.getKey();
        String serviceUrl = serviceEntry.getValue().getUrl();
        long startTime = System.currentTimeMillis();

        // --- Step 5: Circuit Breaker wrap ---
        CircuitBreaker cb = circuitBreakerManager.getCircuitBreaker(serviceName);
        Supplier<ResponseEntity<byte[]>> proxyCall = CircuitBreaker.decorateSupplier(cb,
                () -> {
                    try {
                        return forwardRequest(request, serviceUrl, correlationId, userId);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        try {
            ResponseEntity<byte[]> downstream = proxyCall.get();
            long latency = System.currentTimeMillis() - startTime;

            // Copy downstream response to client
            response.setStatus(downstream.getStatusCode().value());
            downstream.getHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Transfer-Encoding")) {
                    values.forEach(v -> response.addHeader(name, v));
                }
            });
            response.setHeader("X-Correlation-ID", correlationId);
            if (downstream.getBody() != null) {
                response.getOutputStream().write(downstream.getBody());
            }

            logRequest(correlationId, request.getMethod(), path, serviceName,
                    latency, userId, downstream.getStatusCode().value(), null);

        } catch (CallNotPermittedException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Circuit breaker OPEN for service: {}, correlationId: {}", serviceName, correlationId);
            sendError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Service " + serviceName + " is temporarily unavailable (circuit open)", correlationId);
            logRequest(correlationId, request.getMethod(), path, serviceName, latency, userId, 503, "Circuit breaker open");

        } catch (HttpStatusCodeException e) {
            long latency = System.currentTimeMillis() - startTime;
            response.setStatus(e.getStatusCode().value());
            response.setHeader("Content-Type", "application/json");
            response.setHeader("X-Correlation-ID", correlationId);
            response.getWriter().write(e.getResponseBodyAsString());
            logRequest(correlationId, request.getMethod(), path, serviceName, latency, userId,
                    e.getStatusCode().value(), e.getMessage());

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Proxy error for correlationId={}: {}", correlationId, e.getMessage(), e);
            sendError(response, HttpStatus.BAD_GATEWAY, "Upstream service error: " + e.getMessage(), correlationId);
            logRequest(correlationId, request.getMethod(), path, serviceName, latency, userId, 502, e.getMessage());
        }
    }

    private ResponseEntity<byte[]> forwardRequest(HttpServletRequest request, String serviceUrl,
                                                   String correlationId, String userId) throws IOException {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String fullUrl = serviceUrl + path + (query != null ? "?" + query : "");

        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(name, Collections.list(request.getHeaders(name))));

        // Inject correlation ID and user context into downstream headers
        headers.set("X-Correlation-ID", correlationId);
        headers.set("X-User-ID", userId);
        headers.set("X-Forwarded-By", "api-gateway");

        byte[] body = request.getInputStream().readAllBytes();
        HttpEntity<byte[]> entity = new HttpEntity<>(body.length > 0 ? body : null, headers);

        return restTemplate.exchange(fullUrl,
                HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message, String correlationId)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setHeader("X-Correlation-ID", correlationId);
        response.getWriter().write(String.format(
                "{\"error\":\"%s\",\"status\":%d,\"correlationId\":\"%s\",\"timestamp\":\"%s\"}",
                message, status.value(), correlationId, Instant.now()
        ));
    }

    private void logRequest(String correlationId, String method, String path,
                             String serviceName, Long latencyMs, String userId,
                             Integer statusCode, String errorMessage) {
        try {
            RequestLog log = RequestLog.builder()
                    .correlationId(correlationId)
                    .timestamp(Instant.now())
                    .method(method)
                    .path(path)
                    .serviceName(serviceName)
                    .latencyMs(latencyMs)
                    .userId(userId)
                    .statusCode(statusCode)
                    .errorMessage(errorMessage)
                    .build();
            requestLogRepository.save(log);
        } catch (Exception e) {
            GatewayFilter.log.error("Failed to save request log: {}", e.getMessage());
        }
    }
}
