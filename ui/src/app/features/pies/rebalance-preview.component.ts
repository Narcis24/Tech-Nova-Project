import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AccountPieControllerService, RebalancePreviewResponse } from '../../generated/trading-api-client';

@Component({
  selector: 'app-rebalance-preview',
  imports: [CurrencyPipe, DecimalPipe, RouterLink],
  templateUrl: './rebalance-preview.component.html',
  styleUrls: ['./pies-shared.css', './rebalance-preview.component.css'],
})
export class RebalancePreviewComponent {
  readonly preview = signal<RebalancePreviewResponse | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    const accountId = inject(ActivatedRoute).snapshot.paramMap.get('accountId')!;
    inject(AccountPieControllerService)
      .previewRebalance(accountId)
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
}
