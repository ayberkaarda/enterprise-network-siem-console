import { Injectable, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';

import { RealtimeService } from './realtime.service';

/**
 * @deprecated Superseded by {@link RealtimeService}, which owns the single
 * STOMP connection, the typed `/topic/devices`, `/topic/incidents` and
 * `/topic/metrics` subscriptions, the backed-off reconnect and the REST
 * polling fallback.
 *
 * This shim no longer opens a connection of its own — a second client would
 * mean a second socket and a second reconnect loop. It only re-exposes the
 * raw `/topic/alerts` string channel as an observable for callers that have
 * not moved to the signal API yet, and can be removed once none are left.
 */
@Injectable({ providedIn: 'root' })
export class WebsocketService {
  private readonly realtime = inject(RealtimeService);

  readonly alerts$: Observable<string | null> = toObservable(this.realtime.lastAlert);
}
