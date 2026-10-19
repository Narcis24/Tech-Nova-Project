import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AccountService } from '../../services/account.service';
import { AccountPieSummaryComponent } from '../pies/account-pie-summary.component';

/** Lists the user's accounts and lets them pick which one the app shows. Reached from the phone avatar menu. */
@Component({
  selector: 'app-accounts',
  imports: [ReactiveFormsModule, AccountPieSummaryComponent],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.css',
})
export class AccountsComponent {
  private readonly accountService = inject(AccountService);
  private readonly router = inject(Router);

  readonly accounts = this.accountService.accounts;
  readonly currentAccountId = this.accountService.currentAccountId;

  readonly formOpen = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly holderName = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(255)],
  });
  /** [formGroup] on the <form> is what makes (ngSubmit) work and stops the browser reloading the page on submit */
  readonly newAccountForm = new FormGroup({ holderName: this.holderName });

  openForm(): void {
    this.formOpen.set(true);
  }

  cancel(): void {
    this.formOpen.set(false);
    this.holderName.reset();
    this.error.set(null);
  }

  submit(): void {
    if (this.holderName.invalid) {
      this.holderName.markAsTouched();
      return;
    }

    // Disables the button so it cannot be submitted twice
    this.submitting.set(true);
    this.error.set(null);
    this.accountService.openAccount(this.holderName.value.trim()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.cancel();
        void this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.submitting.set(false);
        this.error.set("Couldn't open the account. Please try again.");
      },
    });
  }

  selectAccount(accountId: string): void {
    this.accountService.selectAccount(accountId);
    void this.router.navigateByUrl('/dashboard');
  }
}
