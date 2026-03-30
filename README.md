# API Gateway with Dynamic Rate Limiting & Service Health Dashboard

A production-grade API gateway built with **Java/Spring Boot** (backend) and **React/TypeScript** (frontend), featuring JWT authentication, Redis-backed rate limiting, circuit breaker pattern, correlation ID tracing, and a real-time WebSocket dashboard.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client / Browser                         │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTP / WebSocket
┌────────────────────▼────────────────────────────────────────────┐
│                   React Dashboard (:3000)                       │
│  Request Volume │ Error Rate │ Latency p50/p95/p99 │ CB States  │
└────────────────────┬────────────────────────────────────────────┘
                     │ REST + WebSocket
┌────────────────────▼────────────────────────────────────────────┐
│                  Spring Boot Gateway (:8080)                    │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌───────────┐    │
│  │  JWT     │  │  Rate    │  │   Circuit    │  │  Request  │    │
│  │  Auth    │→ │  Limiter │→ │   Breaker    │→ │   Proxy   │    │
│  │          │  │ (Redis)  │  │  (R4j)       │  │           │    │
│  └──────────┘  └──────────┘  └──────────────┘  └─────┬─────┘    │
│                                                        │        │
│  ┌──────────────┐  ┌────────────────────────────────┐  │        │
│  │ Correlation  │  │  Metrics Logger (PostgreSQL)   │  │        │
│  │  ID Tracker  │  │  + WebSocket Broadcaster       │  │        │
│  └──────────────┘  └────────────────────────────────┘  │        │
└────────────────────────────────────────────────────────┼────────┘
                              ┌──────────────────────────┘
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────────┐
        │  User    │   │  Order   │   │   Product    │
        │ Service  │   │ Service  │   │   Service    │
        │  :8081   │   │  :8082   │   │    :8083     │
        └──────────┘   └──────────┘   └──────────────┘

