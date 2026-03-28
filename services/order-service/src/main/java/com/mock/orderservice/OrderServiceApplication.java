package com.mock.orderservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

@Slf4j
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private static final List<Map<String, Object>> ORDERS = new ArrayList<>(List.of(
        Map.of("id", "o1", "userId", "u1", "product", "Laptop",  "amount", 999.99, "status", "DELIVERED"),
        Map.of("id", "o2", "userId", "u2", "product", "Phone",   "amount", 599.99, "status", "SHIPPED"),
        Map.of("id", "o3", "userId", "u1", "product", "Monitor", "amount", 349.99, "status", "PROCESSING"),
        Map.of("id", "o4", "userId", "u3", "product", "Keyboard","amount",  89.99, "status", "PENDING")
    ));

    @GetMapping
    public ResponseEntity<?> listOrders(@RequestHeader Map<String, String> headers,
                                         @RequestParam(required = false) String userId) {
        simulateLatency();
        maybeThrowError();
        log.info("GET /api/orders correlationId={}", headers.get("x-correlation-id"));
        List<Map<String, Object>> result = userId == null ? ORDERS
            : ORDERS.stream().filter(o -> userId.equals(o.get("userId"))).toList();
        return ResponseEntity.ok(Map.of("orders", result, "count", result.size(),
            "timestamp", Instant.now().toString(), "service", "order-service"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id, @RequestHeader Map<String, String> headers) {
        simulateLatency();
        log.info("GET /api/orders/{} correlationId={}", id, headers.get("x-correlation-id"));
        return ORDERS.stream().filter(o -> o.get("id").equals(id))
            .findFirst().<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body,
                                          @RequestHeader Map<String, String> headers) {
        simulateLatency();
        log.info("POST /api/orders correlationId={}", headers.get("x-correlation-id"));
        Map<String, Object> order = new HashMap<>(body);
        order.put("id", "o" + (ORDERS.size() + 1));
        order.put("status", "PENDING");
        order.put("createdAt", Instant.now().toString());
        ORDERS.add(order);
        return ResponseEntity.status(201).body(order);
    }

    // Order service simulates higher latency (DB joins etc.)
    private void simulateLatency() {
        try {
            int ms = ThreadLocalRandom.current().nextInt(100) < 15
                ? ThreadLocalRandom.current().nextInt(500, 1200)
                : ThreadLocalRandom.current().nextInt(30, 200);
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    // 8% error rate
    private void maybeThrowError() {
        if (ThreadLocalRandom.current().nextInt(100) < 8) {
            throw new RuntimeException("Order service internal error (simulated)");
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleError(RuntimeException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", e.getMessage(), "service", "order-service"));
    }
}
