import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MarketOverviewComponent } from './market-overview.component';
describe('MarketOverviewComponent', () => {
  beforeEach(() => {
    localStorage.removeItem('technova.watchlist');
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });
  afterEach(() => localStorage.removeItem('technova.watchlist'));
  function setup() {
    const fixture = TestBed.createComponent(MarketOverviewComponent);
    fixture.componentRef.setInput('assets', [
      { symbol: 'SPY', name: 'S&P 500 ETF', assetClass: 'ETF', currency: 'USD', changePercent: 0.1, history: [1, 2] },
      { symbol: 'QQQ', name: 'Nasdaq ETF', assetClass: 'ETF', currency: 'USD', changePercent: -0.2, history: [3, 2] },
      { symbol: 'GLD', name: 'Gold', assetClass: 'Commodity', currency: 'EUR', changePercent: 0.3, history: [1, 3] },
    ]);
    fixture.detectChanges();
    return fixture;
  }
  it('sorts top gainers and losers by session change', () => {
    const component = setup().componentInstance;
    component.view.set('gainers');
    expect(component.visible().map((a) => a.symbol)).toEqual(['GLD', 'SPY']);
    component.view.set('losers');
    expect(component.visible().map((a) => a.symbol)).toEqual(['QQQ']);
  });
  it('persists a watchlist and permits an intentionally empty one', () => {
    const fixture = setup(),
      component = fixture.componentInstance;
    for (const symbol of [...component.watchlist()]) component.toggle(symbol);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your watchlist is empty');
    expect(localStorage.getItem('technova.watchlist')).toBe('[]');
    component.add('SPY');
    fixture.detectChanges();
    expect(component.visible().length).toBe(1);
  });
  it('searches names and symbols without case or surrounding whitespace sensitivity', () => {
    const fixture = setup(),
      component = fixture.componentInstance;
    const search: HTMLInputElement = fixture.nativeElement.querySelector('input[type="search"]');
    search.value = '  nAsDaQ  ';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(component.visible().map((a) => a.symbol)).toEqual(['QQQ']);
    expect(fixture.nativeElement.textContent).toContain('1 asset shown');
    component.query.set('spy');
    expect(component.visible().map((a) => a.symbol)).toEqual(['SPY']);
  });
  it('combines search and filters within the selected view and clears them together', () => {
    const fixture = setup(),
      component = fixture.componentInstance;
    component.view.set('all');
    component.query.set('ETF');
    component.assetClass.set('ETF');
    component.currency.set('USD');
    expect(component.visible().map((a) => a.symbol)).toEqual(['SPY', 'QQQ']);
    component.view.set('gainers');
    expect(component.visible().map((a) => a.symbol)).toEqual(['SPY']);
    component.currency.set('EUR');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No assets match your search or filters');
    const clear = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (button) => button.textContent === 'Clear filters',
    )!;
    clear.click();
    fixture.detectChanges();
    expect(component.hasFilters()).toBeFalse();
    expect(component.view()).toBe('gainers');
    expect(component.visible().map((a) => a.symbol)).toEqual(['GLD', 'SPY']);
  });
  it('searches all matching gainers before limiting the results to five', () => {
    const fixture = setup(),
      component = fixture.componentInstance;
    fixture.componentRef.setInput(
      'assets',
      Array.from({ length: 7 }, (_, i) => ({ symbol: `ETF${i}`, changePercent: 7 - i })),
    );
    component.view.set('gainers');
    component.query.set('ETF6');
    expect(component.visible().map((a) => a.symbol)).toEqual(['ETF6']);
  });
  it('renders flat and single-point sparklines without invalid coordinates', () => {
    const component = setup().componentInstance;
    expect(component.sparkline([5, 5])).toBe('0,20 160,20');
    expect(component.sparkline([5])).toBe('80,20');
  });
});
