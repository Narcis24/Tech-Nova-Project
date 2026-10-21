import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnalyticsResponse, AnalyticsBucket, AnalyticsTotals } from '../../../../generated/trading-api-client';
import { MetricKey } from '../analytics';
import { AnalyticsChartComponent, niceScale } from './analytics-chart.component';

const NONE: AnalyticsTotals = { tradedValue: 0, orders: 0, activeTraders: 0 };

function totals(tradedValue: number, orders = 1, fillRate?: number): AnalyticsTotals {
  return { tradedValue, orders, fillRate, activeTraders: 1 };
}

function bucket(start: string, all: AnalyticsTotals, equity = all, future = false): AnalyticsBucket {
  return {
    start,
    future,
    totals: all,
    byAssetClass: { EQUITY: equity, ETF: NONE, BOND: NONE, COMMODITY: NONE },
    bySide: { BUY: all, SELL: NONE },
  };
}

// Fri 16, Sat 17 (not drawn), Mon 19 and Tue 20 Oct, New York dates
const RESPONSE: AdminAnalyticsResponse = {
  period: 'LAST_7_DAYS',
  bucketSize: 'DAY',
  from: '2026-10-14T04:00:00Z',
  to: '2026-10-21T04:00:00Z',
  totals: totals(600, 3, 0.8),
  previousTotals: totals(400, 2, 0.75),
  buckets: [
    bucket('2026-10-16T04:00:00Z', totals(100, 1, 0.5)),
    bucket('2026-10-17T04:00:00Z', NONE),
    bucket('2026-10-19T04:00:00Z', totals(200, 1, 1)),
    bucket('2026-10-20T04:00:00Z', totals(300, 1, 1)),
  ],
  previousBuckets: [
    bucket('2026-10-09T04:00:00Z', totals(50)),
    bucket('2026-10-10T04:00:00Z', NONE),
    bucket('2026-10-12T04:00:00Z', totals(150)),
    bucket('2026-10-13T04:00:00Z', totals(200)),
  ],
  topTradedInstruments: [],
  assetMix: [],
};

