import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { DonutChartComponent } from '../../components/donut-chart/donut-chart.component';
import { InstrumentControllerService, PieControllerService } from '../../generated/trading-api-client';

interface Row {
  symbol: string;
  weight: string;
}

// 0 to 100 with up to two decimals, the same range the API accepts
const WEIGHT_PATTERN = /^\d{1,3}(\.\d{1,2})?$/;
const NAME_MAX = 60;

/** Creates a pie, or edits the one in the route: a name, and stocks with weights that leave the rest as cash. */
@Component({
  selector: 'app-pie-editor',
  imports: [DonutChartComponent, RouterLink],
  templateUrl: './pie-editor.component.html',
  styleUrls: ['./pies-shared.css', './pie-editor.component.css'],
})
export class PieEditorComponent {
  private readonly api = inject(PieControllerService);
  private readonly router = inject(Router);
  readonly pieId = inject(ActivatedRoute).snapshot.paramMap.get('pieId');
  readonly instrumentError = signal(false);

  readonly instruments = toSignal(
    inject(InstrumentControllerService)
      .getInstruments(true)
      .pipe(
        catchError(() => {
          this.instrumentError.set(true);
          return of([]);
        }),
      ),
    {
      initialValue: [],
    },
  );
  readonly name = signal('');
  readonly description = signal('');
  readonly rows = signal<Row[]>([{ symbol: '', weight: '' }]);
  readonly loading = signal(!!this.pieId);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  /** Each row's symbol as typed, trimmed and upper-cased; the search box accepts either case. */
  readonly symbols = computed(() => this.rows().map((r) => r.symbol.trim().toUpperCase()));
  readonly unavailable = computed(() => this.symbols().filter((s) => s && !this.isTradable(s)));
  readonly weights = computed(() =>
    this.rows().map((r) => (WEIGHT_PATTERN.test(r.weight.trim()) ? Number(r.weight) : NaN)),
  );
  readonly total = computed(() => this.weights().reduce((sum, w) => sum + (w > 0 ? w : 0), 0));
  readonly cash = computed(() => Math.round((100 - this.total()) * 100) / 100);
  readonly overAllocated = computed(() => this.cash() < 0);
  readonly segments = computed(() => {
    if (this.overAllocated()) return [];
    const slices = this.symbols()
      .map((symbol, i) => ({ label: symbol, pct: this.weights()[i] }))
      .filter((s) => s.label && s.pct > 0);
    return this.cash() > 0 ? [...slices, { label: 'Cash', pct: this.cash() }] : slices;
  });
  readonly duplicate = computed(() => {
    const symbols = this.symbols().filter(Boolean);
    return new Set(symbols).size !== symbols.length;
  });
  readonly valid = computed(() => {
    const name = this.name().trim();
    return (
      name.length > 0 &&
      name.length <= NAME_MAX &&
      !this.instrumentError() &&
      this.rows().length > 0 &&
      this.symbols().every((symbol) => this.isTradable(symbol)) &&
      this.weights().every((w) => w > 0) &&
      !this.overAllocated() &&
      !this.duplicate()
    );
  });

  constructor() {
    if (this.pieId) {
      this.api.get(this.pieId).subscribe({
        next: (pie) => {
          this.name.set(pie.name ?? '');
          this.description.set(pie.description ?? '');
          this.rows.set((pie.slices ?? []).map((s) => ({ symbol: s.symbol ?? '', weight: String(s.weightPct) })));
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.loadError.set(true);
        },
      });
    }
  }

  addRow(): void {
    this.rows.update((rows) => [...rows, { symbol: '', weight: '' }]);
  }

  isTradable(symbol: string): boolean {
    return this.instruments().some((instrument) => instrument.symbol === symbol);
  }

  removeRow(index: number): void {
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
  }

  setRow(index: number, changes: Partial<Row>): void {
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...changes } : r)));
  }

  save(): void {
    if (!this.valid() || this.saving()) return;
    const body = {
      name: this.name().trim(),
      description: this.description().trim() || undefined,
      slices: this.rows().map((r, i) => ({ symbol: this.symbols()[i], weightPct: Number(r.weight) })),
    };
    this.saving.set(true);
    this.saveError.set(null);
    (this.pieId ? this.api.update(this.pieId, body) : this.api.create(body)).subscribe({
      next: () => this.router.navigateByUrl('/strategies'),
      error: (error: unknown) => {
        this.saving.set(false);
        this.saveError.set(saveError(error));
      },
    });
  }
}

function saveError(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 422 && error.error?.errorCode === 'INVALID_PIE') {
    const message = error.error.message;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return "Couldn't save the strategy. Try again.";
}
