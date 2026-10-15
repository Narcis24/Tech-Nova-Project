import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';

import { AuthApi, Credentials, RegisterRequest, RegisterResponse, TokenResponse } from '../generated/auth-api-client';

export type LoginRequest = Credentials;

const REFRESH_TOKEN_KEY = 'technova.refreshToken';

/**
 * Holds the JWT session for the UI.
 *
 * The access token lives only in memory, so a script injected into the page (XSS) cannot read it from storage.
 * The refresh token goes in localStorage so the session survives a reload. An HttpOnly cookie would be safer,
 * but the auth service returns both tokens in the response body, so the browser cannot store them that way.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);

  private readonly accessToken = signal<string | null>(null);
  private refreshInFlight$: Observable<string> | null = null;
  readonly isAuthenticated = computed(() => this.accessToken() !== null);
  readonly username = computed(() => usernameFrom(this.accessToken()));

  login(credentials: LoginRequest): Observable<void> {
    return this.authApi.login(credentials).pipe(
      tap(tokens => this.storeTokens(tokens)),
      map(() => undefined),
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.authApi.register(request);
  }

  /** Always succeeds for a well-formed email, whether or not an account uses it (202). */
  forgotPassword(email: string): Observable<void> {
    return this.authApi.forgotPassword({ email });
  }

  /** Sets a new password with the token from a reset link. Fails with 400 if the link is invalid or expired. */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.authApi.resetPassword({ token, newPassword });
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  /**
   * Gets a new access token. Callers that arrive while a refresh is running share it,
   * since the backend revokes each refresh token after one use.
   */
  refresh(): Observable<string> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }

    this.refreshInFlight$ = this.authApi.refresh({ refreshToken }).pipe(
      tap(tokens => this.storeTokens(tokens)),
      map(tokens => tokens.accessToken),
      finalize(() => (this.refreshInFlight$ = null)),
      shareReplay(1),
    );
    return this.refreshInFlight$;
  }

  /**
   * Ends the session locally straight away. The server call only revokes the refresh token,
   * so the UI logs out even if it fails.
   */
  logout(): void {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (refreshToken) {
      this.authApi.logout({ refreshToken }).subscribe({ error: () => {} });
    }
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  /**
   * Restores the session after a reload by exchanging the stored refresh token.
   * Never errors: a failed restore leaves the user logged out.
   */
  restoreSession(): Observable<void> {
    if (!localStorage.getItem(REFRESH_TOKEN_KEY)) {
      return of(undefined);
    }
    return this.refresh().pipe(
      map(() => undefined),
      catchError(() => {
        this.clearSession();
        return of(undefined);
      }),
    );
  }

  private storeTokens(tokens: TokenResponse): void {
    this.accessToken.set(tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  private clearSession(): void {
    this.accessToken.set(null);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

function usernameFrom(token: string | null): string | null {
  const payload = token?.split('.')[1];
  if (!payload) {
    return null;    
  }

  try {
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const stub: unknown = JSON.parse(json).sub;
    return typeof stub === 'string' ? stub : null;
  } catch {
    return null;
  }
}