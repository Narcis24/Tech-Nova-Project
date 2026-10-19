import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import { AdminUserControllerService, AdminUserResponse } from '../../../generated/trading-api-client';
import { AdminUsersComponent } from './admin-users.component';

const USERS: AdminUserResponse[] = [
  {
    username: 'invited_01',
    email: 'invited.01@example.com',
    roles: ['TRADER'],
    createdAt: '2026-10-19T10:56:24Z',
    createdBy: 'test_admin',
  },
  { username: 'old_user1', roles: ['TRADER'], createdAt: '2026-09-01T08:00:00Z' },
  { username: 'test_admin', email: 'test_admin@technova.local', roles: ['ADMIN'], createdAt: '2026-10-19T09:25:14Z' },
  {
    username: 'trader_one',
    email: 'trader_one@example.com',
    roles: ['ADMIN', 'TRADER'],
    createdAt: '2026-10-19T09:25:31Z',
  },
];

describe('AdminUsersComponent', () => {
  let fixture: ComponentFixture<AdminUsersComponent>;
  let page: HTMLElement;
  let listUsers: jasmine.Spy<() => Observable<AdminUserResponse[]>>;

  function create(result: Observable<AdminUserResponse[]>): void {
    listUsers = jasmine.createSpy('listUsers').and.returnValue(result);
    TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [{ provide: AdminUserControllerService, useValue: { listUsers } }],
    });
    fixture = TestBed.createComponent(AdminUsersComponent);
    fixture.detectChanges();
    page = fixture.nativeElement;
  }

  function usernames(): string[] {
    return Array.from(page.querySelectorAll('.username')).map((cell) => cell.textContent?.trim() ?? '');
  }

  /** Types into the search box the way a user does: set the text, then fire the input event */
  function typeSearch(text: string): void {
    const input = page.querySelector<HTMLInputElement>('#user-search')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  describe('the list', () => {
    it('shows every user with their email, roles and creator', () => {
      create(of(USERS));

      expect(usernames()).toEqual(['invited_01', 'old_user1', 'test_admin', 'trader_one']);
      const rows = page.querySelectorAll('tbody tr');
      expect(rows[0].textContent).toContain('invited.01@example.com');
      expect(rows[1].querySelector('.email')?.textContent?.trim()).toBe('—');
      expect(Array.from(rows[3].querySelectorAll('.role')).map((r) => r.textContent?.trim())).toEqual([
        'ADMIN',
        'TRADER',
      ]);
    });

    it('shows loading until the users arrive', () => {
      const users = new Subject<AdminUserResponse[]>();
      create(users);

      expect(page.textContent).toContain('Loading users');

      users.next(USERS);
      fixture.detectChanges();

      expect(page.textContent).not.toContain('Loading users');
      expect(usernames()).toHaveSize(4);
    });

    it('says when there are no users, without a search box', () => {
      create(of([]));

      expect(page.textContent).toContain('No users yet.');
      expect(page.querySelector('#user-search')).toBeNull();
    });

    it('shows an error that can be retried', () => {
      create(throwError(() => new Error('403')));

      expect(page.querySelector('[role=alert]')?.textContent).toContain("Couldn't load the users.");

      listUsers.and.returnValue(of(USERS));
      page.querySelector<HTMLButtonElement>('.retry')!.click();
      fixture.detectChanges();

      expect(page.querySelector('[role=alert]')).toBeNull();
      expect(usernames()).toHaveSize(4);
    });

    it('shows who created each user in its own column, or a dash if they registered themselves', () => {
      create(of(USERS));

      const headers = Array.from(page.querySelectorAll('th')).map((th) => th.textContent?.trim());
      expect(headers).toEqual(['Username', 'Email', 'Roles', 'Created', 'Created by']);
      const createdBy = Array.from(page.querySelectorAll('.created-by')).map((cell) => cell.textContent?.trim());
      expect(createdBy).toEqual(['test_admin', '—', '—', '—']);
      expect(page.querySelector('.created')?.textContent?.trim()).toBe('19 Oct 2026');
    });
  });

  describe('search', () => {
    beforeEach(() => create(of(USERS)));

    it('has a labelled search box', () => {
      expect(page.querySelector('label[for=user-search]')?.textContent).toContain('Search users');
    });

    it('finds users by part of their username', () => {
      typeSearch('trader');

      expect(usernames()).toEqual(['trader_one']);
    });

    it('finds users by part of their email', () => {
      typeSearch('example.com');

      expect(usernames()).toEqual(['invited_01', 'trader_one']);
    });

    it('ignores case and surrounding spaces', () => {
      typeSearch('  ADMIN ');

      expect(usernames()).toEqual(['test_admin']);
    });

    it('skips users without an email instead of failing', () => {
      typeSearch('old');

      expect(usernames()).toEqual(['old_user1']);
    });

    it('says how many users match', () => {
      typeSearch('trader');

      expect(page.querySelector('.result-count')?.textContent).toContain('Showing 1 of 4 users');
    });

    it('says when nothing matches, naming the search', () => {
      typeSearch('nobody');

      expect(usernames()).toEqual([]);
      expect(page.textContent).toContain('No users match "nobody".');
    });

    it('shows everyone again when the search is cleared', () => {
      typeSearch('trader');
      typeSearch('');

      expect(usernames()).toHaveSize(4);
      expect(page.querySelector('.result-count')).toBeNull();
    });
  });
});
