import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MarketOverviewComponent } from './market-overview.component';

describe('MarketOverviewComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  function setup(
    assets = [
      { symbol: 'SPY', changePercent: 0.1, history: [1, 2] },
      { symbol: 'QQQ', changePercent: -0.2, history: [3, 2] },
      { symbol: 'GLD', changePercent: 0.3, history: [1, 3] },
    ],
  ) {
    const fixture = TestBed.createComponent(MarketOverviewComponent);
    fixture.componentRef.setInput('assets', assets);
    fixture.detectChanges();
    return fixture;
  }

  it('sorts top gainers and losers by session change, and shows every asset under All', () => {
    const component = setup().componentInstance;
    expect(component.visible().map((a) => a.symbol)).toEqual(['GLD', 'SPY']);
    component.view.set('losers');
    expect(component.visible().map((a) => a.symbol)).toEqual(['QQQ']);
    component.view.set('all');
    expect(component.visible().length).toBe(3);
  });

  it('limits gainers to the top five', () => {
    const fixture = setup(
      Array.from({ length: 7 }, (_, i) => ({ symbol: `ETF${i}`, changePercent: 7 - i, history: [] })),
    );
    expect(fixture.componentInstance.visible().map((a) => a.symbol)).toEqual(['ETF0', 'ETF1', 'ETF2', 'ETF3', 'ETF4']);
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(5);
  });

  it('renders flat and single-point sparklines without invalid coordinates', () => {
    const component = setup().componentInstance;
    expect(component.sparkline([5, 5])).toBe('0,20 160,20');
    expect(component.sparkline([5])).toBe('80,20');
  });
});
