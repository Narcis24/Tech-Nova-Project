import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DonutChartComponent } from './donut-chart.component';

describe('DonutChartComponent', () => {
  let fixture: ComponentFixture<DonutChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DonutChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(DonutChartComponent);
  });

  it('renders one arc per segment', () => {
    fixture.componentRef.setInput('segments', [
      { label: 'VOO', pct: 50 },
      { label: 'AAPL', pct: 30 },
      { label: 'Cash', pct: 20 },
    ]);
    fixture.detectChanges();

    const arcs: SVGCircleElement[] = Array.from(fixture.nativeElement.querySelectorAll('circle.arc'));
    expect(arcs).toHaveSize(3);
    expect(arcs.every((arc) => arc.hasAttribute('stroke-dasharray'))).toBeTrue();
  });

  it('gives the cash segment the cash class', () => {
    fixture.componentRef.setInput('segments', [
      { label: 'VOO', pct: 80 },
      { label: 'Cash', pct: 20 },
    ]);
    fixture.detectChanges();

    const arcs: SVGCircleElement[] = Array.from(fixture.nativeElement.querySelectorAll('circle.arc'));
    expect(arcs[0].classList.contains('cash')).toBeFalse();
    expect(arcs[1].classList.contains('cash')).toBeTrue();
  });

  it('lists every segment in the aria-label', () => {
    fixture.componentRef.setInput('segments', [
      { label: 'VOO', pct: 50 },
      { label: 'AAPL', pct: 30 },
      { label: 'Cash', pct: 20 },
    ]);
    fixture.detectChanges();

    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toBe('VOO 50%, AAPL 30%, Cash 20%');
  });

  it('renders one empty ring with no NaN dasharray for empty segments', () => {
    fixture.detectChanges();

    const circles: SVGCircleElement[] = Array.from(fixture.nativeElement.querySelectorAll('circle'));
    expect(circles).toHaveSize(1);
    expect(circles[0].classList.contains('empty')).toBeTrue();
    expect(circles[0].getAttribute('stroke-dasharray')).not.toContain('NaN');
  });
});
