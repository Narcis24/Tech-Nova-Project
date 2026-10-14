import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

// Same rules as the auth service (contracts/auth-api.yml)
const PASSWORD_RULES = [Validators.required, Validators.minLength(12), Validators.maxLength(20)];

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: '../../shared/auth-page.css',
})
export class ResetPasswordComponent {
  private readonly auth = inject(AuthService);

  // The email links to /reset-password#token=...; read it once, then take it out of the address bar and history
  private readonly token = readToken(inject(ActivatedRoute).snapshot.fragment);

  readonly form = new FormGroup({
    newPassword: new FormControl('', { nonNullable: true, validators: PASSWORD_RULES }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  // Which screen to show: the form, the success message, or "link invalid or expired"
  readonly status = signal<'form' | 'done' | 'invalid'>(this.token ? 'form' : 'invalid');
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  constructor() {
    inject(Location).replaceState('/reset-password');
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.passwordsMatch()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.auth.resetPassword(this.token!, this.form.controls.newPassword.value).subscribe({
      next: () => this.status.set('done'),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // 0 = no response (server down); 5xx = server error. Anything else (400) is a bad or used link.
        if (err.status === 0 || err.status >= 500) {
          this.error.set("We can't reach TechNova right now. Try again in a moment.");
        } else {
          this.status.set('invalid');
        }
      },
    });
  }

  togglePassword(): void {
    this.showPassword.update(shown => !shown);
  }

  dismissError(): void {
    this.error.set(null);
  }

  /** The message under a field, or null. Only after the user has left it or pressed Save. */
  fieldError(field: 'newPassword' | 'confirmPassword'): string | null {
    const control = this.form.controls[field];
    if (!control.touched) {
      return null;
    }
    if (field === 'newPassword') {
      if (control.hasError('required')) return 'Password is required';
      if (control.hasError('minlength') || control.hasError('maxlength')) return 'Password must be 12 to 20 characters';
    } else {
      if (control.hasError('required')) return 'Confirm your new password';
      if (!this.passwordsMatch()) return 'Passwords do not match';
    }
    return null;
  }

  private passwordsMatch(): boolean {
    return this.form.controls.newPassword.value === this.form.controls.confirmPassword.value;
  }
}

/** The token from a "token=..." fragment, or null. */
function readToken(fragment: string | null): string | null {
  return fragment?.startsWith('token=') ? fragment.slice('token='.length) || null : null;
}
