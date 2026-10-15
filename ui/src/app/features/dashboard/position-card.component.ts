import { CurrencyPipe, DecimalPipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { PositionResponse } from '../../generated/trading-api-client';
import { ActionButtonsComponent } from './action-buttons.component';
@Component({
  selector: 'app-position-card', imports: [CurrencyPipe, DecimalPipe, PercentPipe, TitleCasePipe, ActionButtonsComponent],
  template: `<div class="heading"><strong>{{ position().symbol }}</strong><span>{{ assetClass() === 'ETF' ? 'ETF' : (assetClass() | titlecase) }}</span></div>
    <p class="value">{{ position().marketValue | currency:'USD':'symbol':'1.0-0' }}</p>
    <p class="muted">{{ allocation() | percent:'1.1-1' }} of portfolio</p>
    <dl><div><dt>Quantity</dt><dd>{{ position().quantity | number }}</dd></div><div><dt>Entry price</dt><dd>{{ position().averageCost | currency:'USD' }}</dd></div><div><dt>Current price</dt><dd>{{ position().currentPrice | currency:'USD' }}</dd></div></dl>
    <p class="gain" [class.loss]="(position().unrealizedPnL ?? 0) < 0">{{ (position().unrealizedPnL ?? 0) > 0 ? '+' : '' }}{{ position().unrealizedPnL | currency:'USD' }} P&amp;L</p>
    <app-action-buttons [symbol]="position().symbol ?? ''" [closeDisabled]="closeDisabled()" (close)="close.emit(position())" />`,
  styles: `:host{display:flex;flex-direction:column;gap:8px;min-width:210px;box-sizing:border-box;padding:18px;border-radius:14px;background:var(--color-card)}p{margin:0}.heading{display:flex;flex-wrap:wrap;gap:8px;align-items:baseline}.heading strong{font-size:17px}.heading span,.muted,dt{color:var(--color-muted);font-size:13px}.value{font-size:24px;margin-top:16px}dl{margin:8px 0;font-size:14px;display:grid;gap:8px}dl div{display:flex;justify-content:space-between;gap:8px}dd{margin:0}.gain{color:var(--color-gain);font-size:14px}.loss{color:var(--color-loss)}app-action-buttons{margin-top:auto;padding-top:8px}@media(max-width:767.98px){.value{margin-top:8px}dl{grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}dl div{flex-direction:column;gap:4px}dt{font-size:12px}dd{font-size:14px}.heading span{font-size:12px}}`,
})
export class PositionCardComponent {
  readonly position = input.required<PositionResponse>(); readonly allocation = input<number | null>(null);
  readonly assetClass = input<string>(); readonly closeDisabled = input(false); readonly close = output<PositionResponse>();
}
