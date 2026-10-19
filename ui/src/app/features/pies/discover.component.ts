import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { PublicPieControllerService, PublicPieSummary } from '../../generated/trading-api-client';
import { PieTabsComponent } from './pie-tabs.component';

/** Public allocation templates shared by other traders. */
@Component({
  selector: 'app-discover',
  imports: [DecimalPipe, PieTabsComponent, RouterLink],
  templateUrl: './discover.component.html',
  styleUrls: ['./pies-shared.css', './discover.component.css'],
})
export class DiscoverComponent {
  private readonly api = inject(PublicPieControllerService);
  readonly pies = signal<PublicPieSummary[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  private readonly revision = signal(0);

  constructor() {
    toObservable(this.revision)
      .pipe(
        switchMap(() =>
          this.api.listPublicPies().pipe(
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

  retry(): void {
    this.revision.update((v) => v + 1);
  }
}
