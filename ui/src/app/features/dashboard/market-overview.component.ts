import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, computed, effect, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MarketAssetResponse } from '../../generated/trading-api-client';

@Component({
  selector: 'app-market-overview', imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink],
  template: `<div class="heading"><div><h2>Market overview</h2><p>Daily closes · refreshed every 30 seconds while this page is visible</p></div><button type="button" (click)="refresh.emit()" [disabled]="loading()">{{ loading() ? 'Updating…' : 'Refresh market' }}</button></div>
    @if (error()) { <p role="status" class="error">Couldn't update market data. <button type="button" (click)="refresh.emit()">Retry</button></p> }
    <div class="tabs" role="group" aria-label="Market view">
      @for (tab of tabs; track tab.value) { <button type="button" [attr.aria-pressed]="view() === tab.value" (click)="view.set(tab.value)">{{ tab.label }}</button> }
    </div>
    @if (view() === 'watchlist') { <label class="add">Add an asset <select #symbol aria-label="Asset to watch"><option value="">Choose an instrument</option>@for (asset of assets(); track asset.symbol) { <option [value]="asset.symbol">{{ asset.symbol }} — {{ asset.name }}</option> }</select><button type="button" (click)="add(symbol.value)">Add</button></label> }
    @if (loading() && !assets().length) { <p role="status">Loading market data…</p> }
    @else if (visible().length) {
      <div class="assets">@for (asset of visible(); track asset.symbol) {
        <article><div class="asset-heading"><a routerLink="/trade" [queryParams]="{symbol:asset.symbol,side:'BUY'}">{{ asset.symbol }}</a><button type="button" [attr.aria-label]="(watchlist().includes(asset.symbol ?? '') ? 'Unwatch ' : 'Watch ') + asset.symbol" [attr.aria-pressed]="watchlist().includes(asset.symbol ?? '')" (click)="toggle(asset.symbol ?? '')">{{ watchlist().includes(asset.symbol ?? '') ? '★' : '☆' }}</button></div>
          <p class="name">{{ asset.name }}</p><p class="price">{{ asset.currentPrice | currency:(asset.currency ?? 'USD') }}</p>
          <p class="change" [class.gain]="(asset.change ?? 0) > 0" [class.loss]="(asset.change ?? 0) < 0">{{ (asset.change ?? 0) > 0 ? '+' : '' }}{{ (asset.change | currency:(asset.currency ?? 'USD')) ?? '—' }} ({{ (asset.changePercent | percent:'1.2-2') ?? '—' }})</p>
          <svg viewBox="0 0 160 40" role="img" [attr.aria-label]="asset.symbol + ', last ' + (asset.history?.length ?? 0) + ' daily closes'" [class.loss]="(asset.change ?? 0) < 0"><polyline [attr.points]="sparkline(asset.history ?? [])" /></svg>
          <p class="as-of">As of {{ asset.priceDate | date:'mediumDate':'UTC' }}</p>
        </article>
      }</div>
    } @else if (!error()) { <p class="empty">{{ view() === 'watchlist' ? 'Your watchlist is empty. Add an instrument above.' : 'No market data available for this view yet.' }}</p> }`,
  styles: `:host{display:block}h2,p{margin:0}.heading{display:flex;flex-wrap:wrap;justify-content:space-between;gap:16px;align-items:center}h2{font-size:18px}.heading p,.name,.as-of{color:var(--color-muted);font-size:13px;line-height:1.5;margin-top:6px}.tabs{display:flex;flex-wrap:wrap;gap:6px;margin:20px 0}button,select{min-height:40px;padding:0 12px;border:0;border-radius:8px;font:inherit;font-size:14px;background:var(--color-track);color:var(--color-ink)}button{cursor:pointer}button[aria-pressed=true]{background:var(--color-accent-soft);color:var(--color-accent)}button:disabled{opacity:.6}.assets{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(200px,100%),1fr));gap:10px}article{padding:18px;background:var(--color-card);border-radius:14px;min-width:0}.asset-heading{display:flex;align-items:center;justify-content:space-between}.asset-heading a{font-weight:600;text-decoration:none;color:var(--color-ink)}.asset-heading button{font-size:20px;min-height:36px;background:none}.name{min-height:40px}.price{font-size:22px;margin:12px 0 6px}.change{font-size:14px}.gain{color:var(--color-gain)}.loss,.error{color:var(--color-loss)}svg{width:100%;height:40px;margin-top:16px;fill:none;stroke:var(--color-gain);stroke-width:2}svg.loss{stroke:var(--color-loss)}.as-of{font-size:12px}.add{display:flex;flex-wrap:wrap;align-items:center;gap:8px;font-size:14px;margin-bottom:20px}.add select{max-width:100%}.empty{color:var(--color-muted);padding:20px 0}`,
})
export class MarketOverviewComponent {
  readonly assets = input.required<MarketAssetResponse[]>(); readonly loading = input(false); readonly error = input(false); readonly refresh = output<void>();
  readonly tabs = [{ value: 'watchlist', label: 'Watchlist' }, { value: 'gainers', label: 'Top gainers' }, { value: 'losers', label: 'Top losers' }, { value: 'all', label: 'All assets' }];
  readonly view = signal('watchlist');
  readonly watchlist = signal<string[]>(this.readWatchlist());
  readonly visible = computed(() => {
    const assets = this.assets();
    if (this.view() === 'watchlist') return assets.filter(a => this.watchlist().includes(a.symbol ?? ''));
    if (this.view() === 'gainers') return assets.filter(a => a.changePercent != null && a.changePercent > 0).sort((a,b) => b.changePercent! - a.changePercent!).slice(0,5);
    if (this.view() === 'losers') return assets.filter(a => a.changePercent != null && a.changePercent < 0).sort((a,b) => a.changePercent! - b.changePercent!).slice(0,5);
    return assets;
  });
  constructor() { effect(() => { const symbols = this.watchlist(); try { localStorage.setItem('technova.watchlist', JSON.stringify(symbols)); } catch { /* Still works for this session without storage. */ } }); }
  private readWatchlist(): string[] {
    try { const value: unknown = JSON.parse(localStorage.getItem('technova.watchlist') ?? 'null'); if (Array.isArray(value) && value.every(v => typeof v === 'string')) return value; } catch { /* Use defaults. */ }
    return ['SPY','QQQ','TLT','GLD'];
  }
  add(symbol: string): void { if (this.assets().some(a => a.symbol === symbol) && !this.watchlist().includes(symbol)) this.watchlist.update(list => [...list, symbol]); }
  toggle(symbol: string): void { if (this.watchlist().includes(symbol)) this.watchlist.update(list => list.filter(s => s !== symbol)); else this.add(symbol); }
  sparkline(values: number[]): string {
    if (!values.length) return '';
    const min = Math.min(...values), range = Math.max(...values) - min;
    return values.map((v,i) => `${values.length === 1 ? 80 : i * 160 / (values.length - 1)},${range ? 36 - (v-min) * 32 / range : 20}`).join(' ');
  }
}
