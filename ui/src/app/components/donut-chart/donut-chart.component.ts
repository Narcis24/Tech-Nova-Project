import { Component, computed, input } from '@angular/core';

const COLORS = [
  'var(--primary-color)',
  'var(--gain-color)',
  'var(--sell-color)',
  'var(--warning-color)',
  'var(--secondary-text-color)',
];

@Component({
  selector: 'app-donut-chart',
  templateUrl: './donut-chart.component.html',
  styleUrl: './donut-chart.component.css',
})
export class DonutChartComponent {
  readonly segments = input<{ label: string; pct: number }[]>([]);
  readonly size = input(160);
  readonly ariaLabel = computed(() =>
    this.segments().length
      ? this.segments()
          .map(({ label, pct }) => `${label} ${pct}%`)
          .join(', ')
      : 'Empty chart',
  );
  readonly arcs = computed(() => {
    let offset = 0;
    let colorIndex = 0;
    return this.segments().map(({ label, pct }) => {
      const arc = {
        label,
        dasharray: `${pct} ${100 - pct}`,
        dashoffset: -offset,
        color: label === 'Cash' ? null : COLORS[colorIndex++ % COLORS.length],
      };
      offset += pct;
      return arc;
    });
  });
}
