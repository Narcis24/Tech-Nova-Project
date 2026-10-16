import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import {
  AccountControllerService,
  InstrumentControllerService,
  OrderControllerService,
  OrderResponse,
} from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { OrdersComponent } from './orders.component';

const NOW = new Date().toISOString();
const LAST_WEEK = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();

const ORDERS: OrderResponse[] = [
  {
    orderId: 'o1',
    symbol: 'AAPL',
    side: 'BUY',
    orderType: 'MARKET',
    quantity: 10,
    price: 180.5,
    status: 'FILLED',
    createdOn: NOW,
  },
  {
    orderId: 'o2',
    symbol: 'MSFT',
    side: 'BUY',
    orderType: 'LIMIT',
    quantity: 5,
    price: 400,
    status: 'PENDING',
    createdOn: NOW,
  },
  {
    orderId: 'o3',
    symbol: 'TSLA',
    side: 'SELL',
    orderType: 'MARKET',
    quantity: 3,
    price: 200,
    status: 'REJECTED',
    message: 'Only 1 share held',
    createdOn: LAST_WEEK,
  },
  {
    orderId: 'o4',
    symbol: 'AAPL',
    side: 'SELL',
    orderType: 'MARKET',
    quantity: 2,
    price: 190,
    status: 'FILLED',
    createdOn: LAST_WEEK,
  },
];

describe('OrdersComponent', () => {
  const currentAccountId = signal<string | null>('ACC001');
  let accounts: jasmine.SpyObj<AccountControllerService>;
  let orders: jasmine.SpyObj<OrderControllerService>;
  let fixture: ComponentFixture<OrdersComponent>;
  let root: HTMLElement;

  beforeEach(() => {
    currentAccountId.set('ACC001');
    accounts = jasmine.createSpyObj('accounts', ['getAccountOrders']);
    accounts.getAccountOrders.and.returnValue(of(ORDERS) as never);
    orders = jasmine.createSpyObj('orders', ['cancelOrder']);
    TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: { currentAccountId } },
        { provide: AccountControllerService, useValue: accounts },
        { provide: OrderControllerService, useValue: orders },
        {
          provide: InstrumentControllerService,
          useValue: { getInstruments: () => of([{ symbol: 'AAPL', name: 'Apple Inc.' }]) },
        },
      ],
    });
    fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  });

  afterEach(() => fixture.destroy());

  const rows = () => Array.from(root.querySelectorAll<HTMLElement>('.order'));
  const text = () => root.textContent ?? '';

  it('shows the account orders grouped by day, with company names and what each price means', () => {
    expect(accounts.getAccountOrders).toHaveBeenCalledWith('ACC001');
    expect(root.querySelector('.day h2')?.textContent).toContain('Today');
    expect(rows().length).toBe(4);
    expect(rows()[0].textContent).toContain('Apple Inc.');
    expect(rows()[0].textContent).toContain('$1,805.00');
    expect(rows()[0].textContent).toContain('Filled at $180.50');
    expect(rows()[1].textContent).toContain('Limit $400.00');
    expect(rows()[1].querySelector('.status')?.textContent).toContain('Open');
  });

  it('shows the rejection reason under a rejected order', () => {
    expect(rows()[2].querySelector('.reason')?.textContent).toContain('Only 1 share held');
  });

  it('sums counts and filled totals in the tiles', () => {
    const tiles = root.querySelectorAll('.tile');
    expect(tiles[0].textContent).toContain('1'); // open
    expect(tiles[1].textContent).toContain('2'); // filled
    expect(tiles[3].textContent).toContain('$1,805.00'); // bought
    expect(tiles[3].textContent).toContain('$380.00'); // sold
  });

  it('filters by status and searches by symbol or company name', () => {
    root.querySelectorAll<HTMLButtonElement>('.tile')[2].click(); // Rejected
    fixture.detectChanges();
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('TSLA');

    fixture.componentInstance.filter.set('ALL');
    const search = root.querySelector<HTMLInputElement>('.search')!;
    search.value = 'apple';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(rows().length).toBe(2);

    search.value = 'zzz';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(text()).toContain('No orders match.');
  });

  it('offers Cancel only on pending orders and asks before cancelling', () => {
    expect(rows().filter((row) => row.querySelector('.cancel')).length).toBe(1);
    orders.cancelOrder.and.returnValue(of({ ...ORDERS[1], status: 'CANCELLED' }) as never);

    rows()[1].querySelector<HTMLButtonElement>('.cancel button')!.click();
    fixture.detectChanges();
    expect(orders.cancelOrder).not.toHaveBeenCalled();
    expect(rows()[1].textContent).toContain('Cancel this order?');

    rows()[1].querySelector<HTMLButtonElement>('.danger')!.click();
    fixture.detectChanges();
    expect(orders.cancelOrder).toHaveBeenCalledWith('o2');
    expect(rows()[1].querySelector('.status')?.textContent).toContain('Cancelled');
    expect(rows()[1].querySelector('.cancel')).toBeNull();
  });

  it('shows why a cancel failed', () => {
    orders.cancelOrder.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Order is no longer pending' } })),
    );
    rows()[1].querySelector<HTMLButtonElement>('.cancel button')!.click();
    fixture.detectChanges();
    rows()[1].querySelector<HTMLButtonElement>('.danger')!.click();
    fixture.detectChanges();
    expect(rows()[1].querySelector('[role=alert]')?.textContent).toContain('Order is no longer pending');
  });

  it('refreshes while an order is open', fakeAsync(() => {
    fixture.destroy();
    accounts.getAccountOrders.calls.reset();
    fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    expect(accounts.getAccountOrders).toHaveBeenCalledTimes(1);
    tick(5000);
    expect(accounts.getAccountOrders).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it('does not refresh when nothing is open', fakeAsync(() => {
    fixture.destroy();
    accounts.getAccountOrders.and.returnValue(of([ORDERS[0]]) as never);
    accounts.getAccountOrders.calls.reset();
    fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    tick(15000);
    expect(accounts.getAccountOrders).toHaveBeenCalledTimes(1);
    discardPeriodicTasks();
  }));

  it('reloads for another account', () => {
    currentAccountId.set('ACC002');
    fixture.detectChanges();
    expect(accounts.getAccountOrders).toHaveBeenCalledWith('ACC002');
  });

  it('shows an empty state with a link to trade', () => {
    accounts.getAccountOrders.and.returnValue(of([]) as never);
    fixture.componentInstance.retry();
    fixture.detectChanges();
    expect(text()).toContain('No orders yet.');
    expect(root.querySelector('.empty a')?.getAttribute('href')).toBe('/trade');
  });

  it('offers a retry when orders cannot load', () => {
    accounts.getAccountOrders.and.returnValue(throwError(() => new Error('offline')) as never);
    fixture.componentInstance.retry();
    fixture.detectChanges();
    expect(root.querySelector('[role=alert]')?.textContent).toContain("Couldn't load your orders.");

    accounts.getAccountOrders.and.returnValue(of(ORDERS) as never);
    root.querySelector<HTMLButtonElement>('.notice button')!.click();
    fixture.detectChanges();
    expect(rows().length).toBe(4);
  });
});
