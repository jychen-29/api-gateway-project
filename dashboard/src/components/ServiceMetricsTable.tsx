import React from 'react';
import { ServiceMetrics } from '../types';

interface Props {
  services: ServiceMetrics[];
}

const fmt = (n: number, decimals = 0) =>
  isNaN(n) || n === 0 ? '—' : n.toFixed(decimals);

const LatencyBadge: React.FC<{ ms: number }> = ({ ms }) => {
  const color =
    ms === 0 ? 'text-white/30' :
    ms < 100 ? 'text-emerald-400' :
    ms < 300 ? 'text-amber-400' : 'text-red-400';
  return <span className={`font-mono text-sm ${color}`}>{ms === 0 ? '—' : `${fmt(ms, 0)}ms`}</span>;
};

const ErrorRateBadge: React.FC<{ rate: number }> = ({ rate }) => {
  const color = rate === 0 ? 'text-emerald-400' : rate < 5 ? 'text-amber-400' : 'text-red-400';
  const bg    = rate === 0 ? 'bg-emerald-400/10' : rate < 5 ? 'bg-amber-400/10' : 'bg-red-400/10';
  return (
    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${color} ${bg}`}>
      {rate === 0 ? '0%' : `${fmt(rate, 1)}%`}
    </span>
  );
};

export const ServiceMetricsTable: React.FC<Props> = ({ services }) => {
  if (!services.length) {
    return (
      <div className="text-center py-12 text-white/30 text-sm">
        No service data yet — send some requests through the gateway.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10 text-white/40 text-xs uppercase tracking-wider">
            <th className="text-left py-3 px-4">Service</th>
            <th className="text-right py-3 px-4">Requests</th>
            <th className="text-right py-3 px-4">Errors</th>
            <th className="text-right py-3 px-4">Error Rate</th>
            <th className="text-right py-3 px-4">Avg</th>
            <th className="text-right py-3 px-4">p50</th>
            <th className="text-right py-3 px-4">p95</th>
            <th className="text-right py-3 px-4">p99</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {services.map((s) => (
            <tr key={s.serviceName} className="hover:bg-white/[0.03] transition-colors group">
              <td className="py-3 px-4">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-indigo-400 group-hover:bg-indigo-300 transition-colors" />
                  <span className="font-medium text-white">{s.serviceName}</span>
                </div>
              </td>
              <td className="py-3 px-4 text-right font-mono text-white/70">
                {s.totalRequests.toLocaleString()}
              </td>
              <td className="py-3 px-4 text-right font-mono text-white/50">
                {s.errorCount.toLocaleString()}
              </td>
              <td className="py-3 px-4 text-right">
                <ErrorRateBadge rate={s.errorRate} />
              </td>
              <td className="py-3 px-4 text-right"><LatencyBadge ms={s.avgLatencyMs} /></td>
              <td className="py-3 px-4 text-right"><LatencyBadge ms={s.p50} /></td>
              <td className="py-3 px-4 text-right"><LatencyBadge ms={s.p95} /></td>
              <td className="py-3 px-4 text-right"><LatencyBadge ms={s.p99} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