describe('AnalyticsChartComponent', () => {
  let fixture: ComponentFixture<AnalyticsChartComponent>;

  function create(data: AdminAnalyticsResponse = RESPONSE, metric: MetricKey = 'tradedValue'): HTMLElement {
    TestBed.configureTestingModule({ imports: [AnalyticsChartComponent] });
    fixture = TestBed.createComponent(AnalyticsChartComponent);
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('metricKey', metric);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function points(path: Element | null): number {
    return (path?.getAttribute('d') ?? '').split(/[ML]/).filter((part) => part.trim()).length;
  }

  function legend(page: HTMLElement): string[] {
    return Array.from(page.querySelectorAll('.legend li')).map((li) => li.textContent?.trim() ?? '');
  }

  function clickSplit(page: HTMLElement, label: string): void {
    const button = Array.from(page.querySelectorAll<HTMLButtonElement>('.split button')).find(
      (b) => b.textContent?.trim() === label,
    );
    button?.click();
    fixture.detectChanges();
  }

  it('draws the total as one line over the weekdays, with the previous period dashed', () => {
    const page = create();

    expect(page.querySelectorAll('.line')).toHaveSize(1);
    expect(points(page.querySelector('.line'))).toBe(3); // Fri, Mon, Tue; Saturday left out
    expect(points(page.querySelector('.previous'))).toBe(3);
    expect(legend(page)).toEqual(['Traded value', 'Previous period']);
  });

  it('fills under the line with its colour, fading out at the zero line', () => {
    const page = create();
    const area = page.querySelector('.area') as SVGPathElement;
    const stops = Array.from(page.querySelectorAll<SVGStopElement>('linearGradient stop'));

    // One shape over Fri, Mon and Tue, closed along the zero line
    expect(area.getAttribute('d')?.match(/[ML]/g)).toHaveSize(5);
    expect(area.getAttribute('d')).toMatch(/Z$/);
    expect(area.getAttribute('fill')).toBe(`url(#${page.querySelector('linearGradient')?.id})`);
    expect(stops.map((stop) => stop.getAttribute('stop-opacity'))).toEqual(['0.28', '0']);
    expect(stops[0].style.stopColor).toBe('var(--metric-traded-value-color)');
  });

  it('splits by asset class, leaving out classes with nothing in them', () => {
    const page = create();
    clickSplit(page, 'Asset class');

    expect(legend(page)).toEqual(['Equity']);
    expect(page.querySelector('.previous')).toBeNull(); // the dashed line is for the total only
  });

  it('splits by side, in the buy and sell colours', () => {
    const page = create();
    clickSplit(page, 'Side');

    expect(legend(page)).toEqual(['Buy']);
    expect((page.querySelector('.line') as SVGPathElement).style.stroke).toBe('var(--primary-color)');
  });

  it('has no dashed line when there is no previous period', () => {
    const page = create({ ...RESPONSE, period: 'YEAR_TO_DATE', previousTotals: undefined, previousBuckets: [] });

    expect(page.querySelector('.previous')).toBeNull();
    expect(legend(page)).toEqual(['Traded value']);
  });

  it('scales a rate from 0 to 100%', () => {
    const page = create(RESPONSE, 'fillRate');
    const labels = Array.from(page.querySelectorAll('text.axis'))
      .map((t) => t.textContent?.trim())
      .filter((t) => t?.endsWith('%'));

    expect(labels).toEqual(['0.0%', '25.0%', '50.0%', '75.0%', '100.0%']);
  });

  it("doesn't draw hours that haven't happened yet", () => {
    const hours = Array.from({ length: 24 }, (_, h) =>
      bucket(new Date(Date.UTC(2026, 9, 20, 4 + h)).toISOString(), totals(10), totals(10), h > 14),
    );
    const page = create({ ...RESPONSE, period: 'TODAY', bucketSize: 'HOUR', buckets: hours, previousBuckets: [] });

    // Market hours 09:00 to 15:00 are drawn; 15:00 hasn't happened yet
    expect(points(page.querySelector('.line'))).toBe(6);
  });

  it('says so when no orders match', () => {
    const page = create({ ...RESPONSE, buckets: RESPONSE.buckets.map((b) => ({ ...b, totals: NONE })) });

    expect(page.textContent).toContain('No orders match these filters');
    expect(page.querySelector('svg')).toBeNull();
  });

  it('shows a tooltip for the point picked with the keyboard', () => {
    const page = create();
    const slider = page.querySelector('input[type="range"]') as HTMLInputElement;

    slider.value = '1';
    slider.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const tooltip = page.querySelector('.tooltip')?.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(tooltip).toContain('Mon 19 Oct');
    expect(tooltip).toContain('$200');
    expect(tooltip).toContain('Mon 12 Oct'); // the previous period's point at the same position
    expect(tooltip).toContain('$150');
  });

  function showBars(page: HTMLElement): void {
    (page.querySelector('.link') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('switches to a bar chart, one bar per drawn point, and back', () => {
    const page = create();
    showBars(page);

    expect(page.querySelectorAll('.bar')).toHaveSize(3); // Fri, Mon, Tue; Saturday left out
    expect(page.querySelector('.line')).toBeNull();
    expect(points(page.querySelector('.previous'))).toBe(3);
    expect(page.querySelector('.link')?.textContent?.trim()).toBe('Show as line chart');

    showBars(page);
    expect(page.querySelector('.bar')).toBeNull();
    expect(page.querySelectorAll('.line')).toHaveSize(1);
  });

  it('draws taller bars for bigger values, standing on the zero line', () => {
    const page = create();
    showBars(page);

    const bars = Array.from(page.querySelectorAll('.bar')).map((bar) => ({
      height: Number(bar.getAttribute('height')),
      bottom: Number(bar.getAttribute('y')) + Number(bar.getAttribute('height')),
    }));
    expect(bars[0].height).toBeLessThan(bars[1].height);
    expect(bars[1].height).toBeLessThan(bars[2].height);
    expect(new Set(bars.map((bar) => bar.bottom.toFixed(3))).size).toBe(1);
  });

  it('puts the split groups side by side in their colours', () => {
    const page = create({
      ...RESPONSE,
      buckets: RESPONSE.buckets.map((b) => ({ ...b, bySide: { BUY: b.totals, SELL: b.totals } })),
    });
    clickSplit(page, 'Side');
    showBars(page);

    const bars = Array.from(page.querySelectorAll<SVGRectElement>('.bar'));
    expect(bars).toHaveSize(6);
    expect(bars[0].style.fill).toBe('var(--primary-color)');
    expect(bars[3].style.fill).toBe('var(--sell-color)');
    // Buy's first bar ends before Sell's first bar starts
    expect(Number(bars[0].getAttribute('x')) + Number(bars[0].getAttribute('width'))).toBeLessThanOrEqual(
      Number(bars[3].getAttribute('x')),
    );
  });

  it('shades the slot picked with the keyboard and shows its tooltip', () => {
    const page = create();
    showBars(page);
    const slider = page.querySelector('input[type="range"]') as HTMLInputElement;

    slider.value = '2';
    slider.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(page.querySelector('.slot')).not.toBeNull();
    expect(page.querySelector('.tooltip')?.textContent).toContain('Tue 20 Oct');
  });
});

describe('niceScale', () => {
  it('rounds the top of the axis up to a whole number of round steps', () => {
    expect(niceScale(7588936)).toEqual({ max: 8000000, step: 2000000 });
    expect(niceScale(918)).toEqual({ max: 1000, step: 250 });
    expect(niceScale(21)).toEqual({ max: 30, step: 10 });
  });

  it('never uses fractions of an order, and has a scale with nothing to show', () => {
    expect(niceScale(2)).toEqual({ max: 2, step: 1 });
    expect(niceScale(0)).toEqual({ max: 4, step: 1 });
  });
});
