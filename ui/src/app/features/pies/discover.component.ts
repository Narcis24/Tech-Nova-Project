import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { PublicPieControllerService, PublicPieSummary } from '../../generated/trading-api-client';
import { PieTabsComponent } from './pie-tabs.component';
import { WINDOWS, Window, formatReturn, returnTone } from './pie-return';

/** Public pies ranked by how they have performed since they were published. */
@Component({
  selector: 'app-discover',
  imports: [DecimalPipe, PieTabsComponent, RouterLink],
  templateUrl: './discover.component.html',
  styleUrls: ['./pies-shared.css', './discover.component.css'],
})
export class DiscoverComponent {
  private readonly api = inject(PublicPieControllerService);
  readonly windows = WINDOWS;
  readonly formatReturn = formatReturn;
  readonly returnTone = returnTone;

  readonly window = signal<Window>('ALL');
  readonly pies = signal<PublicPieSummary[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ window: this.window(), revision: this.revision() }));

  constructor() {
    toObservable(this.request)
      .pipe(
        switchMap(({ window }) =>
          this.api.listPublicPies(window).pipe(
            map((pies) => ({ loading: false, error: false, pies })),
            catchError(() => of({ loading: false, error: true, pies: [] as PublicPieSummary[] })),
            startWith({ loading: true, error: false, pies: [] as PublicPieSummary[] }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ loading, error, pies }) => {
        this.loading.set(loading);
        this.loadError.set(error);
        this.pies.set(pies);
      });
  }

  choose(window: Window): void {
    this.window.set(window);
  }

  retry(): void {
    this.revision.update((v) => v + 1);
  }
}
