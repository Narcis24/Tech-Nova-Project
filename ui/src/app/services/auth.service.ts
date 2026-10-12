import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';

import { AUTH_BASE } from '../core/api-urls';

export interface LoginRequest {
  username: string;
  password: string;
}

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

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
  private readonly http = inject(HttpClient);

  private readonly accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  login(credentials: LoginRequest): Observable<void> {
    return this.http.post<TokenResponse>(`${AUTH_BASE}/login`, credentials).pipe(
      tap(tokens => this.storeTokens(tokens)),
      map(() => undefined),
    );
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  private storeTokens(tokens: TokenResponse): void {
    this.accessToken.set(tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }
}
