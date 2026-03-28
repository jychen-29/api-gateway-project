import React, { useState, useMemo } from 'react';
import { CircuitBreakerCard } from './components/CircuitBreakerCard';
import { ServiceMetricsTable } from './components/ServiceMetricsTable';
import { RequestVolumeChart } from './components/RequestVolumeChart';
import { ErrorRateChart } from './components/ErrorRateChart';
import { LatencyChart } from './components/LatencyChart';
import { CBEventFeed } from './components/CBEventFeed';
import { StatCard } from './components/StatCard';
import { useWebSocket } from './hooks/useWebSocket';
import { useMetricsPoll } from './hooks/useMetricsPoll';
import { DashboardMetrics } from './types';

type LookbackOption = 5 | 15 | 30 | 60;

const App: React.FC = () => {
  const [lookback, setLookback] = useState<LookbackOption>(30);
  const { metrics: wsMetrics, cbEvents, connected } = useWebSocket();
  const { metrics: pollMetrics, loading, error, refresh } = useMetricsPoll(15000, lookback);

  // Prefer WebSocket data; fall back to polling
  const metrics: DashboardMetrics | null = wsMetrics ?? pollMetrics;

  const kpis = useMemo(() => {
    if (!metrics) return null;
    const total = metrics.services.reduce((s, m) => s + m.totalRequests, 0);
    const errors = metrics.services.reduce((s, m) => s + m.errorCount, 0);
    const avgLatency = metrics.services.length > 0
      ? metrics.services.reduce((s, m) => s + m.avgLatencyMs, 0) / metrics.services.length
      : 0;
    const errorRate = total > 0 ? (errors / total) * 100 : 0;
    const openCBs = Object.values(metrics.circuitBreakers).filter(cb => cb.state === 'OPEN').length;
    return { total, errors, errorRate, avgLatency, openCBs };
  }, [metrics]);

  return (
    <div className="min-h-screen bg-slate-950 text-white font-sans">
      {/* Top bar */}
      <header className="border-b border-white/10 bg-slate-950/80 backdrop-blur-md sticky top-0 z-10">
        <div className="max-w-screen-2xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-indigo-500/20 flex items-center justify-center text-lg">⚡</div>
            <div>
              <h1 className="text-base font-bold text-white">API Gateway</h1>
              <p className="text-xs text-white/30">Service Health Dashboard</p>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {/* Connection indicator */}
            <div className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${connected ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'}`} />
              <span className="text-xs text-white/40">{connected ? 'Live' : 'Polling'}</span>
            </div>

            {/* Lookback selector */}
            <div className="flex items-center gap-1 bg-white/5 rounded-lg p-1">
              {([5, 15, 30, 60] as LookbackOption[]).map((opt) => (
                <button
                  key={opt}
                  onClick={() => setLookback(opt)}
                  className={`px-3 py-1 rounded-md text-xs font-medium transition-all ${
                    lookback === opt
                      ? 'bg-indigo-500 text-white shadow'
                      : 'text-white/40 hover:text-white/70'
                  }`}
                >
                  {opt}m
                </button>
              ))}
            </div>

            <button
              onClick={refresh}
              className="text-xs px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-white/50 hover:text-white transition-all"
            >
              ↻ Refresh
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-screen-2xl mx-auto px-6 py-6 space-y-6">
        {/* Error banner */}
        {error && (
          <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">
            ⚠️ Failed to fetch metrics: {error}
          </div>
        )}

        {/* Loading skeleton */}
        {loading && !metrics && (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-24 rounded-xl bg-white/5 animate-pulse" />
            ))}
          </div>
        )}

        {metrics && (
          <>
            {/* KPI cards */}
            <section className="grid grid-cols-2 md:grid-cols-5 gap-4">
              <StatCard
                label="Total Requests"
                value={kpis!.total.toLocaleString()}
                sub={`last ${lookback}m`}
                color="default"
              />
              <StatCard
                label="Total Errors"
                value={kpis!.errors.toLocaleString()}
                color={kpis!.errors > 0 ? 'red' : 'green'}
              />
              <StatCard
                label="Error Rate"
                value={`${kpis!.errorRate.toFixed(1)}%`}
                color={kpis!.errorRate === 0 ? 'green' : kpis!.errorRate < 5 ? 'amber' : 'red'}
                trend={kpis!.errorRate === 0 ? 'neutral' : kpis!.errorRate < 5 ? 'neutral' : 'down'}
              />
              <StatCard
                label="Avg Latency"
                value={`${kpis!.avgLatency.toFixed(0)}ms`}
                color={kpis!.avgLatency < 100 ? 'green' : kpis!.avgLatency < 300 ? 'amber' : 'red'}
              />
              <StatCard
                label="Open Circuit Breakers"
                value={kpis!.openCBs}
                color={kpis!.openCBs === 0 ? 'green' : 'red'}
                sub={kpis!.openCBs === 0 ? 'All healthy' : `${kpis!.openCBs} service(s) blocked`}
              />
            </section>

            {/* Charts row */}
            <section className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <div className="rounded-xl border border-white/10 bg-white/[0.03] p-5">
                <h2 className="text-sm font-semibold text-white/70 mb-4">Request Volume</h2>
                <RequestVolumeChart timeSeries={metrics.timeSeries} />
              </div>
              <div className="rounded-xl border border-white/10 bg-white/[0.03] p-5">
                <h2 className="text-sm font-semibold text-white/70 mb-4">Error Rate (%)</h2>
                <ErrorRateChart timeSeries={metrics.timeSeries} />
              </div>
            </section>

            {/* Latency + Circuit Breakers */}
            <section className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              <div className="lg:col-span-2 rounded-xl border border-white/10 bg-white/[0.03] p-5">
                <h2 className="text-sm font-semibold text-white/70 mb-4">Latency Percentiles</h2>
                <LatencyChart services={metrics.services} />
              </div>
              <div className="rounded-xl border border-white/10 bg-white/[0.03] p-5">
                <h2 className="text-sm font-semibold text-white/70 mb-4">Circuit Breaker Events</h2>
                <CBEventFeed events={cbEvents} />
              </div>
            </section>

            {/* Circuit Breaker status cards */}
            <section>
              <h2 className="text-sm font-semibold text-white/40 uppercase tracking-wider mb-3">
                Circuit Breaker States
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {Object.entries(metrics.circuitBreakers).map(([service, status]) => (
                  <CircuitBreakerCard
                    key={service}
                    service={service}
                    status={status}
                    onReset={refresh}
                  />
                ))}
              </div>
            </section>

            {/* Full service metrics table */}
            <section>
              <h2 className="text-sm font-semibold text-white/40 uppercase tracking-wider mb-3">
                Service Breakdown — last {lookback}m
              </h2>
              <div className="rounded-xl border border-white/10 bg-white/[0.03] overflow-hidden">
                <ServiceMetricsTable services={metrics.services} />
              </div>
            </section>

            {/* Footer */}
            <footer className="text-center text-xs text-white/20 pb-4">
              Last updated: {new Date(metrics.generatedAt).toLocaleTimeString()} ·
              Updates every {connected ? '5s (WebSocket)' : '15s (polling)'}
            </footer>
          </>
        )}
      </main>
    </div>
  );
};

export default App;
