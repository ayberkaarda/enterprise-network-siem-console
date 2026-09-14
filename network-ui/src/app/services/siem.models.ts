/* ==========================================================================
   Shared transport types for the v1 SIEM API and the STOMP topics.

   These shapes mirror the backend contract exactly; they are the single
   place the frontend describes what the server sends. Anything that talks
   to /api/v1/** or /topic/** imports from here instead of re-declaring a
   local interface, so a contract change breaks in one file.
   ========================================================================== */

/** Spring Data page envelope as returned by every paginated v1 endpoint. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type IncidentSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

/** Ordered lifecycle. A transition is only offered to the immediate successor. */
export const INCIDENT_LIFECYCLE: readonly IncidentStatus[] = [
  'OPEN',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'RESOLVED',
  'CLOSED',
];

/**
 * GET /api/v1/incidents content item.
 *
 * Every field except id/title/severity/status/createdAt/updatedAt is optional
 * because the /topic/incidents push carries a reduced projection of the same
 * entity; a pushed record is merged on top of whatever the REST read already
 * provided rather than replacing it wholesale.
 */
export interface Incident {
  id: number;
  title: string;
  description?: string | null;
  severity: IncidentSeverity;
  status: IncidentStatus;
  sourceDeviceId?: number | null;
  mitreTechniqueId?: string | null;
  assignee?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** /topic/incidents payload — a subset of Incident, same field names. */
export interface IncidentRealtimeUpdate {
  id: number;
  title: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  sourceDeviceId?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface IncidentComment {
  id: number;
  incidentId: number;
  author: string;
  body: string;
  createdAt: string;
}

/** POST /api/v1/incidents/{id}/transition request body. */
export interface IncidentTransitionRequest {
  newStatus: IncidentStatus;
}

/** POST /api/v1/incidents/{id}/comments request body. */
export interface IncidentCommentRequest {
  author: string;
  body: string;
}

/** GET /api/v1/events content item. */
export interface IngestedEvent {
  id: number;
  source: string;
  category: string;
  severity: string;
  rawPayload: string;
  occurredAt: string;
  receivedAt: string;
}

/** GET /api/v1/devices content item — flat, same fields as the legacy device. */
export interface DeviceResponse {
  id: number;
  name: string;
  ipAddress: string;
  status?: string;
  latency?: number;
  deviceType?: string;
}

/** /topic/devices payload — a DeviceResponse plus the change timestamp. */
export interface DeviceRealtimeUpdate extends DeviceResponse {
  changedAt?: string;
}

/** /topic/metrics payload — server-computed snapshot, roughly every 10s. */
export interface MetricsSnapshot {
  totalDevices: number;
  reachableDevices: number;
  openIncidents: number;
  criticalOrHighIncidents: number;
  avgLatencyMs: number;
  timestamp: string;
}

/* --------------------------------------------------------------------------
   Authentication contract — /api/v1/auth/**

   The access token is a JWT whose payload carries `sub` (username), `role`
   (one of the three console roles) and the standard `exp`. The console reads
   those claims to drive its own UI; it never treats them as proof of
   anything, because the signature is only verified on the server.
   -------------------------------------------------------------------------- */

export type AuthRole = 'ADMIN' | 'ANALYST' | 'VIEWER';

/** Roles allowed to change state: incident transitions, comments, device actions. */
export const RESPONDER_ROLES: readonly AuthRole[] = ['ADMIN', 'ANALYST'];

/** POST /api/v1/auth/login request body. */
export interface LoginRequest {
  username: string;
  password: string;
}

/** POST /api/v1/auth/refresh request body. */
export interface RefreshRequest {
  refreshToken: string;
}

/** Response body of both /auth/login and /auth/refresh. `expiresIn` is seconds. */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/** Claims the console reads out of the access token payload. */
export interface AccessTokenClaims {
  sub?: string;
  role?: string;
  exp?: number;
}

/** The signed-in identity as the console understands it. */
export interface AuthUser {
  username: string;
  role: AuthRole;
}

/** Stable error codes the auth layer reacts to, rather than matching on text. */
export const INVALID_CREDENTIALS_CODE = 'INVALID_CREDENTIALS';
export const AUTH_REQUIRED_CODE = 'AUTH_REQUIRED';
export const INSUFFICIENT_ROLE_CODE = 'INSUFFICIENT_ROLE';

/** RFC 7807 problem response, extended with the backend's stable error code. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
}
