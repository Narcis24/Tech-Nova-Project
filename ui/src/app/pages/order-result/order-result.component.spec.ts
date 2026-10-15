import { fakeAsync, TestBed, tick, discardPeriodicTasks } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { OrderControllerService, OrderResponse } from '../../generated/trading-api-client';
import { OrderResultComponent } from './order-result.component';

describe('OrderResultComponent', () => {
  let api: jasmine.SpyObj<{ getOrder: (id: string) => Observable<OrderResponse> }>;
  const order: OrderResponse = {
    orderId: 'order-1',
    accountId: 'ACC001',
    symbol: 'AAPL',
    side: 'BUY',
    orderType: 'LIMIT',
    quantity: 2,
    price: 100,
    status: 'PENDING',
  };
  function setup(response: Observable<OrderResponse> = of(order)) {
    api = jasmine.createSpyObj('orders', ['getOrder']);
    api.getOrder.and.returnValue(response);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ orderId: 'order-1' })) } },
        { provide: OrderControllerService, useValue: api },
      ],
    });
    const fixture = TestBed.createComponent(OrderResultComponent);
    fixture.detectChanges();
    return fixture;
  }
  it('loads details from the route, refreshes active status and stops polling terminal orders', fakeAsync(() => {
    const fixture = setup();
    expect(api.getOrder).toHaveBeenCalledOnceWith('order-1');
    expect(fixture.nativeElement.textContent).toContain('2 AAPL');
    expect(fixture.nativeElement.textContent).toContain('$200.00');
    api.getOrder.and.returnValue(of({ ...order, status: 'FILLED' }));
    tick(15000);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Filled');
    const calls = api.getOrder.calls.count();
    tick(30000);
    expect(api.getOrder.calls.count()).toBe(calls);
    fixture.destroy();
    discardPeriodicTasks();
  }));
  it('shows loading, supports retry and keeps the last result when refresh fails', fakeAsync(() => {
    const pending = new Subject<OrderResponse>(),
      fixture = setup(pending);
    expect(fixture.nativeElement.textContent).toContain('Loading order details');
    pending.error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load");
    api.getOrder.and.returnValue(of(order));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('2 AAPL');
    api.getOrder.and.returnValue(throwError(() => new Error('offline')));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('last successful response');
    expect(fixture.nativeElement.textContent).toContain('2 AAPL');
    fixture.destroy();
    discardPeriodicTasks();
  }));
});
