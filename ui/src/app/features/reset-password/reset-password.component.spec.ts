import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { ResetPasswordComponent } from './reset-password.component';

describe('ResetPasswordComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let result: Subject<void>;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  // Opens the page the way the email link does
  async function open(url = '/reset-password#token=reset-token'): Promise<void> {
    await harness.navigateByUrl(url, ResetPasswordComponent);
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
    result = new Subject<void>();
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['resetPassword']);
    auth.resetPassword.and.returnValue(result);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'reset-password', component: ResetPasswordComponent }]),
        { provide: AuthService, useValue: auth },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('takes the token out of the address bar', async () => {
    await open();

    expect(TestBed.inject(Location).path(true)).toBe('/reset-password');
  });

  it('asks for the new password twice', async () => {
    await open();

    expect(page.querySelector<HTMLInputElement>('#newPassword')!.type).toBe('password');
    expect(page.querySelector<HTMLInputElement>('#confirmPassword')!.type).toBe('password');
  });

  it('requires both fields', async () => {
    await open();

    submit();

    expect(text()).toContain('Password is required');
    expect(text()).toContain('Confirm your new password');
    expect(auth.resetPassword).not.toHaveBeenCalled();
  });

  it('refuses a password of the wrong length', async () => {
    await open();
    type('newPassword', 'short');
    type('confirmPassword', 'short');

    submit();

    expect(text()).toContain('Password must be 12 to 20 characters');
    expect(auth.resetPassword).not.toHaveBeenCalled();
  });

  it('refuses passwords that do not match', async () => {
    await open();
    type('newPassword', 'NewPassword123');
    type('confirmPassword', 'NewPassword124');

    submit();

    expect(text()).toContain('Passwords do not match');
    expect(auth.resetPassword).not.toHaveBeenCalled();
  });

  it('sends the token with the new password and shows the success screen', async () => {
    await open();
    type('newPassword', 'NewPassword123');
    type('confirmPassword', 'NewPassword123');

    submit();
    expect(auth.resetPassword).toHaveBeenCalledOnceWith('reset-token', 'NewPassword123');
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();

    result.next();
    harness.detectChanges();

    expect(text()).toContain('Your password has been changed');
    expect(page.querySelector('a.submit')?.getAttribute('href')).toBe('/login');
  });

  it('shows the invalid link screen when the server refuses the token', async () => {
    await open();
    type('newPassword', 'NewPassword123');
    type('confirmPassword', 'NewPassword123');
    submit();

    result.error(new HttpErrorResponse({ status: 400 }));
    harness.detectChanges();

    expect(text()).toContain('This reset link is invalid or has expired');
    expect(page.querySelector('a.submit')?.getAttribute('href')).toBe('/forgot-password');
  });

  it('shows the invalid link screen straight away when the link has no token', async () => {
    await open('/reset-password');

    expect(text()).toContain('This reset link is invalid or has expired');
    expect(page.querySelector('form')).toBeNull();
  });

  it('says the server cannot be reached when there is no response', async () => {
    await open();
    type('newPassword', 'NewPassword123');
    type('confirmPassword', 'NewPassword123');
    submit();

    result.error(new HttpErrorResponse({ status: 0 }));
    harness.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain("We can't reach TechNova right now");
    expect(page.querySelector('form')).not.toBeNull();
  });

  it('shows and hides both passwords', async () => {
    await open();

    page.querySelector<HTMLButtonElement>('.show')!.click();
    harness.detectChanges();

    expect(page.querySelector<HTMLInputElement>('#newPassword')!.type).toBe('text');
    expect(page.querySelector<HTMLInputElement>('#confirmPassword')!.type).toBe('text');
  });
});
