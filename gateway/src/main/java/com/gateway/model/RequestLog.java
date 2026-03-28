package com.gateway.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "request_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private Instant timestamp;

    private String method;
    private String path;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "request_size_bytes")
    private Integer requestSizeBytes;

    @Column(name = "response_size_bytes")
    private Integer responseSizeBytes;
}
