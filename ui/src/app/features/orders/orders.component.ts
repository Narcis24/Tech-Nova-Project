import { CurrencyPipe, DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, exhaustMap, filter, interval, of, startWith, switchMap } from 'rxjs';

import {
  AccountControllerService,
  InstrumentControllerService,
  OrderControllerService,
  OrderResponse,
} from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';

type Filter = 'ALL' | 'OPEN' | 'FILLED' | 'CANCELLED' | 'REJECTED';

// Which statuses each filter tab shows
const FILTER_STATUSES: Record<Exclude<Filter, 'ALL'>, string[]> = {
  OPEN: ['PENDING', 'PUBLISHED', 'PARTIALLY_FILLED'],
  FILLED: ['FILLED'],
  CANCELLED: ['CANCELLED', 'EXPIRED'],
  REJECTED: ['REJECTED'],
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Open',
  PUBLISHED: 'Open',
  PARTIALLY_FILLED: 'Partially filled',
  FILLED: 'Filled',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  REJECTED: 'Rejected',
};

// Open orders get filled in the background, so the list refreshes this often while any are open
const REFRESH_MS = 5000;

/** The selected account's order history: totals, filters, orders grouped by day, and cancelling open orders. */
@Component({
  selector: 'app-orders',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, TitleCasePipe, RouterLink, SymbolIconComponent],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.css',
})
export class OrdersComponent {
  private readonly accountsApi = inject(AccountControllerService);
  private readonly ordersApi = inject(OrderControllerService);
  private readonly instrumentsApi = inject(InstrumentControllerService);
  readonly accountId = inject(AccountService).currentAccountId;

  readonly orders = signal<OrderResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly names = signal<Record<string, string>>({});
  readonly filter = signal<Filter>('ALL');
  readonly search = signal('');
  // The order whose Cancel was pressed and is waiting for "Yes, cancel"
  readonly confirmingCancel = signal<string | null>(null);
  readonly cancelling = signal(false);
  readonly cancelError = signal<string | null>(null);
  readonly filters: Filter[] = ['ALL', 'OPEN', 'FILLED', 'CANCELLED', 'REJECTED'];
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ id: this.accountId(), revision: this.revision() }));

  readonly counts = computed(() => {
    const counts: Record<Filter, number> = { ALL: 0, OPEN: 0, FILLED: 0, CANCELLED: 0, REJECTED: 0 };
    for (const order of this.orders()) {
      counts.ALL++;
      const match = this.filters.find((f) => f !== 'ALL' && FILTER_STATUSES[f].includes(order.status ?? ''));
      if (match) counts[match]++;
    }
    return counts;
  });

  /** Value of filled buys and sells, at the price they filled at. */
  readonly filledTotals = computed(() => {
    let bought = 0;
    let sold = 0;
    for (const order of this.orders()) {
      if (order.status !== 'FILLED') continue;
      if (order.side === 'SELL') sold += amount(order);
      else bought += amount(order);
    }
    return { bought, sold };
  });

  /** The orders that pass the filter and search, grouped by the day they were placed (newest first, as the API sends them). */
  readonly groups = computed(() => {
    const filter = this.filter();
    const search = this.search().trim().toLowerCase();
    const groups: { label: string; orders: OrderResponse[] }[] = [];
    for (const order of this.orders()) {
      if (filter !== 'ALL' && !FILTER_STATUSES[filter].includes(order.status ?? '')) continue;
      const name = this.names()[order.symbol ?? ''] ?? '';
      if (search && !`${order.symbol} ${name}`.toLowerCase().includes(search)) continue;
      const label = dayLabel(order.createdOn);
      const last = groups[groups.length - 1];
      if (last?.label === label) last.orders.push(order);
      else groups.push({ label, orders: [order] });
    }
    return groups;
  });

  constructor() {
    // Loads when the account changes or the user retries, then refreshes while any order is open
    toObservable(this.request)
      .pipe(
        switchMap(({ id }) => {
          this.loading.set(true);
          this.error.set(false);
          this.orders.set([]);
          if (!id) return of([]);
          return interval(REFRESH_MS).pipe(
            startWith(-1),
            filter((tick) => tick === -1 || (this.counts().OPEN > 0 && document.visibilityState !== 'hidden')),
            exhaustMap(() => this.accountsApi.getAccountOrders(id).pipe(catchError(() => of(null)))),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((orders) => {
        this.loading.set(false);
        if (orders) {
          this.orders.set(orders);
          this.error.set(false);
        } else {
          this.error.set(true);
        }
      });

    // Company names for the rows; without them the rows show only the symbol
    this.instrumentsApi
      .getInstruments()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (instruments) => {
          const names: Record<string, string> = {};
          for (const instrument of instruments) {
            if (instrument.symbol && instrument.name) names[instrument.symbol] = instrument.name;
          }
          this.names.set(names);
        },
        error: () => {},
      });
  }

  retry(): void {
    this.revision.update((v) => v + 1);
  }

  setSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  statusLabel(order: OrderResponse): string {
    return STATUS_LABELS[order.status ?? ''] ?? order.status ?? '';
  }

  amount(order: OrderResponse): number {
    return amount(order);
  }

  /** What the price means depends on the order: the fill price once filled, otherwise the limit or the estimate. */
  priceLabel(order: OrderResponse): string {
    if (order.status === 'FILLED' || order.status === 'PARTIALLY_FILLED') return 'Filled at';
    return order.orderType === 'LIMIT' ? 'Limit' : 'Est.';
  }

  /** When a pending limit order expires, in market time: "Expires Oct 21, 4:00 PM ET". */
  expiryLabel(order: OrderResponse): string | null {
    if (order.status !== 'PENDING' || !order.expiresAt) return null;
    return `Expires ${EXPIRY_TIME.format(new Date(order.expiresAt))} ET`;
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
        this.orders.update((orders) => orders.map((o) => (o.orderId === orderId ? cancelled : o)));
      },
      error: (error: unknown) => {
        this.cancelling.set(false);
        // A 4xx usually means it was filled or cancelled in the meantime
        this.cancelError.set(
          error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500
            ? (error.error?.message ?? 'This order can no longer be cancelled.')
            : "Couldn't cancel the order. Try again.",
        );
      },
    });
  }
}

const EXPIRY_TIME = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

function amount(order: OrderResponse): number {
  return Number(order.quantity ?? 0) * Number(order.price ?? 0);
}

function dayLabel(createdOn: string | undefined): string {
  if (!createdOn) return 'Earlier';
  const day = new Date(createdOn);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (day.toDateString() === today.toDateString()) return 'Today';
  if (day.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return day.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}
