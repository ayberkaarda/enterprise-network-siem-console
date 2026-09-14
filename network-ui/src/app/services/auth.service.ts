import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { finalize, shareReplay, tap } from 'rxjs/operators';

import { SIEM_API_BASE_URL } from './siem-api.service';
import {
  AccessTokenClaims,
  AuthRole,
  AuthUser,
  LoginRequest,
  RESPONDER_ROLES,
  RefreshRequest,
  TokenResponse,
} from './siem.models';

/** Where the two tokens live between reloads. */
export const ACCESS_TOKEN_STORAGE_KEY = 'siem.access-token';
export const REFRESH_TOKEN_STORAGE_KEY = 'siem.refresh-token';

/** Route the console falls back to whenever the session ends. */
export const LOGIN_ROUTE = '/login';

/**
 * Fraction of the access-token lifetime after which a silent refresh is
 * scheduled. Renewing at 80% leaves a full fifth of the window as slack for
 * clock skew and a slow round trip, so an active session never runs into a
 * 401 it could have avoided.
 */
export const REFRESH_AT_LIFETIME_FRACTION = 0.8;

const KNOWN_ROLES: readonly AuthRole[] = ['ADMIN', 'ANALYST', 'VIEWER'];

/**
 * Reads the payload segment of a JWT.
 *
 * This is a decode, not a verification: the signature is never checked here
 * and cannot be, since the key stays on the server. The claims are used to
 * decide what the console *shows*; every action is authorised again server
 * side, so a tampered token buys nothing but a misleading local screen.
 */
export function decodeJwtPayload(token: string): AccessTokenClaims | null {
  const segments = token.split('.');
  if (segments.length !== 3 || segments[1].length === 0) {
    return null;
  }
  try {
    const base64url = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    const padding = (4 - (base64url.length % 4)) % 4;
    const binary = atob(base64url + '='.repeat(padding));
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const parsed: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }
    return parsed as AccessTokenClaims;
  } catch {
    // A malformed token is simply "not signed in"; it never throws into a
    // caller that only wanted to know who the user is.
    return null;
  }
}

/** Maps a raw token onto the identity the console renders, or null. */
export function authUserFromToken(token: string | null): AuthUser | null {
  if (!token) {
    return null;
  }
  const claims = decodeJwtPayload(token);
  if (!claims || typeof claims.sub !== 'string' || claims.sub.length === 0) {
    return null;
  }
  const role = KNOWN_ROLES.find((known) => known === claims.role);
  if (!role) {
    // An unknown role is treated as no session at all rather than silently
    // degrading to the most permissive interpretation.
    return null;
  }
  return { username: claims.sub, role };
}

/** Milliseconds left on a token, or null when it carries no `exp`. */
export function millisUntilExpiry(token: string, now: number = Date.now()): number | null {
  const claims = decodeJwtPayload(token);
  if (!claims || typeof claims.exp !== 'number') {
    return null;
  }
  return claims.exp * 1000 - now;
}

function readStored(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    // Private-mode browsers and blocked storage must not break bootstrap.
    return null;
  }
}

function writeStored(key: string, value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // Session then lasts only as long as the tab; nothing else changes.
  }
}

