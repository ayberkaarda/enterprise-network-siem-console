import { Inject, Injectable, InjectionToken, OnDestroy, Signal, inject, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import { AuthService } from './auth.service';
import { SiemApiService, describeProblem } from './siem-api.service';
import {
  DeviceRealtimeUpdate,
  Incident,
  IncidentRealtimeUpdate,
  MetricsSnapshot,
} from './siem.models';

/**
 * Transport state of the console.
 *
 * `connected`         — STOMP is up, every topic is live.
 * `reconnecting`      — the socket dropped, a backed-off retry is scheduled.
 * `polling-fallback`  — too many consecutive failures; the REST endpoints are
 *                       being polled instead, while socket retries continue in
 *                       the background so the console can return to push mode
 *                       on its own.
 */
export type ConnectionState =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'polling-fallback';

export const DEVICE_TOPIC = '/topic/devices';
export const INCIDENT_TOPIC = '/topic/incidents';
export const METRICS_TOPIC = '/topic/metrics';
export const ALERT_TOPIC = '/topic/alerts';

export interface RealtimeConfig {
  /** SockJS endpoint the STOMP client connects to. */
  socketUrl: string;
  /** Delay before the first retry; doubles on every consecutive failure. */
  initialReconnectDelayMs: number;
  /** Ceiling for the backed-off delay. */
  maxReconnectDelayMs: number;
  /** Fraction of the delay spread randomly around it, to de-synchronise clients. */
  jitterRatio: number;
  /** Consecutive failures tolerated before switching to REST polling. */
  fallbackAfterAttempts: number;
  /** How often the REST endpoints are read while in polling fallback. */
  pollIntervalMs: number;
  devicePageSize: number;
  incidentPageSize: number;
  /** Injected so the jittered delay is deterministic under test. */
  randomSource: () => number;
}

export const DEFAULT_REALTIME_CONFIG: RealtimeConfig = {
  socketUrl: 'http://localhost:8080/ws-siem',
  initialReconnectDelayMs: 1_000,
  maxReconnectDelayMs: 30_000,
  jitterRatio: 0.25,
  fallbackAfterAttempts: 5,
  pollIntervalMs: 20_000,
  devicePageSize: 200,
  incidentPageSize: 200,
  randomSource: () => Math.random(),
};

export const REALTIME_CONFIG = new InjectionToken<RealtimeConfig>('REALTIME_CONFIG', {
  providedIn: 'root',
  factory: () => DEFAULT_REALTIME_CONFIG,
});

export type StompClientFactory = (url: string) => Client;

/**
 * Reads the access token the socket should present.
 *
 * The token travels as a STOMP CONNECT header rather than an HTTP header:
 * the browser's WebSocket API offers no way to set request headers, so the
 * credential has to ride inside the STOMP frame instead. It is read at the
 * moment of every (re)connect, so a session that refreshed while the socket
 * was down reconnects with the new token rather than the one it started with.
 */
export type AccessTokenReader = () => string | null;

export const ACCESS_TOKEN_READER = new InjectionToken<AccessTokenReader>('ACCESS_TOKEN_READER', {
  providedIn: 'root',
  factory: () => {
    const auth = inject(AuthService);
    return () => auth.accessToken();
  },
});

export const STOMP_CLIENT_FACTORY = new InjectionToken<StompClientFactory>('STOMP_CLIENT_FACTORY', {
  providedIn: 'root',
  factory:
    () =>
    (url: string): Client =>
      new Client({
        webSocketFactory: () => new SockJS(url),
        // Reconnection is scheduled by RealtimeService so the delay can grow
        // and carry jitter; the library's fixed-interval retry is switched off.
        reconnectDelay: 0,
        debug: () => undefined,
      }),
});

/**
 * Exponential backoff with symmetric jitter, clamped to the configured ceiling.
 *
 * attempt 1 -> initial, attempt 2 -> 2x, attempt 3 -> 4x … capped at max,
 * each spread by +/- (jitterRatio * delay) so a fleet of consoles does not
 * reconnect in lockstep after a broker restart.
 */
export function computeBackoffDelay(attempt: number, config: RealtimeConfig): number {
  const step = Math.max(0, attempt - 1);
  const base = Math.min(
    config.maxReconnectDelayMs,
    config.initialReconnectDelayMs * Math.pow(2, step),
  );
  const spread = base * config.jitterRatio;
  const jittered = base - spread + config.randomSource() * spread * 2;
  return Math.max(0, Math.round(jittered));
}

/**
 * Single real-time entry point for the console.
 *
 * Owns one STOMP connection, exposes the pushed state as signals, and degrades
 * to REST polling when the socket cannot be kept up. Components read the
 * signals and never touch the transport.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService implements OnDestroy {
  private readonly _connectionState = signal<ConnectionState>('idle');
  private readonly _devicePatches = signal<ReadonlyMap<number, DeviceRealtimeUpdate>>(new Map());
  private readonly _incidents = signal<readonly Incident[]>([]);
  private readonly _metrics = signal<MetricsSnapshot | null>(null);
  private readonly _lastAlert = signal<string | null>(null);
  private readonly _reconnectAttempts = signal(0);
  private readonly _incidentsLoaded = signal(false);
  private readonly _lastRestError = signal<string | null>(null);

  /** Current transport state; drives the connection badge and the threat source. */
  readonly connectionState: Signal<ConnectionState> = this._connectionState.asReadonly();
  /** Latest known state per device id, from pushes or from fallback polling. */
  readonly devicePatches: Signal<ReadonlyMap<number, DeviceRealtimeUpdate>> =
    this._devicePatches.asReadonly();
  /** Incident list, newest first, seeded by REST and patched in place by pushes. */
  readonly incidents: Signal<readonly Incident[]> = this._incidents.asReadonly();
  /** Last server-computed metrics snapshot, or null if none has arrived yet. */
  readonly metrics: Signal<MetricsSnapshot | null> = this._metrics.asReadonly();
  /** Most recent raw string from the legacy alert channel. */
  readonly lastAlert: Signal<string | null> = this._lastAlert.asReadonly();
  /** Consecutive failed connection attempts; resets to 0 on a successful connect. */
  readonly reconnectAttempts: Signal<number> = this._reconnectAttempts.asReadonly();
  /** False until the first incident read completes, so the view can show a loading state. */
  readonly incidentsLoaded: Signal<boolean> = this._incidentsLoaded.asReadonly();
  /** Last REST failure message, already mapped to a user-safe string. */
  readonly lastRestError: Signal<string | null> = this._lastRestError.asReadonly();

  private client?: Client;
  private started = false;
  private attempts = 0;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private pollTimer?: ReturnType<typeof setInterval>;

  constructor(
    private readonly api: SiemApiService,
    @Inject(REALTIME_CONFIG) private readonly config: RealtimeConfig,
    @Inject(STOMP_CLIENT_FACTORY) private readonly clientFactory: StompClientFactory,
    // Defaults to "no token" so a test can construct the service without
    // standing up a session; the application always injects the real reader.
    @Inject(ACCESS_TOKEN_READER) private readonly readAccessToken: AccessTokenReader = () => null,
  ) {}

  /** Idempotent: opens the socket and performs the initial REST read. */
  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.attempts = 0;
    this._reconnectAttempts.set(0);
    // Read before the socket is up: if the broker is unreachable the console
    // still shows real data instead of an empty shell for the whole backoff
    // window. A second read follows once the subscriptions are in place.
    this.refreshIncidents();
    this.refreshDevices();
    this.connect();
  }

  /** Closes the socket and cancels every timer. */
  stop(): void {
    this.started = false;
    this.clearReconnectTimer();
    this.stopPolling();
    this.teardownClient();
    this._connectionState.set('idle');
  }

  ngOnDestroy(): void {
    this.stop();
  }

  /** Re-reads the incident page and replaces the list. */
  refreshIncidents(): void {
    this.api.getIncidents({ page: 0, size: this.config.incidentPageSize }).subscribe({
      next: (page) => {
        this._incidents.set(sortIncidents(page.content ?? []));
        this._incidentsLoaded.set(true);
        this._lastRestError.set(null);
      },
      error: (error: unknown) => this._lastRestError.set(describeProblem(error)),
    });
  }

  /** Re-reads the device page and folds it into the patch map. */
  refreshDevices(): void {
    this.api.getDevicePage({ page: 0, size: this.config.devicePageSize }).subscribe({
      next: (page) => this.mergeDevices(page.content ?? []),
      error: (error: unknown) => this._lastRestError.set(describeProblem(error)),
    });
  }

  /**
   * Folds one incident into the list without a refetch. Used both by the
   * /topic/incidents subscription and by the response of a transition call,
   * so a manual action updates the card immediately even if the push is late.
   */
  applyIncidentUpdate(update: IncidentRealtimeUpdate | Incident): void {
    const current = this._incidents();
    const index = current.findIndex((incident) => incident.id === update.id);
    if (index === -1) {
      this._incidents.set(sortIncidents([{ ...(update as Incident) }, ...current]));
      return;
    }
    const next = current.slice();
    next[index] = { ...current[index], ...update };
    this._incidents.set(sortIncidents(next));
  }

  // ---------------------------------------------------------------- transport

  private connect(): void {
    if (!this.started) {
      return;
    }
    this.teardownClient();
    if (this._connectionState() !== 'polling-fallback') {
      this._connectionState.set(this.attempts === 0 ? 'connecting' : 'reconnecting');
    }

    const client = this.clientFactory(this.config.socketUrl);
    client.reconnectDelay = 0;
    // Set per attempt, not once in the factory: the token in hand at the
    // fifth retry is not necessarily the one held at the first.
    client.connectHeaders = this.buildConnectHeaders();
    client.onConnect = () => this.handleConnected(client);
    // A STOMP-level error or a transport error is always followed by a close
    // frame, so a single handler covers every failure path.
    client.onWebSocketClose = () => this.handleDropped();
    this.client = client;
    client.activate();
  }

  /** CONNECT frame headers; empty when there is no session to present. */
  private buildConnectHeaders(): Record<string, string> {
    const token = this.readAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  private handleConnected(client: Client): void {
    this.attempts = 0;
    this._reconnectAttempts.set(0);
    this.clearReconnectTimer();
    this.stopPolling();
    this._connectionState.set('connected');

    client.subscribe(DEVICE_TOPIC, (message: IMessage) =>
      this.handleDeviceFrame(readJson<DeviceRealtimeUpdate>(message.body)),
    );
    client.subscribe(INCIDENT_TOPIC, (message: IMessage) =>
      this.handleIncidentFrame(readJson<IncidentRealtimeUpdate>(message.body)),
    );
    client.subscribe(METRICS_TOPIC, (message: IMessage) =>
      this.handleMetricsFrame(readJson<MetricsSnapshot>(message.body)),
    );
    client.subscribe(ALERT_TOPIC, (message: IMessage) => this._lastAlert.set(message.body ?? null));

    // Frames published while the socket was down are simply gone; a read after
    // every (re)connect is what keeps the console from drifting silently.
    this.refreshIncidents();
    this.refreshDevices();
  }

  private handleDropped(): void {
    if (!this.started || this.reconnectTimer !== undefined) {
      return;
    }
    this.teardownClient();
    this.attempts += 1;
    this._reconnectAttempts.set(this.attempts);

    if (this.attempts >= this.config.fallbackAfterAttempts) {
      this.enterPollingFallback();
    } else {
      this._connectionState.set('reconnecting');
    }

    const delay = computeBackoffDelay(this.attempts, this.config);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = undefined;
      this.connect();
    }, delay);
  }

  private teardownClient(): void {
    const client = this.client;
    if (!client) {
      return;
    }
    this.client = undefined;
    // Detach first: deactivate() closes the socket and would otherwise
    // re-enter handleDropped through the old client's callback.
    client.onConnect = () => undefined;
    client.onWebSocketClose = () => undefined;
    try {
      void Promise.resolve(client.deactivate()).catch(() => undefined);
    } catch {
      // A client that never opened has nothing to tear down.
    }
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== undefined) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
  }

  // ------------------------------------------------------------- fallback

  private enterPollingFallback(): void {
    if (this._connectionState() === 'polling-fallback') {
      return;
    }
    this._connectionState.set('polling-fallback');
    // Metrics are only ever pushed; while the socket is down the threat
    // indicator must fall back to the locally computed value.
    this._metrics.set(null);
    this.pollOnce();
    this.pollTimer = setInterval(() => this.pollOnce(), this.config.pollIntervalMs);
  }

  private pollOnce(): void {
    this.refreshDevices();
    this.refreshIncidents();
  }

  private stopPolling(): void {
    if (this.pollTimer !== undefined) {
      clearInterval(this.pollTimer);
      this.pollTimer = undefined;
    }
  }

  // --------------------------------------------------------------- frames

  private handleDeviceFrame(update: DeviceRealtimeUpdate | null): void {
    if (!update || typeof update.id !== 'number') {
      return;
    }
    this.mergeDevices([update]);
  }

  private handleIncidentFrame(update: IncidentRealtimeUpdate | null): void {
    if (!update || typeof update.id !== 'number') {
      return;
    }
    this.applyIncidentUpdate(update);
  }

  private handleMetricsFrame(snapshot: MetricsSnapshot | null): void {
    if (!snapshot || typeof snapshot.criticalOrHighIncidents !== 'number') {
      return;
    }
    this._metrics.set(snapshot);
  }

  private mergeDevices(updates: readonly DeviceRealtimeUpdate[]): void {
    if (updates.length === 0) {
      return;
    }
    const current = this._devicePatches();
    const next = new Map(current);
    let changed = false;
    for (const update of updates) {
      if (typeof update.id !== 'number') {
        continue;
      }
      const previous = next.get(update.id);
      if (previous && !deviceChanged(previous, update)) {
        continue;
      }
      next.set(update.id, { ...previous, ...update });
      changed = true;
    }
    if (changed) {
      this._devicePatches.set(next);
    }
  }
}

function deviceChanged(previous: DeviceRealtimeUpdate, update: DeviceRealtimeUpdate): boolean {
  return (
    previous.status !== update.status ||
    previous.latency !== update.latency ||
    previous.name !== update.name ||
    previous.ipAddress !== update.ipAddress ||
    previous.deviceType !== update.deviceType
  );
}

function readJson<T>(body: string | undefined): T | null {
  if (!body) {
    return null;
  }
  try {
    return JSON.parse(body) as T;
  } catch {
    // A malformed frame is dropped rather than taking the subscription down.
    return null;
  }
}

/** Newest first; ties broken by id so the order never flickers between renders. */
function sortIncidents(incidents: readonly Incident[]): Incident[] {
  return [...incidents].sort((a, b) => {
    const byDate = Date.parse(b.createdAt ?? '') - Date.parse(a.createdAt ?? '');
    if (!Number.isNaN(byDate) && byDate !== 0) {
      return byDate;
    }
    return b.id - a.id;
  });
}
