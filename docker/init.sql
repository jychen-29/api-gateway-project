CREATE TABLE IF NOT EXISTS request_logs (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(36) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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

CREATE INDEX idx_request_logs_timestamp ON request_logs(timestamp);
CREATE INDEX idx_request_logs_service ON request_logs(service_name);
CREATE INDEX idx_request_logs_correlation ON request_logs(correlation_id);

CREATE TABLE IF NOT EXISTS circuit_breaker_events (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- OPEN, CLOSE, HALF_OPEN
    failure_count INT,
    details TEXT
);

CREATE INDEX idx_cb_events_service ON circuit_breaker_events(service_name);
CREATE INDEX idx_cb_events_timestamp ON circuit_breaker_events(timestamp);

CREATE TABLE IF NOT EXISTS rate_limit_violations (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id VARCHAR(100),
    endpoint VARCHAR(500),
    limit_type VARCHAR(50) -- USER or ENDPOINT
);