/**
 * Owns the session: the two tokens, the decoded identity, and the timer that
 * renews the access token before it lapses.
 *
 * Tokens are kept in localStorage. That is a deliberate trade for this stage:
 * the console is a single-page app talking to an API on another origin, and a
 * reload has to survive without a round trip. It also means a cross-site
 * scripting bug would expose them — which is why nothing in the console ever
 * writes unsanitised markup, and why this decision is written down as one to
 * revisit if the tokens ever move to httpOnly cookies.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = inject(SIEM_API_BASE_URL);

  private readonly _accessToken = signal<string | null>(readStored(ACCESS_TOKEN_STORAGE_KEY));
  private readonly _refreshToken = signal<string | null>(readStored(REFRESH_TOKEN_STORAGE_KEY));

  /** Decoded identity, or null when there is no usable access token. */
  readonly currentUser: Signal<AuthUser | null> = computed(() =>
    authUserFromToken(this._accessToken()),
  );

  /** True while an access token that decodes to a known role is held. */
  readonly isAuthenticated: Signal<boolean> = computed(() => this.currentUser() !== null);

  /**
   * Whether the signed-in role may change state. Mirrors the server-side
   * gate on incident transitions, incident comments and the device actions;
   * a VIEWER sees the same console but cannot trigger any of them.
   */
  readonly canRespond: Signal<boolean> = computed(() => {
    const user = this.currentUser();
    return user !== null && RESPONDER_ROLES.includes(user.role);
  });

  private refreshTimer?: ReturnType<typeof setTimeout>;
  private inFlightRefresh: Observable<TokenResponse> | null = null;

  private get authUrl(): string {
    return `${this.baseUrl}/api/v1/auth`;
  }

  constructor() {
    // A reload lands here with whatever localStorage held. If that token is
    // already past its expiry the timer fires immediately and either renews
    // the session or ends it, instead of letting the first REST read fail.
    const token = this._accessToken();
    if (token) {
      this.scheduleRefresh(token, undefined);
    }
  }

  /** Current access token, or null. Read by the interceptor and the socket. */
  accessToken(): string | null {
    return this._accessToken();
  }

  /** Current refresh token, or null. */
  refreshToken(): string | null {
    return this._refreshToken();
  }

  /** Convenience for templates: does the session hold one of these roles? */
  hasRole(...roles: readonly AuthRole[]): boolean {
    const user = this.currentUser();
    return user !== null && roles.includes(user.role);
  }

  /**
   * Exchanges credentials for a token pair. A 401 carrying
   * INVALID_CREDENTIALS surfaces to the caller unchanged; the login screen is
   * what turns it into a message.
   */
  login(username: string, password: string): Observable<TokenResponse> {
    const body: LoginRequest = { username, password };
    return this.http
      .post<TokenResponse>(`${this.authUrl}/login`, body)
      .pipe(tap((tokens) => this.acceptTokens(tokens)));
  }

  /**
   * Renews the pair from the refresh token.
   *
   * Concurrent callers share one request: several parallel reads can each hit
   * a 401 in the same instant, and issuing one refresh per failed request
   * would invalidate the rotating refresh token for all but one of them.
   */
  refresh(): Observable<TokenResponse> {
    const existing = this.inFlightRefresh;
    if (existing) {
      return existing;
    }
    const refreshToken = this._refreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token is stored.'));
    }
    const body: RefreshRequest = { refreshToken };
    const request = this.http.post<TokenResponse>(`${this.authUrl}/refresh`, body).pipe(
      tap((tokens) => this.acceptTokens(tokens)),
      finalize(() => {
        this.inFlightRefresh = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.inFlightRefresh = request;
    return request;
  }

  /** Drops the session and returns to the login screen. */
  logout(options: { redirect?: boolean } = {}): void {
    const { redirect = true } = options;
    this.clearRefreshTimer();
    this.inFlightRefresh = null;
    this._accessToken.set(null);
    this._refreshToken.set(null);
    writeStored(ACCESS_TOKEN_STORAGE_KEY, null);
    writeStored(REFRESH_TOKEN_STORAGE_KEY, null);
    if (redirect) {
      void this.router.navigateByUrl(LOGIN_ROUTE);
    }
  }

  /** Stores a freshly issued pair and re-arms the renewal timer. */
  private acceptTokens(tokens: TokenResponse): void {
    this._accessToken.set(tokens.accessToken);
    this._refreshToken.set(tokens.refreshToken);
    writeStored(ACCESS_TOKEN_STORAGE_KEY, tokens.accessToken);
    writeStored(REFRESH_TOKEN_STORAGE_KEY, tokens.refreshToken);
    this.scheduleRefresh(tokens.accessToken, tokens.expiresIn);
  }

  /**
   * Arms a single timer at 80% of whatever lifetime is known.
   *
   * `expiresIn` is used when the server has just stated it; after a reload
   * only the `exp` claim is left, so the remaining time is used instead. A
   * token that is already past `exp` produces a zero delay — the renewal is
   * attempted at once and a failure ends the session.
   */
  private scheduleRefresh(accessToken: string, expiresInSeconds: number | undefined): void {
    this.clearRefreshTimer();
    const remainingMs = millisUntilExpiry(accessToken);
    const lifetimeMs =
      typeof expiresInSeconds === 'number' && expiresInSeconds > 0
        ? expiresInSeconds * 1000
        : remainingMs;
    if (lifetimeMs === null) {
      // No `exp` and no stated lifetime: nothing sensible to schedule. The
      // interceptor's 401 path remains the safety net.
      return;
    }
    let delay = Math.max(0, Math.round(lifetimeMs * REFRESH_AT_LIFETIME_FRACTION));
    if (remainingMs !== null) {
      delay = Math.min(delay, Math.max(0, remainingMs));
    }
    this.refreshTimer = setTimeout(() => {
      this.refreshTimer = undefined;
      this.refresh().subscribe({
        error: () => this.logout(),
      });
    }, delay);
  }

  private clearRefreshTimer(): void {
    if (this.refreshTimer !== undefined) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = undefined;
    }
  }
}
