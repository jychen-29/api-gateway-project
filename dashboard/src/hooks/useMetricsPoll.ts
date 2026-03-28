import { useEffect, useState, useCallback } from 'react';
import { fetchMetrics } from '../api/client';
import { DashboardMetrics } from '../types';

export const useMetricsPoll = (intervalMs = 10000, lookbackMinutes = 30) => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await fetchMetrics(lookbackMinutes);
      setMetrics(data);
      setError(null);
    } catch (e: any) {
      setError(e.message ?? 'Failed to fetch metrics');
    } finally {
      setLoading(false);
    }
  }, [lookbackMinutes]);

  useEffect(() => {
    load();
    const id = setInterval(load, intervalMs);
    return () => clearInterval(id);
  }, [load, intervalMs]);

  return { metrics, loading, error, refresh: load };
};
