import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { API_BASE } from './core/api-urls';
import { provideApi as provideTradingApi } from './generated/trading-api-client';
import { AuthService } from './services/auth.service';
import { authInterceptor } from './interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(withInterceptors([authInterceptor])),
    // The generated trading client defaults to the spec's http://localhost:8081/api; use the relative path
    // so requests go through the dev proxy and work on any host. The auth client's default, /auth, is already relative.
    provideTradingApi(API_BASE),
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
