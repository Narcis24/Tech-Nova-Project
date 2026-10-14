import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { ForgotPasswordComponent } from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let result: Subject<void>;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  function type(value: string): void {
    const input = page.querySelector<HTMLInputElement>('#email')!;
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
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['forgotPassword']);
    auth.forgotPassword.and.returnValue(result);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'forgot-password', component: ForgotPasswordComponent }]),
        { provide: AuthService, useValue: auth },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/forgot-password', ForgotPasswordComponent);
    page = harness.routeNativeElement!;
  });

  it('asks for the email in a required email field', () => {
    const input = page.querySelector<HTMLInputElement>('#email')!;
    expect(input.type).toBe('email');
    expect(input.required).toBeTrue();
  });

  it('requires an email before sending', () => {
    submit();

    expect(text()).toContain('Email is required');
    expect(auth.forgotPassword).not.toHaveBeenCalled();
  });

  it('refuses a badly formatted email without calling the server', () => {
    for (const email of ['john', 'john@example', 'john doe@example.com']) {
      type(email);
      submit();
      expect(text()).toContain('Enter a valid email address');
    }
    expect(auth.forgotPassword).not.toHaveBeenCalled();
  });

  it('sends the email and shows a spinner while sending', () => {
    type('john.doe@example.com');
    submit();

    expect(auth.forgotPassword).toHaveBeenCalledOnceWith('john.doe@example.com');
    const button = page.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(button.disabled).toBeTrue();
    expect(button.querySelector('.spinner')).not.toBeNull();
  });

  it('replaces the form with a message that does not say whether the account exists', () => {
    type('john.doe@example.com');
    submit();

    result.next();
    harness.detectChanges();

    expect(page.querySelector('[role=status]')?.textContent).toContain(
      "If an account uses that email, we've sent it a link",
    );
    expect(page.querySelector('form')).toBeNull();
  });

  it('says the server cannot be reached when there is no response', () => {
    type('john.doe@example.com');
    submit();

    result.error(new HttpErrorResponse({ status: 0 }));
    harness.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain("We can't reach TechNova right now");
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();
  });

  it('links back to sign in', () => {
    expect(page.querySelector('.back a')?.getAttribute('href')).toBe('/login');
  });
});
