import React from 'react';

interface Props {
  label: string;
  value: string | number;
  sub?: string;
  trend?: 'up' | 'down' | 'neutral';
  color?: 'default' | 'green' | 'red' | 'amber';
}

const COLOR_MAP = {
  default: 'text-white',
  green:   'text-emerald-400',
  red:     'text-red-400',
  amber:   'text-amber-400',
};

const TREND_ICONS = { up: '↑', down: '↓', neutral: '→' };
const TREND_COLORS = { up: 'text-emerald-400', down: 'text-red-400', neutral: 'text-white/30' };

export const StatCard: React.FC<Props> = ({ label, value, sub, trend, color = 'default' }) => (
  <div className="rounded-xl border border-white/10 bg-white/[0.04] hover:bg-white/[0.06] transition-colors p-4 flex flex-col gap-1">
    <p className="text-xs text-white/40 uppercase tracking-wider font-medium">{label}</p>
    <p className={`text-2xl font-bold ${COLOR_MAP[color]}`}>{value}</p>
    {(sub || trend) && (
      <div className="flex items-center gap-1.5">
        {trend && (
          <span className={`text-xs font-semibold ${TREND_COLORS[trend]}`}>
            {TREND_ICONS[trend]}
          </span>
        )}
        {sub && <p className="text-xs text-white/30">{sub}</p>}
      </div>
    )}
  </div>
);
