package com.gateway.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "circuit_breaker_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CircuitBreakerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "event_type", nullable = false)
    private String eventType; // OPEN, CLOSED, HALF_OPEN

    @Column(name = "failure_count")
    private Integer failureCount;

    private String details;
}
