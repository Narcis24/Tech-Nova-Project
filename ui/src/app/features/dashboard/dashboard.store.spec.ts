import { signal } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { AccountControllerService, DashboardControllerService, InstrumentControllerService, PortfolioControllerService, PortfolioMetricsResponse, OrderResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { DashboardStore } from './dashboard.store';

describe('DashboardStore', () => {
  const currentAccountId = signal<string | null>('ACC001');
  let portfolioApi: jasmine.SpyObj<{ getPortfolioMetrics: (id: string) => Observable<PortfolioMetricsResponse> }>;
  let accountApi: jasmine.SpyObj<{ getAccountOrders: (id: string) => Observable<OrderResponse[]> }>;
  let instrumentsApi: jasmine.SpyObj<{ getInstruments: () => Observable<{ symbol?: string }[]> }>;
  let store: DashboardStore;

  beforeEach(() => {
    currentAccountId.set('ACC001');
    portfolioApi = jasmine.createSpyObj('PortfolioControllerService', ['getPortfolioMetrics']);
    accountApi = jasmine.createSpyObj('AccountControllerService', ['getAccountOrders']);
    portfolioApi.getPortfolioMetrics.and.returnValue(of({ totalPortfolioValue: 120, totalUnrealizedPnL: 10, positions: [{ quantity: 2, averageCost: 50, marketValue: 110 }] }));
    accountApi.getAccountOrders.and.returnValue(of([]));
    instrumentsApi = jasmine.createSpyObj('InstrumentControllerService', ['getInstruments']);
    instrumentsApi.getInstruments.and.returnValue(of([]));
    TestBed.configureTestingModule({ providers: [DashboardStore,
      { provide: DashboardControllerService, useValue: { getBalanceHistory: () => of({}), getMarketOverview: () => of([]) } },
      { provide: AccountService, useValue: { currentAccountId } },
      { provide: PortfolioControllerService, useValue: portfolioApi },
      { provide: AccountControllerService, useValue: accountApi },
      { provide: InstrumentControllerService, useValue: instrumentsApi },
    ] });
    store = TestBed.inject(DashboardStore);
  });

  it('derives return on cost and allocation from portfolio values', () => {
    TestBed.flushEffects();
    expect(store.returnOnCost()).toBe(0.1);
    expect(store.allocation(60)).toBe(0.5);
    expect(store.positionReturn(0, 50, 0)).toBeNull();
  });

  it('cancels old requests and clears data when switching accounts', () => {
    const first = new Subject<PortfolioMetricsResponse>();
    const second = new Subject<PortfolioMetricsResponse>();
    portfolioApi.getPortfolioMetrics.and.returnValues(first, second);
    TestBed.flushEffects();
    currentAccountId.set('ACC002');
    TestBed.flushEffects();
    expect(store.state().loading).toBeTrue();
    expect(store.state().portfolio).toBeNull();
    first.next({ accountId: 'ACC001' }); first.complete();
    expect(store.state().portfolio).toBeNull();
    second.next({ accountId: 'ACC002' }); second.complete();
    expect(store.state().portfolio?.accountId).toBe('ACC002');
  });

  it('keeps portfolio data when the orders request fails', () => {
    accountApi.getAccountOrders.and.returnValue(throwError(() => new Error('offline')));
    TestBed.flushEffects();
    expect(store.state().portfolio).not.toBeNull();
    expect(store.state().ordersError).toBeTrue();
  });

  it('can retry a failed portfolio request', () => {
    portfolioApi.getPortfolioMetrics.and.returnValue(throwError(() => new Error('offline')));
    TestBed.flushEffects();
    expect(store.state().error).toBeTrue();
    portfolioApi.getPortfolioMetrics.and.returnValue(of({ totalPortfolioValue: 0 }));
    store.refresh(); TestBed.flushEffects();
    expect(store.state().error).toBeFalse();
    expect(store.allocation(0)).toBeNull();
  });


  it('keeps the same account visible during a refresh', () => {
    portfolioApi.getPortfolioMetrics.and.returnValue(of({ accountId: 'ACC001', totalPortfolioValue: 120 }));
    TestBed.flushEffects();
    const pending = new Subject<PortfolioMetricsResponse>();
    portfolioApi.getPortfolioMetrics.and.returnValue(pending);
    store.refresh(); TestBed.flushEffects();
    expect(store.state().loading).toBeTrue();
    expect(store.state().portfolio?.totalPortfolioValue).toBe(120);
    pending.next({ accountId: 'ACC001', totalPortfolioValue: 130 }); pending.complete();
    expect(store.state().portfolio?.totalPortfolioValue).toBe(130);
  });

  it('refreshes every 30 seconds only while visible and stops without an account', fakeAsync(() => {
    const visibility = spyOnProperty(document, 'visibilityState', 'get').and.returnValue('visible');
    TestBed.flushEffects(); expect(portfolioApi.getPortfolioMetrics).toHaveBeenCalledTimes(1);
    tick(30000); expect(portfolioApi.getPortfolioMetrics).toHaveBeenCalledTimes(2);
    visibility.and.returnValue('hidden'); tick(30000);
    expect(portfolioApi.getPortfolioMetrics).toHaveBeenCalledTimes(2);
    currentAccountId.set(null); TestBed.flushEffects(); tick(30000);
    expect(portfolioApi.getPortfolioMetrics).toHaveBeenCalledTimes(2);
  }));

  it('does not request a portfolio without an account', () => {
    currentAccountId.set(null); TestBed.flushEffects();
    expect(portfolioApi.getPortfolioMetrics).not.toHaveBeenCalled();
  });

  it('loads the static instrument list once, then keeps it across refreshes', fakeAsync(() => {
    spyOnProperty(document, 'visibilityState', 'get').and.returnValue('visible');
    instrumentsApi.getInstruments.and.returnValue(of([{ symbol: 'SPY' }]));
    TestBed.flushEffects(); tick(30000); store.refresh(); TestBed.flushEffects();
    expect(portfolioApi.getPortfolioMetrics.calls.count()).toBeGreaterThan(1);
    expect(instrumentsApi.getInstruments).toHaveBeenCalledTimes(1);
    expect(store.instruments().has('SPY')).toBeTrue();
  }));
});
