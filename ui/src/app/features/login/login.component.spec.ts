import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { LoginComponent } from './login.component';

@Component({ template: '' })
class BlankComponent {}

describe('LoginComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let loginResult: Subject<void>;
  // Where the real service would send this user; set to /admin to log in as an admin
  const homeUrl = signal<'/admin' | '/dashboard'>('/dashboard');
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  // Opens /login (optionally with a query string) in a real test router
  async function open(url = '/login'): Promise<void> {
    await harness.navigateByUrl(url, LoginComponent);
    page = harness.routeNativeElement!;
  }

  function type(id: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    harness.detectChanges();
  }

  function text(): string {
    return page.textContent ?? '';
  }

  beforeEach(async () => {
    loginResult = new Subject<void>();
    homeUrl.set('/dashboard');
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login'], { homeUrl });
    auth.login.and.returnValue(loginResult);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: LoginComponent },
          { path: '**', component: BlankComponent },
        ]),
        { provide: AuthService, useValue: auth },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('shows a required text field for the username and a required password field', async () => {
    await open();

    const username = page.querySelector<HTMLInputElement>('#username')!;
    const password = page.querySelector<HTMLInputElement>('#password')!;
    expect(username.type).toBe('text');
    expect(username.required).toBeTrue();
    expect(password.type).toBe('password');
    expect(password.required).toBeTrue();
  });

  it('shows "is required" messages and does not submit an empty form', async () => {
    await open();

    submit();

    expect(text()).toContain('Username is required');
    expect(text()).toContain('Password is required');
    expect(auth.login).not.toHaveBeenCalled();
  });

  // Badly formatted input is never sent, and gets the same message as wrong credentials,
  // so the page does not reveal which usernames or passwords are accepted
  for (const [what, username, password] of [
    ['too short', 'trader', 'short'],
    ['too long', 'a_very_long_username', 'this-password-is-too-long'],
    ['with invalid characters', 'trader-01', 'Trader.Pass01'],
  ]) {
    it(`answers a username or password ${what} like wrong credentials, without calling the server`, async () => {
      await open();
      type('username', username);
      type('password', password);

      submit();

      expect(page.querySelector('[role=alert]')?.textContent).toContain('Invalid username or password');
      expect(text()).not.toMatch(/characters|letters|numbers|underscores/);
      expect(auth.login).not.toHaveBeenCalled();
    });
  }

  it('sends the credentials and disables the button with a spinner while signing in', async () => {
    await open();
    type('username', 'trader01');
    type('password', 'Trader.Pass01');

    submit();

    expect(auth.login).toHaveBeenCalledOnceWith({ username: 'trader01', password: 'Trader.Pass01' });
    const button = page.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(button.disabled).toBeTrue();
    expect(button.querySelector('.spinner')).not.toBeNull();
  });

  it('goes to the dashboard after a successful login', async () => {
    await open();
    type('username', 'trader01');
    type('password', 'Trader.Pass01');
    submit();

    loginResult.next();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/dashboard');
  });

  it('goes to the admin page after an admin logs in', async () => {
    await open();
    homeUrl.set('/admin');
    type('username', 'test_admin');
    type('password', 'AdminPass123!');
    submit();

    loginResult.next();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/admin');
  });

  it('still takes an admin back to the page they came from', async () => {
    await open('/login?returnUrl=%2Forders');
    homeUrl.set('/admin');
    type('username', 'test_admin');
    type('password', 'AdminPass123!');
    submit();

    loginResult.next();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/orders');
  });

  it('goes back to the page the user came from', async () => {
    await open('/login?returnUrl=%2Forders');
    type('username', 'trader01');
    type('password', 'Trader.Pass01');
    submit();

    loginResult.next();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/orders');
  });

  it('ignores a returnUrl that points to another site', async () => {
    await open('/login?returnUrl=https%3A%2F%2Fevil.example');
    type('username', 'trader01');
    type('password', 'Trader.Pass01');
    submit();

    loginResult.next();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/dashboard');
  });

  it('shows a dismissible alert when the credentials are wrong', async () => {
    await open();
    type('username', 'trader01');
    type('password', 'wrong-password');
    submit();

    loginResult.error(new HttpErrorResponse({ status: 401 }));
    harness.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Invalid username or password');
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();

    page.querySelector<HTMLButtonElement>('.alert-close')!.click();
    harness.detectChanges();
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('says the server cannot be reached when there is no response', async () => {
    await open();
    type('username', 'trader01');
    type('password', 'Trader.Pass01');
    submit();

    loginResult.error(new HttpErrorResponse({ status: 0 }));
    harness.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain("We can't reach TechNova right now");
  });

  it('links to the forgot password page', async () => {
    await open();

    const link = page.querySelector<HTMLAnchorElement>('a.forgot')!;
    expect(link.textContent).toContain('Forgot password?');
    expect(link.getAttribute('href')).toBe('/forgot-password');
  });

  it('shows and hides the password', async () => {
    await open();
    const password = page.querySelector<HTMLInputElement>('#password')!;
    const toggle = page.querySelector<HTMLButtonElement>('.show')!;

    toggle.click();
    harness.detectChanges();
    expect(password.type).toBe('text');
    expect(toggle.textContent).toContain('Hide');

    toggle.click();
    harness.detectChanges();
    expect(password.type).toBe('password');
  });
});
