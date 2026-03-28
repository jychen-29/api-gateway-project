import React, { useMemo } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { TimeSeriesBucket } from '../types';
import { format, parseISO } from 'date-fns';

interface Props {
  timeSeries: TimeSeriesBucket[];
}

const SERVICE_COLORS: Record<string, string> = {
  'user-service':    '#818cf8',
  'order-service':   '#34d399',
  'product-service': '#fb923c',
};

const CustomTooltip: React.FC<any> = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-slate-900 border border-white/10 rounded-xl p-3 shadow-xl text-xs">
      <p className="text-white/50 mb-2">{label}</p>
      {payload.map((p: any) => (
        <div key={p.dataKey} className="flex items-center gap-2 mb-1">
          <span className="w-2 h-2 rounded-full" style={{ background: p.color }} />
          <span className="text-white/70">{p.name}:</span>
          <span className="text-white font-semibold">{p.value.toFixed(1)}%</span>
        </div>
      ))}
    </div>
  );
};

export const ErrorRateChart: React.FC<Props> = ({ timeSeries }) => {
  const { chartData, services } = useMemo(() => {
    const bucketMap = new Map<string, Record<string, { req: number; err: number }>>();
    const serviceSet = new Set<string>();

    timeSeries.forEach(({ bucket, serviceName, requestCount, errorCount }) => {
      serviceSet.add(serviceName);
      const time = format(parseISO(bucket), 'HH:mm');
      if (!bucketMap.has(time)) bucketMap.set(time, {});
      const entry = bucketMap.get(time)!;
      entry[serviceName] = entry[serviceName]
        ? { req: entry[serviceName].req + Number(requestCount), err: entry[serviceName].err + Number(errorCount) }
        : { req: Number(requestCount), err: Number(errorCount) };
    });

    const chartData = Array.from(bucketMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([time, svcs]) => {
        const row: Record<string, any> = { time };
        for (const [svc, { req, err }] of Object.entries(svcs)) {
          row[svc] = req > 0 ? (err / req) * 100 : 0;
        }
        return row;
      });

    return { chartData, services: Array.from(serviceSet) };
  }, [timeSeries]);

  if (!chartData.length) {
    return (
      <div className="h-48 flex items-center justify-center text-white/30 text-sm">
        No traffic data in this window
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={chartData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis dataKey="time" tick={{ fontSize: 10, fill: 'rgba(255,255,255,0.3)' }} axisLine={false} tickLine={false} />
        <YAxis tickFormatter={(v) => `${v}%`} tick={{ fontSize: 10, fill: 'rgba(255,255,255,0.3)' }} axisLine={false} tickLine={false} />
        <Tooltip content={<CustomTooltip />} />
        <Legend formatter={(v) => <span className="text-xs text-white/50">{v}</span>} iconType="circle" iconSize={6} />
        {/* 5% error threshold line */}
        <ReferenceLine y={5} stroke="rgba(248,113,113,0.4)" strokeDasharray="4 4" label={{ value: '5% threshold', fill: 'rgba(248,113,113,0.5)', fontSize: 9 }} />
        {services.map((s) => (
          <Line
            key={s}
            type="monotone"
            dataKey={s}
            stroke={SERVICE_COLORS[s] ?? '#94a3b8'}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4 }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
};
