import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-action-buttons', imports: [RouterLink],
  template: `<a routerLink="/trade" [queryParams]="{symbol:symbol(),side:'BUY'}" [attr.aria-label]="'Buy more ' + symbol()">Buy more</a>
    <a routerLink="/trade" [queryParams]="{symbol:symbol(),side:'SELL'}" [attr.aria-label]="'Sell ' + symbol()">Sell</a>
    <button type="button" (click)="close.emit()" [disabled]="closeDisabled()" [attr.aria-label]="'Close ' + symbol() + ' position'">{{ closeDisabled() ? 'Sell pending' : 'Close' }}</button>`,
  styles: `:host{display:flex;flex-wrap:wrap;gap:6px}a,button{box-sizing:border-box;display:inline-flex;align-items:center;min-height:36px;padding:0 10px;border:0;border-radius:8px;font:inherit;font-size:13px;font-weight:600;text-decoration:none;background:var(--color-surface);color:var(--color-accent);cursor:pointer}button{color:var(--color-loss)}a:hover,button:not(:disabled):hover{background:var(--color-accent-soft)}button:disabled{color:var(--color-muted);cursor:default}@media(pointer:coarse){a,button{min-height:44px}}`,
})
export class ActionButtonsComponent {
  readonly symbol = input.required<string>();
  readonly closeDisabled = input(false);
  readonly close = output<void>();
}
