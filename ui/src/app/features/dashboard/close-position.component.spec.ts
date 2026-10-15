import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, Subject, throwError } from 'rxjs';
import { OrderControllerService, OrderResponse, PlaceOrderRequest } from '../../generated/trading-api-client';
import { ClosePositionComponent } from './close-position.component';
describe('ClosePositionComponent', () => {
  let api:jasmine.SpyObj<{placeOrder:(request:PlaceOrderRequest)=>Observable<OrderResponse>}>;
  function setup() { api=jasmine.createSpyObj('orders',['placeOrder']); api.placeOrder.and.returnValue(of({symbol:'SPY',status:'PENDING'})); TestBed.configureTestingModule({providers:[{provide:OrderControllerService,useValue:api}]}); const fixture=TestBed.createComponent(ClosePositionComponent);fixture.componentRef.setInput('accountId','ACC001');fixture.componentRef.setInput('position',{symbol:'SPY',quantity:5,currentPrice:100});fixture.detectChanges();return fixture; }
  it('requires confirmation and submits a full-quantity market sell without claiming it filled', () => {
    const component=setup().componentInstance, completed=jasmine.createSpy('completed');component.completed.subscribe(completed);
    expect(api.placeOrder).not.toHaveBeenCalled();component.confirm();expect(api.placeOrder.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({accountId:'ACC001',symbol:'SPY',quantity:5,side:'SELL',orderType:'MARKET'}));expect(completed.calls.mostRecent().args[0]).toContain('Awaiting execution');
  });
  it('keeps rejected orders visible and reuses its key after network errors', () => {
    const component=setup().componentInstance,pending=new Subject<OrderResponse>();api.placeOrder.and.returnValue(pending);component.confirm();component.confirm();expect(api.placeOrder).toHaveBeenCalledTimes(1);const key=api.placeOrder.calls.mostRecent().args[0].idempotencyKey;pending.error(new Error('offline'));api.placeOrder.and.returnValue(of({status:'REJECTED',message:'No quote'}));component.confirm();expect(component.error()).toBe('No quote');expect(api.placeOrder.calls.mostRecent().args[0].idempotencyKey).toBe(key);
  });
  it('uses a fresh key after a definitive rejection, but keeps it after an ambiguous failure', () => {
    const component=setup().componentInstance; api.placeOrder.and.returnValue(of({status:'REJECTED',message:'No quote'}));
    component.confirm(); const rejectedKey=api.placeOrder.calls.mostRecent().args[0].idempotencyKey;
    api.placeOrder.and.returnValue(throwError(()=>new HttpErrorResponse({status:503}))); component.confirm();
    const retryKey=api.placeOrder.calls.mostRecent().args[0].idempotencyKey; expect(retryKey).not.toBe(rejectedKey);
    component.confirm(); expect(api.placeOrder.calls.mostRecent().args[0].idempotencyKey).toBe(retryKey);
    api.placeOrder.and.returnValue(throwError(()=>new HttpErrorResponse({status:422}))); component.confirm();
    api.placeOrder.and.returnValue(of({status:'PENDING'})); component.confirm();
    expect(api.placeOrder.calls.mostRecent().args[0].idempotencyKey).not.toBe(retryKey);
  });
});
