import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { DonutChartComponent } from '../../components/donut-chart/donut-chart.component';
import {
  AccountPieControllerService,
  AccountPieResponse,
  PieControllerService,
  PieResponse,
} from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { PieTabsComponent } from './pie-tabs.component';

type Header = { state: 'loading' | 'none' | 'error' } | { state: 'linked'; pie: AccountPieResponse };

const TOP_SLICES = 3;

/** The account holder's pies, and which one the current account follows. */
@Component({
  selector: 'app-pies',
  imports: [DonutChartComponent, PieTabsComponent, RouterLink],
  templateUrl: './pies.component.html',
  styleUrls: ['./pies-shared.css', './pies.component.css'],
})
export class PiesComponent {
  private readonly pieApi = inject(PieControllerService);
  private readonly accountPieApi = inject(AccountPieControllerService);
  private readonly router = inject(Router);
  readonly accountId = inject(AccountService).currentAccountId;

  readonly header = signal<Header>({ state: 'loading' });
  readonly pies = signal<PieResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly savingSettings = signal(false);
  readonly linked = computed(() => {
    const header = this.header();
    return header.state === 'linked' ? header.pie : null;
  });
  private readonly headerRevision = signal(0);
  private readonly pieRevision = signal(0);
  private readonly headerRequest = computed(() => ({ id: this.accountId(), revision: this.headerRevision() }));

  constructor() {
    toObservable(this.headerRequest)
      .pipe(
        switchMap(({ id }) => {
          if (!id) return of<Header>({ state: 'none' });
          return this.accountPieApi.getAccountPie(id).pipe(
            map((pie): Header => ({ state: 'linked', pie })),
            catchError((error: unknown) =>
              of<Header>({ state: error instanceof HttpErrorResponse && error.status === 404 ? 'none' : 'error' }),
            ),
            startWith<Header>({ state: 'loading' }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((header) => this.header.set(header));

    toObservable(this.pieRevision)
      .pipe(
        switchMap(() =>
          this.pieApi.listMine().pipe(
            map((pies) => ({ loading: false, error: false, pies })),
            catchError(() => of({ loading: false, error: true, pies: [] as PieResponse[] })),
            startWith({ loading: true, error: false, pies: [] as PieResponse[] }),
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
    this.pieRevision.update((v) => v + 1);
    this.headerRevision.update((v) => v + 1);
  }

  segments(pie: PieResponse): { label: string; pct: number }[] {
    const slices = (pie.slices ?? []).map((s) => ({ label: s.symbol ?? '', pct: Number(s.weightPct) }));
    return pie.cashPct ? [...slices, { label: 'Cash', pct: Number(pie.cashPct) }] : slices;
  }

  topSlices(pie: PieResponse) {
    return (pie.slices ?? []).slice(0, TOP_SLICES);
  }

  use(pie: PieResponse): void {
    const id = this.accountId();
    if (!id) return;
    this.actionError.set(null);
    this.accountPieApi.linkPie(id, { pieId: pie.pieId! }).subscribe({
      next: () => this.router.navigateByUrl(`/accounts/${id}/rebalance`),
      error: () => this.actionError.set("Couldn't use this pie for the account. Try again."),
    });
  }

  unlink(): void {
    const id = this.accountId();
    if (!id) return;
    this.actionError.set(null);
    this.accountPieApi.unlinkPie(id).subscribe({
      next: () => this.headerRevision.update((v) => v + 1),
      error: () => this.actionError.set("Couldn't unlink the pie. Try again."),
    });
  }

  stopCopying(link: AccountPieResponse): void {
    if (!window.confirm(`Stop copying ${link.pieName}? Your holdings stay as they are.`)) return;
    this.unlink();
  }

  setAutoSync(autoSync: boolean): void {
    const id = this.accountId();
    if (!id) return;
    this.actionError.set(null);
    this.savingSettings.set(true);
    this.accountPieApi.updateCopySettings(id, { autoSync }).subscribe({
      next: (pie) => {
        this.savingSettings.set(false);
        this.header.set({ state: 'linked', pie });
      },
      error: (error: unknown) => {
        this.savingSettings.set(false);
        this.actionError.set(conflictMessage(error) ?? "Couldn't change auto-sync. Try again.");
        this.headerRevision.update((v) => v + 1);
      },
    });
  }

  /** Turns the copy into a private pie of the user's own, so the author's changes stop reaching the account. */
  makeMine(): void {
    const id = this.accountId();
    if (!id) return;
    this.actionError.set(null);
    this.accountPieApi.makePieMine(id).subscribe({
      next: () => this.retry(),
      error: (error: unknown) =>
        this.actionError.set(conflictMessage(error) ?? "Couldn't make the pie yours. Try again."),
    });
  }

  remove(pie: PieResponse): void {
    if (!window.confirm(`Delete ${pie.name}?`)) return;
    this.actionError.set(null);
    this.pieApi.deletePie(pie.pieId!).subscribe({
      next: () => this.pieRevision.update((v) => v + 1),
      error: (error: unknown) =>
        this.actionError.set(
          error instanceof HttpErrorResponse && error.status === 409
            ? 'Unlink it from your accounts first'
            : "Couldn't delete the pie. Try again.",
        ),
    });
  }
}

function conflictMessage(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || error.status !== 409) return null;
  const message = error.error?.message;
  return typeof message === 'string' && message.trim() ? message : null;
}
