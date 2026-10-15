import { CurrencyPipe, PercentPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { PortfolioMetricsResponse } from '../../generated/trading-api-client';

export interface DayChange {
  amount: number;
  percent: number | null;
}

@Component({
  selector: 'app-balance-card',
  imports: [CurrencyPipe, PercentPipe],
  template: `
    <p class="label">Portfolio value</p>
    <p class="total">{{ portfolio().totalPortfolioValue | currency:'USD' }}</p>
    @if (dayChange(); as change) {
      <p class="change" [class.gain]="change.amount > 0" [class.loss]="change.amount < 0">
        {{ change.amount > 0 ? '+' : '' }}{{ change.amount | currency:'USD' }}
        @if (change.percent != null) { ({{ change.percent > 0 ? '+' : '' }}{{ change.percent | percent:'1.2-2' }}) }
        <span>since the previous close</span>
      </p>
    }
    <div class="cash"><span>Available cash</span><strong>{{ portfolio().cashBalance | currency:'USD' }}</strong></div>
  `,
  styles: `
    :host { display: block; max-width: 760px; }
    p { margin: 0; }
    .label, .cash span, .change span { color: var(--color-muted); }
    .label { font-size: 14px; margin-bottom: 8px; }
    .total { font-size: clamp(38px, 6vw, 72px); line-height: 1.1; letter-spacing: -.035em; overflow-wrap: anywhere; }
    .change { margin-top: 8px; font-size: 17px; line-height: 1.5; }
    .change span { font-size: 14px; }
    .cash { display: flex; flex-wrap: wrap; gap: 12px 24px; align-items: baseline; margin-top: 24px; }
    .cash strong { font-size: 28px; font-weight: 500; }
    .gain { color: var(--color-gain); }
    .loss { color: var(--color-loss); }
  `,
})
export class BalanceCardComponent {
  readonly portfolio = input.required<PortfolioMetricsResponse>();
  readonly dayChange = input<DayChange | null>(null);
}
