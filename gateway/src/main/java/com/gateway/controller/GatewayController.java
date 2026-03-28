package com.gateway.controller;

import com.gateway.circuitbreaker.CircuitBreakerManager;
import com.gateway.metrics.MetricsService;
import com.gateway.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GatewayController {

    private final MetricsService metricsService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final JwtUtil jwtUtil;

    // ---- Auth endpoint (public) ----
    @PostMapping("/auth/token")
    public ResponseEntity<?> generateToken(@RequestBody TokenRequest request) {
        // In production: verify credentials against a user store
        String token = jwtUtil.generateToken(request.userId(), request.role());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", request.userId(),
                "role", request.role()
        ));
    }

    // ---- Dashboard metrics ----
    @GetMapping("/internal/metrics")
    public ResponseEntity<MetricsService.DashboardMetrics> getMetrics(
            @RequestParam(defaultValue = "30") int lookbackMinutes) {
        return ResponseEntity.ok(metricsService.getDashboardMetrics(lookbackMinutes));
    }

    @GetMapping("/internal/circuit-breakers")
    public ResponseEntity<Map<String, CircuitBreakerManager.CircuitBreakerStatus>> getCircuitBreakers() {
        return ResponseEntity.ok(circuitBreakerManager.getAllStatuses());
    }

    // Manual circuit breaker reset (for testing / ops)
    @PostMapping("/internal/circuit-breakers/{service}/reset")
    public ResponseEntity<?> resetCircuitBreaker(@PathVariable String service) {
        try {
            circuitBreakerManager.getCircuitBreaker(service).reset();
            return ResponseEntity.ok(Map.of("message", "Circuit breaker reset for " + service));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record TokenRequest(String userId, String role) {}
}
