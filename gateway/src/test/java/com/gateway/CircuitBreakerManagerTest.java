package com.gateway;

import com.gateway.circuitbreaker.CircuitBreakerManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CircuitBreakerManagerTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerManager manager;

    @BeforeEach
    void setUp() {
        // Use a fast config for testing: open after 3 failures in a window of 5 calls
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry = CircuitBreakerRegistry.of(config);

        // Pre-register all three services so the manager can find them
        registry.circuitBreaker("user-service");
        registry.circuitBreaker("order-service");
        registry.circuitBreaker("product-service");

        var cbEventRepo = mock(com.gateway.repository.CircuitBreakerEventRepository.class);
        var messaging  = mock(SimpMessagingTemplate.class);
        manager = new CircuitBreakerManager(registry, cbEventRepo, messaging);
        manager.registerStateListeners();
    }

    @Test
    void circuitStartsClosed() {
        CircuitBreaker cb = manager.getCircuitBreaker("user-service");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitOpensAfterConsecutiveFailures() {
        CircuitBreaker cb = manager.getCircuitBreaker("order-service");

        // Record enough failures to exceed the threshold
        for (int i = 0; i < 4; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("boom"));
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void getAllStatusesReturnsAllServices() {
        Map<String, CircuitBreakerManager.CircuitBreakerStatus> statuses = manager.getAllStatuses();
        assertThat(statuses).containsKeys("user-service", "order-service", "product-service");
    }

    @Test
    void circuitResetsToClosedManually() {
        CircuitBreaker cb = manager.getCircuitBreaker("product-service");

        // Force it open
        for (int i = 0; i < 4; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("fail"));
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Reset
        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitTransitionsToHalfOpenAfterWait() throws Exception {
        CircuitBreakerConfig fastConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofMillis(50))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(fastConfig).circuitBreaker("test-cb");
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e1"));
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e2"));
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e3"));

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(200); // wait for auto transition
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }
}
