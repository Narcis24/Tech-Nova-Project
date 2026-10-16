import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { API_BASE } from '../core/api-urls';
import { AuthService } from '../services/auth.service';

/**
 * Attaches the access token to trading API requests only, so it never leaks to the auth service or third-party hosts.
 * On a 401 it refreshes the token and retries the request once; if the refresh fails, the user is logged out.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  if (!isApiRequest(req.url)) {
    return next(req);
  }

  return next(withToken(req, auth.getAccessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError((refreshError) => {
          auth.logout();
          return throwError(() => refreshError);
        }),
        // Outside the first catchError, so a 401 on the retry goes to the caller and is not refreshed again
        switchMap((token) => next(withToken(req, token))),
      );
    }),
  );
};

function isApiRequest(url: string): boolean {
  return url === API_BASE || url.startsWith(`${API_BASE}/`);
}

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}
