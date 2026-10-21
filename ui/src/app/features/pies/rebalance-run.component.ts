import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, switchMap, takeWhile, timer } from 'rxjs';
import { RebalanceControllerService, RebalanceRunResponse } from '../../generated/trading-api-client';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';

@Component({
  selector: 'app-rebalance-run',
  imports: [RouterLink, SymbolIconComponent],
  templateUrl: './rebalance-run.component.html',
  styleUrls: ['./pies-shared.css', './rebalance-run.component.css'],
})
export class RebalanceRunComponent {
  private readonly api = inject(RebalanceControllerService);
  readonly runId = inject(ActivatedRoute).snapshot.paramMap.get('runId')!;
  readonly run = signal<RebalanceRunResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly cancelling = signal(false);
  private readonly revision = signal(0);
  readonly active = computed(() => ['SELLING', 'BUYING'].includes(this.run()?.status ?? ''));
  readonly heading = computed(() => {
    switch (this.run()?.status) {
      case 'SELLING':
        return 'Selling';
      case 'BUYING':
        return 'Buying';
      case 'COMPLETED':
        return 'Rebalance complete';
      case 'PARTIAL':
        return 'Partly done';
      case 'CANCELLED':
        return 'Rebalance cancelled';
      default:
        return 'Rebalancing';
    }
  });

  constructor() {
    toObservable(this.revision)
      .pipe(
        switchMap(() =>
          timer(0, 2000).pipe(
            switchMap(() => this.api.getRebalanceRun(this.runId)),
            takeWhile((run) => ['SELLING', 'BUYING'].includes(run.status ?? ''), true),
            catchError((error: unknown) => {
              this.error.set(
                error instanceof HttpErrorResponse && error.status === 404
                  ? 'This rebalance could not be found.'
                  : "Couldn't refresh the rebalance. Try again to see its latest status.",
              );
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((run) => {
        this.run.set(run);
        this.error.set(null);
      });
  }

  retry(): void {
    this.error.set(null);
    this.revision.update((value) => value + 1);
  }

  cancel(): void {
    if (!this.active() || this.cancelling()) return;
    this.cancelling.set(true);
    this.api.cancelRebalanceRun(this.runId).subscribe({
      next: (run) => {
        this.run.set(run);
        this.cancelling.set(false);
        this.revision.update((value) => value + 1);
      },
      error: () => {
        this.cancelling.set(false);
        this.error.set("Couldn't cancel the rebalance. Try again.");
      },
    });
  }
}
