import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import {
  AccountControllerService,
  OrderControllerService,
  OrderResponse,
  PortfolioSnapshotResponse,
} from '../../generated/trading-api-client';
import { SymbolActivityComponent } from './symbol-activity.component';

describe('SymbolActivityComponent', () => {
  const orders: OrderResponse[] = [
    {
      orderId: 'p1',
      symbol: 'AAPL',
      side: 'SELL',
      orderType: 'LIMIT',
      quantity: 5,
      price: 250,
      status: 'PENDING',
      timeInForce: 'GTC',
      expiresAt: '2027-01-21T21:00:00Z',
    },
    {
      orderId: 'f1',
      symbol: 'AAPL',
      side: 'BUY',
      orderType: 'MARKET',
      quantity: 10,
      price: 200,
      status: 'FILLED',
      createdOn: '2026-10-20T14:00:00Z',
    },
    { orderId: 'm1', symbol: 'MSFT', side: 'BUY', orderType: 'MARKET', quantity: 1, price: 400, status: 'PENDING' },
  ];
  const portfolio: PortfolioSnapshotResponse = {
    accountId: 'ACC001',
    totalPortfolioValue: 10000,
    lockedShares: { AAPL: 5 },
    positions: [
      { symbol: 'AAPL', quantity: 10, averageCost: 200, currentPrice: 230, marketValue: 2300, unrealizedPnL: 300 },
    ],
  };
  let accounts: jasmine.SpyObj<AccountControllerService>;
  let ordersApi: jasmine.SpyObj<OrderControllerService>;

  function render(symbol = 'AAPL') {
    accounts = jasmine.createSpyObj('accounts', ['getAccountOrders']);
    accounts.getAccountOrders.and.returnValue(of(orders) as never);
    ordersApi = jasmine.createSpyObj('orders', ['cancelOrder']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AccountControllerService, useValue: accounts },
        { provide: OrderControllerService, useValue: ordersApi },
      ],
    });
    const fixture = TestBed.createComponent(SymbolActivityComponent);
    fixture.componentRef.setInput('accountId', 'ACC001');
    fixture.componentRef.setInput('symbol', symbol);
    fixture.componentRef.setInput('portfolio', portfolio);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the position, its gain, its share of the portfolio and the shares pending sells hold', () => {
    const text = render().nativeElement.textContent;
    expect(text).toContain('Your AAPL');
    expect(text).toContain('5 in pending sells');
    expect(text).toContain('$2,300.00');
    expect(text).toContain('23.0% of your portfolio');
    expect(text).toContain('+$300.00');
    expect(text).toContain('+15.00%');
  });

  it("lists only this symbol's open orders with how long they last, and its latest fills", () => {
    const root: HTMLElement = render().nativeElement;
    const lists = root.querySelectorAll('.orders');
    expect(lists[0].querySelectorAll('li').length).toBe(1);
    expect(lists[0].textContent).toContain('Sell 5');
    expect(lists[0].textContent).toContain('Until cancelled · expires Jan 21, 4:00 PM ET');
    expect(lists[1].textContent).toContain('Bought 10');
    expect(accounts.getAccountOrders).toHaveBeenCalledOnceWith('ACC001');
  });

  it('says so when there is no position and no order in the symbol', () => {
    const text = render('TSLA').nativeElement.textContent;
    expect(text).toContain("You don't hold TSLA yet.");
    expect(text).toContain('No open orders for TSLA.');
    expect(text).toContain('No fills yet for TSLA.');
  });

  it('cancels an open order after confirming and tells the ticket to reload', () => {
    const fixture = render();
    const changed = jasmine.createSpy('changed');
    fixture.componentInstance.changed.subscribe(changed);
    ordersApi.cancelOrder.and.returnValue(of({ ...orders[0], status: 'CANCELLED' }) as never);
    const root: HTMLElement = fixture.nativeElement;
    root.querySelector<HTMLButtonElement>('.cancel .text-button')!.click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.cancel .danger')!.click();
    fixture.detectChanges();
    expect(ordersApi.cancelOrder).toHaveBeenCalledOnceWith('p1');
    expect(changed).toHaveBeenCalled();
    expect(root.textContent).toContain('No open orders for AAPL.');
  });

  it('shows why a cancel failed and reloads the orders', () => {
    const fixture = render();
    ordersApi.cancelOrder.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Order is FILLED' } })) as never,
    );
    fixture.componentInstance.cancel('p1');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Order is FILLED');
    expect(accounts.getAccountOrders).toHaveBeenCalledTimes(2);
  });
});
