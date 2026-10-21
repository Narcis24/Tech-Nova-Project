import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
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
  private readonly accountPies = inject(AccountPieControllerService);
  private readonly route = inject(ActivatedRoute).snapshot;
  readonly accountId = this.route.paramMap.get('accountId')!;
  /** Set when previewing a copy of someone else's public pie rather than the account's own pie. */
  readonly copyPieId = this.route.queryParamMap.get('copy');
  readonly preview = signal<RebalancePreviewResponse | null>(null);
  readonly hasTrades = computed(() => !!this.preview()?.legs?.some((leg) => leg.action !== 'NONE'));
  readonly error = signal<string | null>(null);
  readonly starting = signal(false);
  readonly startError = signal<string | null>(null);

  constructor() {
    const preview$ = this.copyPieId
      ? this.accountPies.previewCopy(this.accountId, { pieId: this.copyPieId })
      : this.accountPies.previewRebalance(this.accountId);
    preview$.pipe(takeUntilDestroyed()).subscribe({
      next: (preview) => this.preview.set(preview),
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          if (error.error?.errorCode === 'PIE_NOT_LINKED') {
            this.error.set('No pie is linked to this account. Choose one to preview a rebalance.');
            return;
          }
          if (this.copyPieId && error.error?.errorCode === 'PIE_NOT_FOUND') {
            this.error.set("This pie isn't public any more, so it can't be copied.");
            return;
          }
          if (error.error?.errorCode === 'PRICE_NOT_FOUND' && typeof error.error.message === 'string') {
            this.error.set(error.error.message);
            return;
          }
        }
        if (this.copyPieId && error instanceof HttpErrorResponse && [400, 409].includes(error.status)) {
          const message = error.error?.message;
          if (typeof message === 'string' && message.trim()) {
            this.error.set(message);
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
    const start$ = this.copyPieId
      ? this.accountPies.copyPie(this.accountId, { pieId: this.copyPieId })
      : this.runs.startRebalance(this.accountId);
    start$.subscribe({
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
