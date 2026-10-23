import { TestBed } from '@angular/core/testing';
import { PriceHistoryPoint } from '../../generated/trading-api-client';
import { KeyStatsComponent } from './key-stats.component';

describe('KeyStatsComponent', () => {
  const points: PriceHistoryPoint[] = [
    { date: '2025-10-22', open: 70, high: 72, low: 60, close: 70, volume: 900 },
    { date: '2025-12-31', open: 80, high: 81, low: 79, close: 80, volume: 900 },
    { date: '2026-07-22', open: 100, high: 101, low: 99, close: 100, volume: 900 },
    { date: '2026-09-22', open: 110, high: 140, low: 109, close: 110, volume: 1000 },
    { date: '2026-10-15', open: 118, high: 121, low: 117, close: 120, volume: 2000 },
    { date: '2026-10-22', open: 123, high: 128, low: 122, close: 126, volume: 3000 },
  ];

  function render(history: PriceHistoryPoint[] = points) {
    const fixture = TestBed.createComponent(KeyStatsComponent);
    fixture.componentRef.setInput('points', history);
    fixture.detectChanges();
    return fixture;
  }

  it("shows the latest session's open, range, previous close and volume against its average", () => {
    const root: HTMLElement = render().nativeElement;
    const fact = (label: string) =>
      Array.from(root.querySelectorAll('.facts div'))
        .find((d) => d.querySelector('dt')?.textContent === label)
        ?.querySelector('dd')?.textContent;
    expect(fact('Open')).toContain('123.00');
    expect(fact("Day's range")).toContain('122.00 – 128.00');
    expect(fact('Previous close')).toContain('120.00');
    expect(fact('Volume')).toContain('3,000');
    expect(fact('Avg. volume (30d)')).toContain('1,450');
  });

  it('places the last close in the 52-week range, from the lowest low to the highest high', () => {
    const fixture = render();
    expect(fixture.componentInstance.yearLow()).toBe(60);
    expect(fixture.componentInstance.yearHigh()).toBe(140);
    // 126 is 66 of the 80 between 60 and 140
    expect(fixture.componentInstance.yearPosition()).toBeCloseTo(82.5, 5);
    expect(fixture.nativeElement.querySelector('.marker').style.left).toBe('82.5%');
  });

  it('measures each return from the last close on or before the start of its period', () => {
    const returns = render().componentInstance.returns();
    const value = (label: string) => returns.find((r) => r.label === label)!.value!;
    expect(value('1W')).toBeCloseTo(126 / 120 - 1, 10);
    expect(value('1M')).toBeCloseTo(126 / 110 - 1, 10);
    expect(value('3M')).toBeCloseTo(126 / 100 - 1, 10);
    expect(value('YTD')).toBeCloseTo(126 / 80 - 1, 10);
    expect(value('1Y')).toBeCloseTo(126 / 70 - 1, 10);
  });

  it('leaves a return out when the history does not reach back that far, and shows nothing without data', () => {
    const short = render(points.slice(-2)).componentInstance.returns();
    expect(short.find((r) => r.label === '1W')!.value).toBeCloseTo(126 / 120 - 1, 10);
    expect(short.find((r) => r.label === '1Y')!.value).toBeNull();
    expect(render([]).nativeElement.querySelector('section')).toBeNull();
  });
});
