import React, { useMemo } from 'react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
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
          <span className="text-white font-semibold">{p.value}</span>
        </div>
      ))}
    </div>
  );
};

export const RequestVolumeChart: React.FC<Props> = ({ timeSeries }) => {
  const { chartData, services } = useMemo(() => {
    // Collect all unique time buckets and service names
    const bucketMap = new Map<string, Record<string, number>>();
    const serviceSet = new Set<string>();

    timeSeries.forEach(({ bucket, serviceName, requestCount }) => {
      serviceSet.add(serviceName);
      const time = format(parseISO(bucket), 'HH:mm');
      if (!bucketMap.has(time)) bucketMap.set(time, {});
      bucketMap.get(time)![serviceName] = (bucketMap.get(time)![serviceName] ?? 0) + Number(requestCount);
    });

    const chartData = Array.from(bucketMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([time, counts]) => ({ time, ...counts }));

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
      <AreaChart data={chartData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
        <defs>
          {services.map((s) => (
            <linearGradient key={s} id={`grad-${s}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={SERVICE_COLORS[s] ?? '#94a3b8'} stopOpacity={0.3} />
              <stop offset="95%" stopColor={SERVICE_COLORS[s] ?? '#94a3b8'} stopOpacity={0} />
            </linearGradient>
          ))}
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis dataKey="time" tick={{ fontSize: 10, fill: 'rgba(255,255,255,0.3)' }} axisLine={false} tickLine={false} />
        <YAxis tick={{ fontSize: 10, fill: 'rgba(255,255,255,0.3)' }} axisLine={false} tickLine={false} />
        <Tooltip content={<CustomTooltip />} />
        <Legend
          formatter={(value) => <span className="text-xs text-white/50">{value}</span>}
          iconType="circle"
          iconSize={6}
        />
        {services.map((s) => (
          <Area
            key={s}
            type="monotone"
            dataKey={s}
            stroke={SERVICE_COLORS[s] ?? '#94a3b8'}
            strokeWidth={2}
            fill={`url(#grad-${s})`}
          />
        ))}
      </AreaChart>
    </ResponsiveContainer>
  );
};
