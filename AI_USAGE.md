# AI Usage Log

**Project:** API Gateway with Dynamic Rate Limiting & Service Health Dashboard — Java/Spring Boot + React/TypeScript

---

## Tools Used

- **Claude Code** (Anthropic) — used throughout development

---

## How AI Was Used

### Architecture & Design

- Overall system architecture design
- Discussed persistence strategy for request logs (in-memory vs. PostgreSQL); evaluated trade-offs between writing raw per-request rows vs. pre-aggregated counters
- Explored options for making the gateway stateless to support horizontal scaling (JWT-based auth, Redis for rate limit state, Postgres for metrics)
- Discussed sliding window counter pattern in Redis for per-user and per-endpoint rate limiting (`INCR` + TTL on first request, fail-open if Redis is unavailable)
- Reviewed circuit breaker state machine design (`CLOSED → OPEN → HALF_OPEN` transitions), failure-rate threshold evaluation, and the role of the minimum-calls window in avoiding false-positive trips on low-traffic services

### Debugging

- Diagnosed `Cannot find native binding` Docker build failure — platform mismatch between macOS ARM64 host and Linux ARM64 musl Alpine container, caused by copying host `node_modules` into the image instead of installing inside the container
- Diagnosed dashboard time slot buttons (5m / 15m / 30m / 60m) not working — `wsMetrics ?? pollMetrics` always preferred WebSocket data, which carries no `lookback` context, silently ignoring the selected time window
- Diagnosed ClassNotFoundException: com.gateway.ApiGatewayApplication — traced to misplaced .iml files in src/main/ and src/test/ instead of the module root, and a version conflict (spring-boot-starter-websocket:4.1.0-M4 hardcoded in the stale .iml against Spring Boot 3.2.0 in pom.xml)

---

## What I Accepted

- **Fail-open policy for the rate limiter when Redis is unavailable** — AI argued convincingly that silently dropping legitimate traffic due to a Redis outage is a worse failure mode than temporarily allowing excess requests through; agreed, and added explicit logging of every fail-open event for post-incident review
- **Configuration-driven routing via `application.yml`** — keeping route definitions entirely in config means adding a new downstream service requires no code changes; the `ServiceRegistryConfig` abstraction cleanly separates route declarations from proxy execution in `GatewayFilter`
- **PostgreSQL `PERCENTILE_CONT` for latency percentiles** — AI suggested computing p50/p95/p99 in SQL rather than in application memory; agreed once I verified the query plan was using the index on `(service_name, timestamp)` correctly

---

## What I Modified / Rejected

- **Rejected — Resilience4j managed via Spring Boot auto-configuration and annotations** — AI's default suggestion was to use `@CircuitBreaker` annotations and let auto-config wire everything. Rejected: annotation-driven R4j hides the state machine behind framework magic, making it difficult to surface `CLOSED → OPEN → HALF_OPEN` transition events to the dashboard. Went with programmatic instantiation via `CircuitBreakerManager` so every state change is explicitly captured and can be pushed to `MetricsBroadcaster` in real time
- **Modified — metrics aggregation query approach** — AI's first draft fetched raw `request_log` rows and aggregated them in Java for every dashboard refresh. Rewrote to push all aggregation into SQL: `date_trunc('minute', timestamp)` for time bucketing, `PERCENTILE_CONT` for latency, grouped by `service_name`. The AI acknowledged the original approach would degrade as the log table grew; the rewrite also eliminated a class of off-by-one errors in the Java-side bucketing logic
- **Modified — circuit breaker state change push timing** — AI's initial `MetricsBroadcaster` pushed all dashboard updates on a fixed 5-second schedule. Modified so that circuit breaker state transition events trigger an immediate push rather than waiting for the next tick — these are the highest-signal events on the dashboard and a 5-second delay makes them appear to lag behind actual system state
