import { signal } from '@angular/core';
import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';

import { AdminAnalyticsControllerService, AdminLiveResponse, LiveHour } from '../../../../generated/trading-api-client';
import { AnalyticsFilters } from '../analytics';
import { AnalyticsStore } from '../analytics.store';
import { LIVE_REFRESH_MS, LivePanelComponent } from './live-panel.component';

// Seven market hours (09:00 to 15:00 New York, 13:00 to 19:00 UTC in October) for a day
function day(date: string, orders: number[], futureFrom = 99): LiveHour[] {
  return orders.map((count, i) => ({
    start: `${date}T${String(13 + i).padStart(2, '0')}:00:00Z`,
    orders: count,
    future: i >= futureFrom,
  }));
}

function live(ordersToday = 18): AdminLiveResponse {
  return {
    asOf: '2026-10-20T14:50:00Z', // Tuesday, 10:50 in New York
    ordersToday,
    ordersLastHour: 6,
    openOrders: 4,
    hourly: [
      ...day('2026-10-16', [3, 5, 8, 4, 6, 9, 7]),
      ...day('2026-10-19', [2, 9, 3, 6, 8, 3, 8]),
      ...day('2026-10-20', [4, 10, 0, 0, 0, 0, 0], 2),
    ],
    topToday: [
      { symbol: 'NVDA', name: 'NVIDIA Corporation', assetClass: 'EQUITY', orders: 4 },
      { symbol: 'GLD', name: 'SPDR Gold Shares', assetClass: 'COMMODITY', orders: 2 },
    ],
  };
}

const FILTERS: AnalyticsFilters = {
  period: 'LAST_30_DAYS',
  assetClass: null,
  side: null,
  orderType: null,
  trader: null,
};

describe('LivePanelComponent', () => {
  let fixture: ComponentFixture<LivePanelComponent>;
  let filters: ReturnType<typeof signal<AnalyticsFilters>>;
  let liveApi: jasmine.Spy<(...args: unknown[]) => Observable<AdminLiveResponse>>;
  let hidden: boolean;

  function create(initial: AnalyticsFilters = FILTERS): HTMLElement {
    filters = signal(initial);
    TestBed.configureTestingModule({
      imports: [LivePanelComponent],
      providers: [
        { provide: AnalyticsStore, useValue: { filters } },
        { provide: AdminAnalyticsControllerService, useValue: { live: liveApi } },
      ],
    });
    fixture = TestBed.createComponent(LivePanelComponent);
    fixture.detectChanges();
    TestBed.flushEffects();
    tick(0);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function setHidden(value: boolean): void {
    hidden = value;
    document.dispatchEvent(new Event('visibilitychange'));
  }

  function text(page: HTMLElement): string {
    return page.textContent?.replace(/\s+/g, ' ') ?? '';
  }

  beforeEach(() => {
    hidden = false;
    spyOnProperty(document, 'hidden', 'get').and.callFake(() => hidden);
    liveApi = jasmine.createSpy('live').and.returnValue(of(live()));
  });

  it('loads straight away with the filters, but not the period', fakeAsync(() => {
    const page = create({ ...FILTERS, period: 'TODAY', assetClass: 'ETF', trader: 'alice' });

    expect(liveApi).toHaveBeenCalledOnceWith('ETF', undefined, undefined, 'alice');
    expect(text(page)).toContain('18 orders today');
    expect(text(page)).toContain('6 in the last hour');
    expect(text(page)).not.toContain('open right now');
    expect(text(page)).toContain('Updated 10:50');
    discardPeriodicTasks();
  }));

  it('refreshes every minute', fakeAsync(() => {
    create();
    liveApi.and.returnValue(of(live(19)));

    tick(LIVE_REFRESH_MS);
    fixture.detectChanges();

    expect(liveApi).toHaveBeenCalledTimes(2);
    expect(text(fixture.nativeElement)).toContain('19 orders today');
    discardPeriodicTasks();
  }));

  it('stops while the tab is hidden and refreshes as soon as it is back', fakeAsync(() => {
    create();

    setHidden(true);
    tick(LIVE_REFRESH_MS * 3);
    expect(liveApi).toHaveBeenCalledTimes(1);

    setHidden(false);
    tick(0);
    expect(liveApi).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it("doesn't start another request while one is still running", fakeAsync(() => {
    const slow = new Subject<AdminLiveResponse>();
    liveApi.and.returnValue(slow);
    create();

    tick(LIVE_REFRESH_MS * 2);

    expect(liveApi).toHaveBeenCalledTimes(1);
    discardPeriodicTasks();
  }));

  it('starts again with new filters, but not for a new period alone', fakeAsync(() => {
    create();

    filters.set({ ...FILTERS, period: 'TODAY' });
    TestBed.flushEffects();
    tick(0);
    expect(liveApi).toHaveBeenCalledTimes(1);

    filters.set({ ...FILTERS, period: 'TODAY', side: 'BUY' });
    TestBed.flushEffects();
    tick(0);
    expect(liveApi).toHaveBeenCalledTimes(2);
    expect(liveApi.calls.mostRecent().args).toEqual([undefined, 'BUY', undefined, undefined]);
    discardPeriodicTasks();
  }));

  it('keeps the last numbers when a refresh fails, says so, and tries again next minute', fakeAsync(() => {
    const page = create();
    liveApi.and.returnValue(throwError(() => new Error('500')));

    tick(LIVE_REFRESH_MS);
    fixture.detectChanges();
    expect(text(page)).toContain("Couldn't refresh");
    expect(text(page)).toContain('18 orders today');

    liveApi.and.returnValue(of(live(20)));
    tick(LIVE_REFRESH_MS);
    fixture.detectChanges();
    expect(text(page)).not.toContain("Couldn't refresh");
    expect(text(page)).toContain('20 orders today');
    discardPeriodicTasks();
  }));

  it('groups the hours into trading days, with hours still to come drawn empty', fakeAsync(() => {
    const page = create();

    const labels = Array.from(page.querySelectorAll('.day-label')).map((el) => el.textContent?.trim());
    expect(labels).toEqual(['Fri', 'Mon', 'Today']);
    expect(page.querySelectorAll('.bar')).toHaveSize(21);
    expect(page.querySelectorAll('.today .bar.future')).toHaveSize(5);
    // The busiest hour fills the height; the others are scaled to it
    expect((page.querySelectorAll('.today .bar')[1] as HTMLElement).style.height).toBe('100%');
    discardPeriodicTasks();
  }));

  it('lists the most ordered instruments today, or says there are none', fakeAsync(() => {
    const page = create();
    const rows = Array.from(page.querySelectorAll('.top li')).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(rows).toEqual(['NVDA NVDA 4', 'GLD GLD 2']);

    liveApi.and.returnValue(of({ ...live(0), topToday: [] }));
    tick(LIVE_REFRESH_MS);
    fixture.detectChanges();
    expect(text(page)).toContain('No orders yet today.');
    discardPeriodicTasks();
  }));
});
