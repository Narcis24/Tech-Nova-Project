import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { PriceHistoryPoint } from '../../generated/trading-api-client';

/** How far back each return looks, in calendar terms from the latest session. */
const PERIODS = [
  { label: '1W', days: 7 },
  { label: '1M', months: 1 },
  { label: '3M', months: 3 },
  { label: 'YTD', yearStart: true },
  { label: '1Y', months: 12 },
] as const;

/**
 * The selected instrument's day and year at a glance: the latest session's open, range and volume,
 * the 52-week range and returns over common periods. Everything comes from the stored daily bars.
 */
@Component({
  selector: 'app-key-stats',
  imports: [DecimalPipe, PercentPipe],
  templateUrl: './key-stats.component.html',
  styleUrl: './key-stats.component.css',
})
export class KeyStatsComponent {
  /** Daily bars, oldest first, as the price chart loaded them. */
  readonly points = input<PriceHistoryPoint[]>([]);
  readonly latest = computed(() => this.points().at(-1) ?? null);
  readonly previousClose = computed(() => this.points().at(-2)?.close ?? null);
  readonly averageVolume = computed(() => {
    const volumes = this.points()
      .slice(-30)
      .map((p) => Number(p.volume))
      .filter((v) => Number.isFinite(v) && v > 0);
    return volumes.length ? volumes.reduce((sum, v) => sum + v, 0) / volumes.length : null;
  });
  readonly yearLow = computed(() =>
    this.points().length ? Math.min(...this.points().map((p) => Number(p.low ?? p.close))) : null,
  );
  readonly yearHigh = computed(() =>
    this.points().length ? Math.max(...this.points().map((p) => Number(p.high ?? p.close))) : null,
  );
  /** Where the latest close sits in the 52-week range, from 0 (the low) to 100 (the high). */
  readonly yearPosition = computed(() => {
    const low = this.yearLow(),
      high = this.yearHigh(),
      close = Number(this.latest()?.close);
    return low == null || high == null || high <= low ? 50 : ((close - low) / (high - low)) * 100;
  });
  readonly returns = computed(() => {
    const latest = this.latest();
    if (!latest?.date) return [];
    return PERIODS.map((period) => {
      const start = new Date(`${latest.date}T00:00:00Z`);
      if ('days' in period) start.setUTCDate(start.getUTCDate() - period.days);
      else if ('months' in period) start.setUTCMonth(start.getUTCMonth() - period.months);
      else start.setUTCMonth(0, 0); // the last day of the previous year
      const base = this.closeOnOrBefore(start.toISOString().slice(0, 10));
      return { label: period.label, value: base ? Number(latest.close) / base - 1 : null };
    });
  });

  /** The close of the last session on or before the date; null when the history starts after it. */
  private closeOnOrBefore(date: string): number | null {
    const points = this.points();
    for (let i = points.length - 1; i >= 0; i--) {
      if (points[i].date! <= date) return Number(points[i].close);
    }
    return null;
  }
}
