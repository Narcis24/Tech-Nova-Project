import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import {
  AdminUserControllerService,
  AdminUserResponse,
  CreateUserRequest,
  CreateUserResponse,
} from '../../../generated/trading-api-client';
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
  let createUser: jasmine.Spy<(request: CreateUserRequest) => Observable<CreateUserResponse>>;

  function create(result: Observable<AdminUserResponse[]>): void {
    listUsers = jasmine.createSpy('listUsers').and.returnValue(result);
    createUser = jasmine.createSpy('createUser');
    TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [{ provide: AdminUserControllerService, useValue: { listUsers, createUser } }],
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

  describe('create admin', () => {
    const NEW_ADMIN: AdminUserResponse = {
      username: 'new_admin',
      email: 'new.admin@example.com',
      roles: ['ADMIN'],
      createdAt: '2026-10-19T12:00:00Z',
      createdBy: 'test_admin',
    };

    beforeEach(() => create(of(USERS)));

    function type(id: string, text: string): void {
      const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    function roleBox(role: string): HTMLInputElement {
      const label = Array.from(page.querySelectorAll('.role-option')).find((l) => l.textContent?.trim() === role)!;
      return label.querySelector('input')!;
    }

    function fillValid(): void {
      type('new-username', 'new_admin');
      type('new-email', 'new.admin@example.com');
    }

    function submit(): void {
      page.querySelector<HTMLFormElement>('.create-form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    }

    function conflict(message: string): HttpErrorResponse {
      return new HttpErrorResponse({ status: 409, error: { errorCode: 'USER_ALREADY_EXISTS', message } });
    }

    it('shows a card with ADMIN picked and every other role offered', () => {
      expect(page.querySelector('.create-form h2')?.textContent).toContain('Create admin');
      expect(page.querySelector('label[for=new-username]')).not.toBeNull();
      expect(page.querySelector('label[for=new-email]')).not.toBeNull();
      expect(roleBox('ADMIN').checked).toBeTrue();
      expect(roleBox('TRADER').checked).toBeFalse();
    });

    it('is there even when the users failed to load', () => {
      TestBed.resetTestingModule();
      create(throwError(() => new Error('500')));

      expect(page.querySelector('.create-form')).not.toBeNull();
    });

    it('sends the username, email and ADMIN as a JSON array', () => {
      createUser.and.returnValue(of({ user: NEW_ADMIN, inviteSent: true }));
      fillValid();
      submit();

      expect(createUser).toHaveBeenCalledOnceWith({
        username: 'new_admin',
        email: 'new.admin@example.com',
        roles: ['ADMIN'],
      });
      // A Set would serialise to {} and the API would reject it
      expect(JSON.stringify(createUser.calls.mostRecent().args[0].roles)).toBe('["ADMIN"]');
    });

    it('sends only the role picked, as a list of one', () => {
      createUser.and.returnValue(of({ user: { ...NEW_ADMIN, roles: ['TRADER'] }, inviteSent: true }));
      fillValid();
      roleBox('TRADER').click();
      fixture.detectChanges();
      submit();

      expect(createUser.calls.mostRecent().args[0].roles).toEqual(['TRADER']);
    });

    it('allows only one role at a time', () => {
      roleBox('TRADER').click();
      fixture.detectChanges();

      expect(roleBox('ADMIN').type).toBe('radio');
      expect(roleBox('TRADER').checked).toBeTrue();
      expect(roleBox('ADMIN').checked).toBeFalse();

      roleBox('ADMIN').click();
      fixture.detectChanges();

      expect(roleBox('ADMIN').checked).toBeTrue();
      expect(roleBox('TRADER').checked).toBeFalse();
    });

    it('shows field errors instead of submitting an invalid form', () => {
      type('new-username', 'short');
      type('new-email', 'john@example');
      submit();

      expect(createUser).not.toHaveBeenCalled();
      const errors = Array.from(page.querySelectorAll('.field .field-error')).map((e) => e.textContent?.trim());
      expect(errors).toEqual(['Username must be 8-16 characters', 'Enter a valid email address']);
    });

    it('adds the new user to the list in username order and confirms the email was sent', () => {
      createUser.and.returnValue(of({ user: NEW_ADMIN, inviteSent: true }));
      fillValid();
      submit();

      expect(usernames()).toEqual(['invited_01', 'new_admin', 'old_user1', 'test_admin', 'trader_one']);
      expect(page.querySelector('output.create-result')?.textContent).toContain(
        'Created new_admin. A set-password email is on its way.',
      );
    });

    it('warns when the account was created but the email was not sent', () => {
      createUser.and.returnValue(of({ user: NEW_ADMIN, inviteSent: false }));
      fillValid();
      submit();

      const result = page.querySelector('output.create-warning');
      expect(result?.textContent).toContain("the set-password email couldn't be sent");
      expect(usernames()).toContain('new_admin');
    });

    it('disables the buttons while the request is in flight', () => {
      const response = new Subject<CreateUserResponse>();
      createUser.and.returnValue(response);
      fillValid();
      submit();

      const submitButton = page.querySelector<HTMLButtonElement>('.create-form button[type=submit]')!;
      expect(submitButton.disabled).toBeTrue();
      expect(submitButton.textContent).toContain('Creating');

      submit();
      expect(createUser).toHaveBeenCalledTimes(1);

      response.next({ user: NEW_ADMIN, inviteSent: true });
      fixture.detectChanges();
      expect(submitButton.disabled).toBeFalse();
    });

    it('shows what is taken when the username or email already exists, keeping the form open', () => {
      createUser.and.returnValue(throwError(() => conflict('Username already taken: new_admin')));
      fillValid();
      submit();

      expect(page.querySelector('.create-form [role=alert]')?.textContent).toContain(
        'Username already taken: new_admin',
      );
      expect(page.querySelector<HTMLInputElement>('#new-username')?.value).toBe('new_admin');
      expect(page.querySelector<HTMLButtonElement>('.create-form button[type=submit]')?.disabled).toBeFalse();
    });

    it('shows a generic error when the server fails', () => {
      createUser.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      fillValid();
      submit();

      expect(page.querySelector('.create-form [role=alert]')?.textContent).toContain(
        "Couldn't create the account. Please try again.",
      );
    });

    it('clears back to ADMIN after creating, ready for the next account', () => {
      createUser.and.returnValue(of({ user: { ...NEW_ADMIN, roles: ['TRADER'] }, inviteSent: true }));
      fillValid();
      roleBox('TRADER').click();
      fixture.detectChanges();
      submit();

      expect(page.querySelector<HTMLInputElement>('#new-username')?.value).toBe('');
      expect(page.querySelector<HTMLInputElement>('#new-email')?.value).toBe('');
      expect(roleBox('ADMIN').checked).toBeTrue();
      expect(roleBox('TRADER').checked).toBeFalse();
      // A cleared form is untouched again, so it shows no "required" errors
      expect(page.querySelector('.create-form .field-error')).toBeNull();
    });

    it('hides the last confirmation when the next account is submitted', () => {
      createUser.and.returnValue(of({ user: NEW_ADMIN, inviteSent: true }));
      fillValid();
      submit();
      expect(page.querySelector('output.create-result')).not.toBeNull();

      createUser.and.returnValue(throwError(() => conflict('Email already in use: new.admin@example.com')));
      fillValid();
      submit();

      expect(page.querySelector('output.create-result')).toBeNull();
      expect(page.querySelector('.create-form [role=alert]')?.textContent).toContain('Email already in use');
    });
  });
});
