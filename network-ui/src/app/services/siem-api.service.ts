import { Injectable, InjectionToken, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  DeviceResponse,
  Incident,
  IncidentComment,
  IncidentCommentRequest,
  IncidentSeverity,
  IncidentStatus,
  IncidentTransitionRequest,
  IngestedEvent,
  Page,
  ProblemDetail,
} from './siem.models';

/**
 * Origin of the v1 API. Kept as a token so a test (or a future environment
 * file) can point the client somewhere else without editing the service.
 */
export const SIEM_API_BASE_URL = new InjectionToken<string>('SIEM_API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080',
});

export interface IncidentQuery {
  status?: IncidentStatus;
  severity?: IncidentSeverity;
  page?: number;
  size?: number;
  sort?: string;
}

export interface DeviceQuery {
  status?: string;
  type?: string;
  ipPrefix?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface EventQuery {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Typed client for the v1 endpoints (incidents, incident comments, ingested
 * events, paginated devices). The legacy /api/devices endpoints stay in
 * DeviceService; this service deliberately does not duplicate them.
 */
@Injectable({ providedIn: 'root' })
export class SiemApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(SIEM_API_BASE_URL);

  private get apiUrl(): string {
    return `${this.baseUrl}/api/v1`;
  }

  getIncidents(query: IncidentQuery = {}): Observable<Page<Incident>> {
    return this.http.get<Page<Incident>>(`${this.apiUrl}/incidents`, {
      params: toParams(query),
    });
  }

  transitionIncident(id: number, newStatus: IncidentStatus): Observable<Incident> {
    const body: IncidentTransitionRequest = { newStatus };
    return this.http.post<Incident>(`${this.apiUrl}/incidents/${id}/transition`, body);
  }

  getIncidentComments(id: number): Observable<IncidentComment[]> {
    return this.http.get<IncidentComment[]>(`${this.apiUrl}/incidents/${id}/comments`);
  }

  addIncidentComment(id: number, comment: IncidentCommentRequest): Observable<IncidentComment> {
    return this.http.post<IncidentComment>(`${this.apiUrl}/incidents/${id}/comments`, comment);
  }

  getEvents(query: EventQuery = {}): Observable<Page<IngestedEvent>> {
    return this.http.get<Page<IngestedEvent>>(`${this.apiUrl}/events`, {
      params: toParams(query),
    });
  }

  getDevicePage(query: DeviceQuery = {}): Observable<Page<DeviceResponse>> {
    return this.http.get<Page<DeviceResponse>>(`${this.apiUrl}/devices`, {
      params: toParams(query),
    });
  }
}

function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

/**
 * Error codes the console knows how to explain. Anything outside this map
 * falls through to a generic message: the raw server text is never rendered,
 * so a stack trace or an internal identifier cannot leak into the UI.
 */
const ERROR_CODE_MESSAGES: Readonly<Record<string, string>> = {
  INVALID_STATE_TRANSITION: 'Bu durum geçişi olay yaşam döngüsünde geçerli değil.',
  INCIDENT_NOT_FOUND: 'Olay kaydı bulunamadı; liste yenilenmeli.',
  VALIDATION_ERROR: 'Gönderilen veri doğrulamadan geçmedi.',
  FORBIDDEN: 'Bu işlem için yetkiniz yok.',
  INVALID_CREDENTIALS: 'Kullanıcı adı veya parola hatalı.',
  AUTH_REQUIRED: 'Oturum doğrulanamadı; yeniden giriş yapın.',
  INSUFFICIENT_ROLE: 'Rolünüz bu işlemi yapmaya yetkili değil.',
};

const GENERIC_ERROR_MESSAGE = 'İşlem tamamlanamadı. Sunucu isteği reddetti.';
const OFFLINE_ERROR_MESSAGE = 'Sunucuya ulaşılamıyor. Bağlantı geri geldiğinde tekrar deneyin.';

/** Reads an error code out of a ProblemDetail body, if there is one. */
export function problemErrorCode(error: unknown): string | undefined {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ProblemDetail | null;
    if (body && typeof body === 'object' && typeof body.errorCode === 'string') {
      return body.errorCode;
    }
  }
  return undefined;
}

/**
 * Maps a failed request onto a message that is safe to show. Keeping this in
 * one function means every screen explains the same failure the same way.
 */
export function describeProblem(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return GENERIC_ERROR_MESSAGE;
  }
  if (error.status === 0) {
    return OFFLINE_ERROR_MESSAGE;
  }
  const code = problemErrorCode(error);
  if (code && Object.prototype.hasOwnProperty.call(ERROR_CODE_MESSAGES, code)) {
    return `${ERROR_CODE_MESSAGES[code]} (${code})`;
  }
  return code ? `${GENERIC_ERROR_MESSAGE} (${code})` : GENERIC_ERROR_MESSAGE;
}
