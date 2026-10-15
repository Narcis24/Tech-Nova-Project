import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import { AccountControllerService, InstrumentControllerService, PortfolioControllerService, PortfolioMetricsResponse, OrderResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';

interface DashboardState {
  loading: boolean;
  portfolio: PortfolioMetricsResponse | null;
  orders: OrderResponse[];
  ordersError: boolean;
  error: boolean;
}

/** Local to the dashboard. Account changes cancel requests and clear the previous account's data. */
@Injectable()
export class DashboardStore {
  private readonly accounts = inject(AccountService);
  private readonly portfolioApi = inject(PortfolioControllerService);
  private readonly accountApi = inject(AccountControllerService);
  private readonly instrumentsApi = inject(InstrumentControllerService);
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ accountId: this.accounts.currentAccountId(), revision: this.revision() }));
  readonly state = signal<DashboardState>({ loading: false, portfolio: null, orders: [], ordersError: false, error: false });
  readonly instruments = signal(new Map<string, { name?: string; assetClass?: string }>());
  readonly holdings = computed(() => [...(this.state().portfolio?.positions ?? [])].sort((a, b) => Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0)));
  readonly cost = computed(() => this.holdings().reduce((sum, p) => sum + Number(p.averageCost ?? 0) * Number(p.quantity ?? 0), 0));
  readonly gain = computed(() => Number(this.state().portfolio?.totalUnrealizedPnL ?? 0));
  readonly returnOnCost = computed(() => this.cost() > 0 ? this.gain() / this.cost() : null);

  constructor() {
    this.instrumentsApi.getInstruments().pipe(catchError(() => of([])), takeUntilDestroyed()).subscribe(instruments => {
      this.instruments.set(new Map(instruments.filter(i => i.symbol).map(i => [i.symbol!, i])));
    });
    toObservable(this.request).pipe(
      switchMap(({ accountId }) => {
        const empty: DashboardState = { loading: false, portfolio: null, orders: [], ordersError: false, error: false };
        if (!accountId) return of(empty);
        return forkJoin({
          portfolio: this.portfolioApi.getPortfolioMetrics(accountId),
          orders: this.accountApi.getAccountOrders(accountId).pipe(
            map(orders => ({ orders, failed: false })),
            catchError(() => of({ orders: [], failed: true })),
          ),
        }).pipe(
          map(({ portfolio, orders }) => ({ ...empty, portfolio, orders: orders.orders, ordersError: orders.failed })),
          catchError(() => of({ ...empty, error: true })),
          startWith({ ...empty, loading: true }),
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

  positionReturn(quantity?: number, averageCost?: number, gain?: number): number | null {
    const cost = Number(quantity ?? 0) * Number(averageCost ?? 0);
    return gain != null && cost > 0 ? Number(gain) / cost : null;
  }
}
