import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { DonutChartComponent } from '../../components/donut-chart/donut-chart.component';
import { PublicPieControllerService, PublicPieResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';
import { IndexChartComponent } from './index-chart.component';
import { WINDOWS, formatReturn, returnTone } from './pie-return';

type State =
  { status: 'loading' } | { status: 'missing' } | { status: 'error' } | { status: 'ready'; pie: PublicPieResponse };

/** One public pie: who runs it, how it has performed, and what it holds. */
@Component({
  selector: 'app-public-pie',
  imports: [DatePipe, DecimalPipe, DonutChartComponent, IndexChartComponent, RouterLink],
  templateUrl: './public-pie.component.html',
  styleUrls: ['./pies-shared.css', './public-pie.component.css'],
})
export class PublicPieComponent {
  private readonly api = inject(PublicPieControllerService);
  private readonly router = inject(Router);
  private readonly accounts = inject(AccountService);
  private readonly username = inject(AuthService).username;
  private readonly pieId = inject(ActivatedRoute).snapshot.paramMap.get('pieId')!;
  readonly windows = WINDOWS;
  readonly formatReturn = formatReturn;
  readonly returnTone = returnTone;

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
  /** The current account, when the pie is someone else's and could be copied into it. */
  readonly copyTarget = computed(() => {
    const pie = this.current();
    if (!pie || pie.authorUsername === this.username()) return null;
    const id = this.accounts.currentAccountId();
    return this.accounts.accounts().find((a) => a.accountId === id) ?? null;
  });
  readonly ownPie = computed(() => this.current()?.authorUsername === this.username());
  readonly pending = computed(() => this.windows.some((w) => this.current()?.returns?.[w] == null));
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

  copy(): void {
    const account = this.copyTarget();
    if (!account) return;
    this.router.navigate(['/accounts', account.accountId, 'rebalance'], { queryParams: { copy: this.pieId } });
  }

  retry(): void {
    this.revision.update((v) => v + 1);
  }
}
