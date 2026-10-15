import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { BalanceHistoryResponse, PortfolioMetricsResponse } from '../../generated/trading-api-client';

@Component({
  selector: 'app-balance-card', imports: [CurrencyPipe, DatePipe, PercentPipe],
  template: `
    <p class="label">Portfolio value</p>
    <p class="total">{{ portfolio().totalPortfolioValue | currency:'USD' }}</p>
    @if (history()?.portfolioValue != null) {
      <p class="previous">Previous session ({{ history()?.sessionDate | date:'mediumDate':'UTC' }}): {{ history()?.portfolioValue | currency:'USD' }}</p>
      <p class="change" [class.loss]="change()! < 0" [class.gain]="change()! > 0">
        {{ change()! > 0 ? '+' : '' }}{{ change() | currency:'USD' }}
        @if (percentage() != null) { ({{ percentage()! > 0 ? '+' : '' }}{{ percentage() | percent:'1.2-2' }}) }
        <span>balance change, including cash transfers</span>
      </p>
    } @else { <p class="previous">Previous session balance unavailable until a daily snapshot is recorded.</p> }
    @if (historyError()) { <p class="loss" role="status">Couldn't update balance history. Use Refresh to retry.</p> }
    <div class="cash"><span>Available cash</span><strong>{{ portfolio().cashBalance | currency:'USD' }}</strong></div>
    @if (history()?.cashBalance != null) { <p class="previous">Previous cash balance: {{ history()?.cashBalance | currency:'USD' }}</p> }
  `,
  styles: `:host{display:block;max-width:760px}p{margin:0}.label,.previous,.cash span,.change span{color:var(--color-muted)}.label{font-size:14px;margin-bottom:8px}.total{font-size:clamp(38px,6vw,72px);line-height:1.1;letter-spacing:-.035em;overflow-wrap:anywhere}.previous{font-size:14px;margin-top:12px;line-height:1.5}.change{margin-top:8px;font-size:17px;line-height:1.5}.change span{font-size:14px}.cash{display:flex;flex-wrap:wrap;gap:12px 24px;align-items:baseline;margin-top:24px}.cash strong{font-size:28px;font-weight:500}.gain{color:var(--color-gain)}.loss{color:var(--color-loss)}`,
})
export class BalanceCardComponent {
  readonly portfolio = input.required<PortfolioMetricsResponse>();
  readonly history = input<BalanceHistoryResponse | null>(null);
  readonly historyError = input(false);
  readonly change = computed(() => this.history()?.portfolioValue == null || this.portfolio().totalPortfolioValue == null ? null : Number(this.portfolio().totalPortfolioValue) - Number(this.history()!.portfolioValue));
  readonly percentage = computed(() => Number(this.history()?.portfolioValue) > 0 && this.change() != null ? this.change()! / Number(this.history()!.portfolioValue) : null);
}
