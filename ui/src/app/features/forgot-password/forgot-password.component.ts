import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

// Something@domain.tld: Angular's Validators.email also accepts 'john@example', which the auth service refuses
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: '../../shared/auth-page.css',
})
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthService);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(EMAIL_PATTERN), Validators.maxLength(254)],
    }),
  });

  readonly submitting = signal(false);
  // Shown after sending whether or not an account uses the email, like the server's answer
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.auth.forgotPassword(this.form.controls.email.value).subscribe({
      next: () => this.sent.set(true),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // 0 = no response (server down); 5xx = server error
        if (err.status === 0 || err.status >= 500) {
          this.error.set("We can't reach TechNova right now. Try again in a moment.");
        } else {
          this.sent.set(true);
        }
      },
    });
  }

  dismissError(): void {
    this.error.set(null);
  }

  /** The message under the email field, or null. Only after the user has left it or pressed Send. */
  emailError(): string | null {
    const email = this.form.controls.email;
    if (!email.touched) {
      return null;
    }
    if (email.hasError('required')) {
      return 'Email is required';
    }
    if (email.hasError('pattern') || email.hasError('maxlength')) {
      return 'Enter a valid email address';
    }
    return null;
  }
}
