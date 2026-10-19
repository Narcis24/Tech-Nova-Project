import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { safeReturnUrl } from '../../core/return-url';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

const INVALID_CREDENTIALS = 'Invalid username or password';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['../../shared/auth-page.css', './login.component.css'],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // Same rules as the auth service (contracts/auth-api.yml). Input that breaks them is never sent, and gets the
  // same generic message as wrong credentials, so the page does not reveal which usernames or passwords are accepted.
  readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(8),
        Validators.maxLength(16),
        Validators.pattern(/^[A-Za-z0-9_]+$/),
      ],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12), Validators.maxLength(20)],
    }),
  });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  submit(): void {
    this.form.markAllAsTouched();
    const { username, password } = this.form.controls;

    // Empty fields: the "is required" messages are enough
    if (username.hasError('required') || password.hasError('required')) {
      return;
    }
    // Badly formatted: answer exactly like the server answers wrong credentials
    if (this.form.invalid) {
      this.error.set(INVALID_CREDENTIALS);
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(safeReturnUrl(returnUrl));
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // 0 = no response (server down); 5xx = server error. Anything else (401, 400) is bad credentials.
        const serverProblem = err.status === 0 || err.status >= 500;
        this.error.set(
          serverProblem ? "We can't reach TechNova right now. Try again in a moment." : INVALID_CREDENTIALS,
        );
      },
    });
  }

  togglePassword(): void {
    this.showPassword.update((shown) => !shown);
  }

  dismissError(): void {
    this.error.set(null);
  }

  /** True when a field should show its "is required" message. Only after the user has left it or pressed Sign in. */
  showRequired(field: 'username' | 'password'): boolean {
    const control = this.form.controls[field];
    return control.touched && control.hasError('required');
  }
}
