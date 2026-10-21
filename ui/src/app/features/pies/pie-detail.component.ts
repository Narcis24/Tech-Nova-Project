import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DonutChartComponent } from '../../components/donut-chart/donut-chart.component';
import { PieControllerService, PieResponse } from '../../generated/trading-api-client';

/** One of the account holder's pies: its slices, and whether it is shown on Discover. */
@Component({
  selector: 'app-pie-detail',
  imports: [DatePipe, DecimalPipe, DonutChartComponent, RouterLink],
  templateUrl: './pie-detail.component.html',
  styleUrls: ['./pies-shared.css', './pie-detail.component.css'],
})
export class PieDetailComponent {
  private readonly api = inject(PieControllerService);
  readonly pieId = inject(ActivatedRoute).snapshot.paramMap.get('pieId')!;

  readonly pie = signal<PieResponse | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly working = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly published = computed(() => !!this.pie()?.publishedAt);
  readonly segments = computed(() => {
    const pie = this.pie();
    if (!pie) return [];
    const slices = (pie.slices ?? []).map((s) => ({ label: s.symbol ?? '', pct: Number(s.weightPct) }));
    return pie.cashPct ? [...slices, { label: 'Cash', pct: Number(pie.cashPct) }] : slices;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.api.get(this.pieId).subscribe({
      next: (pie) => {
        this.pie.set(pie);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  togglePublished(): void {
    const publishing = !this.published();
    this.working.set(true);
    this.actionError.set(null);
    (publishing ? this.api.publishPie(this.pieId) : this.api.unpublishPie(this.pieId)).subscribe({
      next: (pie) => {
        this.pie.set(pie);
        this.working.set(false);
      },
      error: () => {
        this.working.set(false);
        this.actionError.set(`Couldn't ${publishing ? 'publish' : 'unpublish'} the pie. Try again.`);
      },
    });
  }
}
