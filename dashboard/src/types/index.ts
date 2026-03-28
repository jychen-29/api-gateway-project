export interface ServiceMetrics {
  serviceName: string;
  totalRequests: number;
  errorCount: number;
  errorRate: number;
  avgLatencyMs: number;
  p50: number;
  p95: number;
  p99: number;
}

export interface TimeSeriesBucket {
  bucket: string;
  serviceName: string;
  requestCount: number;
  errorCount: number;
}

export interface CircuitBreakerStatus {
  serviceName: string;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' | 'DISABLED' | 'METRICS_ONLY';
  failureRate: number;
  failedCalls: number;
  successfulCalls: number;
  notPermittedCalls: number;
}

export interface DashboardMetrics {
  services: ServiceMetrics[];
  timeSeries: TimeSeriesBucket[];
  circuitBreakers: Record<string, CircuitBreakerStatus>;
  generatedAt: string;
}

export interface CircuitBreakerEvent {
  service: string;
  state: string;
  timestamp: string;
}
