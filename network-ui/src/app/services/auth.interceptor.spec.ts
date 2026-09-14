import { beforeEach, describe, expect, it } from 'vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { ProblemDetail, TokenResponse } from './siem.models';

const PROTECTED_URL = 'http://localhost:8080/api/v1/incidents';
const REFRESH_URL = 'http://localhost:8080/api/v1/auth/refresh';

const RENEWED: TokenResponse = {
  accessToken: 'access-2',
  refreshToken: 'refresh-2',
  expiresIn: 900,
};

const AUTH_REQUIRED_BODY: ProblemDetail = {
  status: 401,
  title: 'Unauthorized',
  errorCode: 'AUTH_REQUIRED',
};

const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

/**
 * Stands in for the session. Counting the calls is the point of these tests:
 * the interceptor must renew at most once and must never retry in a loop.
 */
class AuthStub {
  token: string | null = 'access-1';
  refreshCalls = 0;
  logoutCalls = 0;
  refreshResult: Observable<TokenResponse> = of(RENEWED);

  accessToken(): string | null {
    return this.token;
  }

  refresh(): Observable<TokenResponse> {
    this.refreshCalls += 1;
    return this.refreshResult;
  }

  logout(): void {
    this.logoutCalls += 1;
  }
}

describe('authInterceptor', () => {
  let auth: AuthStub;
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    auth = new AuthStub();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('attaches the bearer token to a protected request', () => {
    http.get(PROTECTED_URL).subscribe();

    const request = httpMock.expectOne(PROTECTED_URL);
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-1');
    request.flush({});
    httpMock.verify();
  });

  it('sends the auth endpoints without a token', () => {
    http.post(REFRESH_URL, { refreshToken: 'refresh-1' }).subscribe();

    const request = httpMock.expectOne(REFRESH_URL);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(RENEWED);
    httpMock.verify();
  });

  it('refreshes once and replays the original request with the new token', () => {
    let payload: unknown = null;
    http.get(PROTECTED_URL).subscribe((body) => (payload = body));

    httpMock.expectOne(PROTECTED_URL).flush(AUTH_REQUIRED_BODY, UNAUTHORIZED);

    const retry = httpMock.expectOne(PROTECTED_URL);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush({ ok: true });

    expect(payload).toEqual({ ok: true });
    expect(auth.refreshCalls).toBe(1);
    expect(auth.logoutCalls).toBe(0);
    httpMock.verify();
  });

  it('logs out instead of looping when the replayed request is rejected again', () => {
    let failure: unknown = null;
    http.get(PROTECTED_URL).subscribe({ error: (error: unknown) => (failure = error) });

    httpMock.expectOne(PROTECTED_URL).flush(AUTH_REQUIRED_BODY, UNAUTHORIZED);
    httpMock.expectOne(PROTECTED_URL).flush(AUTH_REQUIRED_BODY, UNAUTHORIZED);

    // Exactly one renewal, exactly one replay, then the session ends. A third
    // request would be caught here: verify() fails on any outstanding call.
    expect(auth.refreshCalls).toBe(1);
    expect(auth.logoutCalls).toBe(1);
    expect(failure).not.toBeNull();
    httpMock.verify();
  });

  it('logs out when the refresh itself fails', () => {
    auth.refreshResult = throwError(() => new Error('refresh rejected'));
    let failure: unknown = null;
    http.get(PROTECTED_URL).subscribe({ error: (error: unknown) => (failure = error) });

    httpMock.expectOne(PROTECTED_URL).flush(AUTH_REQUIRED_BODY, UNAUTHORIZED);

    expect(auth.refreshCalls).toBe(1);
    expect(auth.logoutCalls).toBe(1);
    expect(failure).toBeInstanceOf(Error);
    httpMock.verify();
  });

  it('does not try to renew a 403 that the role simply does not allow', () => {
    let failure: unknown = null;
    http.post(PROTECTED_URL, {}).subscribe({ error: (error: unknown) => (failure = error) });

    httpMock
      .expectOne(PROTECTED_URL)
      .flush(
        { status: 403, errorCode: 'INSUFFICIENT_ROLE' },
        { status: 403, statusText: 'Forbidden' },
      );

    expect(auth.refreshCalls).toBe(0);
    expect(auth.logoutCalls).toBe(0);
    expect(failure).not.toBeNull();
    httpMock.verify();
  });
});
