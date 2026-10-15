import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, Observable, of, startWith, switchMap } from 'rxjs';
import {
  AccountControllerService,
  DashboardControllerService,
  InstrumentControllerService,
  PortfolioControllerService,
  PortfolioMetricsResponse,
  OrderResponse,
  MarketAssetResponse,
} from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';

interface DashboardState {
  accountId: string | null;
  loading: boolean;
  portfolio: PortfolioMetricsResponse | null;
  orders: OrderResponse[];
  market: MarketAssetResponse[];
  ordersError: boolean;
  marketError: boolean;
  error: boolean;
  updatedAt: number | null;
}

@Injectable()
export class DashboardStore {
  private readonly accounts = inject(AccountService);
  private readonly portfolioApi = inject(PortfolioControllerService);
  private readonly accountApi = inject(AccountControllerService);
  private readonly dashboardApi = inject(DashboardControllerService);
  private readonly instrumentsApi = inject(InstrumentControllerService);
  private readonly revision = signal(0);
  private readonly request = computed(() => ({
    accountId: this.accounts.currentAccountId(),
    revision: this.revision(),
  }));
  private empty(accountId: string | null): DashboardState {
    return {
      accountId,
      loading: false,
      portfolio: null,
      orders: [],
      market: [],
      ordersError: false,
      marketError: false,
      error: false,
      updatedAt: null,
    };
  }
  readonly state = signal<DashboardState>(this.empty(null));
  readonly instruments = signal(new Map<string, { name?: string }>());
  readonly holdings = computed(() =>
    [...(this.state().portfolio?.positions ?? [])]
      .filter((p) => Number(p.quantity) > 0)
      .sort((a, b) => Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0)),
  );
  readonly cost = computed(() =>
    this.holdings().reduce((sum, p) => sum + Number(p.averageCost ?? 0) * Number(p.quantity ?? 0), 0),
  );
  readonly gain = computed(() => Number(this.state().portfolio?.totalUnrealizedPnL ?? 0));
  readonly returnOnCost = computed(() => (this.cost() > 0 ? this.gain() / this.cost() : null));
  /** Holdings valued at the change since each instrument's previous close; null unless every holding has one. */
  readonly dayChange = computed(() => {
    const market = new Map(this.state().market.map((asset) => [asset.symbol, asset]));
    let change = 0,
      previousValue = 0;
    for (const position of this.holdings()) {
      const asset = market.get(position.symbol);
      if (asset?.change == null || asset.previousClose == null) return null;
      change += Number(position.quantity) * asset.change;
      previousValue += Number(position.quantity) * asset.previousClose;
    }
    return this.holdings().length
      ? { amount: change, percent: previousValue > 0 ? change / previousValue : null }
      : null;
  });

  constructor() {
    // Loads on account change and on refresh(); switchMap drops a stale account's response.
    toObservable(this.request)
      .pipe(
        switchMap(({ accountId }) => (accountId ? this.load(accountId) : of(this.empty(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  private load(accountId: string): Observable<DashboardState> {
    const previous = this.state().accountId === accountId ? this.state() : this.empty(accountId);
    return forkJoin({
      portfolio: this.portfolioApi.getPortfolioMetrics(accountId),
      orders: optional(this.accountApi.getAccountOrders(accountId), []),
      market: optional(this.dashboardApi.getMarketOverview(), []),
      instruments: optional(this.instrumentsApi.getInstruments(), []),
    }).pipe(
      map(({ portfolio, orders, market, instruments }) => {
        if (!instruments.failed) this.instruments.set(new Map(instruments.value.map((i) => [i.symbol ?? '', i])));
        return {
          ...this.empty(accountId),
          portfolio,
          orders: orders.failed ? previous.orders : orders.value,
          market: market.failed ? previous.market : market.value,
          ordersError: orders.failed,
          marketError: market.failed,
          updatedAt: Date.now(),
        };
      }),
      catchError(() => of({ ...previous, loading: false, error: true })),
      startWith({ ...previous, loading: true, error: false }),
    );
  }

  refresh(): void {
    this.revision.update((value) => value + 1);
  }
}

/** Orders, market data and instruments are secondary: if one fails, keep the portfolio and flag the section. */
function optional<T>(request: Observable<T>, fallback: T): Observable<{ value: T; failed: boolean }> {
  return request.pipe(
    map((value) => ({ value, failed: false })),
    catchError(() => of({ value: fallback, failed: true })),
  );
}
