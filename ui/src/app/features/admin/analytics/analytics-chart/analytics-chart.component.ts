import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import { AdminAnalyticsResponse, AnalyticsBucket, AnalyticsTotals } from '../../../../generated/trading-api-client';
import { ASSET_CLASSES, drawnBuckets, MetricKey, METRICS } from '../analytics';

export type Split = 'total' | 'assetClass' | 'side';
export type ChartType = 'line' | 'bar';

/** One line on the chart: a value per drawn point, null where there's nothing to draw. */
export interface Series {
  key: string;
  label: string;
  color: string;
  values: (number | null)[];
}

const SPLITS: readonly { value: Split; label: string }[] = [
  { value: 'total', label: 'Total' },
  { value: 'assetClass', label: 'Asset class' },
  { value: 'side', label: 'Side' },
];

// Buy and sell keep the colours they have everywhere else in the app
const SIDE_GROUPS = [
  { value: 'BUY', label: 'Buy', color: 'var(--primary-color)' },
  { value: 'SELL', label: 'Sell', color: 'var(--sell-color)' },
];

const HEIGHT = 260;
// Room for the value labels on the left and the date labels underneath
const PAD = { top: 12, right: 12, bottom: 28, left: 60 };
// Bars take this much of their point's slot, and are no wider than the cap each
const BAR_FILL = 0.7;
const MAX_BAR_WIDTH = 28;

// Gives each chart its own gradient ids, since ids are shared by the whole page
let nextChartId = 0;

const NY = 'America/New_York';
const LABELS = {
  HOUR: new Intl.DateTimeFormat('en-US', { timeZone: NY, hour: 'numeric' }),
  DAY: new Intl.DateTimeFormat('en-GB', { timeZone: NY, day: 'numeric', month: 'short' }),
  LONG_DAY: new Intl.DateTimeFormat('en-GB', { timeZone: NY, weekday: 'short', day: 'numeric', month: 'short' }),
};

/**
 * The analytics chart: the metric picked in the tabs over the period, as one line or split by asset
 * class or side, with the previous period dashed behind the total. Hover, or the arrow keys on the
 * focused chart, show a point's values; "Show as bar chart" draws the same values as bars, one per
 * line side by side at each point.
 */
@Component({
  selector: 'app-analytics-chart',
  templateUrl: './analytics-chart.component.html',
  styleUrl: './analytics-chart.component.css',
})
export class AnalyticsChartComponent {
  readonly data = input.required<AdminAnalyticsResponse>();
  readonly metricKey = input.required<MetricKey>();

  readonly splits = SPLITS;
  readonly split = signal<Split>('total');
  readonly chartType = signal<ChartType>('line');
  readonly bars = computed(() => this.chartType() === 'bar');
  // The point under the pointer, or the one picked with the keyboard
  readonly hovered = signal<number | null>(null);
  readonly height = HEIGHT;
  readonly pad = PAD;
  private readonly chartId = nextChartId++;

