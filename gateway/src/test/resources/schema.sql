CREATE TABLE IF NOT EXISTS request_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    correlation_id VARCHAR(36) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    method VARCHAR(10),
    path VARCHAR(500),
    service_name VARCHAR(100),
    status_code INT,
    latency_ms BIGINT,
    user_id VARCHAR(100),
    error_message TEXT,
    request_size_bytes INT,
    response_size_bytes INT
);

CREATE TABLE IF NOT EXISTS circuit_breaker_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    failure_count INT,
    details TEXT
);

CREATE TABLE IF NOT EXISTS rate_limit_violations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(100),
    endpoint VARCHAR(500),
    limit_type VARCHAR(50)
);
