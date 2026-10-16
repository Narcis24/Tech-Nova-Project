import { RegisterResponse } from '../../generated/auth-api-client';
import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { RegisterComponent } from './register.component';

@Component({ template: '' })
class BlankComponent {}

describe('RegisterComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let registerResult: Subject<RegisterResponse>;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  async function open(): Promise<void> {
    await harness.navigateByUrl('/register', RegisterComponent);
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
    registerResult = new Subject<RegisterResponse>();
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['register']);
    auth.register.and.returnValue(registerResult);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'register', component: RegisterComponent },
          { path: 'login', component: BlankComponent },
        ]),
        { provide: AuthService, useValue: auth },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('shows the required registration fields', async () => {
    await open();

    expect(page.querySelector<HTMLInputElement>('#username')!.required).toBeTrue();
    expect(page.querySelector<HTMLInputElement>('#email')!.required).toBeTrue();
    expect(page.querySelector<HTMLInputElement>('#password')!.required).toBeTrue();
    expect(page.querySelector<HTMLInputElement>('#confirmPassword')!.required).toBeTrue();
  });

  it('does not submit an empty form', async () => {
    await open();

    submit();

    expect(text()).toContain('Username is required');
    expect(text()).toContain('Email is required');
    expect(text()).toContain('Password is required');
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('rejects usernames that do not meet the format rules', async () => {
    await open();
    type('username', 'bad-name');
    type('email', 'trader@example.com');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass01');

    submit();

    expect(text()).toContain('Username may contain only letters, numbers, and underscores');
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('rejects an invalid email without calling the server', async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass01');

    submit();

    expect(text()).toContain('Enter a valid email address');
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('rejects passwords that do not meet the strength rules', async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example.com');
    type('password', 'weakpassword');
    type('confirmPassword', 'weakpassword');

    submit();

    expect(text()).toContain(
      'Password must contain a digit, uppercase letter, lowercase letter, and special character',
    );
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords without calling the server', async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example.com');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass02');

    submit();

    expect(text()).toContain('Passwords do not match');
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('sends valid credentials and disables the button while registering', async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example.com');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass01');

    submit();

    expect(auth.register).toHaveBeenCalledOnceWith({
      username: 'trader_01',
      email: 'trader@example.com',
      password: 'Trader.Pass01',
    });
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();
    expect(page.querySelector('.spinner')).not.toBeNull();
  });

  it('shows success and navigates to login after registration', fakeAsync(async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example.com');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass01');
    submit();

    registerResult.next({ username: 'testuser' });
    harness.detectChanges();
    expect(page.querySelector('[role=status]')?.textContent).toContain('Registration successful');

    tick(2000);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
  }));

  it('shows a specific message when the username or email is taken', async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example.com');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass01');
    submit();

    registerResult.error(new HttpErrorResponse({ status: 409 }));
    harness.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Username or email already registered');
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();
  });

  it('shows the server error message for other registration failures', async () => {
    await open();
    type('username', 'trader_01');
    type('email', 'trader@example.com');
    type('password', 'Trader.Pass01');
    type('confirmPassword', 'Trader.Pass01');
    submit();

    registerResult.error(
      new HttpErrorResponse({
        status: 400,
        error: { message: 'Username is not available' },
      }),
    );
    harness.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Username is not available');
  });

  it('shows and hides both passwords', async () => {
    await open();
    const password = page.querySelector<HTMLInputElement>('#password')!;
    const confirmPassword = page.querySelector<HTMLInputElement>('#confirmPassword')!;
    const toggles = page.querySelectorAll<HTMLButtonElement>('.show');

    toggles[0].click();
    toggles[1].click();
    harness.detectChanges();
    expect(password.type).toBe('text');
    expect(confirmPassword.type).toBe('text');

    toggles[0].click();
    toggles[1].click();
    harness.detectChanges();
    expect(password.type).toBe('password');
    expect(confirmPassword.type).toBe('password');
  });
});
