import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './services/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // Every outgoing call carries the bearer token and recovers from a single
    // expired session; the auth endpoints themselves are excluded inside the
    // interceptor so a refresh never travels with a stale token.
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
