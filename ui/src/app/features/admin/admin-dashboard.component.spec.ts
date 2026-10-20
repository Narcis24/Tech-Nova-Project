import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { AdminUserControllerService, AdminUserResponse } from '../../generated/trading-api-client';
import { AuthService } from '../../services/auth.service';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { AnalyticsCardComponent } from './analytics/analytics-card.component';

// Stands in for the analytics card, which has its own spec and services
@Component({ selector: 'app-analytics-card', template: '' })
class AnalyticsCardStub {}

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
      remove: { imports: [AnalyticsCardComponent] },
      add: { imports: [AnalyticsCardStub] },
    });
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
});
