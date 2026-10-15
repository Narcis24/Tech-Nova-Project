import { CurrencyPipe } from '@angular/common';
import { afterNextRender, Component, effect, ElementRef, inject, Injector, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, exhaustMap, catchError, of, tap } from 'rxjs';
import { AccountControllerService } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-cash-actions',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './cash-actions.component.html',
  styleUrl: './cash-actions.component.css',
})
export class CashActionsComponent {
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('cashDialog');
  private readonly injector = inject(Injector);
  private readonly api = inject(AccountControllerService);
  private readonly accounts = inject(AccountService);
  readonly accountId = input.required<string>();
  readonly cashBalance = input.required<number>();
  readonly completed = output<void>();
  readonly mode = signal<'deposit' | 'withdraw' | null>(null);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly amount = new FormControl<number | null>(null, [Validators.required, Validators.min(0.01), Validators.pattern(/^\d+(\.\d{1,2})?$/)]);
  readonly form = new FormGroup({ amount: this.amount });
  private readonly submissions = new Subject<{ id: string; mode: 'deposit' | 'withdraw'; amount: number }>();

  constructor() {
    effect(() => { this.accountId(); this.close(); this.success.set(null); });
    this.submissions.pipe(
      exhaustMap(({ id, mode, amount }) => this.api[mode](id, { amount }).pipe(
        tap(account => {
          this.accounts.accounts.update(accounts => accounts.map(a => a.accountId === id ? account : a));
          if (id === this.accountId()) {
            this.close();
            this.success.set(mode === 'deposit' ? 'Cash deposited.' : 'Cash withdrawn.');
            this.completed.emit();
          }
        }),
        catchError(() => {
          if (id === this.accountId()) this.error.set("Couldn't complete the transfer. Check your available cash and try again.");
          return of(null);
        }),
        tap(() => this.submitting.set(false)),
      )),
      takeUntilDestroyed(),
    ).subscribe();
  }

  open(mode: 'deposit' | 'withdraw'): void {
    this.close(); this.success.set(null); this.mode.set(mode);
    afterNextRender(() => {
      const dialog = this.dialog()?.nativeElement;
      if (this.mode() && dialog?.isConnected && !dialog.open) {
        dialog.showModal();
        dialog.querySelector<HTMLInputElement>('input')?.focus();
      }
    }, { injector: this.injector });
  }
  close(): void { this.dialog()?.nativeElement.close(); this.mode.set(null); this.amount.reset(); this.error.set(null); }

  cancel(event: Event): void { event.preventDefault(); if (!this.submitting()) this.close(); }

  submit(): void {
    const mode = this.mode();
    if (!mode || this.submitting()) return;
    const amount = this.amount.value;
    if (this.form.invalid || amount == null || !Number.isFinite(amount)) {
      this.amount.markAsTouched(); return;
    }
    if (mode === 'withdraw' && amount > Number(this.cashBalance())) {
      this.error.set('Enter an amount within your available cash.'); return;
    }
    this.error.set(null); this.submitting.set(true);
    this.submissions.next({ id: this.accountId(), mode, amount });
  }
}
