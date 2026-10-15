import { CurrencyPipe } from '@angular/common';
import { AfterViewInit, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, exhaustMap, catchError, of } from 'rxjs';
import { newUuid } from '../../core/uuid';
import { OrderControllerService, PositionResponse } from '../../generated/trading-api-client';
@Component({
  selector: 'app-close-position', imports: [CurrencyPipe],
  template: `<dialog #dialog aria-labelledby="close-title" (cancel)="onCancel($event)" (close)="dismiss.emit()"><h2 id="close-title">Close {{ position().symbol }} position?</h2><p>Submit a market sell order for all {{ position().quantity }} shares in {{ accountId() }}.</p><p>Last stored price: {{ position().currentPrice | currency:'USD' }}. The execution price may differ. Your position remains open until the order fills.</p>@if (error()) { <p class="error" role="alert">{{ error() }}</p> }<div class="actions"><button type="button" (click)="confirm()" [disabled]="submitting()">{{ submitting() ? 'Submitting…' : 'Confirm market sell' }}</button><button type="button" (click)="dismiss.emit()" [disabled]="submitting()">Cancel</button></div></dialog>`,
  styles: `dialog{box-sizing:border-box;width:min(480px,calc(100vw - 32px));border:0;border-radius:18px;padding:28px;color:var(--color-ink);font:inherit;box-shadow:0 20px 80px #14182b33}dialog::backdrop{background:#14182b66}h2{margin:0;font-size:22px}p{line-height:1.6}.actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:24px}button{min-height:44px;padding:0 16px;border:0;border-radius:10px;background:var(--color-track);color:var(--color-ink);font:inherit;font-size:14px;cursor:pointer}button:first-child{background:var(--color-loss);color:white}button:disabled{opacity:.6}.error{color:var(--color-loss)}`,
})
export class ClosePositionComponent implements AfterViewInit {
  private readonly api = inject(OrderControllerService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly accountId = input.required<string>(); readonly position = input.required<PositionResponse>();
  readonly dismiss = output<void>(); readonly completed = output<string>();
  readonly submitting = signal(false); readonly error = signal<string | null>(null);
  private key = newUuid(); private readonly submissions = new Subject<void>();
  constructor() {
    this.submissions.pipe(exhaustMap(() => this.api.placeOrder({ accountId:this.accountId(), symbol:this.position().symbol!, side:'SELL', orderType:'MARKET', quantity:Number(this.position().quantity), idempotencyKey:this.key }).pipe(catchError(error => { if (error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500) this.key = newUuid(); this.error.set("Couldn't confirm submission. Try again; retrying uses the same order ID to prevent duplicates."); return of(null); }))),takeUntilDestroyed()).subscribe(order => {
      this.submitting.set(false);
      if (!order) return;
      if (['REJECTED','CANCELLED','EXPIRED'].includes(order.status ?? '')) { this.key = newUuid(); this.error.set(order.message ?? `Order ${order.status?.toLowerCase()}. No position was closed.`); return; }
      this.completed.emit(order.status === 'FILLED' ? `${order.symbol} sell order filled.` : `${order.symbol} sell order submitted (${order.status ?? 'pending'}). Awaiting execution.`);
    });
  }
  ngAfterViewInit(): void { this.dialog().nativeElement.showModal(); }
  onCancel(event: Event): void { if (this.submitting()) event.preventDefault(); }
  confirm(): void {
    if (this.submitting()) return;
    if (!this.position().symbol || !Number.isInteger(Number(this.position().quantity)) || Number(this.position().quantity) < 1 || Number(this.position().quantity) > 100000) { this.error.set('This position cannot be closed with a single order. Use Sell to choose a quantity.'); return; }
    this.error.set(null); this.submitting.set(true); this.submissions.next();
  }
}
