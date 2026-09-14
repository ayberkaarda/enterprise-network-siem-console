import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './services/auth.guard';

/**
 * Two screens: the login page and the console behind it.
 *
 * Both are loaded lazily, which also means the console's bundle is never
 * fetched for a visitor who cannot get past the guard. The catch-all keeps a
 * stale bookmark on the console root rather than on a blank page; the guard
 * then decides whether that resolves to the console or to the login screen.
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Giriş · Enterprise Network SIEM Console',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.LoginPage),
  },
  {
    path: '',
    title: 'Enterprise Network SIEM Console',
    canActivate: [authGuard],
    loadComponent: () => import('./features/console/console').then((m) => m.ConsolePage),
  },
  { path: '**', redirectTo: '' },
];
