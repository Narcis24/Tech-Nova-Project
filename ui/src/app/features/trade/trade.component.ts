import { Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, exhaustMap, of, Subject } from 'rxjs';
import { InstrumentControllerService, InstrumentResponse, OrderControllerService, OrderResponse, PlaceOrderRequest } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';

@Component({
  selector:'app-trade', imports:[ReactiveFormsModule,RouterLink], templateUrl:'./trade.component.html', styleUrl:'./trade.component.css',
})
export class TradeComponent {
  private readonly accounts = inject(AccountService); private readonly api = inject(OrderControllerService);
  private readonly instrumentsApi = inject(InstrumentControllerService); private readonly route = inject(ActivatedRoute);
  readonly accountId = this.accounts.currentAccountId; readonly instruments = signal<InstrumentResponse[]>([]);
  readonly submitting = signal(false); readonly error = signal<string | null>(null); readonly result = signal<OrderResponse | null>(null);
  readonly review = signal<PlaceOrderRequest | null>(null); private readonly submissions = new Subject<PlaceOrderRequest>();
  private lastFingerprint = ''; private key = '';
  readonly form = new FormGroup({
    symbol:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.pattern(/^[A-Z0-9.-]{1,10}$/)]}),
    side:new FormControl('BUY',{nonNullable:true,validators:[Validators.pattern(/^(BUY|SELL)$/)]}),
    quantity:new FormControl<number | null>(1,[Validators.required,Validators.min(1),Validators.max(100000),Validators.pattern(/^\d+$/)]),
    orderType:new FormControl('MARKET',{nonNullable:true,validators:[Validators.pattern(/^(MARKET|LIMIT)$/)]}),
    price:new FormControl<number | null>(null),
  });
  constructor() {
    this.loadInstruments();
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(params => {
      this.form.patchValue({symbol:params.get('symbol')?.toUpperCase() ?? '',side:params.get('side') === 'SELL' ? 'SELL' : 'BUY'});
      this.review.set(null); this.result.set(null);
    });
    effect(() => { this.accountId(); this.review.set(null); this.result.set(null); this.error.set(null); });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => { this.review.set(null); this.error.set(null); this.result.set(null); });
    this.submissions.pipe(exhaustMap(request => this.api.placeOrder(request).pipe(catchError(() => { this.error.set("Couldn't confirm submission. Retry without changing the order to avoid duplicates."); return of(null); }))),takeUntilDestroyed()).subscribe(order => {
      this.submitting.set(false);
      if (order) { this.result.set(order); this.review.set(null); this.lastFingerprint = ''; this.key = ''; }
    });
  }
  loadInstruments(): void { this.instrumentsApi.getInstruments(true).subscribe({ next:assets => this.instruments.set(assets), error:() => this.error.set('Instrument list unavailable. You can enter a symbol or reload the list.') }); }
  prepare(): void {
    this.error.set(null); this.form.markAllAsTouched(); const value = this.form.getRawValue(); const id = this.accountId();
    if (this.form.invalid || !id || value.quantity == null) return;
    if (value.orderType === 'LIMIT' && (value.price == null || !Number.isFinite(value.price) || value.price <= 0)) { this.error.set('Enter a positive limit price.'); return; }
    const request = { accountId:id,symbol:value.symbol,side:value.side,quantity:value.quantity,orderType:value.orderType,...(value.orderType === 'LIMIT' ? {price:value.price!} : {}) };
    const fingerprint = JSON.stringify(request);
    if (fingerprint !== this.lastFingerprint) { this.lastFingerprint=fingerprint; this.key=crypto.randomUUID(); }
    this.review.set({...request,idempotencyKey:this.key});
  }
  confirm(): void { const order = this.review(); if (!order || this.submitting() || order.accountId !== this.accountId()) return; this.error.set(null); this.submitting.set(true); this.submissions.next(order); }
}
