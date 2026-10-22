import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnalyticsResponse } from '../../../../generated/trading-api-client';
import { AnalyticsFilters } from '../analytics';
import { AnalyticsStore } from '../analytics.store';
import { AnalyticsFiltersComponent } from './analytics-filters.component';

describe('AnalyticsFiltersComponent', () => {
  let fixture: ComponentFixture<AnalyticsFiltersComponent>;
  let store: ReturnType<typeof fakeStore>;

  function fakeStore() {
    return {
      filters: signal<AnalyticsFilters>({
        period: 'LAST_30_DAYS',
        assetClass: null,
        side: null,
        orderType: null,
        trader: null,
      }),
      hasFilters: signal(false),
      data: signal<AdminAnalyticsResponse | null>(null),
      traders: signal(['alice', 'bob']),
      exporting: signal(false),
      exportError: signal(false),
      setFilter: jasmine.createSpy('setFilter'),
      clearFilters: jasmine.createSpy('clearFilters'),
      exportCsv: jasmine.createSpy('exportCsv'),
    };
  }

  function create(): HTMLElement {
    store = fakeStore();
    TestBed.configureTestingModule({
      imports: [AnalyticsFiltersComponent],
      providers: [{ provide: AnalyticsStore, useValue: store }],
    });
    fixture = TestBed.createComponent(AnalyticsFiltersComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function button(page: HTMLElement, text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.replace(/\s+/g, ' ').trim() === text,
    ) as HTMLButtonElement;
  }

  it('marks the current period and sets another one when clicked', () => {
    const page = create();

    expect(button(page, '30D').getAttribute('aria-pressed')).toBe('true');
    expect(button(page, 'Today').getAttribute('aria-pressed')).toBe('false');

    button(page, 'Today').click();

    expect(store.setFilter).toHaveBeenCalledWith('period', 'TODAY');
  });

  it('shows a chip per filter and sets the filter chosen in one', () => {
    const page = create();
    const chips = Array.from(page.querySelectorAll('.chip')).map((c) => c.textContent?.replace(/\s+/g, ' ').trim());
    expect(chips).toEqual(['Asset class: All', 'Side: Buy and sell', 'Order type: Market and limit', 'Trader: All']);

    (page.querySelector('.chip') as HTMLButtonElement).click();
    fixture.detectChanges();
    button(page, 'ETF').click();

    expect(store.setFilter).toHaveBeenCalledWith('assetClass', 'ETF');
  });

  it('offers the traders from the store in the trader chip', () => {
    const page = create();
    (page.querySelectorAll('.chip')[3] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(button(page, 'alice')).toBeTruthy();
    expect(button(page, 'bob')).toBeTruthy();
  });

  it('offers "Clear filters" only while something is filtered', () => {
    const page = create();
    expect(button(page, 'Clear filters')).toBeUndefined();

    store.hasFilters.set(true);
    fixture.detectChanges();
    button(page, 'Clear filters').click();

    expect(store.clearFilters).toHaveBeenCalled();
  });

  it('exports, disabling the button while it runs and saying when it fails', () => {
    const page = create();
    button(page, 'Export CSV').click();
    expect(store.exportCsv).toHaveBeenCalled();

    store.exporting.set(true);
    fixture.detectChanges();
    expect(button(page, 'Exporting…').disabled).toBeTrue();

    store.exporting.set(false);
    store.exportError.set(true);
    fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain("Couldn't export the data.");
  });

  it('shows the date range once data has loaded', () => {
    const page = create();
    expect(page.querySelector('.range')).toBeNull();

    store.data.set({ from: '2026-09-21T04:00:00Z', to: '2026-10-21T04:00:00Z' } as AdminAnalyticsResponse);
    fixture.detectChanges();

    // Angular's en-US month names; the day depends on the browser's time zone, so it isn't checked
    expect(page.querySelector('.range')?.textContent).toContain('Sep');
    expect(page.querySelector('.range')?.textContent).toContain('Oct');
  });
});
