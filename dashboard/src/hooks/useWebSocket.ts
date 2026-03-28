import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { DashboardMetrics, CircuitBreakerEvent } from '../types';

interface UseWebSocketReturn {
  metrics: DashboardMetrics | null;
  cbEvents: CircuitBreakerEvent[];
  connected: boolean;
}

export const useWebSocket = (): UseWebSocketReturn => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [cbEvents, setCbEvents] = useState<CircuitBreakerEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);

        client.subscribe('/topic/metrics', (msg) => {
          try {
            setMetrics(JSON.parse(msg.body));
          } catch (e) {
            console.error('Failed to parse metrics', e);
          }
        });

        client.subscribe('/topic/circuit-breaker', (msg) => {
          try {
            const event: CircuitBreakerEvent = JSON.parse(msg.body);
            setCbEvents((prev) => [event, ...prev].slice(0, 50));
          } catch (e) {
            console.error('Failed to parse CB event', e);
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        console.error('STOMP error', frame);
        setConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;
  }, []);

  useEffect(() => {
    connect();
    return () => {
      clientRef.current?.deactivate();
    };
  }, [connect]);

  return { metrics, cbEvents, connected };
};
