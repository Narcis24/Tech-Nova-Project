import { HttpErrorResponse } from '@angular/common/http';
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
  function setup(quote=true) {
    currentAccountId.set('ACC001'); api=jasmine.createSpyObj('orders',['placeOrder']); api.placeOrder.and.returnValue(of({orderId:'test',status:'PENDING'}));
    TestBed.configureTestingModule({imports:[TradeComponent],providers:[provideRouter([]),
      {provide:ActivatedRoute,useValue:{queryParamMap:of(convertToParamMap({symbol:'AAPL',side:'SELL'}))}},
      {provide:AccountService,useValue:{currentAccountId}},
      {provide:OrderControllerService,useValue:api},
      {provide:PortfolioControllerService,useValue:{getPortfolioSnapshot:(id:string)=>of({accountId:id,cashBalance:50000,positions:[{symbol:'AAPL',quantity:120}]})}},
      {provide:PriceControllerService,useValue:{getPriceHistory:()=>of([])}},
      {provide:InstrumentControllerService,useValue:{getInstruments:()=>of([])}},
    ]}); const fixture=TestBed.createComponent(TradeComponent); fixture.detectChanges(); if(quote) { fixture.componentInstance.onQuote({symbol:'AAPL',price:100,previousClose:99,date:'2026-10-14'}); fixture.detectChanges(); } return fixture;
  }
  it('prefills a dashboard action but does not submit without review and confirmation', () => {
    const component=setup().componentInstance;
    expect(component.form.controls.symbol.value).toBe('AAPL'); expect(component.form.controls.side.value).toBe('SELL');
    expect(api.placeOrder).not.toHaveBeenCalled(); component.prepare(); expect(api.placeOrder).not.toHaveBeenCalled();
    component.confirm(); expect(api.placeOrder.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({accountId:'ACC001',symbol:'AAPL',side:'SELL',orderType:'MARKET',quantity:25}));
  });
  it('opens review above the ticket and returns focus to the unchanged action when editing', () => {
    const fixture=setup(), root:HTMLElement=fixture.nativeElement;
    const trigger=root.querySelector<HTMLButtonElement>('fieldset > .submit-order')!;
    trigger.focus();trigger.click();fixture.detectChanges();
    const dialog=root.querySelector<HTMLDialogElement>('app-order-confirmation dialog')!;
    expect(dialog.open).toBeTrue();expect(dialog.closest('.order-ticket')).toBeNull();
    expect(document.activeElement).toBe(dialog.querySelector('h2'));
    expect(root.querySelector('fieldset > .submit-order')).toBe(trigger);
    expect(api.placeOrder).not.toHaveBeenCalled();
    dialog.querySelector<HTMLButtonElement>('.secondary')!.click();fixture.detectChanges();
    expect(root.querySelector('dialog')).toBeNull();expect(document.activeElement).toBe(trigger);
  });
  it('keeps pending, errors and results in the modal without replacing the ticket action', () => {
    const fixture=setup(), component=fixture.componentInstance, root:HTMLElement=fixture.nativeElement;
    const pending=new Subject<OrderResponse>();api.placeOrder.and.returnValue(pending);
    const trigger=root.querySelector('fieldset > .submit-order');
    component.prepare();fixture.detectChanges();component.confirm();fixture.detectChanges();
    const dialog=root.querySelector<HTMLDialogElement>('dialog')!;
    const cancel=new Event('cancel',{cancelable:true});dialog.dispatchEvent(cancel);fixture.detectChanges();
    expect(dialog.open).toBeTrue();expect(component.review()).not.toBeNull();
    expect(dialog.textContent).toContain('Submitting');
    pending.error(new Error('offline'));fixture.detectChanges();
    expect(dialog.querySelector('[role="alert"]')?.textContent).toContain("Couldn't confirm");
    api.placeOrder.and.returnValue(of({orderId:'placed',status:'PENDING'}));component.confirm();fixture.detectChanges();
    expect(root.querySelector('dialog')).toBe(dialog);expect(dialog.open).toBeTrue();
    expect(dialog.textContent).toContain('Order Pending');expect(dialog.textContent).toContain('placed');
    expect(root.querySelector('fieldset > .submit-order')).toBe(trigger);
  });
  it('keeps the price ruler space reserved before the quote arrives', () => {
    const fixture=setup(false), root:HTMLElement=fixture.nativeElement;
    const ruler=root.querySelector<HTMLElement>('.price-ruler')!;
    expect(ruler.style.visibility).toBe('hidden');
    expect(ruler.getAttribute('aria-hidden')).toBe('true');
    fixture.componentInstance.onQuote({symbol:'AAPL',price:100,previousClose:99,date:'2026-10-14'});
    fixture.detectChanges();
    expect(root.querySelector('.price-ruler')).toBe(ruler);
    expect(ruler.style.visibility).toBe('');
    expect(ruler.getAttribute('aria-hidden')).toBeNull();
  });
  it('blocks invalid quantities and missing limit prices', () => {
    const component=setup().componentInstance;
    component.form.controls.quantity.setValue(1.5); component.prepare(); expect(component.review()).toBeNull();
    component.form.controls.quantity.setValue(1); component.form.controls.orderType.setValue('LIMIT'); component.prepare();
    expect(component.review()).toBeNull(); expect(component.form.controls.price.hasError('required')).toBeTrue();
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

  it('validates limit price precision immediately and clears price validation for market orders', () => {
    const component=setup().componentInstance;component.setOrderType('LIMIT');
    for(const value of [null,0,-1,.001,1.234,Infinity]) { component.form.controls.price.setValue(value);expect(component.form.controls.price.invalid).toBeTrue();component.prepare();expect(component.review()).toBeNull(); }
    component.form.controls.price.setValue(12.34);expect(component.form.controls.price.valid).toBeTrue();
    component.setOrderType('MARKET');component.form.controls.price.setValue(null);expect(component.form.controls.price.valid).toBeTrue();
  });
  it('checks market buying power and blocks review when account or reference data is unavailable', () => {
    const component=setup().componentInstance;component.setSide('BUY');
    expect(component.maxShares()).toBe(500);component.form.controls.quantity.setValue(501);component.prepare();
    expect(component.validationIssue()).toContain('available cash');expect(component.review()).toBeNull();
    component.form.controls.quantity.setValue(1);component.quote.set(null);component.prepare();expect(component.review()).toBeNull();
    component.onQuote({symbol:'AAPL',price:100,previousClose:99,date:'2026-10-14'});
    component.context.set({id:'ACC001',loading:false,error:true,portfolio:null});component.prepare();expect(component.review()).toBeNull();
  });
  it('shows a server rejection reason and allows editing and resubmitting', () => {
    const component=setup().componentInstance;
    api.placeOrder.and.returnValue(new Observable(s=>s.error(new HttpErrorResponse({status:400,error:{message:'Only 10 shares held.',errorCode:'INSUFFICIENT_HOLDINGS'}}))));
    component.prepare();component.confirm();expect(component.error()).toBe('Only 10 shares held.');
    component.dismissConfirmation();component.form.controls.quantity.setValue(10);component.prepare();
    api.placeOrder.and.returnValue(of({orderId:'retry',status:'PENDING'}));component.confirm();
    expect(api.placeOrder.calls.mostRecent().args[0].quantity).toBe(10);expect(component.result()?.orderId).toBe('retry');
  });

  it('invalidates review when the account changes', () => {
    const fixture=setup(); fixture.componentInstance.prepare(); currentAccountId.set('ACC002'); fixture.detectChanges();
    fixture.componentInstance.confirm(); expect(api.placeOrder).not.toHaveBeenCalled();
  });
});
