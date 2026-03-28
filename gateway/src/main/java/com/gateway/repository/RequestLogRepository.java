package com.gateway.repository;

import com.gateway.model.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    @Query("""
        SELECT r FROM RequestLog r
        WHERE r.timestamp >= :since
        ORDER BY r.timestamp DESC
    """)
    List<RequestLog> findSince(@Param("since") Instant since);

    @Query("""
        SELECT r.serviceName,
               COUNT(r) as total,
               SUM(CASE WHEN r.statusCode >= 500 THEN 1 ELSE 0 END) as errors,
               AVG(r.latencyMs) as avgLatency
        FROM RequestLog r
        WHERE r.timestamp >= :since
        GROUP BY r.serviceName
    """)
    List<Object[]> getServiceSummary(@Param("since") Instant since);

    @Query(value = """
        SELECT
            date_trunc('minute', timestamp) as bucket,
            service_name,
            COUNT(*) as request_count,
            SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END) as error_count
        FROM request_logs
        WHERE timestamp >= :since
        GROUP BY bucket, service_name
        ORDER BY bucket ASC
    """, nativeQuery = true)
    List<Object[]> getRequestVolumeBuckets(@Param("since") Instant since);

    @Query(value = """
        SELECT
            service_name,
            PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms) as p50,
            PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms) as p95,
            PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms) as p99
        FROM request_logs
        WHERE timestamp >= :since
        GROUP BY service_name
    """, nativeQuery = true)
    List<Object[]> getLatencyPercentiles(@Param("since") Instant since);
}
