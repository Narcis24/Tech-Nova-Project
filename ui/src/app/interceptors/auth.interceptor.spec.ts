import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

const LOGIN_URL = '/auth/v1/auth/login';
const REFRESH_URL = '/auth/v1/auth/refresh';
const LOGOUT_URL = '/auth/v1/auth/logout';
const ORDERS_URL = '/api/v1/orders';

const TOKENS = { accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900 };
const NEW_TOKENS = { accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900 };
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

// Uses the real AuthService, so "concurrent 401s share one refresh" is checked end to end
describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    localStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    router.navigateByUrl.and.resolveTo(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function logIn(): void {
    auth.login({ username: 'trader01', password: 'Trader.Pass01' }).subscribe();
    http.expectOne(LOGIN_URL).flush(TOKENS);
  }

  describe('attaching the token', () => {
    it('adds the Bearer token to API requests', () => {
      logIn();

      client.get(ORDERS_URL).subscribe();

      const req = http.expectOne(ORDERS_URL);
      expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
      req.flush([]);
    });

    it('sends API requests without a header when logged out', () => {
      client.get(ORDERS_URL).subscribe();

      const req = http.expectOne(ORDERS_URL);
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush([]);
    });

    it('never adds the token to auth service requests', () => {
      logIn();

      client.post(REFRESH_URL, {}).subscribe();

      const req = http.expectOne(REFRESH_URL);
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush(NEW_TOKENS);
    });

    it('never adds the token to other hosts or look-alike paths', () => {
      logIn();

      client.get('https://example.com/api/data').subscribe();
      client.get('/apis-other/data').subscribe();

      const thirdParty = http.expectOne('https://example.com/api/data');
      const lookAlike = http.expectOne('/apis-other/data');
      expect(thirdParty.request.headers.has('Authorization')).toBeFalse();
      expect(lookAlike.request.headers.has('Authorization')).toBeFalse();
      thirdParty.flush({});
      lookAlike.flush({});
    });
  });

  describe('handling a 401', () => {
    it('refreshes the token and retries the request with the new one', () => {
      logIn();
      let orders: unknown;
      client.get(ORDERS_URL).subscribe((body) => (orders = body));

      http.expectOne(ORDERS_URL).flush(null, UNAUTHORIZED);
      http.expectOne(REFRESH_URL).flush(NEW_TOKENS);

      const retry = http.expectOne(ORDERS_URL);
      expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
      retry.flush([{ id: 1 }]);
      expect(orders).toEqual([{ id: 1 }]);
    });

    it('shares one refresh between concurrent 401s', () => {
      logIn();
      client.get(ORDERS_URL).subscribe();
      client.get(ORDERS_URL).subscribe();

      http.match(ORDERS_URL).forEach((req) => req.flush(null, UNAUTHORIZED));
      http.expectOne(REFRESH_URL).flush(NEW_TOKENS);

      const retries = http.match(ORDERS_URL);
      expect(retries.length).toBe(2);
      retries.forEach((req) => {
        expect(req.request.headers.get('Authorization')).toBe('Bearer access-2');
        req.flush([]);
      });
    });

    it('logs out and fails the request when the refresh fails', () => {
      logIn();
      let error: unknown;
      client.get(ORDERS_URL).subscribe({ error: (e) => (error = e) });

      http.expectOne(ORDERS_URL).flush(null, UNAUTHORIZED);
      http.expectOne(REFRESH_URL).flush(null, UNAUTHORIZED);
      http.expectOne(LOGOUT_URL).flush(null, { status: 204, statusText: 'No Content' });

      expect(error).toBeInstanceOf(HttpErrorResponse);
      expect(auth.isAuthenticated()).toBeFalse();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    });

    it('retries at most once', () => {
      logIn();
      let error: HttpErrorResponse | undefined;
      client.get(ORDERS_URL).subscribe({ error: (e) => (error = e) });

      http.expectOne(ORDERS_URL).flush(null, UNAUTHORIZED);
      http.expectOne(REFRESH_URL).flush(NEW_TOKENS);
      http.expectOne(ORDERS_URL).flush(null, UNAUTHORIZED);

      // verify() in afterEach fails if a second refresh was sent
      expect(error?.status).toBe(401);
    });

    it('passes other errors through without refreshing', () => {
      logIn();
      let error: HttpErrorResponse | undefined;
      client.get(ORDERS_URL).subscribe({ error: (e) => (error = e) });

      http.expectOne(ORDERS_URL).flush(null, { status: 500, statusText: 'Server Error' });

      http.expectNone(REFRESH_URL);
      expect(error?.status).toBe(500);
    });
  });
});
