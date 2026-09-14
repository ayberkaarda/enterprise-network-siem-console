import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { Client } from '@stomp/stompjs';

import {
  DEVICE_TOPIC,
  INCIDENT_TOPIC,
  METRICS_TOPIC,
  RealtimeConfig,
  RealtimeService,
  StompClientFactory,
  computeBackoffDelay,
} from './realtime.service';
import { SiemApiService } from './siem-api.service';
import { DeviceResponse, Incident, Page } from './siem.models';

const TEST_CONFIG: RealtimeConfig = {
  socketUrl: 'http://test.invalid/ws-siem',
  initialReconnectDelayMs: 1_000,
  maxReconnectDelayMs: 30_000,
  jitterRatio: 0.25,
  fallbackAfterAttempts: 5,
  pollIntervalMs: 20_000,
  devicePageSize: 200,
  incidentPageSize: 200,
  // Mid-point of the jitter window, so a delay equals its un-jittered base.
  randomSource: () => 0.5,
};

/** Minimal stand-in for the STOMP client; the test drives its callbacks. */
class FakeStompClient {
  reconnectDelay = 5_000;
  onConnect: (frame?: unknown) => void = () => undefined;
  onWebSocketClose: () => void = () => undefined;
  activated = 0;
  deactivated = 0;
  readonly subscriptions = new Map<string, (message: { body: string }) => void>();

  activate(): void {
    this.activated += 1;
  }

  deactivate(): Promise<void> {
    this.deactivated += 1;
    return Promise.resolve();
  }

  subscribe(destination: string, callback: (message: { body: string }) => void): { id: string } {
    this.subscriptions.set(destination, callback);
    return { id: destination };
  }
}

function page<T>(content: T[]): Page<T> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: content.length };
}

function incident(overrides: Partial<Incident> & { id: number }): Incident {
  return {
    title: 'Port scan detected',
    severity: 'HIGH',
    status: 'OPEN',
    createdAt: '2026-09-14T10:00:00Z',
    updatedAt: '2026-09-14T10:00:00Z',
    ...overrides,
  };
}

describe('computeBackoffDelay', () => {
  it('doubles each attempt and clamps at the configured ceiling', () => {
    const delays = [1, 2, 3, 4, 5, 6, 7].map((attempt) =>
      computeBackoffDelay(attempt, TEST_CONFIG),
    );
    expect(delays).toEqual([1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000]);
  });

  it('spreads the delay symmetrically by the jitter ratio', () => {
    const low = computeBackoffDelay(3, { ...TEST_CONFIG, randomSource: () => 0 });
    const high = computeBackoffDelay(3, { ...TEST_CONFIG, randomSource: () => 1 });
    expect(low).toBe(3_000);
    expect(high).toBe(5_000);
  });
});

