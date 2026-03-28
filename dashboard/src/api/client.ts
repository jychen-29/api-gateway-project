import axios from 'axios';
import { DashboardMetrics, CircuitBreakerStatus } from '../types';

const api = axios.create({ baseURL: '' });

export const fetchMetrics = async (lookbackMinutes = 30): Promise<DashboardMetrics> => {
  const { data } = await api.get(`/internal/metrics?lookbackMinutes=${lookbackMinutes}`);
  return data;
};

export const fetchCircuitBreakers = async (): Promise<Record<string, CircuitBreakerStatus>> => {
  const { data } = await api.get('/internal/circuit-breakers');
  return data;
};

export const resetCircuitBreaker = async (service: string): Promise<void> => {
  await api.post(`/internal/circuit-breakers/${service}/reset`);
};

export const generateToken = async (userId: string, role: string): Promise<string> => {
  const { data } = await api.post('/auth/token', { userId, role });
  return data.token;
};
