import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, exhaustMap, filter, forkJoin, map, merge, of, startWith, switchMap, timer } from 'rxjs';
import { AccountControllerService, DashboardControllerService, InstrumentControllerService, PortfolioControllerService, PortfolioMetricsResponse, OrderResponse, BalanceHistoryResponse, MarketAssetResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';

interface DashboardState {
  accountId: string | null;
  loading: boolean;
  portfolio: PortfolioMetricsResponse | null;
  orders: OrderResponse[];
  history: BalanceHistoryResponse | null;
  market: MarketAssetResponse[];
  ordersError: boolean;
  historyError: boolean;
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
  private readonly document = inject(DOCUMENT);
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ accountId: this.accounts.currentAccountId(), revision: this.revision() }));
  private empty(accountId: string | null): DashboardState {
    return { accountId, loading: false, portfolio: null, orders: [], history: null, market: [], ordersError: false, historyError: false, marketError: false, error: false, updatedAt: null };
  }
  readonly state = signal<DashboardState>(this.empty(null));
  readonly instruments = signal(new Map<string, { name?: string; assetClass?: string }>());
  readonly holdings = computed(() => [...(this.state().portfolio?.positions ?? [])].filter(p => Number(p.quantity) > 0).sort((a, b) => Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0)));
  readonly cost = computed(() => this.holdings().reduce((sum, p) => sum + Number(p.averageCost ?? 0) * Number(p.quantity ?? 0), 0));
  readonly gain = computed(() => Number(this.state().portfolio?.totalUnrealizedPnL ?? 0));
  readonly returnOnCost = computed(() => this.cost() > 0 ? this.gain() / this.cost() : null);

  constructor() {
    toObservable(this.request).pipe(
      switchMap(({ accountId }) => {
        if (!accountId) return of(this.empty(null));
        // One request at a time; no background polling in hidden tabs. Manual refresh restarts immediately.
        return merge(of(0), timer(30_000, 30_000).pipe(filter(() => this.document.visibilityState !== 'hidden'))).pipe(
          exhaustMap(() => {
            const previous = this.state().accountId === accountId ? this.state() : this.empty(accountId);
            const optional = <T>(request: import('rxjs').Observable<T>, fallback: T) => request.pipe(
              map(value => ({ value, failed: false })), catchError(() => of({ value: fallback, failed: true })),
            );
            return forkJoin({
              portfolio: this.portfolioApi.getPortfolioMetrics(accountId),
              orders: optional(this.accountApi.getAccountOrders(accountId), []),
              history: optional(this.dashboardApi.getBalanceHistory(accountId), null),
              market: optional(this.dashboardApi.getMarketOverview(), []),
              // The instrument list is effectively static, so fetch it once instead of on every poll.
              instruments: this.instruments().size ? of({ value: null, failed: false }) : optional(this.instrumentsApi.getInstruments(), []),
            }).pipe(
              map(({ portfolio, orders, history, market, instruments }) => {
                if (!instruments.failed && instruments.value) this.instruments.set(new Map(instruments.value.filter(i => i.symbol).map(i => [i.symbol!, i])));
                return { ...this.empty(accountId), portfolio, orders: orders.failed ? previous.orders : orders.value,
                  history: history.failed ? previous.history : history.value, market: market.failed ? previous.market : market.value,
                  ordersError: orders.failed, historyError: history.failed, marketError: market.failed, updatedAt: Date.now() };
              }),
              catchError(() => of({ ...previous, loading: false, error: true })),
              startWith({ ...previous, loading: true, error: false }),
            );
          }),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(state => this.state.set(state));
  }

  refresh(): void { this.revision.update(value => value + 1); }
  allocation(value?: number): number | null {
    const total = Number(this.state().portfolio?.totalPortfolioValue ?? 0);
    return value != null && total > 0 ? Number(value) / total : null;
  }
}
