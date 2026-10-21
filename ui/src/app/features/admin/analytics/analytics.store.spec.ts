import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Params, Router } from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';

import {
  AdminAnalyticsControllerService,
  AdminAnalyticsResponse,
  AdminUserControllerService,
  AdminUserResponse,
} from '../../../generated/trading-api-client';
import { AnalyticsStore } from './analytics.store';

function response(orders: number): AdminAnalyticsResponse {
  return {
    period: 'LAST_30_DAYS',
    bucketSize: 'DAY',
    from: '2026-09-21T04:00:00Z',
    to: '2026-10-21T04:00:00Z',
    totals: { tradedValue: orders * 100, orders, activeTraders: 1 },
    buckets: [],
    previousBuckets: [],
    topTradedInstruments: [],
    assetMix: [],
  };
}

const USERS: AdminUserResponse[] = [
  { username: 'zoe', roles: ['TRADER'], createdAt: '2026-10-19T09:00:00Z' },
  { username: 'test_admin', roles: ['ADMIN'], createdAt: '2026-10-19T09:00:00Z' },
  { username: 'alice', roles: ['TRADER'], createdAt: '2026-10-19T09:00:00Z' },
];

describe('AnalyticsStore', () => {
  let query: BehaviorSubject<ParamMap>;
  let navigate: jasmine.Spy;
  let api: jasmine.SpyObj<{
    analytics: (...args: unknown[]) => Observable<AdminAnalyticsResponse>;
    _export: (...args: unknown[]) => Observable<string>;
  }>;
  let listUsers: jasmine.Spy;
  let store: AnalyticsStore;

  function create(params: Params = {}): void {
    query = new BehaviorSubject(convertToParamMap(params));
    navigate = jasmine.createSpy('navigate').and.resolveTo(true);
    TestBed.configureTestingModule({
      providers: [
        AnalyticsStore,
        { provide: ActivatedRoute, useValue: { queryParamMap: query } },
        { provide: Router, useValue: { navigate } },
        { provide: AdminAnalyticsControllerService, useValue: api },
        { provide: AdminUserControllerService, useValue: { listUsers } },
      ],
    });
    store = TestBed.inject(AnalyticsStore);
    TestBed.flushEffects();
  }

  beforeEach(() => {
    api = jasmine.createSpyObj('AdminAnalyticsControllerService', ['analytics', '_export']);
    api.analytics.and.returnValue(of(response(10)));
    listUsers = jasmine.createSpy('listUsers').and.returnValue(of(USERS));
  });

  describe('filters', () => {
    it('reads them from the URL', () => {
      create({ period: 'TODAY', assetClass: 'ETF', side: 'SELL', orderType: 'LIMIT', trader: 'alice' });

      expect(store.filters()).toEqual({
        period: 'TODAY',
        assetClass: 'ETF',
        side: 'SELL',
        orderType: 'LIMIT',
        trader: 'alice',
      });
      expect(store.hasFilters()).toBeTrue();
    });

    it('uses the last 30 days and no filters for a plain URL, and ignores values it does not know', () => {
      create({ period: 'FOREVER', assetClass: 'etf', side: 'HOLD' });

      expect(store.filters()).toEqual({
        period: 'LAST_30_DAYS',
        assetClass: null,
        side: null,
        orderType: null,
        trader: null,
      });
      expect(store.hasFilters()).toBeFalse();
    });

    it('writes a filter to the URL, keeping the others and not adding to the history', () => {
      create();

      store.setFilter('side', 'BUY');

      expect(navigate).toHaveBeenCalledWith(
        [],
        jasmine.objectContaining({
          queryParams: { side: 'BUY' },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        }),
      );
    });

    it('removes a filter set to null, and leaves the default period out of the URL', () => {
      create();

      store.setFilter('trader', null);
      store.setFilter('period', 'LAST_30_DAYS');

      expect(navigate.calls.argsFor(0)[1].queryParams).toEqual({ trader: null });
      expect(navigate.calls.argsFor(1)[1].queryParams).toEqual({ period: null });
    });

    it('clears every filter but keeps the period', () => {
      create({ period: 'TODAY', side: 'BUY' });

      store.clearFilters();

      expect(navigate.calls.mostRecent().args[1].queryParams).toEqual({
        assetClass: null,
        side: null,
        orderType: null,
        trader: null,
      });
    });
  });

  describe('loading', () => {
    it('asks for the filtered analytics, leaving out filters that are not set', () => {
      create({ period: 'TODAY', side: 'SELL', trader: 'alice' });

      expect(api.analytics).toHaveBeenCalledWith('TODAY', undefined, 'SELL', undefined, 'alice');
      expect(store.state().status).toBe('ready');
      expect(store.data()?.totals.orders).toBe(10);
    });

    it('loads again when the URL changes, keeping the old data on show meanwhile', () => {
      create();
      const next = new Subject<AdminAnalyticsResponse>();
      api.analytics.and.returnValue(next);

      query.next(convertToParamMap({ period: 'LAST_7_DAYS' }));
      TestBed.flushEffects();

      expect(api.analytics).toHaveBeenCalledWith('LAST_7_DAYS', undefined, undefined, undefined, undefined);
      expect(store.loading()).toBeTrue();
      expect(store.data()?.totals.orders).toBe(10);

      next.next(response(20));
      expect(store.loading()).toBeFalse();
      expect(store.data()?.totals.orders).toBe(20);
    });

    it('ignores a slow response that a newer filter replaced', () => {
      const slow = new Subject<AdminAnalyticsResponse>();
      api.analytics.and.returnValues(slow, of(response(30)));
      create();

      query.next(convertToParamMap({ period: 'TODAY' }));
      TestBed.flushEffects();
      slow.next(response(99));

      expect(store.data()?.totals.orders).toBe(30);
    });

    it('reports an error, and loads the same filters again on retry', () => {
      api.analytics.and.returnValue(throwError(() => new Error('500')));
      create({ period: 'TODAY' });
      expect(store.state().status).toBe('error');

      api.analytics.and.returnValue(of(response(5)));
      store.retry();
      TestBed.flushEffects();

      expect(api.analytics).toHaveBeenCalledTimes(2);
      expect(api.analytics.calls.mostRecent().args[0]).toBe('TODAY');
      expect(store.data()?.totals.orders).toBe(5);
    });
  });

  describe('traders', () => {
    it('lists traders only, alphabetically', () => {
      create();

      expect(store.traders()).toEqual(['alice', 'zoe']);
    });

    it('is empty when users cannot be loaded, without breaking anything else', () => {
      listUsers.and.returnValue(throwError(() => new Error('403')));
      create();

      expect(store.traders()).toEqual([]);
      expect(store.state().status).toBe('ready');
    });
  });

  describe('export', () => {
    it('downloads the filtered CSV, named after the period', () => {
      api._export.and.returnValue(of('start_new_york,traded_value\n'));
      spyOn(URL, 'createObjectURL').and.returnValue('blob:csv');
      spyOn(URL, 'revokeObjectURL');
      const click = spyOn(HTMLAnchorElement.prototype, 'click');
      create({ period: 'LAST_7_DAYS', assetClass: 'BOND' });

      store.exportCsv();

      expect(api._export).toHaveBeenCalledWith('LAST_7_DAYS', 'BOND', undefined, undefined, undefined);
      const link = click.calls.mostRecent().object as HTMLAnchorElement;
      expect(link.download).toBe('technova-analytics-last-7-days.csv');
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:csv');
      expect(store.exporting()).toBeFalse();
      expect(store.exportError()).toBeFalse();
    });

    it('reports a failed export', () => {
      api._export.and.returnValue(throwError(() => new Error('500')));
      create();

      store.exportCsv();

      expect(store.exporting()).toBeFalse();
      expect(store.exportError()).toBeTrue();
    });
  });
});
