import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../generated/trading-api-client';
import { ActionButtonsComponent } from './action-buttons.component';
@Component({
  selector: 'app-position-summary', imports: [DecimalPipe, PercentPipe, RouterLink, ActionButtonsComponent],
  template: `<h2>Holdings</h2>
    @if (positions().length) {
      <p class="scroll-hint" aria-hidden="true">Swipe across for prices and actions <span>→</span></p><div class="scroll" tabindex="0" role="region" aria-label="Holdings table, scroll horizontally for all columns"><table><thead><tr><th scope="col">Instrument</th><th scope="col">Quantity</th><th scope="col">Entry price</th><th scope="col">Current price</th><th scope="col">Value</th><th scope="col">P&amp;L</th><th scope="col">Actions</th></tr></thead><tbody>
        @for (p of positions(); track p.symbol) { <tr><th scope="row"><a routerLink="/trade" [queryParams]="{symbol:p.symbol,side:'BUY'}">{{ p.symbol }}</a><span class="name">{{ instruments().get(p.symbol ?? '')?.name }}</span></th><td>{{ p.quantity | number }}</td><td>{{ p.averageCost | number:'1.2-2' }}</td><td>{{ p.currentPrice | number:'1.2-2' }}</td><td>{{ p.marketValue | number:'1.2-2' }}</td><td class="gain" [class.loss]="(p.unrealizedPnL ?? 0) < 0">{{ (p.unrealizedPnL ?? 0) > 0 ? '+' : '' }}{{ p.unrealizedPnL | number:'1.2-2' }}<span class="name">{{ positionReturn(p) | percent:'1.2-2' }}</span></td><td><app-action-buttons [symbol]="p.symbol ?? ''" [closeDisabled]="pendingSells().has(p.symbol ?? '')" (close)="close.emit(p)" /></td></tr> }
      </tbody></table></div>
    } @else { <p>No open positions yet. <a routerLink="/trade">Place your first trade</a>.</p> }`,
  styles: `:host{display:block;min-width:0}h2{margin:0 0 12px;font-size:18px;font-weight:600}.scroll{overflow:auto}table{border-collapse:collapse;width:100%;min-width:820px;font-size:14px}th,td{text-align:right;padding:16px 10px;border-bottom:1px solid #eceef2;font-weight:400}thead th{font-size:13px;color:var(--color-muted);white-space:nowrap}th:first-child{text-align:left;padding-left:0}tbody th a{font-weight:600;text-decoration:none;color:var(--color-ink)}th:last-child,td:last-child{padding-right:0}.name{display:block;font-size:13px;margin-top:4px;color:var(--color-muted)}td.gain .name{color:inherit}.gain{color:var(--color-gain)}.loss{color:var(--color-loss)}app-action-buttons{min-width:110px}th:last-child{text-align:left;width:210px}.scroll-hint{display:none}tbody tr:hover{background:#fafbfe}@media(max-width:1000px){.scroll-hint{display:flex;justify-content:space-between;gap:12px;margin:0 0 10px;color:var(--color-muted);font-size:12px}}`,
})
export class PositionSummaryComponent {
  readonly positions = input.required<PositionResponse[]>();
  readonly instruments = input.required<Map<string, { name?: string }>>();
  readonly pendingSells = input(new Set<string>()); readonly close = output<PositionResponse>();
  positionReturn(p: PositionResponse): number | null { const cost = Number(p.quantity) * Number(p.averageCost); return cost > 0 && p.unrealizedPnL != null ? Number(p.unrealizedPnL) / cost : null; }
}
