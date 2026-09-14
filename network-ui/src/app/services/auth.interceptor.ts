import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { AuthService } from './auth.service';
import { problemErrorCode } from './siem-api.service';
import { AUTH_REQUIRED_CODE } from './siem.models';

/** Path prefix of the endpoints that must never carry a bearer token. */
export const AUTH_ENDPOINT_PATH = '/api/v1/auth/';

/**
 * True for /api/v1/auth/login and /api/v1/auth/refresh.
 *
 * These are the only calls that must go out bare: attaching an expired access
 * token to the refresh request would make the refresh itself fail the moment
 * the session it is trying to rescue has lapsed.
 */
export function isAuthEndpoint(url: string): boolean {
  return url.includes(AUTH_ENDPOINT_PATH);
}

function withBearer<T>(request: HttpRequest<T>, token: string): HttpRequest<T> {
  return request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Whether a failure means "this request had no usable session".
 *
 * The contract is a 401 carrying AUTH_REQUIRED. A 401 with no error code at
 * all is treated the same way, because a security filter that rejects a
 * request before the controller layer can answer with an empty body — and
 * that case is still an expired session, not a business failure. A 403
 * (INSUFFICIENT_ROLE) is deliberately *not* included: the token is fine, the
 * role is not, and refreshing it would change nothing.
 */
export function isAuthRequiredFailure(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
    return false;
  }
  const code = problemErrorCode(error);
  return code === undefined || code === AUTH_REQUIRED_CODE;
}

/**
 * Attaches the bearer token and recovers from exactly one expired session.
 *
 * On a 401 the interceptor asks AuthService for a single silent refresh and
 * replays the original request once with the new token. There is no loop: the
 * replayed request is not itself wrapped in this recovery, so a second 401 —
 * or a failed refresh — ends the session and returns to the login screen.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  if (isAuthEndpoint(request.url)) {
    return next(request);
  }

  const token = auth.accessToken();
  const authorised = token ? withBearer(request, token) : request;

  return next(authorised).pipe(
    catchError((error: unknown) => {
      if (!isAuthRequiredFailure(error)) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError((refreshError: unknown) => {
          // The refresh token is gone or rejected: nothing left to try.
          auth.logout();
          return throwError(() => refreshError);
        }),
        switchMap((tokens) =>
          next(withBearer(request, tokens.accessToken)).pipe(
            catchError((retryError: unknown) => {
              if (isAuthRequiredFailure(retryError)) {
                // A brand-new token that is still rejected means the session
                // cannot be repaired from here.
                auth.logout();
              }
              return throwError(() => retryError);
            }),
          ),
        ),
      );
    }),
  );
};
