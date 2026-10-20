import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { AdminUserControllerService, AdminUserResponse } from '../../generated/trading-api-client';
import { AuthService } from '../../services/auth.service';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { AnalyticsFiltersComponent } from './analytics/analytics-filters/analytics-filters.component';
import { AnalyticsOverviewComponent } from './analytics/analytics-overview/analytics-overview.component';
import { AnalyticsStore } from './analytics/analytics.store';
import { AssetMixComponent } from './analytics/asset-mix/asset-mix.component';
import { LivePanelComponent } from './analytics/live-panel/live-panel.component';
import { TopTradedInstrumentsComponent } from './analytics/top-traded-instruments/top-traded-instruments.component';

// Stand in for the analytics widgets and their store, which have their own specs
@Component({ selector: 'app-analytics-filters', template: '<ng-content />' })
class AnalyticsFiltersStub {}

@Component({ selector: 'app-analytics-overview', template: '' })
class AnalyticsOverviewStub {}

@Component({ selector: 'app-live-panel', template: '' })
class LivePanelStub {}

@Component({ selector: 'app-top-traded-instruments', template: '' })
class TopTradedInstrumentsStub {}

@Component({ selector: 'app-asset-mix', template: '' })
class AssetMixStub {}

const USERS: AdminUserResponse[] = [
  { username: 'test_admin', email: 'test_admin@technova.local', roles: ['ADMIN'], createdAt: '2026-10-19T09:25:14Z' },
  {
    username: 'invited_01',
    email: 'invited.01@example.com',
    roles: ['TRADER'],
    createdAt: '2026-10-19T10:56:24Z',
    createdBy: 'test_admin',
  },
  {
    username: 'trader_one',
    email: 'trader_one@example.com',
    roles: ['ADMIN', 'TRADER'],
    createdAt: '2026-10-19T09:25:31Z',
  },
];

describe('AdminDashboardComponent', () => {
  let fixture: ComponentFixture<AdminDashboardComponent>;
  let listUsers: jasmine.Spy<() => Observable<AdminUserResponse[]>>;

  function create(result: Observable<AdminUserResponse[]>): HTMLElement {
    listUsers = jasmine.createSpy('listUsers').and.returnValue(result);
    TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        // The "Manage users" routerLink needs a router to build its href
        provideRouter([]),
        { provide: AdminUserControllerService, useValue: { listUsers } },
        { provide: AuthService, useValue: { username: signal('test_admin') } },
      ],
    });
    TestBed.overrideComponent(AdminDashboardComponent, {
      remove: {
        imports: [
          AnalyticsFiltersComponent,
          AnalyticsOverviewComponent,
          LivePanelComponent,
          TopTradedInstrumentsComponent,
          AssetMixComponent,
        ],
      },
      add: {
        imports: [AnalyticsFiltersStub, AnalyticsOverviewStub, LivePanelStub, TopTradedInstrumentsStub, AssetMixStub],
      },
    });
    TestBed.overrideProvider(AnalyticsStore, { useValue: {} });
    fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function texts(page: HTMLElement, selector: string): string[] {
    return Array.from(page.querySelectorAll(selector)).map((el) => el.textContent?.replace(/\s+/g, ' ').trim() ?? '');
  }

  it('greets the signed-in admin', () => {
    const page = create(of(USERS));

    expect(page.textContent).toContain('Signed in as test_admin');
  });

  it('links to the users page', () => {
    const page = create(of(USERS));

    const link = page.querySelector<HTMLAnchorElement>('.manage-link')!;
    expect(link.textContent).toContain('Manage users');
    expect(link.getAttribute('href')).toBe('/admin/users');
  });

  it('counts users, admins, traders and admin-created accounts', () => {
    const page = create(of(USERS));

    expect(texts(page, '.stat-label')).toEqual(['Users', 'Admins', 'Traders', 'Created by admins']);
    expect(texts(page, '.stat-value')).toEqual(['3', '2', '2', '1']);
  });

  it("doesn't list individual users; that's the users page", () => {
    const page = create(of(USERS));

    expect(page.textContent).not.toContain('Newest users');
    expect(page.textContent).not.toContain('invited.01@example.com');
  });

  it('shows loading until the users arrive', () => {
    const users = new Subject<AdminUserResponse[]>();
    const page = create(users);

    expect(page.textContent).toContain('Loading users');

    users.next(USERS);
    fixture.detectChanges();

    expect(page.textContent).not.toContain('Loading users');
    expect(texts(page, '.stat')).toHaveSize(4);
  });

  it('counts zero when there are no users', () => {
    const page = create(of([]));

    expect(texts(page, '.stat-value')).toEqual(['0', '0', '0', '0']);
  });

  it('shows an error that can be retried', () => {
    const page = create(throwError(() => new Error('403')));

    expect(page.querySelector('[role=alert]')?.textContent).toContain("Couldn't load the users.");

    listUsers.and.returnValue(of(USERS));
    page.querySelector<HTMLButtonElement>('.retry')!.click();
    fixture.detectChanges();

    expect(page.querySelector('[role=alert]')).toBeNull();
    expect(texts(page, '.stat-value')).toEqual(['3', '2', '2', '1']);
  });

  describe('chart size button', () => {
    afterEach(() => localStorage.removeItem('technova-admin-chart-width'));

    function grid(page: HTMLElement): HTMLElement {
      return page.querySelector('.analytics') as HTMLElement;
    }

    it('starts with the chart full width', () => {
      localStorage.removeItem('technova-admin-chart-width');
      const page = create(of(USERS));

      expect(grid(page).classList).not.toContain('chart-narrow');
      expect(page.querySelector('.size')?.getAttribute('aria-pressed')).toBe('false');
    });

    it('makes the chart narrower, so Live sits beside it, and back again', () => {
      const page = create(of(USERS));
      const size = page.querySelector('.size') as HTMLButtonElement;

      size.click();
      fixture.detectChanges();
      expect(grid(page).classList).toContain('chart-narrow');
      expect(size.getAttribute('aria-pressed')).toBe('true');
      expect(size.getAttribute('aria-label')).toContain('full width');

      size.click();
      fixture.detectChanges();
      expect(grid(page).classList).not.toContain('chart-narrow');
    });

    it('remembers the choice in this browser', () => {
      const page = create(of(USERS));
      (page.querySelector('.size') as HTMLButtonElement).click();

      expect(localStorage.getItem('technova-admin-chart-width')).toBe('narrow');
    });

    it('opens narrower if that was the last choice', () => {
      localStorage.setItem('technova-admin-chart-width', 'narrow');
      const page = create(of(USERS));

      expect(grid(page).classList).toContain('chart-narrow');
    });
  });
});
