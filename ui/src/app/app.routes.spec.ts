import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { safeReturnUrl } from './core/return-url';
import { AuthService } from './services/auth.service';

// Runs the real route table and guards, with only AuthService faked
describe('app routes', () => {
  const isAuthenticated = signal(false);
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
    isAuthenticated.set(false);
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), { provide: AuthService, useValue: { isAuthenticated } }],
    });
    harness = await RouterTestingHarness.create();
    router = TestBed.inject(Router);
  });

  describe('when logged out', () => {
    it('sends /dashboard to /login with the attempted URL as returnUrl', async () => {
      await harness.navigateByUrl('/dashboard');

      expect(router.url).toBe('/login?returnUrl=%2Fdashboard');
    });

    it('keeps the query string in returnUrl', async () => {
      await harness.navigateByUrl('/dashboard?tab=orders');

      expect(router.url).toBe('/login?returnUrl=%2Fdashboard%3Ftab%3Dorders');
    });

    it('sends / to /login through the dashboard redirect', async () => {
      await harness.navigateByUrl('/');

      expect(router.url).toBe('/login?returnUrl=%2Fdashboard');
    });

    it('shows the login page', async () => {
      await harness.navigateByUrl('/login');

      expect(router.url).toBe('/login');
      expect(harness.routeNativeElement?.textContent).toContain('Login');
    });

    it('lands on the returnUrl after logging in', async () => {
      await harness.navigateByUrl('/dashboard');
      isAuthenticated.set(true);

      const returnUrl = router.parseUrl(router.url).queryParamMap.get('returnUrl');
      await harness.navigateByUrl(safeReturnUrl(returnUrl));

      expect(router.url).toBe('/dashboard');
      expect(harness.routeNativeElement?.textContent).toContain('Dashboard');
    });

    it('shows no shell on the login page', async () => {
      await harness.navigateByUrl('/login');

      expect(router.url).toBe('/login');
      const shell = harness.fixture.nativeElement.querySelector('app-shell');
      expect(shell).toBeNull();
    });
  });

  describe('when logged in', () => {
    beforeEach(() => isAuthenticated.set(true));

    it('shows the dashboard', async () => {
      await harness.navigateByUrl('/dashboard');

      expect(router.url).toBe('/dashboard');
      expect(harness.routeNativeElement?.textContent).toContain('Dashboard');
    });

    it('sends /login to /dashboard', async () => {
      await harness.navigateByUrl('/login');

      expect(router.url).toBe('/dashboard');
    });

    it('shows the trade page', async () => {
      await harness.navigateByUrl('/trade');

      expect(router.url).toBe('/trade');
      expect(harness.routeNativeElement?.textContent).toContain('Trade');
    });

    it('shows the orders page', async () => {
      await harness.navigateByUrl('/orders');

      expect(router.url).toBe('/orders');
      expect(harness.routeNativeElement?.textContent).toContain('Orders');
    });

    it('wraps the page in the shell', async () => {
      await harness.navigateByUrl('/dashboard');

      const shell = harness.fixture.nativeElement.querySelector('app-shell');
      expect(shell).not.toBeNull();
    });
  });

  it('shows the 404 page for unknown URLs', async () => {
    await harness.navigateByUrl('/no-such-page');

    expect(router.url).toBe('/no-such-page');
    expect(harness.routeNativeElement?.textContent).toContain('Page not found');
  });
});
