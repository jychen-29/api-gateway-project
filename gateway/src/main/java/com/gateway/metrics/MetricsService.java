package com.gateway.metrics;

import com.gateway.circuitbreaker.CircuitBreakerManager;
import com.gateway.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final RequestLogRepository requestLogRepository;
    private final CircuitBreakerManager circuitBreakerManager;

    public DashboardMetrics getDashboardMetrics(int lookbackMinutes) {
        Instant since = Instant.now().minus(lookbackMinutes, ChronoUnit.MINUTES);

        List<ServiceMetrics> serviceMetrics = buildServiceMetrics(since);
        List<TimeSeriesBucket> timeSeries = buildTimeSeries(since);
        Map<String, CircuitBreakerManager.CircuitBreakerStatus> cbStatuses = circuitBreakerManager.getAllStatuses();

        return new DashboardMetrics(serviceMetrics, timeSeries, cbStatuses, Instant.now().toString());
    }

    private List<ServiceMetrics> buildServiceMetrics(Instant since) {
        Map<String, ServiceMetrics.Builder> builders = new LinkedHashMap<>();

        // Aggregate counts
        List<Object[]> summaries = requestLogRepository.getServiceSummary(since);
        for (Object[] row : summaries) {
            String service = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long errors = ((Number) row[2]).longValue();
            double avgLatency = row[3] != null ? ((Number) row[3]).doubleValue() : 0;
            builders.put(service, new ServiceMetrics.Builder()
                    .serviceName(service)
                    .totalRequests(total)
                    .errorCount(errors)
                    .avgLatencyMs(avgLatency));
        }

        // Latency percentiles
        List<Object[]> percentiles = requestLogRepository.getLatencyPercentiles(since);
        for (Object[] row : percentiles) {
            String service = (String) row[0];
            if (builders.containsKey(service)) {
                builders.get(service)
                        .p50(row[1] != null ? ((Number) row[1]).doubleValue() : 0)
                        .p95(row[2] != null ? ((Number) row[2]).doubleValue() : 0)
                        .p99(row[3] != null ? ((Number) row[3]).doubleValue() : 0);
            }
        }

        return builders.values().stream().map(ServiceMetrics.Builder::build).toList();
    }

    private List<TimeSeriesBucket> buildTimeSeries(Instant since) {
        List<Object[]> rows = requestLogRepository.getRequestVolumeBuckets(since);
        List<TimeSeriesBucket> buckets = new ArrayList<>();
        for (Object[] row : rows) {
            buckets.add(new TimeSeriesBucket(
                    row[0].toString(),
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()
            ));
        }
        return buckets;
    }

    public record DashboardMetrics(
            List<ServiceMetrics> services,
            List<TimeSeriesBucket> timeSeries,
            Map<String, CircuitBreakerManager.CircuitBreakerStatus> circuitBreakers,
            String generatedAt
    ) {}

    public record TimeSeriesBucket(
            String bucket,
            String serviceName,
            long requestCount,
            long errorCount
    ) {}

    public static class ServiceMetrics {
        public final String serviceName;
        public final long totalRequests;
        public final long errorCount;
        public final double errorRate;
        public final double avgLatencyMs;
        public final double p50;
        public final double p95;
        public final double p99;

        private ServiceMetrics(Builder b) {
            this.serviceName = b.serviceName;
            this.totalRequests = b.totalRequests;
            this.errorCount = b.errorCount;
            this.errorRate = b.totalRequests > 0 ? (double) b.errorCount / b.totalRequests * 100 : 0;
            this.avgLatencyMs = b.avgLatencyMs;
            this.p50 = b.p50;
            this.p95 = b.p95;
            this.p99 = b.p99;
        }

        public static class Builder {
            String serviceName; long totalRequests; long errorCount;
            double avgLatencyMs; double p50; double p95; double p99;
            public Builder serviceName(String v) { serviceName = v; return this; }
            public Builder totalRequests(long v) { totalRequests = v; return this; }
            public Builder errorCount(long v) { errorCount = v; return this; }
            public Builder avgLatencyMs(double v) { avgLatencyMs = v; return this; }
            public Builder p50(double v) { p50 = v; return this; }
            public Builder p95(double v) { p95 = v; return this; }
            public Builder p99(double v) { p99 = v; return this; }
            public ServiceMetrics build() { return new ServiceMetrics(this); }
        }
    }
}
