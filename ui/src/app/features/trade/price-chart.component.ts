import { sessionDate } from '../../core/session-date';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap, timer } from 'rxjs';
import { PriceControllerService, PriceHistoryPoint } from '../../generated/trading-api-client';

export interface TradeQuote {
  symbol: string;
  price: number | null;
  previousClose: number | null;
  date: string | null;
}

interface HistoryState {
  symbol: string;
  loading: boolean;
  error: boolean;
  points: PriceHistoryPoint[];
}
@Component({
  selector: 'app-price-chart',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  templateUrl: './price-chart.component.html',
  styleUrl: './price-chart.component.css',
})
export class PriceChartComponent {
  readonly sessionDate = sessionDate;
  readonly Math = Math;
  readonly symbol = input('');
  readonly instrumentName = input('');
  readonly pendingSymbol = input(false);
  readonly changeInstrument = output<void>();
  readonly quoteChange = output<TradeQuote>();
  readonly expanded = signal(false);
  private readonly api = inject(PriceControllerService);
  private readonly revision = signal(0);
  readonly normalizedSymbol = computed(() => this.symbol().trim().toUpperCase());
  private readonly request = computed(() => ({ symbol: this.normalizedSymbol(), revision: this.revision() }));
  readonly state = signal<HistoryState>({ symbol: '', loading: false, error: false, points: [] });
  readonly loading = computed(
    () => this.pendingSymbol() || this.state().loading || this.state().symbol !== this.normalizedSymbol(),
  );
  readonly range = signal<1 | 3 | 12>(1);
  readonly ranges = [
    { months: 1, label: '1M' },
    { months: 3, label: '3M' },
    { months: 12, label: '1Y' },
  ] as const;
  readonly hovered = signal<number | null>(null);
  readonly points = computed(() => (this.state().symbol === this.normalizedSymbol() ? this.state().points : []));
  readonly latest = computed(() => this.points().at(-1));
  readonly dailyChange = computed(() =>
    this.points().length > 1 ? Number(this.latest()!.close) - Number(this.points().at(-2)!.close) : null,
  );
  readonly dailyPercent = computed(() =>
    this.dailyChange() == null ? null : this.dailyChange()! / Number(this.points().at(-2)!.close),
  );
  readonly visible = computed(() => {
    const latest = this.latest();
    if (!latest?.date) return [];
    const end = new Date(latest.date + 'T00:00:00Z'),
      day = end.getUTCDate();
    end.setUTCDate(1);
    end.setUTCMonth(end.getUTCMonth() - this.range());
    const lastDay = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth() + 1, 0)).getUTCDate();
    end.setUTCDate(Math.min(day, lastDay));
    const start = end.toISOString().slice(0, 10);
    return this.points().filter((p) => p.date! >= start);
  });
  readonly rangeChange = computed(() =>
    this.visible().length > 1 ? Number(this.visible().at(-1)!.close) / Number(this.visible()[0].close) - 1 : null,
  );
  readonly minimum = computed(() => Math.min(...this.visible().map((p) => Number(p.close))));
  readonly maximum = computed(() => Math.max(...this.visible().map((p) => Number(p.close))));
  readonly line = computed(() =>
    this.visible()
      .map((p, i) => `${this.x(i)},${this.y(Number(p.close))}`)
      .join(' '),
  );
  readonly area = computed(() =>
    this.visible().length ? `${this.x(0)},208 ${this.line()} ${this.x(this.visible().length - 1)},208` : '',
  );
  readonly selectedIndex = computed(() =>
    this.hovered() == null
      ? this.visible().length - 1
      : Math.max(0, Math.min(this.hovered()!, this.visible().length - 1)),
  );
  readonly selected = computed(() => this.visible().at(this.selectedIndex()));

  constructor() {
    toObservable(this.request)
      .pipe(
        switchMap(({ symbol }) => {
          const empty: HistoryState = { symbol, loading: false, error: false, points: [] };
          if (!/^[A-Z0-9.-]{1,10}$/.test(symbol)) return of(empty);
          // Cancel on every symbol change, including the brief typing delay.
          return timer(250).pipe(
            switchMap(() => this.api.getPriceHistory(symbol)),
            map((points) => ({
              ...empty,
              points: points
                .filter((p) => p.date && Number.isFinite(Number(p.close)) && Number(p.close) > 0)
                .sort((a, b) => a.date!.localeCompare(b.date!)),
            })),
            catchError(() => of({ ...empty, error: true })),
            startWith({ ...empty, loading: true }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((state) => {
        this.hovered.set(null);
        this.state.set(state);
        this.quoteChange.emit({
          symbol: state.symbol,
          price: state.points.at(-1)?.close ?? null,
          previousClose: state.points.at(-2)?.close ?? null,
          date: state.points.at(-1)?.date ?? null,
        });
      });
  }
  refresh(): void {
    this.revision.update((v) => v + 1);
  }
  chooseRange(months: 1 | 3 | 12): void {
    this.hovered.set(null);
    this.range.set(months);
  }
  x(index: number): number {
    return this.visible().length < 2 ? 320 : 8 + (index * 624) / (this.visible().length - 1);
  }
  y(value: number): number {
    const span = this.maximum() - this.minimum();
    return span ? 192 - ((value - this.minimum()) * 176) / span : 104;
  }
  pointAt(event: PointerEvent): void {
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    this.hovered.set(
      Math.round(Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)) * (this.visible().length - 1)),
    );
  }
  move(event: KeyboardEvent): void {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    this.hovered.set(
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? this.visible().length - 1
          : Math.max(
              0,
              Math.min(this.visible().length - 1, this.selectedIndex() + (event.key === 'ArrowLeft' ? -1 : 1)),
            ),
    );
  }
}
