import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { DonutChartComponent } from '../../components/donut-chart/donut-chart.component';
import { PublicPieControllerService, PublicPieResponse } from '../../generated/trading-api-client';

type State =
  { status: 'loading' } | { status: 'missing' } | { status: 'error' } | { status: 'ready'; pie: PublicPieResponse };

/** One public allocation template and its author. */
@Component({
  selector: 'app-public-pie',
  imports: [DatePipe, DecimalPipe, DonutChartComponent, RouterLink],
  templateUrl: './public-pie.component.html',
  styleUrls: ['./pies-shared.css', './public-pie.component.css'],
})
export class PublicPieComponent {
  private readonly api = inject(PublicPieControllerService);
  private readonly pieId = inject(ActivatedRoute).snapshot.paramMap.get('pieId')!;

  readonly state = signal<State>({ status: 'loading' });
  readonly current = computed(() => {
    const state = this.state();
    return state.status === 'ready' ? state.pie : null;
  });
  readonly segments = computed(() => {
    const pie = this.current();
    if (!pie) return [];
    const slices = (pie.slices ?? []).map((s) => ({ label: s.symbol ?? '', pct: Number(s.weightPct) }));
    return pie.cashPct ? [...slices, { label: 'Cash', pct: Number(pie.cashPct) }] : slices;
  });
  private readonly revision = signal(0);

  constructor() {
    toObservable(this.revision)
      .pipe(
        switchMap(() =>
          this.api.getPublicPie(this.pieId).pipe(
            map((pie): State => ({ status: 'ready', pie })),
            catchError((error: unknown) =>
              of<State>({ status: error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'error' }),
            ),
            startWith<State>({ status: 'loading' }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  retry(): void {
    this.revision.update((v) => v + 1);
  }
}
