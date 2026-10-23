import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { PriceControllerService, PriceHistoryPoint, QuoteResponse } from '../../generated/trading-api-client';
import { PriceChartComponent } from './price-chart.component';

describe('PriceChartComponent', () => {
  let api: jasmine.SpyObj<{
    getPriceHistory: (symbol: string) => Observable<PriceHistoryPoint[]>;
    getQuote: (symbol: string) => Observable<QuoteResponse | null>;
  }>;
  const points = [
    { date: '2025-05-01', close: 80 },
    { date: '2026-01-31', close: 90 },
    { date: '2026-02-28', close: 95 },
    { date: '2026-03-01', close: 98 },
    { date: '2026-03-31', close: 100 },
  ];
  function setup(symbol = 'AAPL') {
    api = jasmine.createSpyObj('prices', ['getPriceHistory', 'getQuote']);
    api.getPriceHistory.and.returnValue(of(points));
    api.getQuote.and.returnValue(of(null));
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
    // Opens on three months; one month is a click away
    expect(component.visible()).toHaveSize(4);
    component.chooseRange(1);
    expect(component.visible().map((p) => p.date)).toEqual(['2026-02-28', '2026-03-01', '2026-03-31']);
    component.chooseRange(3);
    expect(component.visible()).toHaveSize(4);
    component.chooseRange(12);
    expect(component.visible()).toHaveSize(5);
    expect(api.getPriceHistory).toHaveBeenCalledTimes(1);
  }));
  it('draws a candle per day by default, green when it closed up and red when down', fakeAsync(() => {
    const fixture = setup();
    api.getPriceHistory.and.returnValue(
      of([
        { date: '2026-03-30', open: 100, high: 110, low: 95, close: 105 },
        { date: '2026-03-31', open: 105, high: 106, low: 90, close: 92 },
      ]),
    );
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    tick(250);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const candles = Array.from(root.querySelectorAll('svg[role="img"] .candle'));
    expect(candles).toHaveSize(2);
    expect(candles.map((c) => c.classList.contains('down'))).toEqual([false, true]);
    expect(root.querySelector('svg[role="img"] .line')).toBeNull();
    // The scale runs from the lowest low to the highest high, not just the closes
    const component = fixture.componentInstance;
    expect([component.minimum(), component.maximum()]).toEqual([90, 110]);
    const [up] = component.candles();
    expect([up.wickTop, up.wickBottom]).toEqual([component.y(110), component.y(95)]);
    expect(up.bodyTop).toBe(component.y(105));
    inspect(fixture, 1);
    fixture.detectChanges();
    expect(root.querySelector('.readout')?.textContent).toContain('$92.00 · O 105.00 · H 106.00 · L 90.00');
  }));
  it('fills bid, ask and spread from the live quote, and keeps dashes without one', fakeAsync(() => {
    const fixture = setup();
    tick(250);
    fixture.detectChanges();
    const facts = () =>
      Array.from(fixture.nativeElement.querySelectorAll('.quote-facts dd')).map((d) =>
        (d as HTMLElement).textContent!.trim(),
      );
    expect(facts().slice(0, 3)).toEqual(['—', '—', '—']);
    expect(fixture.nativeElement.querySelector('.quote-note').textContent).toContain('No live bid and ask');

    api.getQuote.and.returnValue(of({ symbol: 'AAPL', bid: 99.9, ask: 100.1, asOf: '2026-10-22T19:58:00Z' }));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    tick(250);
    fixture.detectChanges();
    expect(facts().slice(0, 3)).toEqual(['99.90', '100.10', '0.20 (0.20%)']);
    expect(fixture.nativeElement.querySelector('.quote-note').textContent).toContain('Live quote from Alpaca');
  }));
  it('treats an empty side of the quote as missing', fakeAsync(() => {
    const fixture = setup();
    api.getQuote.and.returnValue(of({ symbol: 'AAPL', bid: 0, ask: 100.1, asOf: '2026-10-22T23:00:00Z' }));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    tick(250);
    const component = fixture.componentInstance;
    expect([component.bid(), component.ask(), component.spread()]).toEqual([null, 100.1, null]);
  }));
  it('draws volume bars by default and moving averages on request', fakeAsync(() => {
    const daily = Array.from({ length: 60 }, (_, i) => ({
      date: new Date(Date.UTC(2026, 0, 1 + i)).toISOString().slice(0, 10),
      open: 100 + i,
      high: 102 + i,
      low: 99 + i,
      close: 101 + i,
      volume: 1000 * (i + 1),
    }));
    const fixture = setup();
    api.getPriceHistory.and.returnValue(of(daily));
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    tick(250);
    fixture.detectChanges();
    const component = fixture.componentInstance,
      root: HTMLElement = fixture.nativeElement;
    component.chooseRange(12);
    fixture.detectChanges();
    expect(root.querySelectorAll('svg[role="img"] .volume')).toHaveSize(60);
    // The busiest day fills the volume band
    expect(component.volumeBars().at(-1)!.height).toBe(28);
    expect(root.querySelector('.ma20')?.tagName).not.toBe('polyline');

    component.toggleIndicator('ma20');
    component.toggleIndicator('volume');
    fixture.detectChanges();
    expect(root.querySelectorAll('svg[role="img"] .volume')).toHaveSize(0);
    // A 20-day average needs 20 closes, so it starts on the 20th day
    expect(component.ma20().split(' ')).toHaveSize(41);
    const lastAverage = (101 + 59 + (101 + 40)) / 2;
    expect(component.ma20().split(' ').at(-1)).toBe(`${component.x(59)},${component.y(lastAverage)}`);
    expect(root.querySelector('polyline.ma20')).not.toBeNull();
  }));
  describe('zooming and panning', () => {
    const year = Array.from({ length: 250 }, (_, i) => ({
      date: new Date(Date.UTC(2025, 9, 1 + i)).toISOString().slice(0, 10),
      open: 100,
      high: 101,
      low: 99,
      close: 100 + i / 10,
    }));
    function loadYear() {
      const fixture = setup();
      api.getPriceHistory.and.returnValue(of(year));
      fixture.componentInstance.refresh();
      fixture.detectChanges();
      tick(250);
      fixture.detectChanges();
      return fixture;
    }
    const svg = (fixture: ComponentFixture<PriceChartComponent>) =>
      fixture.nativeElement.querySelector('svg[role="img"]') as SVGElement;

    it('zooms out and in from the buttons, keeping the latest session in view, within limits', fakeAsync(() => {
      const fixture = loadYear(),
        component = fixture.componentInstance;
      component.chooseRange(1);
      const month = component.visible().length;
      component.zoom(1.5);
      expect(component.visible()).toHaveSize(Math.round(month * 1.5));
      expect(component.visible().at(-1)).toBe(year.at(-1));
      // The range buttons no longer describe what is shown
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[aria-label="Price chart range"] [aria-pressed="true"]')).toBeNull();

      for (let i = 0; i < 10; i++) component.zoom(1.5);
      expect(component.visible()).toHaveSize(250);
      expect(component.canZoomOut()).toBeFalse();
      for (let i = 0; i < 20; i++) component.zoom(1 / 1.5);
      expect(component.visible()).toHaveSize(10);
      expect(component.canZoomIn()).toBeFalse();
    }));

    it('zooms around the point under the mouse wheel', fakeAsync(() => {
      const fixture = loadYear(),
        component = fixture.componentInstance,
        chart = svg(fixture);
      spyOn(chart, 'getBoundingClientRect').and.returnValue(new DOMRect(0, 0, 1000, 300));
      component.chooseRange(12);
      const wheel = new WheelEvent('wheel', { deltaY: -100, clientX: 500, cancelable: true });
      chart.dispatchEvent(wheel);
      expect(wheel.defaultPrevented).toBeTrue();
      // 250 sessions become 200, centred where the wheel was
      expect(component.bounds()).toEqual({ start: 25, end: 224 });
    }));

    it('pans through the history by dragging with a mouse, and a range button resets the view', fakeAsync(() => {
      const fixture = loadYear(),
        component = fixture.componentInstance,
        chart = svg(fixture);
      spyOn(chart, 'getBoundingClientRect').and.returnValue(new DOMRect(0, 0, 1000, 300));
      component.chooseRange(1);
      const { start, end } = component.bounds(),
        count = end - start + 1;
      chart.dispatchEvent(new PointerEvent('pointerdown', { clientX: 400, button: 0, pointerType: 'mouse' }));
      chart.dispatchEvent(new PointerEvent('pointermove', { clientX: 900, pointerType: 'mouse' }));
      chart.dispatchEvent(new PointerEvent('pointerup', { clientX: 900, pointerType: 'mouse' }));
      // Dragging right by half the chart goes back half a window
      expect(component.bounds()).toEqual({ start: start - Math.round(count / 2), end: end - Math.round(count / 2) });
      chart.dispatchEvent(new PointerEvent('pointermove', { clientX: 100, pointerType: 'mouse' }));
      expect(component.bounds().start).toBe(start - Math.round(count / 2));

      component.chooseRange(1);
      expect(component.bounds()).toEqual({ start, end });
    }));

    it('leaves touch drags to scroll the page', fakeAsync(() => {
      const fixture = loadYear(),
        component = fixture.componentInstance,
        chart = svg(fixture);
      const before = component.bounds();
      chart.dispatchEvent(new PointerEvent('pointerdown', { clientX: 400, button: 0, pointerType: 'touch' }));
      chart.dispatchEvent(new PointerEvent('pointermove', { clientX: 900, pointerType: 'touch' }));
      expect(component.bounds()).toEqual(before);
    }));
  });
  it('hands the loaded history to the page', fakeAsync(() => {
    const fixture = setup(),
      history = jasmine.createSpy('history');
    fixture.componentInstance.historyChange.subscribe(history);
    tick(250);
    expect(history).toHaveBeenCalledWith(points);
  }));
  it('switches to the line chart and back', fakeAsync(() => {
    const fixture = setup();
    tick(250);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const styleButton = (label: string) =>
      Array.from(root.querySelectorAll<HTMLButtonElement>('[aria-label="Price chart style"] button')).find(
        (b) => b.textContent?.trim() === label,
      )!;
    expect(styleButton('Candles').getAttribute('aria-pressed')).toBe('true');
    styleButton('Line').click();
    fixture.detectChanges();
    expect(root.querySelector('svg[role="img"] .line')).not.toBeNull();
    expect(root.querySelector('svg[role="img"] .candle')).toBeNull();
    expect(root.querySelector('.as-of')?.textContent).toContain('Daily close');
    styleButton('Candles').click();
    fixture.detectChanges();
    expect(root.querySelectorAll('svg[role="img"] .candle')).toHaveSize(4);
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
