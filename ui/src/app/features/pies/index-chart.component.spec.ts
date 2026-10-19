import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IndexPoint } from '../../generated/trading-api-client';
import { IndexChartComponent } from './index-chart.component';

describe('IndexChartComponent', () => {
  let fixture: ComponentFixture<IndexChartComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function render(points: IndexPoint[]): HTMLElement {
    fixture = TestBed.createComponent(IndexChartComponent);
    fixture.componentRef.setInput('points', points);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('draws a line through the days in date order', () => {
    const root = render([
      { date: '2026-01-03', value: 110 },
      { date: '2026-01-01', value: 100 },
      { date: '2026-01-02', value: 90 },
    ]);
    expect(root.querySelector('polyline')!.getAttribute('points')).toBe('8,104 320,192 632,16');
    expect(root.querySelector('.axis-top')!.textContent).toBe('110.00');
    expect(root.querySelector('.axis-bottom')!.textContent).toBe('90.00');
  });

  it('reads out the day chosen with the keyboard', () => {
    const root = render([
      { date: '2026-01-01', value: 100 },
      { date: '2026-01-02', value: 104.5 },
    ]);
    const input = root.querySelector('input')!;
    input.value = '0';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelector('.readout')!.textContent).toContain('Jan 1, 2026 · 100.00');
  });

  it('shows the same pending history message before any index points exist', () => {
    const root = render([]);
    expect(root.querySelector('svg')).toBeNull();
    expect(root.textContent).toContain('first nightly update');
  });

  it('waits for a second day before drawing', () => {
    const root = render([{ date: '2026-01-01', value: 100 }]);
    expect(root.querySelector('svg')).toBeNull();
    expect(root.textContent).toContain('first nightly update');
  });
  it('uses the shared chart for the full index history without stock or dollar labels', () => {
    const root = render([
      { date: '2025-01-01', value: 100 },
      { date: '2026-01-02', value: 110 },
    ]);
    expect(root.querySelector('app-price-chart')).not.toBeNull();
    expect(root.querySelector('polyline')!.getAttribute('points')).toBe('8,192 632,16');
    expect(root.querySelector('input')!.getAttribute('aria-valuetext')).toBe('Jan 2, 2026, 110.00');
    expect(root.querySelector('.quote-facts')).toBeNull();
    expect(root.querySelector('.ranges')).toBeNull();
    expect(root.textContent).not.toContain('$');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });
});
