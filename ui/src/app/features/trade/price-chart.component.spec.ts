import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { PriceControllerService, PriceHistoryPoint } from '../../generated/trading-api-client';
import { PriceChartComponent } from './price-chart.component';

describe('PriceChartComponent', () => {
  let api: jasmine.SpyObj<{ getPriceHistory: (symbol: string) => Observable<PriceHistoryPoint[]> }>;
  const points = [
    { date: '2025-05-01', close: 80 },
    { date: '2026-01-31', close: 90 },
    { date: '2026-02-28', close: 95 },
    { date: '2026-03-01', close: 98 },
    { date: '2026-03-31', close: 100 },
  ];
  function setup(symbol = 'AAPL') {
    api = jasmine.createSpyObj('prices', ['getPriceHistory']);
    api.getPriceHistory.and.returnValue(of(points));
    TestBed.configureTestingModule({ providers: [{ provide: PriceControllerService, useValue: api }] });
    const fixture = TestBed.createComponent(PriceChartComponent);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    return fixture;
  }
  // Moves the chart's range input the way a keyboard or touch user would
  function inspect(fixture: ComponentFixture<PriceChartComponent>, index: number) {
    const range: HTMLInputElement = fixture.nativeElement.querySelector('.plot input[type="range"]');
    range.value = String(index);
    range.dispatchEvent(new Event('input'));
  }
  it('shows the latest close, actual date, daily change, and calendar-month ranges', fakeAsync(() => {
    const fixture = setup();
    tick(250);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(api.getPriceHistory).toHaveBeenCalledOnceWith('AAPL');
    expect(fixture.nativeElement.textContent).toContain('$100.00');
    expect(fixture.nativeElement.textContent).toContain('Mar 31, 2026');
    expect(component.dailyChange()).toBe(2);
    expect(component.visible().map((p) => p.date)).toEqual(['2026-02-28', '2026-03-01', '2026-03-31']);
    component.chooseRange(3);
    expect(component.visible().length).toBe(4);
    component.chooseRange(12);
    expect(component.visible().length).toBe(5);
    expect(api.getPriceHistory).toHaveBeenCalledTimes(1);
  }));
  it('reserves the quote and chart layout while price history is pending', fakeAsync(() => {
    const fixture = setup(),
      pending = new Subject<PriceHistoryPoint[]>();
    api.getPriceHistory.and.returnValue(pending);
    tick(250);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const quote = root.querySelector('.quote');
    expect(root.querySelector('.chart-loading')).not.toBeNull();
    expect(root.querySelector('.history .plot svg')).not.toBeNull();
    expect(root.querySelector('.quote-facts')).not.toBeNull();
    expect(root.querySelector('svg[role="img"]')).toBeNull();
    pending.next(points);
    pending.complete();
    fixture.detectChanges();
    expect(root.querySelector('.quote')).toBe(quote);
    expect(root.querySelector('.chart-loading')).toBeNull();
    expect(root.querySelector('svg[role="img"]')).not.toBeNull();
  }));
  it('cancels an old symbol request and never labels its data as the new instrument', fakeAsync(() => {
    const fixture = setup();
    const first = new Subject<PriceHistoryPoint[]>();
    api.getPriceHistory.and.returnValue(first);
    tick(250);
    fixture.componentRef.setInput('symbol', 'MSFT');
    fixture.detectChanges();
    expect(fixture.componentInstance.points()).toEqual([]);
    first.next(points);
    first.complete();
    api.getPriceHistory.and.returnValue(of([{ date: '2026-03-31', close: 200 }]));
    tick(250);
    fixture.detectChanges();
    expect(fixture.componentInstance.latest()?.close).toBe(200);
    expect(fixture.nativeElement.textContent).toContain('MSFT');
    expect(fixture.nativeElement.textContent).not.toContain('$100.00');
  }));
  it('can retry a failed request', fakeAsync(() => {
    const fixture = setup();
    api.getPriceHistory.and.returnValue(throwError(() => new Error('offline')));
    tick(250);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load AAPL");
    api.getPriceHistory.and.returnValue(of(points));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    tick(250);
    fixture.detectChanges();
    expect(fixture.componentInstance.state().error).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('$100.00');
  }));
  it('handles flat prices and keyboard inspection without invalid coordinates', fakeAsync(() => {
    const fixture = setup();
    api.getPriceHistory.and.returnValue(
      of([
        { date: '2026-03-30', close: 5 },
        { date: '2026-03-31', close: 5 },
      ]),
    );
    tick(250);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.line()).not.toMatch(/NaN|Infinity/);
    expect(component.y(5)).toBe(104);
    inspect(fixture, 0);
    expect(component.selected()?.date).toBe('2026-03-30');
    inspect(fixture, 1);
    expect(component.selected()?.date).toBe('2026-03-31');
  }));
  it('keeps the axis badge aligned with the price being inspected', fakeAsync(() => {
    const fixture = setup();
    api.getPriceHistory.and.returnValue(
      of([
        { date: '2026-03-30', close: 20 },
        { date: '2026-03-31', close: 10 },
      ]),
    );
    tick(250);
    fixture.detectChanges();
    const badge: HTMLElement = fixture.nativeElement.querySelector('.current-price');
    expect(badge.textContent).toContain('10.00');
    expect(parseFloat(badge.style.top)).toBeGreaterThan(75);
    inspect(fixture, 0);
    fixture.detectChanges();
    expect(badge.textContent).toContain('20.00');
    expect(parseFloat(badge.style.top)).toBeLessThan(25);
  }));
  it('shows empty history instead of inventing a chart', fakeAsync(() => {
    const fixture = setup();
    api.getPriceHistory.and.returnValue(of([]));
    tick(250);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No stored price history');
    expect(fixture.nativeElement.querySelector('svg[role="img"]')).toBeNull();
  }));
  it('keeps the loading layout while the default instrument is being resolved', fakeAsync(() => {
    const fixture = setup('');
    fixture.componentRef.setInput('pendingSymbol', true);
    fixture.detectChanges();
    tick(500);
    fixture.detectChanges();
    expect(api.getPriceHistory).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.chart-loading')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Choose an instrument to see');
    fixture.componentRef.setInput('pendingSymbol', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Choose an instrument to see');
  }));
  it('waits for an instrument before requesting history', fakeAsync(() => {
    const fixture = setup('');
    tick(500);
    fixture.detectChanges();
    expect(api.getPriceHistory).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Choose an instrument');
  }));
});
