import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnalyticsResponse, TopTradedInstrument } from '../../../../generated/trading-api-client';
import { AnalyticsStore, LoadState } from '../analytics.store';
import { TopTradedInstrumentsComponent } from './top-traded-instruments.component';

const INSTRUMENTS: TopTradedInstrument[] = [
  {
    symbol: 'MSFT',
    name: 'Microsoft Corporation',
    assetClass: 'EQUITY',
    orders: 51,
    traders: 14,
    tradedValue: 654000,
    buyValue: 438180,
  },
  {
    symbol: 'SPY',
    name: 'SPDR S&P 500 ETF Trust',
    assetClass: 'ETF',
    orders: 50,
    traders: 12,
    tradedValue: 327000,
    buyValue: 0,
  },
];

function withInstruments(topTradedInstruments: TopTradedInstrument[]): AdminAnalyticsResponse {
  return {
    period: 'LAST_30_DAYS',
    bucketSize: 'DAY',
    from: '2026-09-21T04:00:00Z',
    to: '2026-10-21T04:00:00Z',
    totals: { tradedValue: 0, orders: 0, activeTraders: 0 },
    buckets: [],
    previousBuckets: [],
    topTradedInstruments,
    assetMix: [],
  };
}

describe('TopTradedInstrumentsComponent', () => {
  let fixture: ComponentFixture<TopTradedInstrumentsComponent>;
  let state: ReturnType<typeof signal<LoadState>>;
  let data: ReturnType<typeof signal<AdminAnalyticsResponse | null>>;

  function create(response: AdminAnalyticsResponse | null): HTMLElement {
    state = signal<LoadState>(response ? { status: 'ready', data: response } : { status: 'loading' });
    data = signal(response);
    TestBed.configureTestingModule({
      imports: [TopTradedInstrumentsComponent],
      providers: [
        { provide: AnalyticsStore, useValue: { data, state, loading: computed(() => state().status === 'loading') } },
      ],
    });
    fixture = TestBed.createComponent(TopTradedInstrumentsComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function rows(page: HTMLElement): HTMLElement[] {
    return Array.from(page.querySelectorAll<HTMLElement>('.rows li'));
  }

  it('ranks the instruments with their value, orders and traders', () => {
    const page = create(withInstruments(INSTRUMENTS));

    expect(rows(page)).toHaveSize(2);
    expect(rows(page)[0].querySelector('.rank')?.textContent?.trim()).toBe('1');
    expect(rows(page)[0].querySelector('.symbol')?.textContent?.trim()).toBe('MSFT');
    expect(rows(page)[0].querySelector('small')?.textContent).toContain('51 orders · 14 traders');
    expect(rows(page)[0].querySelector('.value')?.textContent?.trim()).toBe('$654K');
  });

  it('colours each ticker tile by asset class', () => {
    const page = create(withInstruments(INSTRUMENTS));
    const tiles = rows(page).map((row) =>
      (row.querySelector('.tile') as HTMLElement).style.getPropertyValue('--asset-color'),
    );

    expect(tiles).toEqual(['var(--asset-equity-color)', 'var(--asset-etf-color)']);
  });

  it('splits each bar into buys and sells, scaled to the top instrument', () => {
    const page = create(withInstruments(INSTRUMENTS));
    const [first, second] = rows(page);

    expect((first.querySelector('.buy') as HTMLElement).style.width).toBe('67%');
    expect((first.querySelector('.sell') as HTMLElement).style.width).toBe('33%');
    expect(first.querySelector('.visually-hidden')?.textContent).toBe('67% buy, 33% sell');
    // Half the top instrument's value, and sells only, so no buy part at all
    expect(second.querySelector('.buy')).toBeNull();
    expect((second.querySelector('.sell') as HTMLElement).style.width).toBe('50%');
  });

  it('says so when nothing was traded', () => {
    const page = create(withInstruments([]));

    expect(page.textContent).toContain('Nothing was traded in this period with these filters.');
  });

  it('shows loading until the first response, and an error if it fails', () => {
    const page = create(null);
    expect(page.textContent).toContain('Loading instruments');

    state.set({ status: 'error' });
    fixture.detectChanges();
    expect(page.textContent).toContain("Couldn't load the instruments.");
  });

  it('dims the list while new figures load', () => {
    const page = create(withInstruments(INSTRUMENTS));

    state.set({ status: 'loading' });
    fixture.detectChanges();

    expect(page.querySelector('.rows')?.classList).toContain('stale');
  });
});
