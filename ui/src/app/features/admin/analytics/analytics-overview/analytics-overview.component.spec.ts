import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnalyticsResponse } from '../../../../generated/trading-api-client';
import { MetricKey } from '../analytics';
import { AnalyticsStore, LoadState } from '../analytics.store';
import { AnalyticsOverviewComponent } from './analytics-overview.component';

const RESPONSE: AdminAnalyticsResponse = {
  period: 'LAST_7_DAYS',
  bucketSize: 'DAY',
  from: '2026-10-14T04:00:00Z',
  to: '2026-10-21T04:00:00Z',
  totals: { tradedValue: 1000, orders: 10, fillRate: 0.8, activeTraders: 3 },
  buckets: [
    {
      start: '2026-10-19T04:00:00Z',
      future: false,
      totals: { tradedValue: 400, orders: 4, activeTraders: 2 },
      byAssetClass: {},
      bySide: {},
    },
    {
      start: '2026-10-20T04:00:00Z',
      future: false,
      totals: { tradedValue: 600, orders: 6, activeTraders: 2 },
      byAssetClass: {},
      bySide: {},
    },
  ],
  previousBuckets: [],
  topTradedInstruments: [],
  assetMix: [],
};

describe('AnalyticsOverviewComponent', () => {
  let fixture: ComponentFixture<AnalyticsOverviewComponent>;
  let store: ReturnType<typeof fakeStore>;

  function fakeStore() {
    const state = signal<LoadState>({ status: 'loading' });
    const metric = signal<MetricKey>('tradedValue');
    return {
      state,
      data: signal<AdminAnalyticsResponse | null>(null),
      loading: computed(() => state().status === 'loading'),
      metric,
      metricLabel: computed(() => (metric() === 'orders' ? 'Orders placed' : 'Traded value')),
      retry: jasmine.createSpy('retry'),
    };
  }

  function create(): HTMLElement {
    store = fakeStore();
    TestBed.configureTestingModule({
      imports: [AnalyticsOverviewComponent],
      providers: [{ provide: AnalyticsStore, useValue: store }],
    });
    fixture = TestBed.createComponent(AnalyticsOverviewComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function ready(): void {
    store.data.set(RESPONSE);
    store.state.set({ status: 'ready', data: RESPONSE });
    fixture.detectChanges();
  }

  it('says it is loading until the first response arrives', () => {
    const page = create();

    expect(page.textContent).toContain('Loading trading activity');
    expect(page.getAttribute('aria-busy')).toBe('true');
  });

  it('shows the tabs and the chart they control once data arrives', () => {
    const page = create();
    ready();

    expect(page.querySelectorAll('[role="tab"]')).toHaveSize(4);
    const panel = page.querySelector('[role="tabpanel"]');
    expect(panel?.id).toBe('analytics-chart');
    expect(panel?.getAttribute('aria-label')).toBe('Traded value');
    expect(page.querySelector('[role="tab"]')?.getAttribute('aria-controls')).toBe('analytics-chart');
    expect(page.getAttribute('aria-busy')).toBe('false');
  });

  it('charts the metric picked in the tabs', () => {
    const page = create();
    ready();

    page.querySelectorAll<HTMLButtonElement>('[role="tab"]')[1].click();
    fixture.detectChanges();

    expect(store.metric()).toBe('orders');
    expect(page.querySelector('[role="tabpanel"]')?.getAttribute('aria-label')).toBe('Orders placed');
  });

  it('dims the old figures while new ones load', () => {
    const page = create();
    ready();

    store.state.set({ status: 'loading' });
    fixture.detectChanges();

    expect(page.querySelector('.body')?.classList).toContain('stale');
    expect(page.querySelectorAll('[role="tab"]')).toHaveSize(4);
  });

  it('shows an error with a way to try again', () => {
    const page = create();
    store.state.set({ status: 'error' });
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load the trading activity.");

    (page.querySelector('.retry') as HTMLButtonElement).click();

    expect(store.retry).toHaveBeenCalled();
  });
});
