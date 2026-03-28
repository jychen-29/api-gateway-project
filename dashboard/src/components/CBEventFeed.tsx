import React from 'react';
import { CircuitBreakerEvent } from '../types';
import { formatDistanceToNow } from 'date-fns';

interface Props {
  events: CircuitBreakerEvent[];
}

const STATE_COLORS: Record<string, string> = {
  CLOSED:    'text-emerald-400 bg-emerald-400/10',
  OPEN:      'text-red-400 bg-red-400/10',
  HALF_OPEN: 'text-amber-400 bg-amber-400/10',
};

export const CBEventFeed: React.FC<Props> = ({ events }) => {
  if (!events.length) {
    return (
      <div className="text-center py-6 text-white/25 text-sm">
        No circuit breaker events yet
      </div>
    );
  }

  return (
    <div className="space-y-2 max-h-48 overflow-y-auto pr-1 scrollbar-thin">
      {events.map((evt, i) => {
        const style = STATE_COLORS[evt.state] ?? 'text-slate-400 bg-slate-400/10';
        return (
          <div key={i} className="flex items-center gap-3 py-2 px-3 rounded-lg bg-white/[0.03] border border-white/5 hover:border-white/10 transition-colors">
            <span className={`px-2 py-0.5 rounded-md text-xs font-bold ${style}`}>
              {evt.state}
            </span>
            <span className="text-sm text-white/70 flex-1">{evt.service}</span>
            <span className="text-xs text-white/30">
              {formatDistanceToNow(new Date(evt.timestamp), { addSuffix: true })}
            </span>
          </div>
        );
      })}
    </div>
  );
};
