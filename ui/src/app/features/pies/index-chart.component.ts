import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input, signal } from '@angular/core';

import { sessionDate } from '../../core/session-date';
import { IndexPoint } from '../../generated/trading-api-client';

/** A pie's daily index, drawn like the price chart: a line with a keyboard-friendly scrubber. */
@Component({
  selector: 'app-index-chart',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './index-chart.component.html',
  styleUrl: './index-chart.component.css',
})
export class IndexChartComponent {
  readonly sessionDate = sessionDate;
  readonly Math = Math;
  readonly points = input<IndexPoint[]>([]);
  readonly hovered = signal<number | null>(null);
  readonly data = computed(() =>
    this.points()
      .filter((p) => p.date && Number.isFinite(Number(p.value)))
      .sort((a, b) => a.date!.localeCompare(b.date!)),
  );
  readonly minimum = computed(() => Math.min(...this.data().map((p) => Number(p.value))));
  readonly maximum = computed(() => Math.max(...this.data().map((p) => Number(p.value))));
  readonly line = computed(() =>
    this.data()
      .map((p, i) => `${this.x(i)},${this.y(Number(p.value))}`)
      .join(' '),
  );
  readonly selectedIndex = computed(() =>
    this.hovered() == null ? this.data().length - 1 : Math.max(0, Math.min(this.hovered()!, this.data().length - 1)),
  );
  readonly selected = computed(() => this.data().at(this.selectedIndex()));

  x(index: number): number {
    return 8 + (index * 624) / (this.data().length - 1);
  }

  y(value: number): number {
    const span = this.maximum() - this.minimum();
    return span ? 192 - ((value - this.minimum()) * 176) / span : 104;
  }

  pointAt(event: PointerEvent): void {
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    this.hovered.set(
      Math.round(Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)) * (this.data().length - 1)),
    );
  }
}
