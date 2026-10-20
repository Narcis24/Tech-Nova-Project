import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  catchError,
  distinctUntilChanged,
  EMPTY,
  exhaustMap,
  fromEvent,
  map,
  of,
  startWith,
  switchMap,
  tap,
  timer,
} from 'rxjs';

import { AdminAnalyticsControllerService, AdminLiveResponse, LiveHour } from '../../../../generated/trading-api-client';
import { ASSET_CLASSES } from '../analytics';
import { AnalyticsStore } from '../analytics.store';

/** How often the panel refreshes while the page is visible. */
export const LIVE_REFRESH_MS = 60_000;

const NY = 'America/New_York';
const DAY_KEY = new Intl.DateTimeFormat('en-CA', { timeZone: NY, year: 'numeric', month: '2-digit', day: '2-digit' });
const WEEKDAY = new Intl.DateTimeFormat('en-US', { timeZone: NY, weekday: 'short' });
const HOUR = new Intl.DateTimeFormat('en-US', { timeZone: NY, hour: 'numeric' });
const TIME = new Intl.DateTimeFormat('en-US', { timeZone: NY, hour: 'numeric', minute: '2-digit' });

/**
 * What's happening right now, YouTube Studio's "Realtime" for trading: orders today and in the last
 * hour, open orders, orders per market hour over the last three trading days, and the most ordered
 * instruments today. Uses the card's filters but not its period, refreshes every minute, and pauses
 * while the browser tab is hidden. Needs an AnalyticsStore provided above it.
 */
@Component({
  selector: 'app-live-panel',
  templateUrl: './live-panel.component.html',
  styleUrls: ['../../../../shared/admin-page.css', './live-panel.component.css'],
})
export class LivePanelComponent {
  private readonly api = inject(AdminAnalyticsControllerService);
  private readonly store = inject(AnalyticsStore);
  private readonly document = inject(DOCUMENT);

  // The card's filters without the period, compared by value: changing the period alone shouldn't
  // restart the polling, but changing a filter should
  private readonly filters = computed(
    () => {
      const { assetClass, side, orderType, trader } = this.store.filters();
      return { assetClass, side, orderType, trader };
    },
    {
      equal: (a, b) =>
        a.assetClass === b.assetClass && a.side === b.side && a.orderType === b.orderType && a.trader === b.trader,
    },
  );

  readonly live = signal<AdminLiveResponse | null>(null);
  // A failed refresh keeps the last numbers on show and says so; the next minute tries again
  readonly failed = signal(false);
  readonly refreshing = signal(false);

  constructor() {
    const visible = fromEvent(this.document, 'visibilitychange').pipe(
      startWith(null),
      map(() => !this.document.hidden),
      distinctUntilChanged(),
    );

    toObservable(this.filters)
      .pipe(
        // New filters: don't show the old filters' numbers, or their error, as if they were current
        tap(() => {
          this.live.set(null);
          this.failed.set(false);
        }),
        switchMap((filters) =>
          // Hidden tab: stop. Visible again: refresh straight away, then every minute
          visible.pipe(
            switchMap((isVisible) => (isVisible ? timer(0, LIVE_REFRESH_MS) : EMPTY)),
            // A slow request isn't stacked up behind another; the tick that finds one running is skipped
            exhaustMap(() => {
              this.refreshing.set(true);
              return this.api
                .live(
                  filters.assetClass ?? undefined,
                  filters.side ?? undefined,
                  filters.orderType ?? undefined,
                  filters.trader ?? undefined,
                )
                .pipe(catchError(() => of(null)));
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((live) => {
        this.refreshing.set(false);
        this.failed.set(live === null);
        if (live) {
          this.live.set(live);
        }
      });
  }

  readonly updatedAt = computed(() => {
    const live = this.live();
    return live ? TIME.format(new Date(live.asOf)) : null;
  });

  /** The hourly counts grouped into trading days, each bar scaled to the busiest hour shown. */
  readonly days = computed(() => {
    const hourly = this.live()?.hourly ?? [];
    const busiest = Math.max(1, ...hourly.map((hour) => hour.orders));
    const today = this.live() ? DAY_KEY.format(new Date(this.live()!.asOf)) : null;
    const days = new Map<string, LiveHour[]>();
    for (const hour of hourly) {
      const key = DAY_KEY.format(new Date(hour.start));
      days.set(key, [...(days.get(key) ?? []), hour]);
    }
    return [...days.entries()].map(([key, hours]) => ({
      key,
      label: key === today ? 'Today' : WEEKDAY.format(new Date(hours[0].start)),
      today: key === today,
      bars: hours.map((hour) => ({
        start: hour.start,
        future: hour.future,
        // At least a sliver, so an hour with no orders still shows where it is
        height: hour.future ? 100 : Math.max(4, (hour.orders / busiest) * 100),
        title: `${HOUR.format(new Date(hour.start))}: ${hour.future ? 'not yet' : `${hour.orders} orders`}`,
      })),
    }));
  });

  readonly summary = computed(() => {
    const total = (this.live()?.hourly ?? []).filter((hour) => !hour.future).reduce((sum, h) => sum + h.orders, 0);
    return `${total} orders in market hours over the last ${this.days().length} trading days`;
  });

  readonly top = computed(() =>
    (this.live()?.topToday ?? []).map((instrument) => ({
      ...instrument,
      color: ASSET_CLASSES.find((assetClass) => assetClass.value === instrument.assetClass)?.color ?? 'currentColor',
    })),
  );
}
