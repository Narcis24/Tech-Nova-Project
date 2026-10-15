import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MarketOverviewComponent } from './market-overview.component';
describe('MarketOverviewComponent', () => {
  beforeEach(() => {localStorage.removeItem('technova.watchlist'); TestBed.configureTestingModule({providers:[provideRouter([])]});});
  afterEach(() => localStorage.removeItem('technova.watchlist'));
  function setup() { const fixture=TestBed.createComponent(MarketOverviewComponent); fixture.componentRef.setInput('assets',[{symbol:'SPY',changePercent:0.1,history:[1,2]},{symbol:'QQQ',changePercent:-0.2,history:[3,2]},{symbol:'GLD',changePercent:0.3,history:[1,3]}]);fixture.detectChanges(); return fixture; }
  it('sorts top gainers and losers by session change', () => {
    const component=setup().componentInstance; component.view.set('gainers'); expect(component.visible().map(a=>a.symbol)).toEqual(['GLD','SPY']);
    component.view.set('losers'); expect(component.visible().map(a=>a.symbol)).toEqual(['QQQ']);
  });
  it('persists a watchlist and permits an intentionally empty one', () => {
    const fixture=setup(), component=fixture.componentInstance; for(const symbol of [...component.watchlist()]) component.toggle(symbol); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your watchlist is empty');
    expect(localStorage.getItem('technova.watchlist')).toBe('[]'); component.add('SPY'); fixture.detectChanges(); expect(component.visible().length).toBe(1);
  });
  it('renders flat and single-point sparklines without invalid coordinates', () => {
    const component=setup().componentInstance; expect(component.sparkline([5,5])).toBe('0,20 160,20'); expect(component.sparkline([5])).toBe('80,20');
  });
});