  // Drawn at its real width, so text and dots aren't stretched
  readonly width = signal(640);
  private readonly plot = viewChild.required<ElementRef<HTMLElement>>('plot');

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(([entry]) =>
        this.width.set(Math.max(280, Math.round(entry.contentRect.width))),
      );
      observer.observe(this.plot().nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  readonly metric = computed(() => METRICS.find((metric) => metric.key === this.metricKey()) ?? METRICS[0]);
  readonly points = computed(() => drawnBuckets(this.data().buckets, this.data().bucketSize));
  // Lined up with the current points by position; the dashed line is the previous period's shape
  private readonly previousPoints = computed(() =>
    drawnBuckets(this.data().previousBuckets, this.data().bucketSize).slice(0, this.points().length),
  );

  readonly series = computed<Series[]>(() => {
    const key = this.metricKey();
    const points = this.points();
    const values = (pick: (bucket: AnalyticsBucket) => AnalyticsTotals | undefined) =>
      points.map((bucket) => (bucket.future ? null : (pick(bucket)?.[key] ?? null)));

    if (this.split() === 'total') {
      const metric = this.metric();
      return [{ key: 'total', label: metric.label, color: metric.color, values: values((bucket) => bucket.totals) }];
    }
    const groups = this.split() === 'assetClass' ? ASSET_CLASSES : SIDE_GROUPS;
    const field = this.split() === 'assetClass' ? 'byAssetClass' : 'bySide';
    return (
      groups
        .map((group) => ({
          key: group.value,
          label: group.label,
          color: group.color,
          values: values((bucket) => bucket[field][group.value]),
        }))
        // Groups with nothing in them are left out, like the other asset classes while one is filtered
        .filter((series) => series.values.some(Boolean))
    );
  });

  readonly previous = computed<(number | null)[] | null>(() =>
    this.split() === 'total' && this.data().previousTotals
      ? this.previousPoints().map((bucket) => bucket.totals[this.metricKey()] ?? null)
      : null,
  );

  readonly empty = computed(() => this.points().every((bucket) => bucket.future || bucket.totals.orders === 0));

  readonly scale = computed(() => {
    const values = [...this.series().flatMap((series) => series.values), ...(this.previous() ?? [])];
    const { max, step } = this.metric().isRate
      ? { max: 1, step: 0.25 }
      : niceScale(Math.max(0, ...values.filter((value): value is number => value !== null)));
    const ticks: { value: number; y: number; label: string }[] = [];
    for (let value = 0; value <= max + step / 1e6; value += step) {
      ticks.push({ value, y: this.yFor(value, max), label: this.metric().format(value) });
    }
    return { max, ticks };
  });

  readonly lines = computed(() =>
    this.series().map((series) => {
      const last = lastIndex(series.values);
      return {
        ...series,
        path: this.path(series.values),
        area: this.area(series.values),
        gradientId: `analytics-fade-${this.chartId}-${series.key}`,
        end: last === null ? null : { x: this.x(last), y: this.y(series.values[last] ?? 0) },
      };
    }),
  );
  // The bar chart: each point's slot holds one bar per series, side by side, centred on the point
  readonly barGroups = computed(() => {
    const series = this.series();
    const slot = this.slotWidth();
    const group = Math.min(slot * BAR_FILL, MAX_BAR_WIDTH * series.length);
    const width = group / Math.max(1, series.length);
    // A 1px gap between bars that share a slot
    const gap = series.length > 1 ? 1 : 0;
    return series.map((s, position) => ({
      key: s.key,
      color: s.color,
      rects: s.values.flatMap((value, index) => {
        if (value === null) {
          return [];
        }
        const top = this.y(value);
        return [
          {
            index,
            x: this.x(index) - group / 2 + position * width,
            y: top,
            width: Math.max(1, width - gap),
            height: this.y(0) - top,
          },
        ];
      }),
    }));
  });

  // The fill under the lines starts this strong under the line; fainter when several lines overlap
  readonly fadeOpacity = computed(() => (this.split() === 'total' ? 0.28 : 0.14));

  readonly previousPath = computed(() => {
    const previous = this.previous();
    return previous ? this.path(previous) : null;
  });

  // About one date label per 80px, always including the first point
  readonly xLabels = computed(() => {
    const points = this.points();
    const every = Math.max(1, Math.ceil(points.length / Math.max(2, Math.floor((this.width() - PAD.left) / 80))));
    return points
      .map((bucket, index) => ({ index, x: this.x(index), label: this.shortLabel(bucket.start) }))
      .filter((label) => label.index % every === 0);
  });

  readonly tooltip = computed(() => {
    const index = this.hovered();
    if (index === null || !this.points()[index]) {
      return null;
    }
    const bucket = this.points()[index];
    const metric = this.metric();
    const show = (value: number | null) => (value === null ? '–' : metric.format(value));
    const previousBucket = this.previousPoints()[index];
    const x = this.x(index);
    return {
      x,
      // The hovered point's slot, shaded behind its bars
      slotX: x - this.slotWidth() / 2,
      slotWidth: this.slotWidth(),
      // Flip to the left of the line near the right edge, so it stays inside the card
      left: x > this.width() - 220 ? x - 210 : x + 14,
      title: this.longLabel(bucket.start),
      future: bucket.future,
      rows: this.series()
        .map((series) => ({ label: series.label, color: series.color, value: series.values[index] }))
        .sort((a, b) => (b.value ?? -1) - (a.value ?? -1))
        .map((row) => ({ ...row, text: show(row.value) })),
      total: this.split() === 'total' ? null : show(bucket.totals[this.metricKey()] ?? null),
      previous:
        this.previous() && previousBucket
          ? { label: this.longLabel(previousBucket.start), text: show(this.previous()?.[index] ?? null) }
          : null,
      dots: this.series()
        .filter((series) => series.values[index] != null)
        .map((series) => ({ color: series.color, y: this.y(series.values[index] ?? 0) })),
    };
  });

  chooseSplit(split: Split): void {
    this.split.set(split);
    this.hovered.set(null);
  }

  /** The point nearest the pointer, from its position across the drawing. */
  pointAt(event: PointerEvent): void {
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    const n = this.points().length;
    if (!n || !rect.width) {
      return;
    }
    const plotWidth = this.width() - PAD.left - PAD.right;
    const offset = ((event.clientX - rect.left) / rect.width) * this.width() - PAD.left;
    // Bars: the slot the pointer is in; lines: the nearest point
    const index = this.bars() ? Math.floor((offset / plotWidth) * n) : Math.round((offset / plotWidth) * (n - 1));
    this.hovered.set(n === 1 ? 0 : Math.max(0, Math.min(n - 1, index)));
  }

  // Lines run from edge to edge; bars sit in the middle of equal slots, so the end ones fit inside
  x(index: number): number {
    const n = this.points().length;
    const plotWidth = this.width() - PAD.left - PAD.right;
    if (this.bars()) {
      return PAD.left + (index + 0.5) * this.slotWidth();
    }
    return PAD.left + (n <= 1 ? plotWidth / 2 : (index / (n - 1)) * plotWidth);
  }

  private slotWidth(): number {
    return (this.width() - PAD.left - PAD.right) / Math.max(1, this.points().length);
  }

  y(value: number): number {
    return this.yFor(value, this.scale().max);
  }

  private yFor(value: number, max: number): number {
    return PAD.top + (HEIGHT - PAD.top - PAD.bottom) * (1 - value / max);
  }

  // Lifts the pen over nulls, so a missing value is a gap rather than a drop to zero
  private path(values: (number | null)[]): string {
    let path = '';
    let penDown = false;
    values.forEach((value, index) => {
      if (value === null) {
        penDown = false;
        return;
      }
      path += `${penDown ? 'L' : 'M'}${this.x(index).toFixed(1)} ${this.y(value).toFixed(1)} `;
      penDown = true;
    });
    return path.trim();
  }

  // The shape under each unbroken run of the line, down to the zero line; a lone point has none
  private area(values: (number | null)[]): string {
    const runs: number[][] = [[]];
    values.forEach((value, index) => (value === null ? runs.push([]) : runs.at(-1)!.push(index)));
    const base = this.y(0).toFixed(1);
    return runs
      .filter((run) => run.length > 1)
      .map((run) => {
        const top = run.map(
          (index, i) => `${i ? 'L' : 'M'}${this.x(index).toFixed(1)} ${this.y(values[index] ?? 0).toFixed(1)}`,
        );
        const first = this.x(run[0]).toFixed(1);
        const last = this.x(run.at(-1)!).toFixed(1);
        return `${top.join(' ')} L${last} ${base} L${first} ${base} Z`;
      })
      .join(' ');
  }

  private shortLabel(start: string): string {
    const date = new Date(start);
    return this.data().bucketSize === 'HOUR' ? LABELS.HOUR.format(date) : LABELS.DAY.format(date);
  }

  private longLabel(start: string): string {
    const date = new Date(start);
    switch (this.data().bucketSize) {
      case 'HOUR':
        return `${LABELS.LONG_DAY.format(date)}, ${LABELS.HOUR.format(date)}`;
      case 'WEEK':
        return `Week of ${LABELS.DAY.format(date)}`;
      default:
        return LABELS.LONG_DAY.format(date);
    }
  }
}

/** A round top for the value axis and a step that splits it into about four. */
export function niceScale(peak: number): { max: number; step: number } {
  if (peak <= 0) {
    return { max: 4, step: 1 };
  }
  const raw = peak / 4;
  const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = Math.max(1, [1, 2, 2.5, 5, 10].map((s) => s * magnitude).find((s) => s >= raw) ?? raw);
  return { max: step * Math.ceil(peak / step), step };
}

function lastIndex(values: (number | null)[]): number | null {
  for (let index = values.length - 1; index >= 0; index--) {
    if (values[index] !== null) {
      return index;
    }
  }
  return null;
}