Supporting services: PostgreSQL (:5432) · Redis (:6379)
```

---

## Features

### Gateway Core
- **JWT Authentication** — every request to `/api/**` is validated at the gateway; downstream services receive `X-User-ID` and `X-Forwarded-By` headers
- **Dynamic Rate Limiting** — per-user and per-endpoint sliding window counters backed by Redis; configurable limits per endpoint; fails open if Redis is unavailable
- **Circuit Breaker** — Resilience4j wraps each downstream call; configurable failure-rate threshold, sliding window, cooldown; automatic OPEN → HALF_OPEN → CLOSED transitions; manual reset via API
- **Correlation IDs** — every request gets a `X-Correlation-ID` (generated or forwarded from client) that flows to all downstream services and appears in every log line
- **Request/Response Logging** — full structured logs in PostgreSQL: method, path, service, status, latency, userId, error messages
- **Extensible Routing** — add new services by adding a block to `application.yml`; no code changes required

### Dashboard
- **Real-time updates** — WebSocket (STOMP over SockJS) pushes metrics every 5 seconds; falls back to HTTP polling
- **Request volume** — area chart per service per minute
- **Error rates** — line chart with 5% threshold reference line
- **Latency percentiles** — p50 / p95 / p99 grouped bar chart per service
- **Circuit breaker panel** — live state (CLOSED / OPEN / HALF_OPEN), failure rate, blocked calls; one-click reset
- **KPI cards** — total requests, error count, error rate, avg latency, open circuit breakers
- **Lookback window** — 5m / 15m / 30m / 60m selector

---

## Project Structure

```
api-gateway-project/
├── docker-compose.yml
├── docker/
│   └── init.sql                        # PostgreSQL schema
├── gateway/                            # Spring Boot API Gateway
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/gateway/
│       ├── ApiGatewayApplication.java
│       ├── config/
│       │   ├── AppConfig.java          # RestTemplate, Filter registration
│       │   ├── ServiceRegistryConfig.java  # Dynamic service route config
│       │   └── WebSocketConfig.java    # STOMP/SockJS setup
│       ├── security/
│       │   └── JwtUtil.java            # JWT generation & validation
│       ├── ratelimit/
│       │   └── RateLimiterService.java # Redis sliding window rate limiter
│       ├── circuitbreaker/
│       │   └── CircuitBreakerManager.java  # R4j wrapper + state event logger
│       ├── filter/
│       │   └── GatewayFilter.java      # Core gateway pipeline filter
│       ├── metrics/
│       │   ├── MetricsService.java     # Aggregates DB data for dashboard
│       │   └── MetricsBroadcaster.java # Scheduled WebSocket push
│       ├── model/
│       │   ├── RequestLog.java
│       │   └── CircuitBreakerEvent.java
│       ├── repository/
│       │   ├── RequestLogRepository.java
│       │   └── CircuitBreakerEventRepository.java
│       └── controller/
│           └── GatewayController.java  # /auth/token, /internal/metrics, /internal/circuit-breakers
├── services/
│   ├── user-service/                   # Mock user microservice (:8081)
│   ├── order-service/                  # Mock order microservice (:8082)
│   └── product-service/               # Mock product microservice (:8083)
└── dashboard/                          # React/TypeScript frontend
    ├── Dockerfile
    ├── nginx.conf
    ├── src/
    │   ├── api/client.ts               # Axios API calls
    │   ├── hooks/
    │   │   ├── useWebSocket.ts         # STOMP WebSocket hook
    │   │   └── useMetricsPoll.ts       # HTTP polling fallback
    │   ├── components/
    │   │   ├── StatCard.tsx            # KPI card
    │   │   ├── CircuitBreakerCard.tsx  # CB state card with reset
    │   │   ├── ServiceMetricsTable.tsx # Per-service breakdown table
    │   │   ├── RequestVolumeChart.tsx  # Recharts AreaChart
    │   │   ├── ErrorRateChart.tsx      # Recharts LineChart
    │   │   ├── LatencyChart.tsx        # Recharts BarChart (p50/95/99)
    │   │   └── CBEventFeed.tsx         # Live CB event log
    │   ├── types/index.ts
    │   └── App.tsx                     # Main layout
    └── ...config files
```

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development without Docker)
- Node 20+ (for local dashboard development)

### Run Everything with Docker Compose

```bash
git clone <repo>
cd api-gateway-project
docker compose up --build
```

| Service   | URL                          |
|-----------|------------------------------|
| Dashboard | http://localhost:3000        |
| Gateway   | http://localhost:8080        |
| User Svc  | http://localhost:8081        |
| Order Svc | http://localhost:8082        |
| Product Svc | http://localhost:8083      |

---

## API Usage

### 1. Get a JWT Token

```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"userId": "alice", "role": "USER"}'
```

Response:
```json
{
  "token": "eyJhbGci...",
  "userId": "alice",
  "role": "USER"
}
```

### 2. Proxy Requests Through the Gateway

```bash
export TOKEN="eyJhbGci..."

# List users
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN"

# List orders
curl http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN"

# List products
curl http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN"

# With your own correlation ID
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-ID: my-trace-id-123"
```

### 3. Dashboard Metrics API

```bash
# Last 30 minutes of metrics
curl http://localhost:8080/internal/metrics?lookbackMinutes=30

# Circuit breaker states
curl http://localhost:8080/internal/circuit-breakers

# Reset a circuit breaker
curl -X POST http://localhost:8080/internal/circuit-breakers/order-service/reset
```

---

## Design Decisions

### How is the gateway extensible?

New routes are added **purely in configuration** — no code changes needed:

```yaml
# application.yml
services:
  my-new-service:
    url: http://my-new-service:9000
    path-prefix: /api/new-resource
```

New middleware (e.g., request transformation, header injection) can be added as a `javax.servlet.Filter` bean registered before or after `GatewayFilter`. The pipeline is a standard servlet filter chain.

### Circuit Breaker State Machine

```
         failures ≥ threshold
CLOSED ─────────────────────────► OPEN
  ▲                                  │
  │  probe succeeds                  │ wait-duration elapsed
  │                                  ▼
  └──────────────────────────── HALF_OPEN
         probe fails → back to OPEN
```

Thresholds (configurable per service in `application.yml`):
- **Failure rate**: 50% within a sliding window of 10 calls
- **Slow call rate**: 80% of calls taking > 3 seconds
- **Minimum calls**: 5 before the rate is evaluated
- **Cooldown**: 30 seconds before transitioning to HALF_OPEN
- **Probe calls**: 3 allowed in HALF_OPEN before deciding

### Rate Limiting

Uses a **sliding window counter** pattern in Redis:

```
Key:   rl:user:{userId}   or   rl:endpoint:{path-prefix}
Value: integer counter
TTL:   window-seconds (60s)
```

On each request:
1. `INCR` the key — atomic, returns new count
2. If count == 1, set TTL (first request in window)
3. If count > limit → 429 Too Many Requests

Fails **open** if Redis is unavailable (request is allowed through).

### Metrics Aggregation

Metrics are written to PostgreSQL as individual rows per request. The dashboard queries use:
- `date_trunc('minute', timestamp)` for time bucketing
- `PERCENTILE_CONT` for p50/p95/p99 latency
- Grouped aggregations per `service_name`

To avoid overwhelming the DB at scale, this could be extended with:
1. **Async batch writes** (buffer in-memory, flush every N seconds)
2. **TimescaleDB** for automatic time-series partitioning
3. **Redis counters** for hot metrics with periodic DB flush

### Single Point of Failure

The gateway itself is stateless (auth via JWT, rate limiting state in Redis, metrics in Postgres). To eliminate the SPOF:
1. Run multiple gateway instances behind a **load balancer** (e.g., nginx, AWS ALB)
2. Redis can be deployed as a **Redis Cluster** or **Redis Sentinel**
3. Use **Kubernetes HPA** to auto-scale gateway replicas

---

## Running Tests

```bash
cd gateway
./mvnw test
```

Test coverage includes:
- `RateLimiterServiceTest` — allow/block/fail-open behavior, Redis TTL expiry
- `JwtUtilTest` — token generation, validation, expiry, tamper detection
- `CircuitBreakerManagerTest` — state transitions (CLOSED → OPEN → HALF_OPEN), manual reset
- `GatewayFilterIntegrationTest` — full filter pipeline via MockMvc: 401 on missing/invalid JWT, 404 for unknown routes, correlation ID forwarding, downstream proxy

---

## Load Testing (Optional)

Use [k6](https://k6.io) to stress-test rate limiting and circuit breaker behavior:

```bash
# Install k6, then:
k6 run - <<'EOF'
import http from 'k6/http';
import { check } from 'k6';

const TOKEN = __ENV.TOKEN;

export let options = {
  vus: 50,
  duration: '60s',
};

export default function () {
  const services = ['/api/users', '/api/orders', '/api/products'];
  const path = services[Math.floor(Math.random() * services.length)];
  const res = http.get(`http://localhost:8080${path}`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(res, {
    'status is 200 or 429 or 503': (r) => [200, 429, 503].includes(r.status),
    'has correlation id': (r) => !!r.headers['X-Correlation-Id'],
  });
}
EOF
```

Watch the dashboard as circuit breakers trip and rate limits kick in in real time.

---

## Configuration Reference

| Key | Default | Description |
|-----|---------|-------------|
| `jwt.secret` | *(required)* | HMAC-SHA256 signing key (min 32 chars) |
| `jwt.expiration-ms` | `86400000` | Token TTL in milliseconds |
| `rate-limit.default-user-limit` | `100` | Max requests/minute per user |
| `rate-limit.window-seconds` | `60` | Rate limit window size |
| `resilience4j.circuitbreaker.instances.*.failure-rate-threshold` | `50` | % failures to open circuit |
| `resilience4j.circuitbreaker.instances.*.wait-duration-in-open-state` | `30s` | Cooldown before HALF_OPEN |
| `resilience4j.circuitbreaker.instances.*.sliding-window-size` | `10` | Calls evaluated in window |
