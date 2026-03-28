import React, { useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { ServiceMetrics } from '../types';

interface Props {
  services: ServiceMetrics[];
}

const CustomTooltip: React.FC<any> = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-slate-900 border border-white/10 rounded-xl p-3 shadow-xl text-xs">
      <p className="text-white/70 font-semibold mb-2">{label}</p>
      {payload.map((p: any) => (
        <div key={p.name} className="flex items-center gap-2 mb-1">
          <span className="w-2 h-2 rounded-full" style={{ background: p.fill }} />
          <span className="text-white/60">{p.name}:</span>
          <span className="text-white font-mono">{p.value.toFixed(0)}ms</span>
        </div>
      ))}
    </div>
  );
};

export const LatencyChart: React.FC<Props> = ({ services }) => {
  const chartData = useMemo(() =>
    services
      .filter((s) => s.p50 > 0 || s.p95 > 0 || s.p99 > 0)
      .map((s) => ({
        name: s.serviceName.replace('-service', ''),
        p50: Math.round(s.p50),
        p95: Math.round(s.p95),
        p99: Math.round(s.p99),
      })),
    [services]
  );

  if (!chartData.length) {
    return (
      <div className="h-48 flex items-center justify-center text-white/30 text-sm">
        No latency data yet
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={chartData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }} barCategoryGap="30%">
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis dataKey="name" tick={{ fontSize: 11, fill: 'rgba(255,255,255,0.5)' }} axisLine={false} tickLine={false} />
        <YAxis tickFormatter={(v) => `${v}ms`} tick={{ fontSize: 10, fill: 'rgba(255,255,255,0.3)' }} axisLine={false} tickLine={false} />
        <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(255,255,255,0.04)' }} />
        <Legend formatter={(v) => <span className="text-xs text-white/50">{v}</span>} iconType="circle" iconSize={6} />
        <Bar dataKey="p50" name="p50" fill="#34d399" radius={[4, 4, 0, 0]} />
        <Bar dataKey="p95" name="p95" fill="#fb923c" radius={[4, 4, 0, 0]} />
        <Bar dataKey="p99" name="p99" fill="#f87171" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
};
