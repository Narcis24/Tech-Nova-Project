import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AccountPieControllerService,
  RebalanceControllerService,
  RebalancePreviewResponse,
} from '../../generated/trading-api-client';

@Component({
  selector: 'app-rebalance-preview',
  imports: [CurrencyPipe, DecimalPipe, RouterLink],
  templateUrl: './rebalance-preview.component.html',
  styleUrls: ['./pies-shared.css', './rebalance-preview.component.css'],
})
export class RebalancePreviewComponent {
  private readonly runs = inject(RebalanceControllerService);
  private readonly router = inject(Router);
  readonly accountId = inject(ActivatedRoute).snapshot.paramMap.get('accountId')!;
  readonly preview = signal<RebalancePreviewResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly starting = signal(false);
  readonly startError = signal<string | null>(null);

  constructor() {
    inject(AccountPieControllerService)
      .previewRebalance(this.accountId)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (preview) => this.preview.set(preview),
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            if (error.error?.errorCode === 'PIE_NOT_LINKED') {
              this.error.set('No pie is linked to this account. Choose one to preview a rebalance.');
              return;
            }
            if (error.error?.errorCode === 'PRICE_NOT_FOUND' && typeof error.error.message === 'string') {
              this.error.set(error.error.message);
              return;
            }
          }
          this.error.set("Couldn't load the rebalance preview. Reload the page to try again.");
        },
      });
  }

  start(): void {
    if (this.starting() || !this.preview()) return;
    this.starting.set(true);
    this.startError.set(null);
    this.runs.startRebalance(this.accountId).subscribe({
      next: (run) => this.router.navigate(['/rebalance-runs', run.runId]),
      error: (error: unknown) => {
        this.starting.set(false);
        this.startError.set(
          error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
            ? error.error.message
            : "Couldn't start the rebalance. Try again.",
        );
      },
    });
  }
}
