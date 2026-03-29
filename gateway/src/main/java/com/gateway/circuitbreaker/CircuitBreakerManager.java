package com.gateway.circuitbreaker;

import com.gateway.model.CircuitBreakerEvent;
import com.gateway.repository.CircuitBreakerEventRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerManager {

    private final CircuitBreakerRegistry registry;
    private final CircuitBreakerEventRepository eventRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final List<String> SERVICES = List.of("user-service", "order-service", "product-service");

    @PostConstruct
    public void registerStateListeners() {
        for (String service : SERVICES) {
            CircuitBreaker cb = registry.circuitBreaker(service);
            cb.getEventPublisher()
                    .onStateTransition(event -> onStateTransition(service, event));
            log.info("Registered circuit breaker for service: {}", service);
        }
    }

    private void onStateTransition(String service, CircuitBreakerOnStateTransitionEvent event) {
        String newState = event.getStateTransition().getToState().name();
        log.warn("Circuit breaker state transition: service={}, newState={}", service, newState);

        CircuitBreakerEvent cbEvent = CircuitBreakerEvent.builder()
                .timestamp(Instant.now())
                .serviceName(service)
                .eventType(newState)
                .details("Transitioned from " + event.getStateTransition().getFromState().name() + " to " + newState)
                .build();
        eventRepository.save(cbEvent);

        // Push real-time notification via WebSocket
        messagingTemplate.convertAndSend("/topic/circuit-breaker", Map.of(
                "service", service,
                "state", newState,
                "timestamp", Instant.now().toString()
        ));
    }

    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return registry.circuitBreaker(serviceName);
    }

    public Map<String, CircuitBreakerStatus> getAllStatuses() {
        Map<String, CircuitBreakerStatus> statuses = new HashMap<>();
        for (String service : SERVICES) {
            CircuitBreaker cb = registry.circuitBreaker(service);
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            statuses.put(service, new CircuitBreakerStatus(
                    service,
                    cb.getState().name(),
                    metrics.getFailureRate(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfSuccessfulCalls(),
                    (int) metrics.getNumberOfNotPermittedCalls()
            ));
        }
        return statuses;
    }

    public record CircuitBreakerStatus(
            String serviceName,
            String state,
            float failureRate,
            int failedCalls,
            int successfulCalls,
            int notPermittedCalls
    ) {}
}
