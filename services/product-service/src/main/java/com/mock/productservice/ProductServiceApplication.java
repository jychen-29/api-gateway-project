package com.mock.productservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}

@Slf4j
@RestController
@RequestMapping("/api/products")
class ProductController {

    private static final List<Map<String, Object>> PRODUCTS = List.of(
        Map.of("id", "p1", "name", "Laptop Pro",      "price", 999.99,  "stock", 42,  "category", "Electronics"),
        Map.of("id", "p2", "name", "Smartphone X",    "price", 599.99,  "stock", 120, "category", "Electronics"),
        Map.of("id", "p3", "name", "4K Monitor",      "price", 349.99,  "stock", 30,  "category", "Electronics"),
        Map.of("id", "p4", "name", "Mechanical Keyboard","price", 89.99, "stock", 75,  "category", "Accessories"),
        Map.of("id", "p5", "name", "USB-C Hub",       "price", 39.99,   "stock", 200, "category", "Accessories")
    );

    @GetMapping
    public ResponseEntity<?> listProducts(@RequestHeader Map<String, String> headers,
                                           @RequestParam(required = false) String category) {
        simulateLatency();
        maybeThrowError();
        log.info("GET /api/products correlationId={}", headers.get("x-correlation-id"));
        List<Map<String, Object>> result = category == null ? PRODUCTS
            : PRODUCTS.stream().filter(p -> category.equals(p.get("category"))).toList();
        return ResponseEntity.ok(Map.of("products", result, "count", result.size(),
            "timestamp", Instant.now().toString(), "service", "product-service"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable String id, @RequestHeader Map<String, String> headers) {
        simulateLatency();
        log.info("GET /api/products/{} correlationId={}", id, headers.get("x-correlation-id"));
        return PRODUCTS.stream().filter(p -> p.get("id").equals(id))
            .findFirst().<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // Product service is the fastest - mostly reads from cache
    private void simulateLatency() {
        try {
            int ms = ThreadLocalRandom.current().nextInt(100) < 5
                ? ThreadLocalRandom.current().nextInt(200, 500)
                : ThreadLocalRandom.current().nextInt(5, 80);
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    // 3% error rate - most stable service
    private void maybeThrowError() {
        if (ThreadLocalRandom.current().nextInt(100) < 3) {
            throw new RuntimeException("Product service internal error (simulated)");
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleError(RuntimeException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", e.getMessage(), "service", "product-service"));
    }
}
