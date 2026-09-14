import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthService, LOGIN_ROUTE } from './auth.service';

/** Query parameter carrying the page the user was heading for. */
export const REDIRECT_PARAM = 'redirectTo';

/**
 * Keeps the console behind a session.
 *
 * This is a usability gate, not a security boundary: every endpoint the
 * console calls is authorised again on the server. What the guard buys is
 * that an unauthenticated visitor lands on the login screen instead of an
 * empty console firing a burst of doomed requests.
 */
export const authGuard: CanActivateFn = (_route, state): boolean | UrlTree => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree([LOGIN_ROUTE], {
    queryParams: { [REDIRECT_PARAM]: state.url },
  });
};

/** Sends an already signed-in user away from the login screen. */
export const guestGuard: CanActivateFn = (): boolean | UrlTree => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/']) : true;
};
