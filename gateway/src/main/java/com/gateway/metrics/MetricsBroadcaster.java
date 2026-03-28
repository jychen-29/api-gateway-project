package com.gateway.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsBroadcaster {

    private final MetricsService metricsService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 5000) // every 5 seconds
    public void broadcastMetrics() {
        try {
            MetricsService.DashboardMetrics metrics = metricsService.getDashboardMetrics(30);
            messagingTemplate.convertAndSend("/topic/metrics", metrics);
        } catch (Exception e) {
            log.warn("Failed to broadcast metrics: {}", e.getMessage());
        }
    }
}
