import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, of, Subject } from 'rxjs';
import { AccountService } from '../../services/account.service';
import { PortfolioControllerService, PriceControllerService, InstrumentControllerService, OrderControllerService, OrderResponse, PlaceOrderRequest } from '../../generated/trading-api-client';
import { TradeComponent } from './trade.component';

describe('TradeComponent', () => {
  let api: jasmine.SpyObj<{placeOrder:(request:PlaceOrderRequest)=>Observable<OrderResponse>}>;
  const currentAccountId = signal<string | null>('ACC001');
  function setup() {
    currentAccountId.set('ACC001'); api=jasmine.createSpyObj('orders',['placeOrder']); api.placeOrder.and.returnValue(of({orderId:'test',status:'PENDING'}));
    TestBed.configureTestingModule({imports:[TradeComponent],providers:[provideRouter([]),
      {provide:ActivatedRoute,useValue:{queryParamMap:of(convertToParamMap({symbol:'AAPL',side:'SELL'}))}},
      {provide:AccountService,useValue:{currentAccountId}},
      {provide:OrderControllerService,useValue:api},
      {provide:PortfolioControllerService,useValue:{getPortfolioSnapshot:(id:string)=>of({accountId:id,cashBalance:50000,positions:[{symbol:'AAPL',quantity:120}]})}},
      {provide:PriceControllerService,useValue:{getPriceHistory:()=>of([])}},
      {provide:InstrumentControllerService,useValue:{getInstruments:()=>of([])}},
    ]}); const fixture=TestBed.createComponent(TradeComponent); fixture.detectChanges(); return fixture;
  }
  it('prefills a dashboard action but does not submit without review and confirmation', () => {
    const component=setup().componentInstance;
    expect(component.form.controls.symbol.value).toBe('AAPL'); expect(component.form.controls.side.value).toBe('SELL');
    expect(api.placeOrder).not.toHaveBeenCalled(); component.prepare(); expect(api.placeOrder).not.toHaveBeenCalled();
    component.confirm(); expect(api.placeOrder.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({accountId:'ACC001',symbol:'AAPL',side:'SELL',orderType:'MARKET',quantity:25}));
  });
  it('blocks invalid quantities and missing limit prices', () => {
    const component=setup().componentInstance;
    component.form.controls.quantity.setValue(1.5); component.prepare(); expect(component.review()).toBeNull();
    component.form.controls.quantity.setValue(1); component.form.controls.orderType.setValue('LIMIT'); component.prepare();
    expect(component.review()).toBeNull(); expect(component.error()).toContain('limit price');
  });
  it('reuses its idempotency key on network retry and prevents double submission', () => {
    const component=setup().componentInstance; const pending=new Subject<OrderResponse>(); api.placeOrder.and.returnValue(pending);
    component.prepare(); component.confirm(); component.confirm(); expect(api.placeOrder).toHaveBeenCalledTimes(1);
    const key=api.placeOrder.calls.mostRecent().args[0].idempotencyKey; pending.error(new Error('offline'));
    api.placeOrder.and.returnValue(of({status:'PENDING'})); component.confirm();
    expect(api.placeOrder.calls.mostRecent().args[0].idempotencyKey).toBe(key);
    component.prepare(); expect(component.review()?.idempotencyKey).not.toBe(key);
  });

  it('updates side, limit steps and account estimates from the ticket controls', () => {
    const fixture=setup(), component=fixture.componentInstance;
    component.onQuote({symbol:'AAPL',price:100,previousClose:99,date:'2026-10-14'});
    component.setSide('BUY'); component.setOrderType('LIMIT');
    expect(component.form.controls.price.value).toBe(100);
    component.stepLimit(1); expect(component.form.controls.price.value).toBe(100.05);
    component.stepLimit(-1); expect(component.form.controls.price.value).toBe(100);
    component.form.controls.quantity.setValue(50);
    expect(component.total()).toBe(5000); expect(component.cashAfter()).toBe(45000);
    component.setSide('SELL'); expect(component.cashAfter()).toBe(55000);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.order-ticket').classList.contains('sell')).toBeTrue();
  });
  it('blocks a limit buy beyond cash and a sell beyond held shares before review', () => {
    const component=setup().componentInstance;
    component.setSide('BUY');component.setOrderType('LIMIT');component.form.controls.price.setValue(1000);component.form.controls.quantity.setValue(100);
    component.prepare();expect(component.validationIssue()).toContain('available cash');expect(component.review()).toBeNull();
    component.setSide('SELL');component.form.controls.quantity.setValue(121);component.prepare();
    expect(component.validationIssue()).toContain('120 AAPL');expect(api.placeOrder).not.toHaveBeenCalled();
  });

  it('invalidates review when the account changes', () => {
    const fixture=setup(); fixture.componentInstance.prepare(); currentAccountId.set('ACC002'); fixture.detectChanges();
    fixture.componentInstance.confirm(); expect(api.placeOrder).not.toHaveBeenCalled();
  });
});
