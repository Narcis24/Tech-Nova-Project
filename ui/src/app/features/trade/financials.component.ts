import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { FinancialPeriod, FundamentalsResponse, InstrumentControllerService } from '../../generated/trading-api-client';

const COMPACT_USD = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 2,
});
const PERIOD_MONTH = new Intl.DateTimeFormat('en-US', { month: 'short', year: 'numeric', timeZone: 'UTC' });

/** One period drawn as two bars, revenue and net income, in chart units (viewBox 0 0 600 200). */
export interface PeriodBars {
  label: string;
  period: FinancialPeriod;
  width: number;
  revenue: { x: number; y: number; height: number };
  income: { x: number; y: number; height: number; loss: boolean };
}

/**
 * The company or fund behind the selected instrument: valuation, recent results and what it does,
 * as the data pipeline loaded them from Yahoo Finance. Shows nothing for a symbol it has not loaded.
 */
@Component({
  selector: 'app-financials',
  imports: [DatePipe, DecimalPipe, PercentPipe],
  templateUrl: './financials.component.html',
  styleUrl: './financials.component.css',
})
export class FinancialsComponent {
  private readonly api = inject(InstrumentControllerService);
  readonly symbol = input('');
  readonly data = signal<FundamentalsResponse | null>(null);
  readonly periodType = signal<'quarters' | 'years'>('quarters');
  readonly aboutOpen = signal(false);
  readonly isFund = computed(() => this.data()?.quoteType !== 'EQUITY');
  /** Sector and industry for a company, category and fund family for a fund. */
  readonly subtitle = computed(() => {
    const data = this.data();
    const parts = this.isFund() ? [data?.category ?? 'Fund', data?.fundFamily] : [data?.sector, data?.industry];
    return parts.filter(Boolean).join(' · ');
  });
  readonly periods = computed(() => {
    const data = this.data();
    return (this.periodType() === 'quarters' ? data?.quarters : data?.years) ?? [];
  });
  /** Chart y for an amount: the largest at the top, a loss below the zero line. */
  private readonly scale = computed(() => {
    const values = this.periods().flatMap((p) => [Number(p.revenue ?? 0), Number(p.netIncome ?? 0)]),
      top = Math.max(0, ...values),
      span = top - Math.min(0, ...values) || 1;
    return (value: number) => 12 + ((top - value) / span) * 168;
  });
  readonly zeroY = computed(() => this.scale()(0));
  readonly bars = computed<PeriodBars[]>(() => {
    const periods = this.periods(),
      y = this.scale(),
      slot = 600 / Math.max(1, periods.length),
      width = Math.min(44, slot * 0.3);
    return periods.map((period, i) => {
      const centre = slot * (i + 0.5),
        revenue = Number(period.revenue ?? 0),
        income = Number(period.netIncome ?? 0);
      return {
        label: this.label(period),
        period,
        width,
        revenue: { x: centre - width - 2, y: y(Math.max(revenue, 0)), height: Math.abs(y(revenue) - y(0)) },
        income: {
          x: centre + 2,
          y: y(Math.max(income, 0)),
          // A thin line for a break-even period, nothing when the figure is missing
          height: period.netIncome == null ? 0 : Math.max(1, Math.abs(y(income) - y(0))),
          loss: income < 0,
        },
      };
    });
  });

  constructor() {
    toObservable(this.symbol)
      .pipe(
        switchMap((symbol) =>
          /^[A-Z0-9.-]{1,10}$/.test(symbol)
            ? this.api.getFundamentals(symbol).pipe(
                // 204 when the pipeline has not loaded the symbol
                map((data) => (data?.symbol === symbol ? data : null)),
                catchError(() => of(null)),
              )
            : of(null),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((data) => {
        this.aboutOpen.set(false);
        this.data.set(data);
      });
  }

  money(value: number | undefined | null): string {
    return value == null ? '—' : COMPACT_USD.format(Number(value));
  }

  /** Quarters by the month they end in, since companies' fiscal quarters differ; years as fiscal years. */
  label(period: FinancialPeriod): string {
    const end = new Date(`${period.periodEnd}T00:00:00Z`);
    return this.periodType() === 'quarters' ? PERIOD_MONTH.format(end) : `FY ${end.getUTCFullYear()}`;
  }
}
