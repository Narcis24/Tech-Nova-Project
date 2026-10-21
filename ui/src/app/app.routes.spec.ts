import { computed, signal } from '@angular/core';
import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import {
  AccountControllerService,
  AdminUserControllerService,
  DashboardControllerService,
  OrderControllerService,
  PriceControllerService,
  InstrumentControllerService,
  PieControllerService,
  PortfolioControllerService,
  PublicPieControllerService,
  RiskControllerService,
  RiskLimitControllerService,
} from './generated/trading-api-client';
import { routes } from './app.routes';
import { safeReturnUrl } from './core/return-url';
import { AccountService } from './services/account.service';
import { AuthService } from './services/auth.service';

// Runs the real route table and guards, with only the services faked
describe('app routes', () => {
  const isAuthenticated = signal(false);
  const isAdmin = signal(false);
  // The shell loads the accounts and its nav bar shows the username
  const auth = {
    isAuthenticated,
    isAdmin,
    homeUrl: computed(() => (isAdmin() ? '/admin' : '/dashboard')),
    username: signal(null),
  };
  const accounts = { accounts: signal([]), currentAccountId: signal(null), loadAccounts: () => of(undefined) };
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
    isAuthenticated.set(false);
    isAdmin.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: PriceControllerService, useValue: { getPriceHistory: () => of([]) } },
        { provide: DashboardControllerService, useValue: { getMarketOverview: () => of([]) } },
        { provide: OrderControllerService, useValue: { placeOrder: () => of({}) } },
        { provide: RiskControllerService, useValue: { check: () => of([]) } },
        { provide: RiskLimitControllerService, useValue: { getLimits: () => of([]) } },
        {
          provide: PortfolioControllerService,
          useValue: { getPortfolioMetrics: () => of({}), getPortfolioSnapshot: () => of({}) },
        },
        { provide: AccountControllerService, useValue: { getAccountOrders: () => of([]) } },
        { provide: InstrumentControllerService, useValue: { getInstruments: () => of([]) } },
        { provide: PieControllerService, useValue: { get: () => of({ pieId: 'p1', name: 'Tech Growth' }) } },
        {
          provide: PublicPieControllerService,
          useValue: {
            listPublicPies: () => of([]),
            getPublicPie: () => of({ pieId: 'p1', name: 'Public Growth', authorUsername: 'ada' }),
          },
        },
        { provide: AdminUserControllerService, useValue: { listUsers: () => of([]) } },
        { provide: AuthService, useValue: auth },
        { provide: AccountService, useValue: accounts },
      ],
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

    it('sends / straight to /login, so each user lands on their own home page after logging in', async () => {
      await harness.navigateByUrl('/');

      expect(router.url).toBe('/login');
    });

    it('shows the login page', async () => {
      await harness.navigateByUrl('/login');

      expect(router.url).toBe('/login');
      expect(harness.routeNativeElement?.textContent).toContain('Welcome back');
    });

    it('lands on the returnUrl after logging in', async () => {
      await harness.navigateByUrl('/dashboard');
      isAuthenticated.set(true);

      const returnUrl = router.parseUrl(router.url).queryParamMap.get('returnUrl');
      await harness.navigateByUrl(safeReturnUrl(returnUrl));

      expect(router.url).toBe('/dashboard');
      expect(harness.routeNativeElement?.textContent).toContain('Portfolio');
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
      expect(harness.routeNativeElement?.textContent).toContain('Portfolio');
    });

    it('sends /forgot-password to /dashboard', async () => {
      await harness.navigateByUrl('/forgot-password');

      expect(router.url).toBe('/dashboard');
    });

    it('still opens /reset-password, so the link from the email works', async () => {
      await harness.navigateByUrl('/reset-password#token=abc');

      // The page took the token out of the address bar
      expect(TestBed.inject(Location).path(true)).toBe('/reset-password');
      expect(harness.routeNativeElement?.textContent).toContain('Choose a new password');
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

    it('shows the accounts page', async () => {
      await harness.navigateByUrl('/accounts');

      expect(router.url).toBe('/accounts');
      expect(harness.routeNativeElement?.textContent).toContain('Accounts');
    });

    it('shows the risk limits page', async () => {
      await harness.navigateByUrl('/risk-limits');

      expect(router.url).toBe('/risk-limits');
      expect(harness.routeNativeElement?.textContent).toContain('Risk limits');
    });

    it('opens the new pie editor, not the pie detail', async () => {
      await harness.navigateByUrl('/pies/new');

      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('New pie');
    });

    it('shows one of my pies', async () => {
      await harness.navigateByUrl('/pies/p1');

      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Tech Growth');
    });

    it('shows public templates in Discover', async () => {
      await harness.navigateByUrl('/discover');

      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Discover');
    });

    it('shows a public pie', async () => {
      await harness.navigateByUrl('/discover/p1');

      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Public Growth');
    });

    it('sends / to /dashboard for a trader', async () => {
      await harness.navigateByUrl('/');

      expect(router.url).toBe('/dashboard');
    });

    it('sends /admin to /dashboard for a trader', async () => {
      await harness.navigateByUrl('/admin');

      expect(router.url).toBe('/dashboard');
    });

    it('wraps the page in the shell', async () => {
      await harness.navigateByUrl('/dashboard');

      const shell = harness.fixture.nativeElement.querySelector('app-shell');
      expect(shell).not.toBeNull();
    });
  });

  describe('when logged in as an admin', () => {
    beforeEach(() => {
      isAuthenticated.set(true);
      isAdmin.set(true);
    });

    it('sends / to /admin', async () => {
      await harness.navigateByUrl('/');

      expect(router.url).toBe('/admin');
    });

    it('sends /login to /admin', async () => {
      await harness.navigateByUrl('/login');

      expect(router.url).toBe('/admin');
    });

    it('shows the admin page inside the shell', async () => {
      await harness.navigateByUrl('/admin');

      expect(router.url).toBe('/admin');
      expect(harness.routeNativeElement?.textContent).toContain('Admin');
      expect(harness.fixture.nativeElement.querySelector('app-shell')).not.toBeNull();
    });

    it('shows the users page', async () => {
      await harness.navigateByUrl('/admin/users');

      expect(router.url).toBe('/admin/users');
      expect(harness.routeNativeElement?.textContent).toContain('Create admin');
    });

    for (const url of ['/dashboard', '/trade', '/orders', '/orders/ORD-1', '/accounts', '/risk-limits']) {
      it(`sends the trading page ${url} to /admin`, async () => {
        await harness.navigateByUrl(url);

        expect(router.url).toBe('/admin');
      });
    }
  });

  it('sends /admin to /login when logged out', async () => {
    await harness.navigateByUrl('/admin');

    expect(router.url).toBe('/login?returnUrl=%2Fadmin');
  });

  it('shows the 404 page for unknown URLs', async () => {
    await harness.navigateByUrl('/no-such-page');

    expect(router.url).toBe('/no-such-page');
    expect(harness.routeNativeElement?.textContent).toContain('Page not found');
  });
});
