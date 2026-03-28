package com.mock.userservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

@Slf4j
@RestController
@RequestMapping("/api/users")
class UserController {

    private static final List<Map<String, Object>> USERS = List.of(
        Map.of("id", "u1", "name", "Alice Johnson", "email", "alice@example.com", "role", "admin"),
        Map.of("id", "u2", "name", "Bob Smith",    "email", "bob@example.com",   "role", "user"),
        Map.of("id", "u3", "name", "Carol White",  "email", "carol@example.com", "role", "user")
    );

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestHeader Map<String, String> headers) {
        simulateLatency();
        maybeThrowError();
        log.info("GET /api/users correlationId={}", headers.get("x-correlation-id"));
        return ResponseEntity.ok(Map.of(
            "users", USERS,
            "count", USERS.size(),
            "timestamp", Instant.now().toString(),
            "service", "user-service"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id, @RequestHeader Map<String, String> headers) {
        simulateLatency();
        log.info("GET /api/users/{} correlationId={}", id, headers.get("x-correlation-id"));
        return USERS.stream()
            .filter(u -> u.get("id").equals(id))
            .findFirst()
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body,
                                         @RequestHeader Map<String, String> headers) {
        simulateLatency();
        log.info("POST /api/users correlationId={}", headers.get("x-correlation-id"));
        Map<String, Object> user = new HashMap<>(body);
        user.put("id", UUID.randomUUID().toString());
        user.put("createdAt", Instant.now().toString());
        return ResponseEntity.status(201).body(user);
    }

    // Simulate realistic latency: 10-150ms, occasionally 300-800ms (slow)
    private void simulateLatency() {
        try {
            int ms = ThreadLocalRandom.current().nextInt(100) < 10
                ? ThreadLocalRandom.current().nextInt(300, 800)
                : ThreadLocalRandom.current().nextInt(10, 150);
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    // 5% error rate
    private void maybeThrowError() {
        if (ThreadLocalRandom.current().nextInt(100) < 5) {
            throw new RuntimeException("User service internal error (simulated)");
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleError(RuntimeException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", e.getMessage(), "service", "user-service"));
    }
}
