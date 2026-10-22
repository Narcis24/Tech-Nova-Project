import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { AccountPieControllerService, AccountPieResponse } from '../../generated/trading-api-client';

@Component({
  selector: 'app-account-pie-summary',
  imports: [DecimalPipe, RouterLink],
  template: `
    @if (pie(); as linked) {
      <div class="pie-summary">
        <span
          ><strong>{{ linked.pieName }}</strong>
          @if (linked.maxDriftPct != null) {
            · Drift {{ linked.maxDriftPct | number: '1.0-2' }}%
          }
        </span>
        @if (linked.activeRunId) {
          <a [routerLink]="['/rebalance-runs', linked.activeRunId]">View rebalance</a>
        } @else {
          <a [routerLink]="['/accounts', accountId(), 'rebalance']">{{
            (linked.maxDriftPct ?? 0) >= 5 ? 'Rebalance' : 'Preview strategy'
          }}</a>
        }
      </div>
    } @else if (error()) {
      <p class="error">Couldn't load the account's strategy. <a routerLink="/pies">View strategies</a></p>
    }
  `,
  styles: `
    .pie-summary {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 8px 16px;
      padding: 12px 16px;
      margin: 8px 0 16px;
      border: 1px solid var(--border-color);
      border-radius: 12px;
      font-size: 13px;
    }
    a {
      display: inline-flex;
      align-items: center;
      min-height: 32px;
      font-weight: 600;
    }
    .error {
      color: var(--secondary-text-color);
      font-size: 13px;
    }
  `,
})
export class AccountPieSummaryComponent {
  readonly accountId = input.required<string>();
  readonly pie = signal<AccountPieResponse | null>(null);
  readonly error = signal(false);

  constructor() {
    const api = inject(AccountPieControllerService);
    toObservable(this.accountId)
      .pipe(
        switchMap((id) => {
          this.pie.set(null);
          this.error.set(false);
          return api.getAccountPie(id).pipe(
            catchError((error: unknown) => {
              this.error.set(
                !(
                  error instanceof HttpErrorResponse &&
                  error.status === 404 &&
                  error.error?.errorCode === 'PIE_NOT_LINKED'
                ),
              );
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((pie) => this.pie.set(pie));
  }
}
