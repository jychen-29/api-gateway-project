import React from 'react';
import { CircuitBreakerStatus } from '../types';
import { resetCircuitBreaker } from '../api/client';

interface Props {
  service: string;
  status: CircuitBreakerStatus;
  onReset: () => void;
}

const STATE_STYLES: Record<string, { bg: string; text: string; dot: string; label: string }> = {
  CLOSED:    { bg: 'bg-emerald-950/60', text: 'text-emerald-300', dot: 'bg-emerald-400', label: 'Healthy' },
  OPEN:      { bg: 'bg-red-950/60',     text: 'text-red-300',     dot: 'bg-red-400 animate-pulse', label: 'Open – Blocking Traffic' },
  HALF_OPEN: { bg: 'bg-amber-950/60',   text: 'text-amber-300',   dot: 'bg-amber-400 animate-pulse', label: 'Half-Open – Testing' },
  DISABLED:  { bg: 'bg-slate-800/60',   text: 'text-slate-400',   dot: 'bg-slate-500', label: 'Disabled' },
  METRICS_ONLY: { bg: 'bg-slate-800/60', text: 'text-slate-400',  dot: 'bg-slate-500', label: 'Metrics Only' },
};

const SERVICE_ICONS: Record<string, string> = {
  'user-service': '👤',
  'order-service': '📦',
  'product-service': '🛍️',
};

export const CircuitBreakerCard: React.FC<Props> = ({ service, status, onReset }) => {
  const style = STATE_STYLES[status.state] ?? STATE_STYLES['DISABLED'];
  const icon = SERVICE_ICONS[service] ?? '⚙️';

  const handleReset = async () => {
    try {
      await resetCircuitBreaker(service);
      onReset();
    } catch (e) {
      console.error('Reset failed', e);
    }
  };

  const totalCalls = status.failedCalls + status.successfulCalls;
  const successRate = totalCalls > 0
    ? ((status.successfulCalls / totalCalls) * 100).toFixed(1)
    : '—';

  return (
    <div className={`rounded-xl border border-white/10 p-4 ${style.bg} flex flex-col gap-3`}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xl">{icon}</span>
          <div>
            <p className="text-sm font-semibold text-white">{service}</p>
            <div className="flex items-center gap-1.5 mt-0.5">
              <span className={`w-2 h-2 rounded-full ${style.dot}`} />
              <span className={`text-xs font-medium ${style.text}`}>{style.label}</span>
            </div>
          </div>
        </div>
        {status.state !== 'CLOSED' && (
          <button
            onClick={handleReset}
            className="text-xs px-2.5 py-1 rounded-lg bg-white/10 hover:bg-white/20 text-white/70 hover:text-white transition-all"
          >
            Reset
          </button>
        )}
      </div>

      {/* Metrics grid */}
      <div className="grid grid-cols-3 gap-2 text-center">
        <Stat label="Success Rate" value={`${successRate}%`} dim={status.state !== 'CLOSED'} />
        <Stat label="Failure Rate" value={`${isNaN(status.failureRate) ? '0' : status.failureRate.toFixed(1)}%`}
              warn={status.failureRate > 30} />
        <Stat label="Blocked" value={String(status.notPermittedCalls)} warn={status.notPermittedCalls > 0} />
      </div>

      {/* Mini bar */}
      {totalCalls > 0 && (
        <div className="h-1.5 rounded-full bg-white/10 overflow-hidden">
          <div
            className="h-full rounded-full bg-emerald-400 transition-all duration-700"
            style={{ width: `${successRate}%` }}
          />
        </div>
      )}
    </div>
  );
};

const Stat: React.FC<{ label: string; value: string; dim?: boolean; warn?: boolean }> = ({ label, value, dim, warn }) => (
  <div className={`rounded-lg bg-white/5 py-2 px-1 ${dim ? 'opacity-50' : ''}`}>
    <p className={`text-sm font-bold ${warn ? 'text-red-400' : 'text-white'}`}>{value}</p>
    <p className="text-[10px] text-white/40 mt-0.5">{label}</p>
  </div>
);
