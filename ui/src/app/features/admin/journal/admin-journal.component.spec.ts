import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  AdminJournalControllerService,
  JournalDaySummary,
  JournalTimelineEntry,
} from '../../../generated/trading-api-client';
import { AdminJournalComponent } from './admin-journal.component';

const SUMMARY: JournalDaySummary = {
  day: '2026-10-14',
  engineRuns: 2,
  quotes: 780,
  orders: 2,
  fills: 1,
  rejects: 1,
  waits: 1,
  queued: 0,
  cancels: 0,
  rejectReasons: [{ reason: 'No fresh quote for TSLA', count: 1 }],
  firstTime: '2026-10-14T13:29:00Z',
  lastTime: '2026-10-14T20:59:00Z',
  orderRows: [
    {
      orderId: 'o-1',
      receivedAt: '2026-10-14T14:02:10Z',
      accountId: 'ACC001',
      symbol: 'AAPL',
      side: 'BUY',
      orderType: 'LIMIT',
      quantity: 25,
      limitPrice: 340,
      outcome: 'FILLED',
      fillPrice: 339.95,
      resends: 1,
    },
    {
      orderId: 'o-2',
      receivedAt: '2026-10-14T14:10:00Z',
      accountId: 'ACC002',
      symbol: 'TSLA',
      side: 'SELL',
      orderType: 'MARKET',
      quantity: 1,
      outcome: 'REJECTED',
      reason: 'No fresh quote for TSLA',
      resends: 0,
    },
  ],
};

const TIMELINE: JournalTimelineEntry[] = [
  {
    seq: 41,
    time: '2026-10-14T14:02:10Z',
    type: 'ORDER_RECEIVED',
    order: {
      orderId: 'o-1',
      accountId: 'ACC001',
      symbol: 'AAPL',
      side: 'BUY',
      orderType: 'LIMIT',
      quantity: 25,
      price: 340,
    },
  },
  {
    seq: 42,
    time: '2026-10-14T14:02:10Z',
    type: 'DECISION',
    decision: 'WAIT',
    quote: { symbol: 'AAPL', bid: 340.1, ask: 340.4, asOf: '2026-10-14T14:00:30Z' },
  },
  {
    seq: 44,
    time: '2026-10-14T14:04:12Z',
    type: 'DECISION',
    decision: 'FILL',
    price: 339.95,
    quote: { symbol: 'AAPL', bid: 339.7, ask: 339.95, asOf: '2026-10-14T14:04:11Z' },
  },
];

describe('AdminJournalComponent', () => {
  let api: jasmine.SpyObj<AdminJournalControllerService>;
  let fixture: ComponentFixture<AdminJournalComponent>;
  let root: HTMLElement;

  function setup(): void {
    TestBed.configureTestingModule({
      imports: [AdminJournalComponent],
      providers: [{ provide: AdminJournalControllerService, useValue: api }],
    });
    fixture = TestBed.createComponent(AdminJournalComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  }

  beforeEach(() => {
    api = jasmine.createSpyObj('AdminJournalControllerService', ['days', 'day', 'timeline', 'verify', 'download']);
    api.days.and.returnValue(
      of([
        { day: '2026-10-14', sizeBytes: 4_400_000 },
        { day: '2026-10-13', sizeBytes: 200_000 },
      ]) as never,
    );
    api.day.and.returnValue(of(SUMMARY) as never);
    api.timeline.and.returnValue(of(TIMELINE) as never);
  });

  it('opens the newest day with its totals, reject reasons and orders', () => {
    setup();

    expect(api.day).toHaveBeenCalledWith('2026-10-14');
    const select = root.querySelector<HTMLSelectElement>('#journal-day')!;
    expect(select.options.length).toBe(2);
    expect(select.options[0].textContent).toContain('4.2 MB');
    expect(root.querySelector('.tiles')?.textContent).toContain('780');
    expect(root.querySelector('.reasons')?.textContent).toContain('No fresh quote for TSLA');
    const rows = root.querySelectorAll('.orders tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Filled');
    expect(rows[0].textContent).toContain('at 339.95');
    expect(rows[0].textContent).toContain('resent 1× after restarts');
    expect(rows[1].textContent).toContain('Rejected');
    expect(rows[1].textContent).toContain('—'); // a market order has no limit
  });

  it('shows times in New York time, with UTC on hover', () => {
    setup();

    const received = root.querySelector<HTMLElement>('.orders tbody td')!;
    expect(received.textContent?.trim()).toBe('10:02:10'); // 14:02 UTC is 10:02 in New York in October
    expect(received.title).toBe('2026-10-14T14:02:10Z UTC');
  });

  it('shows an order timeline with the quote behind each decision', () => {
    setup();

    root.querySelector<HTMLElement>('.orders tbody tr')!.click();
    fixture.detectChanges();

    expect(api.timeline).toHaveBeenCalledWith('2026-10-14', 'o-1');
    const events = root.querySelectorAll('.timeline li');
    expect(events.length).toBe(3);
    expect(events[0].textContent).toContain('Order received');
    expect(events[0].textContent).toContain('limit 340.00');
    expect(events[1].textContent).toContain('Waiting for its price');
    expect(events[2].textContent).toContain('Filled');
    expect(events[2].textContent).toContain('ask 339.95');
  });

  it('reports a verify that matches', () => {
    api.verify.and.returnValue(
      of({
        day: '2026-10-14',
        engineRuns: 2,
        orders: 2,
        quotes: 780,
        decisions: 3,
        matches: true,
        differences: [],
      }) as never,
    );
    setup();

    root.querySelector<HTMLButtonElement>('.actions .primary')!.click();
    fixture.detectChanges();

    expect(api.verify).toHaveBeenCalledWith('2026-10-14');
    expect(root.querySelector('.verify.good')?.textContent).toContain('All 3 decisions match');
  });

  it('lists every decision a verify found changed', () => {
    api.verify.and.returnValue(
      of({
        day: '2026-10-14',
        engineRuns: 2,
        orders: 2,
        quotes: 780,
        decisions: 3,
        matches: false,
        differences: [{ seq: 44, orderId: 'o-1', recorded: 'o-1 FILL 339.95', replayed: 'o-1 FILL 340.00' }],
      }) as never,
    );
    setup();

    root.querySelector<HTMLButtonElement>('.actions .primary')!.click();
    fixture.detectChanges();

    const box = root.querySelector('.verify.bad')!;
    expect(box.textContent).toContain('1 decision(s) came out differently');
    expect(box.textContent).toContain('o-1 FILL 340.00');
  });

  it('downloads the day as a gzip file', () => {
    api.download.and.returnValue(of(new Blob(['x'])) as never);
    const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:journal');
    spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    setup();

    root.querySelector<HTMLButtonElement>('.actions .secondary')!.click();

    expect(api.download).toHaveBeenCalledWith('2026-10-14');
    expect(createUrl).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
  });

  it("shows the API's message when the journal is unavailable", () => {
    api.days.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { message: 'The execution engine cannot be reached; try again in a moment' },
          }),
      ) as never,
    );
    setup();

    expect(root.querySelector('.load-error')?.textContent).toContain('The execution engine cannot be reached');
  });

  it('says so when there is no journal yet', () => {
    api.days.and.returnValue(of([]) as never);
    setup();

    expect(root.querySelector('.empty')?.textContent).toContain('No journal yet');
    expect(api.day).not.toHaveBeenCalled();
  });
});
