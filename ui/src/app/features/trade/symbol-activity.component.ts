import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import {
  AccountControllerService,
  OrderControllerService,
  OrderResponse,
  PortfolioSnapshotResponse,
} from '../../generated/trading-api-client';
import { timeInForceLabel } from '../../core/time-in-force';

const EXPIRY_TIME = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

/**
 * Everything the account has in the selected instrument, beside its chart: the position and how it is
 * doing, open orders (which can be cancelled here) and the latest fills.
 */
@Component({
  selector: 'app-symbol-activity',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, TitleCasePipe],
  templateUrl: './symbol-activity.component.html',
  styleUrl: './symbol-activity.component.css',
})
export class SymbolActivityComponent {
  private readonly accountsApi = inject(AccountControllerService);
  private readonly ordersApi = inject(OrderControllerService);
  readonly Number = Number;
  readonly accountId = input<string | null>(null);
  readonly symbol = input('');
  readonly portfolio = input<PortfolioSnapshotResponse | null>(null);
  /** Bumped by the order ticket after it places an order, so the lists here catch up. */
  readonly revision = input(0);
  /** A pending order was cancelled here; the ticket reloads the cash and shares it frees. */
  readonly changed = output<void>();
  private readonly reloads = signal(0);
  private readonly request = computed(() => ({
    id: this.accountId(),
    revision: this.revision(),
    reloads: this.reloads(),
  }));
  readonly orders = signal<OrderResponse[] | null>(null);
  readonly loadError = signal(false);
  readonly confirmingCancel = signal<string | null>(null);
  readonly cancelling = signal(false);
  readonly cancelError = signal<string | null>(null);

  readonly position = computed(() => this.portfolio()?.positions?.find((p) => p.symbol === this.symbol()) ?? null);
  readonly lockedShares = computed(() => Number(this.portfolio()?.lockedShares?.[this.symbol()] ?? 0));
  readonly share = computed(() => {
    const value = Number(this.position()?.marketValue),
      total = Number(this.portfolio()?.totalPortfolioValue);
    return total > 0 && Number.isFinite(value) ? value / total : null;
  });
  readonly returnOnCost = computed(() => {
    const position = this.position(),
      cost = Number(position?.averageCost) * Number(position?.quantity);
    return position && cost > 0 ? Number(position.unrealizedPnL) / cost : null;
  });
  private readonly forSymbol = computed(() => (this.orders() ?? []).filter((o) => o.symbol === this.symbol()));
  readonly pending = computed(() => this.forSymbol().filter((o) => o.status === 'PENDING'));
  readonly fills = computed(() =>
    this.forSymbol()
      .filter((o) => o.status === 'FILLED')
      .slice(0, 3),
  );

  constructor() {
    toObservable(this.request)
      .pipe(
        switchMap(({ id }) =>
          id
            ? this.accountsApi.getAccountOrders(id).pipe(
                map((orders) => ({ orders, error: false })),
                catchError(() => of({ orders: null, error: true })),
              )
            : of({ orders: [], error: false }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ orders, error }) => {
        this.orders.set(orders);
        this.loadError.set(error);
      });
  }

  reload(): void {
    this.reloads.update((v) => v + 1);
  }

  goodFor(order: OrderResponse): string {
    if (order.orderType !== 'LIMIT') return 'Until filled';
    return order.expiresAt
      ? `${timeInForceLabel(order.timeInForce)} · expires ${EXPIRY_TIME.format(new Date(order.expiresAt))} ET`
      : timeInForceLabel(order.timeInForce);
  }

  askCancel(orderId: string): void {
    this.cancelError.set(null);
    this.confirmingCancel.set(orderId);
  }

  keepOrder(): void {
    this.confirmingCancel.set(null);
    this.cancelError.set(null);
  }

  cancel(orderId: string): void {
    if (this.cancelling()) return;
    this.cancelling.set(true);
    this.cancelError.set(null);
    this.ordersApi.cancelOrder(orderId).subscribe({
      next: (cancelled) => {
        this.cancelling.set(false);
        this.confirmingCancel.set(null);
        this.orders.update((orders) => (orders ?? []).map((o) => (o.orderId === orderId ? cancelled : o)));
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.cancelling.set(false);
        // A 4xx usually means it filled or expired in the meantime
        this.cancelError.set(
          error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500
            ? (error.error?.message ?? 'This order can no longer be cancelled.')
            : "Couldn't cancel the order. Try again.",
        );
        this.reload();
      },
    });
  }
}
