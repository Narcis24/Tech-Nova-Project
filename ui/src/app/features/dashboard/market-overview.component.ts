import { sessionDate } from '../../core/session-date';
import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MarketAssetResponse } from '../../generated/trading-api-client';

type View = 'gainers' | 'losers' | 'all';

@Component({
  selector: 'app-market-overview',
  imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink],
  templateUrl: './market-overview.component.html',
  styleUrl: './market-overview.component.css',
})
export class MarketOverviewComponent {
  readonly sessionDate = sessionDate;
  readonly assets = input.required<MarketAssetResponse[]>();
  readonly loading = input(false);
  readonly error = input(false);
  readonly refresh = output<void>();
  readonly tabs: { value: View; label: string }[] = [
    { value: 'gainers', label: 'Top gainers' },
    { value: 'losers', label: 'Top losers' },
    { value: 'all', label: 'All assets' },
  ];
  readonly view = signal<View>('gainers');
  readonly visible = computed(() => {
    const assets = this.assets();
    if (this.view() === 'all') return assets;
    const direction = this.view() === 'gainers' ? 1 : -1;
    return assets
      .filter((a) => a.changePercent != null && Math.sign(a.changePercent) === direction)
      .sort((a, b) => direction * (b.changePercent! - a.changePercent!))
      .slice(0, 5);
  });

  /** SVG polyline points for a 160x40 sparkline; a flat or single-point series is drawn mid-height. */
  sparkline(values: number[]): string {
    if (!values.length) return '';
    const min = Math.min(...values);
    const range = Math.max(...values) - min;
    return values
      .map((v, i) => {
        const x = values.length === 1 ? 80 : (i * 160) / (values.length - 1);
        const y = range ? 36 - ((v - min) * 32) / range : 20;
        return `${x},${y}`;
      })
      .join(' ');
  }
}
