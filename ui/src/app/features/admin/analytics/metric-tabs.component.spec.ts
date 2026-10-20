import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnalyticsResponse, AnalyticsBucket, AnalyticsTotals } from '../../../generated/trading-api-client';
import { MetricKey } from './analytics';
import { MetricTabsComponent } from './metric-tabs.component';

const ZERO: AnalyticsTotals = { tradedValue: 0, orders: 0, activeTraders: 0 };

function day(start: string, totals: AnalyticsTotals, future = false): AnalyticsBucket {
  return { start, future, totals, byAssetClass: {}, bySide: {} };
}

const RESPONSE: AdminAnalyticsResponse = {
  period: 'LAST_7_DAYS',
  bucketSize: 'DAY',
  from: '2026-10-14T04:00:00Z',
  to: '2026-10-21T04:00:00Z',
  totals: { tradedValue: 1540000, orders: 182, fillRate: 0.844, activeTraders: 21 },
  previousTotals: { tradedValue: 1950000, orders: 223, fillRate: 0.83, activeTraders: 21 },
  buckets: [
    day('2026-10-16T04:00:00Z', { tradedValue: 300000, orders: 40, fillRate: 0.8, activeTraders: 15 }), // Fri
    day('2026-10-17T04:00:00Z', ZERO), // Sat: not drawn
    day('2026-10-19T04:00:00Z', { tradedValue: 350000, orders: 45, fillRate: 0.85, activeTraders: 17 }), // Mon
    day('2026-10-20T04:00:00Z', { tradedValue: 90000, orders: 12, fillRate: 0.9, activeTraders: 8 }), // Tue
  ],
  previousBuckets: [],
  topTradedInstruments: [],
  assetMix: [],
};

describe('MetricTabsComponent', () => {
  let fixture: ComponentFixture<MetricTabsComponent>;
  let chosen: MetricKey[];

  function create(data: AdminAnalyticsResponse = RESPONSE, selected: MetricKey = 'tradedValue'): HTMLElement {
    TestBed.configureTestingModule({ imports: [MetricTabsComponent] });
    fixture = TestBed.createComponent(MetricTabsComponent);
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('selected', selected);
    fixture.componentRef.setInput('panelId', 'analytics-chart');
    chosen = [];
    fixture.componentInstance.selectedChange.subscribe((key) => chosen.push(key));
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function tabs(page: HTMLElement): HTMLButtonElement[] {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
  }

  function text(el: Element | null): string {
    return el?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('shows the four figures with their labels', () => {
    const page = create();

    expect(tabs(page).map((tab) => text(tab.querySelector('.label')))).toEqual([
      'Traded value',
      'Orders placed',
      'Fill rate',
      'Active traders',
    ]);
    expect(tabs(page).map((tab) => text(tab.querySelector('.value')))).toEqual(['$1.54M', '182', '84.4%', '21']);
  });

  it('shows each change since the previous period, coloured by direction', () => {
    const page = create();
    const changes = tabs(page).map((tab) => tab.querySelector('.change'));

    expect(text(changes[0])).toContain('▼ 21%');
    expect(changes[0]?.getAttribute('data-direction')).toBe('down');
    expect(text(changes[2])).toContain('▲ 1.4 pts');
    expect(text(changes[3])).toContain('No change');
    expect(text(changes[0])).toContain('on the previous period'); // for screen readers
  });

  it('shows no changes when there is no previous period', () => {
    const page = create({ ...RESPONSE, period: 'YEAR_TO_DATE', previousTotals: undefined });

    expect(page.querySelector('.change')).toBeNull();
  });

  it('shows a dash for a fill rate with nothing finished yet', () => {
    const page = create({ ...RESPONSE, totals: { ...RESPONSE.totals, fillRate: undefined } });

    expect(text(tabs(page)[2].querySelector('.value'))).toBe('–');
    expect(tabs(page)[2].querySelector('.change')).toBeNull();
  });

  it('draws a trend line from the weekday points only', () => {
    const page = create();
    const line = tabs(page)[0].querySelector('.line')?.getAttribute('d') ?? '';

    // Fri, Mon and Tue: three points; Saturday's zero is left out
    expect(line.split(/[ML]/).filter((s) => s.trim())).toHaveSize(3);
  });

  it('marks the chosen tab and makes only it reachable with Tab', () => {
    const page = create(RESPONSE, 'orders');

    expect(tabs(page).map((tab) => tab.getAttribute('aria-selected'))).toEqual(['false', 'true', 'false', 'false']);
    expect(tabs(page).map((tab) => tab.tabIndex)).toEqual([-1, 0, -1, -1]);
    expect(tabs(page)[0].getAttribute('aria-controls')).toBe('analytics-chart');
  });

  it('emits the clicked tab, but not the one already chosen', () => {
    const page = create();

    tabs(page)[2].click();
    tabs(page)[0].click();

    expect(chosen).toEqual(['fillRate']);
  });

  it('moves with the arrow keys, wrapping round, and Home and End', () => {
    const page = create();
    document.body.appendChild(page);
    const press = (index: number, key: string) =>
      tabs(page)[index].dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));

    press(0, 'ArrowRight');
    press(0, 'ArrowLeft');
    press(0, 'End');
    press(3, 'Home');

    expect(chosen).toEqual(['orders', 'activeTraders', 'activeTraders']);
    expect(document.activeElement).toBe(tabs(page)[0]);
    page.remove();
  });
});
