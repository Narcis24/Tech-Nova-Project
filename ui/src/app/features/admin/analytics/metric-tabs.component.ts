import { Component, computed, ElementRef, input, output, viewChildren } from '@angular/core';

import { AdminAnalyticsResponse } from '../../../generated/trading-api-client';
import { change, Change, drawnBuckets, Metric, MetricKey, METRICS } from './analytics';

/** One tab, worked out once per response rather than in the template. */
interface Tab {
  metric: Metric;
  value: string;
  change: Change | null;
  // SVG path data for the tab's trend line, and the area under it; null with under two points
  line: string | null;
  area: string | null;
}

// The trend lines' drawing size; preserveAspectRatio="none" stretches it to the tab's width
const WIDTH = 100;
const HEIGHT = 28;

/**
 * The four headline figures as tabs, YouTube Studio style: each shows its value, the change since the
 * previous period and a small trend line, and picking one chooses what the chart below shows.
 */
@Component({
  selector: 'app-metric-tabs',
  templateUrl: './metric-tabs.component.html',
  styleUrl: './metric-tabs.component.css',
})
export class MetricTabsComponent {
  readonly data = input.required<AdminAnalyticsResponse>();
  readonly selected = input.required<MetricKey>();
  // The id of the chart the tabs control, for screen readers
  readonly panelId = input<string | null>(null);
  readonly selectedChange = output<MetricKey>();

  private readonly buttons = viewChildren<ElementRef<HTMLButtonElement>>('tab');

  readonly tabs = computed<Tab[]>(() => {
    const data = this.data();
    const buckets = drawnBuckets(data.buckets, data.bucketSize).filter((bucket) => !bucket.future);
    return METRICS.map((metric) => {
      const current = data.totals[metric.key];
      const { line, area } = trend(buckets.map((bucket) => bucket.totals[metric.key] ?? null));
      return {
        metric,
        value: current == null ? '–' : metric.format(current),
        change: change(metric, current, data.previousTotals?.[metric.key]),
        line,
        area,
      };
    });
  });

  choose(key: MetricKey): void {
    if (key !== this.selected()) {
      this.selectedChange.emit(key);
    }
  }

  // Left and right move between tabs, Home and End jump to the ends, as the ARIA tabs pattern expects
  onKeydown(event: KeyboardEvent, index: number): void {
    const last = METRICS.length - 1;
    const next =
      event.key === 'ArrowRight'
        ? (index + 1) % METRICS.length
        : event.key === 'ArrowLeft'
          ? (index - 1 + METRICS.length) % METRICS.length
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? last
              : null;
    if (next === null) {
      return;
    }
    event.preventDefault();
    this.choose(METRICS[next].key);
    this.buttons()[next]?.nativeElement.focus();
  }
}

/** A trend line through the values, scaled to fill the drawing; gaps (no fill rate yet) are skipped. */
function trend(values: (number | null)[]): { line: string | null; area: string | null } {
  const points = values
    .map((value, index) => ({ value, index }))
    .filter((point): point is { value: number; index: number } => point.value !== null);
  if (points.length < 2) {
    return { line: null, area: null };
  }
  const min = Math.min(...points.map((p) => p.value));
  const max = Math.max(...points.map((p) => p.value));
  const span = max - min || 1;
  const x = (index: number) => (index / (values.length - 1)) * WIDTH;
  // 3px of room top and bottom, so the stroke isn't cut off
  const y = (value: number) => HEIGHT - 3 - ((value - min) / span) * (HEIGHT - 6);
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(p.index).toFixed(1)} ${y(p.value).toFixed(1)}`).join(' ');
  const first = x(points[0].index).toFixed(1);
  const lastX = x(points[points.length - 1].index).toFixed(1);
  return { line, area: `${line} L${lastX} ${HEIGHT} L${first} ${HEIGHT} Z` };
}
