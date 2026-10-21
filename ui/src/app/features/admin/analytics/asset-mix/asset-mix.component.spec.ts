import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnalyticsResponse, AssetMixEntry } from '../../../../generated/trading-api-client';
import { AnalyticsFilters } from '../analytics';
import { AnalyticsStore, LoadState } from '../analytics.store';
import { AssetMixComponent } from './asset-mix.component';

// In the API's order (the Java enum's), which isn't the legend's
const MIX: AssetMixEntry[] = [
  { assetClass: 'EQUITY', tradedValue: 5000000, share: 0.66 },
  { assetClass: 'BOND', tradedValue: 450000, share: 0.06 },
  { assetClass: 'COMMODITY', tradedValue: 450000, share: 0.06 },
  { assetClass: 'ETF', tradedValue: 1700000, share: 0.22 },
];

function withMix(assetMix: AssetMixEntry[]): AdminAnalyticsResponse {
  return {
    period: 'LAST_30_DAYS',
    bucketSize: 'DAY',
    from: '2026-09-21T04:00:00Z',
    to: '2026-10-21T04:00:00Z',
    totals: { tradedValue: 0, orders: 0, activeTraders: 0 },
    buckets: [],
    previousBuckets: [],
    topTradedInstruments: [],
    assetMix,
  };
}

const FILTERS: AnalyticsFilters = {
  period: 'LAST_30_DAYS',
  assetClass: null,
  side: null,
  orderType: null,
  trader: null,
};

describe('AssetMixComponent', () => {
  let fixture: ComponentFixture<AssetMixComponent>;
  let state: ReturnType<typeof signal<LoadState>>;
  let filters: ReturnType<typeof signal<AnalyticsFilters>>;

  function create(response: AdminAnalyticsResponse | null, initial = FILTERS): HTMLElement {
    state = signal<LoadState>(response ? { status: 'ready', data: response } : { status: 'loading' });
    filters = signal(initial);
    TestBed.configureTestingModule({
      imports: [AssetMixComponent],
      providers: [
        {
          provide: AnalyticsStore,
          useValue: {
            data: signal(response),
            state,
            filters,
            loading: computed(() => state().status === 'loading'),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(AssetMixComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  // Each row's name, share and value cells, joined by a space
  function legend(page: HTMLElement): string[] {
    return Array.from(page.querySelectorAll('.legend li')).map((li) =>
      Array.from(li.children)
        .map((cell) => cell.textContent?.trim())
        .join(' '),
    );
  }

  it('lists every class in the legend order, with its share and value', () => {
    const page = create(withMix(MIX));

    expect(legend(page)).toEqual(['Equity 66% $5M', 'ETF 22% $1.7M', 'Bond 6% $450K', 'Commodity 6% $450K']);
  });

  it('draws one segment per class with a share, sized by it and in its colour', () => {
    const page = create(withMix(MIX));
    const segments = Array.from(page.querySelectorAll<HTMLElement>('.segment'));

    expect(segments).toHaveSize(4);
    expect(segments[0].style.flexGrow).toBe('0.66');
    expect(segments[0].style.background).toContain('var(--asset-equity-color)');
    expect(page.querySelector('.compact .visually-hidden')?.textContent?.trim()).toBe(
      'Equity 66%, ETF 22%, Bond 6%, Commodity 6%',
    );
  });

  it('dims the classes the asset class filter leaves out, keeping them in place', () => {
    const page = create(withMix(MIX), { ...FILTERS, assetClass: 'BOND' });
    const dimmed = Array.from(page.querySelectorAll('.legend li')).map((li) => li.classList.contains('dimmed'));

    expect(dimmed).toEqual([true, true, false, true]);
  });

  it('shows an empty bar and says so when nothing was traded', () => {
    const page = create(withMix(MIX.map((entry) => ({ ...entry, tradedValue: 0, share: 0 }))));

    expect(page.querySelector('.segment')).toBeNull();
    expect(page.querySelector('.empty-bar')).not.toBeNull();
    expect(page.textContent).toContain('Nothing was traded in this period with these filters.');
    expect(legend(page)[0]).toBe('Equity 0% $0');
  });

  describe('bar chart, for wider screens', () => {
    function columns(page: HTMLElement): HTMLElement[] {
      return Array.from(page.querySelectorAll<HTMLElement>('.columns li'));
    }

    it('has a column per class in the legend order, with share and value above and name below', () => {
      const page = create(withMix(MIX));

      expect(columns(page).map((li) => li.querySelector('.column-label')?.textContent?.trim())).toEqual([
        'Equity',
        'ETF',
        'Bond',
        'Commodity',
      ]);
      expect(columns(page)[0].querySelector('.column-value')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('66%$5M');
    });

    it('scales the columns to the biggest share, in each class colour', () => {
      const page = create(withMix(MIX));
      const bars = columns(page).map((li) => li.querySelector('.column-bar') as HTMLElement);

      expect(bars.map((bar) => bar.style.height)).toEqual(['100%', '33.3%', '9.1%', '9.1%']);
      expect(bars[1].style.background).toContain('var(--asset-etf-color)');
    });

    it('dims the columns the asset class filter leaves out', () => {
      const page = create(withMix(MIX), { ...FILTERS, assetClass: 'ETF' });

      expect(columns(page).map((li) => li.classList.contains('dimmed'))).toEqual([true, false, true, true]);
    });

    it('draws flat columns when nothing was traded', () => {
      const page = create(withMix(MIX.map((entry) => ({ ...entry, tradedValue: 0, share: 0 }))));

      expect(columns(page).map((li) => (li.querySelector('.column-bar') as HTMLElement).style.height)).toEqual([
        '0%',
        '0%',
        '0%',
        '0%',
      ]);
    });
  });

  it('shows loading until the first response, and an error if it fails', () => {
    const page = create(null);
    expect(page.textContent).toContain('Loading asset mix');

    state.set({ status: 'error' });
    fixture.detectChanges();
    expect(page.textContent).toContain("Couldn't load the asset mix.");
  });
});
