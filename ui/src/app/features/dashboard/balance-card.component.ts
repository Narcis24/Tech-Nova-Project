import { CurrencyPipe, PercentPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { PortfolioMetricsResponse } from '../../generated/trading-api-client';

@Component({
  selector: 'app-balance-card',
  imports: [CurrencyPipe, PercentPipe],
  template: `
    <p class="label">Portfolio value</p>
    <p class="total">{{ portfolio().totalPortfolioValue | currency: 'USD' }}</p>
    @if (dayChange() != null) {
      <p class="change" [class.gain]="dayChange()! > 0" [class.loss]="dayChange()! < 0">
        {{ dayChange()! > 0 ? '+' : '' }}{{ dayChange() | currency: 'USD' }}
        @if (percent() != null) {
          ({{ percent()! > 0 ? '+' : '' }}{{ percent() | percent: '1.2-2' }})
        }
        <span>since the previous close</span>
      </p>
      <p class="previous">Previous close value: {{ previousValue() | currency: 'USD' }}</p>
    }
    <div class="cash">
      <span>Available cash</span><strong>{{ portfolio().cashBalance | currency: 'USD' }}</strong>
    </div>
  `,
  styles: `
    :host {
      display: block;
      max-width: 760px;
    }
    p {
      margin: 0;
    }
    .label,
    .cash span,
    .change span {
      color: var(--secondary-text-color);
    }
    .label {
      font-size: 14px;
      margin-bottom: 8px;
    }
    .total {
      font-size: clamp(38px, 6vw, 72px);
      line-height: 1.1;
      letter-spacing: -0.035em;
      overflow-wrap: anywhere;
    }
    .change {
      margin-top: 8px;
      font-size: 17px;
      line-height: 1.5;
    }
    .change span {
      font-size: 14px;
    }
    .previous {
      margin-top: 4px;
      font-size: 14px;
      color: var(--secondary-text-color);
    }
    .cash {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 24px;
      align-items: baseline;
      margin-top: 24px;
    }
    .cash strong {
      font-size: 28px;
      font-weight: 500;
    }
    .gain {
      color: var(--gain-color);
    }
    .loss {
      color: var(--loss-color);
    }
  `,
})
export class BalanceCardComponent {
  readonly portfolio = input.required<PortfolioMetricsResponse>();
  /** Change in holdings' value since the previous close, from DashboardStore.dayChange. */
  readonly dayChange = input<number | null>(null);
  /** Cash is unchanged by price moves, so the previous close value is today's value minus the change. */
  readonly previousValue = computed(() => {
    const change = this.dayChange(),
      total = this.portfolio().totalPortfolioValue;
    return change == null || total == null ? null : Number(total) - change;
  });
  readonly percent = computed(() => {
    const previous = this.previousValue();
    return previous != null && previous > 0 ? this.dayChange()! / previous : null;
  });
}
