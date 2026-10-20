import { DatePipe, DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import {
  AdminAnalyticsControllerService,
  AdminAnalyticsResponse,
  AdminUserControllerService,
} from '../../../generated/trading-api-client';
import {
  AnalyticsFilters,
  ASSET_CLASSES,
  DEFAULT_PERIOD,
  MetricKey,
  METRICS,
  oneOf,
  ORDER_TYPES,
  PERIODS,
  SIDES,
} from './analytics';
import { ChipOption, FilterChipComponent } from './filter-chip.component';
import { MetricTabsComponent } from './metric-tabs.component';

/** Where the latest request is; only one of these at a time, so the template can't show two. */
type LoadState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: AdminAnalyticsResponse };

/** Trading activity across all accounts, on the admin home page. Data from GET /v1/admin/analytics. */
@Component({
  selector: 'app-analytics-card',
  imports: [DatePipe, FilterChipComponent, MetricTabsComponent],
  templateUrl: './analytics-card.component.html',
  styleUrls: ['../../../shared/admin-page.css', './analytics-card.component.css'],
})
export class AnalyticsCardComponent {
  private readonly api = inject(AdminAnalyticsControllerService);
  private readonly adminUsers = inject(AdminUserControllerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  readonly periods = PERIODS;
  readonly assetClasses = ASSET_CLASSES;
  readonly sides = SIDES;
  readonly orderTypes = ORDER_TYPES;

  // The URL is the only place the filters live, so reloading or sharing the link keeps the view
  private readonly query = toSignal(this.route.queryParamMap, { requireSync: true });

  readonly filters = computed<AnalyticsFilters>(() => {
    const query = this.query();
    return {
      period: oneOf(query.get('period'), PERIODS) ?? DEFAULT_PERIOD,
      assetClass: oneOf(query.get('assetClass'), ASSET_CLASSES),
      side: oneOf(query.get('side'), SIDES),
      orderType: oneOf(query.get('orderType'), ORDER_TYPES),
      trader: query.get('trader') || null,
    };
  });

  readonly hasFilters = computed(() => {
    const { assetClass, side, orderType, trader } = this.filters();
    return !!(assetClass || side || orderType || trader);
  });

  // Bumped by "Try again", so the same filters load again
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ filters: this.filters(), revision: this.revision() }));

  readonly state = signal<LoadState>({ status: 'loading' });
  // The last good response, kept while the next one loads so the card doesn't flash empty
  readonly data = signal<AdminAnalyticsResponse | null>(null);

  // Usernames for the trader filter; an empty list if they can't load, as the filter is optional
  readonly traders = toSignal(
    this.adminUsers.listUsers().pipe(
      map((users) =>
        users
          .filter((user) => user.roles.includes('TRADER'))
          .map((user) => user.username)
          .sort((a, b) => a.localeCompare(b)),
      ),
      catchError(() => of<string[]>([])),
    ),
    { initialValue: [] },
  );
  readonly traderOptions = computed<ChipOption[]>(() =>
    this.traders().map((trader) => ({ value: trader, label: trader })),
  );

  // Which figure the chart shows; picked with the metric tabs
  readonly metric = signal<MetricKey>('tradedValue');
  readonly metricLabel = computed(() => METRICS.find((m) => m.key === this.metric())?.label ?? '');

  readonly exporting = signal(false);
  readonly exportError = signal(false);

  constructor() {
    toObservable(this.request)
      .pipe(
        // A new filter cancels the request still in flight, so a slow old response can't overwrite a new one
        switchMap(({ filters }) =>
          this.api
            .analytics(
              filters.period,
              filters.assetClass ?? undefined,
              filters.side ?? undefined,
              filters.orderType ?? undefined,
              filters.trader ?? undefined,
            )
            .pipe(
              map((data): LoadState => ({ status: 'ready', data })),
              catchError(() => of<LoadState>({ status: 'error' })),
              startWith<LoadState>({ status: 'loading' }),
            ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => {
        this.state.set(state);
        if (state.status === 'ready') {
          this.data.set(state.data);
        }
      });
  }

  /**
   * Writes one filter to the URL; filters() reads it back and checks it. null removes it, and the
   * default period is left out, so plain /admin means the last 30 days.
   */
  setFilter(key: keyof AnalyticsFilters, value: string | null): void {
    const param = value === null || (key === 'period' && value === DEFAULT_PERIOD) ? null : value;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [key]: param },
      queryParamsHandling: 'merge',
      replaceUrl: true, // changing a filter isn't a new page, so Back leaves the dashboard
    });
  }

  clearFilters(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { assetClass: null, side: null, orderType: null, trader: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  retry(): void {
    this.revision.update((value) => value + 1);
  }

  exportCsv(): void {
    const filters = this.filters();
    this.exporting.set(true);
    this.exportError.set(false);
    this.api
      ._export(
        filters.period,
        filters.assetClass ?? undefined,
        filters.side ?? undefined,
        filters.orderType ?? undefined,
        filters.trader ?? undefined,
      )
      .subscribe({
        next: (csv) => {
          this.download(csv, `technova-analytics-${filters.period.toLowerCase().replaceAll('_', '-')}.csv`);
          this.exporting.set(false);
        },
        error: () => {
          this.exporting.set(false);
          this.exportError.set(true);
        },
      });
  }

  // The API needs the login token, so a plain link can't fetch the file; build it in memory instead
  private download(text: string, filename: string): void {
    const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
    const link = this.document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }
}
