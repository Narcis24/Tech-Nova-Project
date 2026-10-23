import { sessionDate } from '../../core/session-date';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, defer, map, of, startWith, switchMap, timer } from 'rxjs';
import { PriceControllerService, PriceHistoryPoint, QuoteResponse } from '../../generated/trading-api-client';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';

export interface TradeQuote {
  symbol: string;
  price: number | null;
  previousClose: number | null;
  date: string | null;
}

/** One day drawn as a candle, in chart units: the wick from high to low, the body from open to close. */
export interface Candle {
  x: number;
  width: number;
  wickTop: number;
  wickBottom: number;
  bodyTop: number;
  bodyHeight: number;
  up: boolean;
}

/** The fewest sessions the chart zooms in to, so candles never get absurdly wide. */
const MIN_SESSIONS = 10;

interface HistoryState {
  symbol: string;
  loading: boolean;
  error: boolean;
  points: PriceHistoryPoint[];
}
@Component({
  selector: 'app-price-chart',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, SymbolIconComponent],
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
  /** The full year of daily bars for the symbol, for the stats beside the chart. */
  readonly historyChange = output<PriceHistoryPoint[]>();
  readonly expanded = signal(false);
  private readonly api = inject(PriceControllerService);
  private readonly revision = signal(0);
  readonly normalizedSymbol = computed(() => this.symbol().trim().toUpperCase());
  private readonly request = computed(() => ({ symbol: this.normalizedSymbol(), revision: this.revision() }));
  readonly state = signal<HistoryState>({ symbol: '', loading: false, error: false, points: [] });
  readonly loading = computed(
    () => this.pendingSymbol() || this.state().loading || this.state().symbol !== this.normalizedSymbol(),
  );
  readonly range = signal<1 | 3 | 6 | 12>(3);
  readonly ranges = [
    { months: 1, label: '1M' },
    { months: 3, label: '3M' },
    { months: 6, label: '6M' },
    { months: 12, label: '1Y' },
  ] as const;
  /** The sessions on screen after zooming or panning, as indexes into points(); null follows the range. */
  readonly window = signal<{ start: number; end: number } | null>(null);
  private drag: { x: number; start: number } | null = null;
  readonly chartStyle = signal<'candles' | 'line'>('candles');
  readonly chartStyles = [
    { value: 'candles', label: 'Candles' },
    { value: 'line', label: 'Line' },
  ] as const;
  readonly hovered = signal<number | null>(null);
  readonly indicators = signal({ volume: true, ma20: false, ma50: false });
  readonly indicatorOptions = [
    { key: 'volume', label: 'Volume' },
    { key: 'ma20', label: 'MA 20' },
    { key: 'ma50', label: 'MA 50' },
  ] as const;
  // The engine's latest bid and ask from the market data feed; null until it has one
  readonly liveQuote = signal<QuoteResponse | null>(null);
  readonly bid = computed(() => positive(this.liveQuote()?.bid));
  readonly ask = computed(() => positive(this.liveQuote()?.ask));
  readonly spread = computed(() => (this.bid() != null && this.ask() != null ? this.ask()! - this.bid()! : null));
  readonly spreadPercent = computed(() =>
    this.spread() == null ? null : this.spread()! / ((this.ask()! + this.bid()!) / 2),
  );
  readonly points = computed(() => (this.state().symbol === this.normalizedSymbol() ? this.state().points : []));
  readonly latest = computed(() => this.points().at(-1));
  readonly dailyChange = computed(() =>
    this.points().length > 1 ? Number(this.latest()!.close) - Number(this.points().at(-2)!.close) : null,
  );
  readonly dailyPercent = computed(() =>
    this.dailyChange() == null ? null : this.dailyChange()! / Number(this.points().at(-2)!.close),
  );
  /** The first session of the chosen range: the same day of the month, that many months back. */
  private readonly rangeStart = computed(() => {
    const latest = this.latest();
    if (!latest?.date) return 0;
    const end = new Date(latest.date + 'T00:00:00Z'),
      day = end.getUTCDate();
    end.setUTCDate(1);
    end.setUTCMonth(end.getUTCMonth() - this.range());
    const lastDay = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth() + 1, 0)).getUTCDate();
    end.setUTCDate(Math.min(day, lastDay));
    const start = end.toISOString().slice(0, 10);
    return Math.max(
      0,
      this.points().findIndex((p) => p.date! >= start),
    );
  });
  readonly bounds = computed(() => this.window() ?? { start: this.rangeStart(), end: this.points().length - 1 });
  readonly visible = computed(() => this.points().slice(this.bounds().start, this.bounds().end + 1));
  readonly canZoomIn = computed(() => this.visible().length > Math.min(MIN_SESSIONS, this.points().length));
  readonly canZoomOut = computed(() => this.visible().length < this.points().length);
  readonly rangeChange = computed(() =>
    this.visible().length > 1 ? Number(this.visible().at(-1)!.close) / Number(this.visible()[0].close) - 1 : null,
  );
  // Candles span each day's low to high, so their scale does too; the line only needs the closes
  readonly minimum = computed(() =>
    Math.min(...this.visible().map((p) => (this.chartStyle() === 'candles' ? low(p) : Number(p.close)))),
  );
  readonly maximum = computed(() =>
    Math.max(...this.visible().map((p) => (this.chartStyle() === 'candles' ? high(p) : Number(p.close)))),
  );
  readonly line = computed(() =>
    this.visible()
      .map((p, i) => `${this.x(i)},${this.y(Number(p.close))}`)
      .join(' '),
  );
  readonly candles = computed<Candle[]>(() => {
    const width = Math.max(1, (624 / Math.max(1, this.visible().length)) * 0.6);
    return this.visible().map((p, i) => {
      const open = this.y(Number(p.open ?? p.close)),
        close = this.y(Number(p.close));
      return {
        x: this.x(i),
        width,
        wickTop: this.y(high(p)),
        wickBottom: this.y(low(p)),
        bodyTop: Math.min(open, close),
        // A day that closed where it opened still shows as a thin line
        bodyHeight: Math.max(1, Math.abs(open - close)),
        up: Number(p.close) >= Number(p.open ?? p.close),
      };
    });
  });
  /** Volume as bars along the bottom of the chart, low enough to stay mostly clear of the candles. */
  readonly volumeBars = computed(() => {
    const visible = this.visible(),
      most = Math.max(0, ...visible.map((p) => Number(p.volume ?? 0))),
      width = Math.max(1, (624 / Math.max(1, visible.length)) * 0.6);
    if (!most) return [];
    return visible.map((p, i) => {
      const height = (Number(p.volume ?? 0) / most) * 28;
      return {
        x: this.x(i) - width / 2,
        y: 208 - height,
        width,
        height,
        up: Number(p.close) >= Number(p.open ?? p.close),
      };
    });
  });
  readonly ma20 = computed(() => this.averageLine(20));
  readonly ma50 = computed(() => this.averageLine(50));
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
    // The quote is extra: without one the Bid, Ask and Spread boxes keep their dashes, never an error
    toObservable(this.request)
      .pipe(
        switchMap(({ symbol }) =>
          /^[A-Z0-9.-]{1,10}$/.test(symbol)
            ? defer(() => this.api.getQuote(symbol)).pipe(
                map((quote) => (quote?.symbol === symbol ? quote : null)),
                catchError(() => of(null)),
                startWith(null),
              )
            : of(null),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((quote) => this.liveQuote.set(quote));
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
        this.window.set(null);
        this.state.set(state);
        this.historyChange.emit(state.points);
        this.quoteChange.emit({
          symbol: state.symbol,
          price: state.points.at(-1)?.close ?? null,
          previousClose: state.points.at(-2)?.close ?? null,
          date: state.points.at(-1)?.date ?? null,
        });
      });
  }
  toggleIndicator(key: 'volume' | 'ma20' | 'ma50'): void {
    this.indicators.update((shown) => ({ ...shown, [key]: !shown[key] }));
  }
  refresh(): void {
    this.revision.update((v) => v + 1);
  }
  readonly high = high;
  readonly low = low;
  chooseStyle(style: 'candles' | 'line'): void {
    this.hovered.set(null);
    this.chartStyle.set(style);
  }
  chooseRange(months: 1 | 3 | 6 | 12): void {
    this.hovered.set(null);
    this.window.set(null);
    this.range.set(months);
  }
  /**
   * Shows more (factor above 1) or fewer sessions, keeping the session at `anchor` (0 = left edge,
   * 1 = right edge) where it is. The buttons keep the latest session in view; the wheel zooms where it points.
   */
  zoom(factor: number, anchor = 1): void {
    const total = this.points().length,
      { start, end } = this.bounds(),
      count = end - start + 1,
      next = Math.max(Math.min(MIN_SESSIONS, total), Math.min(total, Math.round(count * factor)));
    if (next === count) return;
    const first = Math.round(start + anchor * (count - 1) - anchor * (next - 1));
    this.show(first, next);
  }
  zoomWith(event: WheelEvent): void {
    event.preventDefault();
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    this.zoom(event.deltaY > 0 ? 1.25 : 0.8, Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)));
  }
  /** Dragging with a mouse or pen moves through the history; touch keeps scrolling the page. */
  startPan(event: PointerEvent): void {
    if (event.button !== 0 || event.pointerType === 'touch') return;
    this.drag = { x: event.clientX, start: this.bounds().start };
    try {
      // Keeps the drag going when the pointer leaves the chart; a pointer the browser no longer tracks just skips it
      (event.currentTarget as Element).setPointerCapture(event.pointerId);
    } catch {
      // Panning still works while the pointer stays over the chart
    }
  }
  endPan(): void {
    this.drag = null;
  }
  /** Shows `count` sessions from `first`, kept inside the history. */
  private show(first: number, count: number): void {
    const start = Math.max(0, Math.min(this.points().length - count, first));
    this.hovered.set(null);
    this.window.set({ start, end: start + count - 1 });
  }
  x(index: number): number {
    const count = this.visible().length;
    // Candles sit in the middle of equal slots, so the first and last are not cut off at the edges
    if (this.chartStyle() === 'candles') return 8 + ((index + 0.5) * 624) / Math.max(1, count);
    return count < 2 ? 320 : 8 + (index * 624) / (count - 1);
  }
  y(value: number): number {
    const span = this.maximum() - this.minimum();
    return span ? 192 - ((value - this.minimum()) * 176) / span : 104;
  }
  /**
   * A moving average of the closes over the visible days, as polyline points. It reads the whole year,
   * so the first visible day already averages the sessions before it; days without enough history are left out.
   */
  private averageLine(days: number): string {
    const all = this.points(),
      offset = this.bounds().start;
    let sum = 0;
    const averages = all.map((p, i) => {
      sum += Number(p.close) - (i >= days ? Number(all[i - days].close) : 0);
      return i + 1 >= days ? sum / days : null;
    });
    return this.visible()
      .map((_, i) => averages[offset + i])
      .map((average, i) => (average == null ? null : `${this.x(i)},${Math.max(8, Math.min(208, this.y(average)))}`))
      .filter((point) => point != null)
      .join(' ');
  }
  pointAt(event: PointerEvent): void {
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect(),
      share = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
      count = this.visible().length;
    if (this.drag) {
      this.show(this.drag.start + Math.round(((this.drag.x - event.clientX) / rect.width) * count), count);
      return;
    }
    this.hovered.set(
      this.chartStyle() === 'candles'
        ? Math.min(count - 1, Math.floor(share * count))
        : Math.round(share * (count - 1)),
    );
  }
}

/** A day's high and low, falling back to the close for a bar stored without them. */
function high(point: PriceHistoryPoint): number {
  return Number(point.high ?? point.close);
}
function low(point: PriceHistoryPoint): number {
  return Number(point.low ?? point.close);
}

/** A price the feed actually quoted: outside market hours a side can be missing or zero. */
function positive(value: number | undefined): number | null {
  return value != null && Number(value) > 0 ? Number(value) : null;
}