describe('RealtimeService', () => {
  let clients: FakeStompClient[];
  let factory: ReturnType<typeof vi.fn>;
  let getIncidents: ReturnType<typeof vi.fn>;
  let getDevicePage: ReturnType<typeof vi.fn>;
  let service: RealtimeService;

  const currentClient = (): FakeStompClient => clients[clients.length - 1];

  /** Closes the live socket and lets the scheduled retry open the next one. */
  const dropSocket = (): void => {
    currentClient().onWebSocketClose();
    vi.advanceTimersByTime(TEST_CONFIG.maxReconnectDelayMs + 1);
  };

  beforeEach(() => {
    vi.useFakeTimers();
    clients = [];
    factory = vi.fn(() => {
      const client = new FakeStompClient();
      clients.push(client);
      return client as unknown as Client;
    });
    getIncidents = vi.fn(() => of(page<Incident>([incident({ id: 1 })])));
    getDevicePage = vi.fn(() =>
      of(page<DeviceResponse>([{ id: 1, name: 'FW-EDGE-01', ipAddress: '10.0.1.1' }])),
    );
    const api = { getIncidents, getDevicePage } as unknown as SiemApiService;
    service = new RealtimeService(api, TEST_CONFIG, factory as unknown as StompClientFactory);
  });

  afterEach(() => {
    service.stop();
    vi.useRealTimers();
  });

  it('reads REST and opens a socket on start', () => {
    service.start();

    expect(getIncidents).toHaveBeenCalledTimes(1);
    expect(getDevicePage).toHaveBeenCalledTimes(1);
    expect(factory).toHaveBeenCalledTimes(1);
    expect(currentClient().activated).toBe(1);
    expect(service.connectionState()).toBe('connecting');
    // The library's own fixed-interval retry must be off; backoff is ours.
    expect(currentClient().reconnectDelay).toBe(0);
  });

  it('subscribes to the three typed topics once connected', () => {
    service.start();
    currentClient().onConnect();

    expect(service.connectionState()).toBe('connected');
    expect([...currentClient().subscriptions.keys()]).toEqual(
      expect.arrayContaining([DEVICE_TOPIC, INCIDENT_TOPIC, METRICS_TOPIC]),
    );
  });

  it('stays in reconnecting for the first four consecutive failures', () => {
    service.start();

    for (let i = 0; i < 4; i += 1) {
      dropSocket();
      expect(service.connectionState()).toBe('reconnecting');
    }
    expect(service.reconnectAttempts()).toBe(4);
    // One fresh client per retry: the initial one plus four reopens.
    expect(factory).toHaveBeenCalledTimes(5);
  });

  it('falls back to REST polling after the fifth consecutive failure', () => {
    service.start();
    for (let i = 0; i < 4; i += 1) {
      dropSocket();
    }
    const devicesBefore = getDevicePage.mock.calls.length;
    const incidentsBefore = getIncidents.mock.calls.length;

    currentClient().onWebSocketClose();

    expect(service.connectionState()).toBe('polling-fallback');
    // Entering fallback polls immediately rather than waiting one interval.
    expect(getDevicePage.mock.calls.length).toBe(devicesBefore + 1);
    expect(getIncidents.mock.calls.length).toBe(incidentsBefore + 1);
  });

  it('keeps polling on the configured interval while in fallback', () => {
    service.start();
    for (let i = 0; i < 5; i += 1) {
      currentClient().onWebSocketClose();
      vi.advanceTimersByTime(TEST_CONFIG.maxReconnectDelayMs + 1);
    }
    expect(service.connectionState()).toBe('polling-fallback');

    const before = getDevicePage.mock.calls.length;
    vi.advanceTimersByTime(TEST_CONFIG.pollIntervalMs * 3);

    expect(getDevicePage.mock.calls.length).toBeGreaterThanOrEqual(before + 3);
  });

  it('keeps retrying the socket in the background and leaves fallback on recovery', () => {
    service.start();
    for (let i = 0; i < 5; i += 1) {
      currentClient().onWebSocketClose();
      vi.advanceTimersByTime(TEST_CONFIG.maxReconnectDelayMs + 1);
    }
    expect(service.connectionState()).toBe('polling-fallback');
    const clientsDuringFallback = factory.mock.calls.length;

    currentClient().onConnect();
    expect(service.connectionState()).toBe('connected');
    expect(service.reconnectAttempts()).toBe(0);

    const devicesAfterRecovery = getDevicePage.mock.calls.length;
    vi.advanceTimersByTime(TEST_CONFIG.pollIntervalMs * 4);

    // Polling stopped and no further retry was scheduled.
    expect(getDevicePage.mock.calls.length).toBe(devicesAfterRecovery);
    expect(factory.mock.calls.length).toBe(clientsDuringFallback);
  });

  it('clears the metrics snapshot when it enters fallback so the local value is used', () => {
    service.start();
    currentClient().onConnect();
    currentClient().subscriptions.get(METRICS_TOPIC)?.({
      body: JSON.stringify({
        totalDevices: 12,
        reachableDevices: 10,
        openIncidents: 3,
        criticalOrHighIncidents: 1,
        avgLatencyMs: 38.4,
        timestamp: '2026-09-14T10:00:00Z',
      }),
    });
    expect(service.metrics()?.criticalOrHighIncidents).toBe(1);

    for (let i = 0; i < 5; i += 1) {
      currentClient().onWebSocketClose();
      vi.advanceTimersByTime(TEST_CONFIG.maxReconnectDelayMs + 1);
    }

    expect(service.connectionState()).toBe('polling-fallback');
    expect(service.metrics()).toBeNull();
  });

  it('patches a pushed device in place instead of refetching the list', () => {
    service.start();
    currentClient().onConnect();
    const devicesBefore = getDevicePage.mock.calls.length;

    currentClient().subscriptions.get(DEVICE_TOPIC)?.({
      body: JSON.stringify({
        id: 1,
        name: 'FW-EDGE-01',
        ipAddress: '10.0.1.1',
        status: 'INACTIVE',
        latency: -1,
        deviceType: 'FIREWALL',
        changedAt: '2026-09-14T10:00:00Z',
      }),
    });

    expect(service.devicePatches().get(1)?.status).toBe('INACTIVE');
    expect(getDevicePage.mock.calls.length).toBe(devicesBefore);
  });

  it('merges a pushed incident onto the record already read over REST', () => {
    getIncidents.mockReturnValue(
      of(page<Incident>([incident({ id: 7, description: 'seen over REST' })])),
    );
    service.start();
    currentClient().onConnect();

    currentClient().subscriptions.get(INCIDENT_TOPIC)?.({
      body: JSON.stringify({
        id: 7,
        title: 'Port scan detected',
        severity: 'CRITICAL',
        status: 'ACKNOWLEDGED',
        sourceDeviceId: 1,
        createdAt: '2026-09-14T10:00:00Z',
        updatedAt: '2026-09-14T10:05:00Z',
      }),
    });

    const merged = service.incidents().find((item) => item.id === 7);
    expect(merged?.status).toBe('ACKNOWLEDGED');
    expect(merged?.severity).toBe('CRITICAL');
    // Fields the push does not carry survive the merge.
    expect(merged?.description).toBe('seen over REST');
  });

  it('ignores a malformed frame without tearing down the subscription', () => {
    service.start();
    currentClient().onConnect();
    const before = service.devicePatches().size;

    expect(() => currentClient().subscriptions.get(DEVICE_TOPIC)?.({ body: 'not-json' })).not.toThrow();
    expect(service.devicePatches().size).toBe(before);
  });

  it('stops every timer and closes the socket on stop', () => {
    service.start();
    const client = currentClient();

    service.stop();

    expect(service.connectionState()).toBe('idle');
    expect(client.deactivated).toBe(1);

    const calls = getDevicePage.mock.calls.length;
    vi.advanceTimersByTime(TEST_CONFIG.pollIntervalMs * 5);
    expect(getDevicePage.mock.calls.length).toBe(calls);
  });
});
