import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';

const LOGIN_URL = '/auth/v1/auth/login';
const REFRESH_URL = '/auth/v1/auth/refresh';
const LOGOUT_URL = '/auth/v1/auth/logout';
const FORGOT_URL = '/auth/v1/auth/forgot-password';
const RESET_URL = '/auth/v1/auth/reset-password';
const REFRESH_TOKEN_KEY = 'technova.refreshToken';

const CREDENTIALS = { username: 'trader01', password: 'Trader.Pass01' };
const TOKENS = { accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 };
const NEW_TOKENS = { accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 };
// Only the payload is real: username() reads it without checking the signature
const JWT = `header.${btoa(JSON.stringify({ sub: 'trader01' }))}.signature`;

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    localStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    router.navigateByUrl.and.resolveTo(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function logIn(): void {
    service.login(CREDENTIALS).subscribe();
    http.expectOne(LOGIN_URL).flush(TOKENS);
  }

  describe('login', () => {
    it('posts the credentials and stores both tokens', () => {
      service.login(CREDENTIALS).subscribe();

      const req = http.expectOne(LOGIN_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(CREDENTIALS);
      req.flush(TOKENS);

      expect(service.getAccessToken()).toBe('access-1');
      expect(service.isAuthenticated()).toBeTrue();
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-1');
    });

    it('stores nothing when the credentials are rejected', () => {
      let failed = false;
      service.login(CREDENTIALS).subscribe({ error: () => (failed = true) });

      http.expectOne(LOGIN_URL).flush({ message: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });

      expect(failed).toBeTrue();
      expect(service.isAuthenticated()).toBeFalse();
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    });
  });

  describe('password reset', () => {
    it('asks for a reset link with only the email', () => {
      let done = false;
      service.forgotPassword('john.doe@example.com').subscribe(() => (done = true));

      const req = http.expectOne(FORGOT_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'john.doe@example.com' });
      req.flush(null, { status: 202, statusText: 'Accepted' });
      expect(done).toBeTrue();
    });

    it('sends the token and the new password', () => {
      service.resetPassword('reset-token', 'NewPassword123').subscribe();

      const req = http.expectOne(RESET_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ token: 'reset-token', newPassword: 'NewPassword123' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('passes a rejected reset link on to the caller', () => {
      let status = 0;
      service.resetPassword('bad-token', 'NewPassword123').subscribe({ error: err => (status = err.status) });

      http.expectOne(RESET_URL).flush({ message: 'Invalid or expired reset link' }, { status: 400, statusText: 'Bad Request' });
      expect(status).toBe(400);
    });
  });

  describe('refresh', () => {
    it('sends the stored refresh token and stores the rotated pair', () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1');
      let token: string | undefined;
      service.refresh().subscribe(t => (token = t));

      const req = http.expectOne(REFRESH_URL);
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(NEW_TOKENS);

      expect(token).toBe('access-2');
      expect(service.getAccessToken()).toBe('access-2');
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-2');
    });

    it('shares one request between concurrent callers', () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1');
      const tokens: string[] = [];
      service.refresh().subscribe(t => tokens.push(t));
      service.refresh().subscribe(t => tokens.push(t));
      service.refresh().subscribe(t => tokens.push(t));

      http.expectOne(REFRESH_URL).flush(NEW_TOKENS);

      expect(tokens).toEqual(['access-2', 'access-2', 'access-2']);
    });

    it('starts a new request once the previous one has finished', () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1');
      service.refresh().subscribe();
      http.expectOne(REFRESH_URL).flush(NEW_TOKENS);

      service.refresh().subscribe();

      expect(http.expectOne(REFRESH_URL).request.body).toEqual({ refreshToken: 'refresh-2' });
    });

    it('errors without a request when there is no refresh token', () => {
      let failed = false;
      service.refresh().subscribe({ error: () => (failed = true) });

      http.expectNone(REFRESH_URL);
      expect(failed).toBeTrue();
    });
  });

  describe('logout', () => {
    it('revokes the refresh token, clears the session and goes to /login', () => {
      logIn();

      service.logout();

      const req = http.expectOne(LOGOUT_URL);
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(null, { status: 204, statusText: 'No Content' });
      expect(service.isAuthenticated()).toBeFalse();
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    });

    it('clears the session even if the server call fails', () => {
      logIn();

      service.logout();

      http.expectOne(LOGOUT_URL).flush(null, { status: 500, statusText: 'Server Error' });
      expect(service.isAuthenticated()).toBeFalse();
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    });
  });

  describe('username', () => {
    it('reads sub from the access token', () => {
      service.login(CREDENTIALS).subscribe();
      http.expectOne(LOGIN_URL).flush({ ...TOKENS, accessToken: JWT });

      expect(service.username()).toBe('trader01');
    });

    it('is null when logged out', () => {
      expect(service.username()).toBeNull();
    });

    it('is null when the token is not a JWT', () => {
      logIn();

      expect(service.username()).toBeNull();
    });
  });

  describe('restoreSession', () => {
    it('finishes straight away without a request when there is no refresh token', () => {
      let done = false;
      service.restoreSession().subscribe({ complete: () => (done = true) });

      http.expectNone(REFRESH_URL);
      expect(done).toBeTrue();
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('restores the session from the stored refresh token', () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1');
      service.restoreSession().subscribe();

      http.expectOne(REFRESH_URL).flush(NEW_TOKENS);

      expect(service.isAuthenticated()).toBeTrue();
      expect(service.getAccessToken()).toBe('access-2');
    });

    it('clears a dead refresh token without erroring', () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'expired');
      let failed = false;
      let done = false;
      service.restoreSession().subscribe({ error: () => (failed = true), complete: () => (done = true) });

      http.expectOne(REFRESH_URL).flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(failed).toBeFalse();
      expect(done).toBeTrue();
      expect(service.isAuthenticated()).toBeFalse();
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    });
  });
});
