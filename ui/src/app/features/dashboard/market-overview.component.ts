import { sessionDate } from '../../core/session-date';
import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, computed, effect, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MarketAssetResponse } from '../../generated/trading-api-client';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';

@Component({
  selector: 'app-market-overview',
  imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink, SymbolIconComponent],
  templateUrl: './market-overview.component.html',
  styleUrl: './market-overview.component.css',
})
export class MarketOverviewComponent {
  readonly sessionDate = sessionDate;
  readonly assets = input.required<MarketAssetResponse[]>();
  readonly loading = input(false);
  readonly error = input(false);
  readonly refresh = output<void>();
  readonly tabs = [
    { value: 'watchlist', label: 'Watchlist' },
    { value: 'gainers', label: 'Top gainers' },
    { value: 'losers', label: 'Top losers' },
    { value: 'all', label: 'All assets' },
  ];
  readonly view = signal('watchlist');
  readonly watchlist = signal<string[]>(this.readWatchlist());
  readonly query = signal('');
  readonly assetClass = signal('');
  readonly currency = signal('');
  readonly assetClasses = computed(() =>
    [
      ...new Set(
        this.assets()
          .map((a) => a.assetClass)
          .filter((value): value is string => !!value),
      ),
    ].sort((a, b) => a.localeCompare(b)),
  );
  readonly currencies = computed(() =>
    [
      ...new Set(
        this.assets()
          .map((a) => a.currency)
          .filter((value): value is string => !!value),
      ),
    ].sort((a, b) => a.localeCompare(b)),
  );
  readonly hasFilters = computed(() => !!(this.query().trim() || this.assetClass() || this.currency()));
  readonly visible = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    const assets = this.assets().filter(
      (asset) =>
        (!query || [asset.symbol, asset.name].some((value) => value?.toLocaleLowerCase().includes(query))) &&
        (!this.assetClass() || asset.assetClass === this.assetClass()) &&
        (!this.currency() || asset.currency === this.currency()),
    );
    if (this.view() === 'watchlist') return assets.filter((a) => this.watchlist().includes(a.symbol ?? ''));
    if (this.view() === 'gainers')
      return assets
        .filter((a) => a.changePercent != null && a.changePercent > 0)
        .sort((a, b) => b.changePercent! - a.changePercent!)
        .slice(0, 5);
    if (this.view() === 'losers')
      return assets
        .filter((a) => a.changePercent != null && a.changePercent < 0)
        .sort((a, b) => a.changePercent! - b.changePercent!)
        .slice(0, 5);
    return assets;
  });
  constructor() {
    effect(() => {
      const symbols = this.watchlist();
      try {
        localStorage.setItem('technova.watchlist', JSON.stringify(symbols));
      } catch {
        /* Still works for this session without storage. */
      }
    });
  }
  private readWatchlist(): string[] {
    try {
      const value: unknown = JSON.parse(localStorage.getItem('technova.watchlist') ?? 'null');
      if (Array.isArray(value) && value.every((v) => typeof v === 'string')) return value;
    } catch {
      /* Use defaults. */
    }
    return ['SPY', 'QQQ', 'TLT', 'GLD'];
  }
  clearFilters(): void {
    this.query.set('');
    this.assetClass.set('');
    this.currency.set('');
  }
  add(symbol: string): void {
    if (this.assets().some((a) => a.symbol === symbol) && !this.watchlist().includes(symbol))
      this.watchlist.update((list) => [...list, symbol]);
  }
  toggle(symbol: string): void {
    if (this.watchlist().includes(symbol)) this.watchlist.update((list) => list.filter((s) => s !== symbol));
    else this.add(symbol);
  }
  sparkline(values: number[]): string {
    if (!values.length) return '';
    const min = Math.min(...values),
      range = Math.max(...values) - min;
    return values
      .map(
        (v, i) =>
          `${values.length === 1 ? 80 : (i * 160) / (values.length - 1)},${range ? 36 - ((v - min) * 32) / range : 20}`,
      )
      .join(' ');
  }
}
