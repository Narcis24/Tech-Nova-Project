import { CurrencyPipe, DecimalPipe } from '@angular/common';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, exhaustMap, map, of, Subject, startWith, switchMap } from 'rxjs';
import {
  InstrumentControllerService,
  InstrumentResponse,
  OrderControllerService,
  OrderResponse,
  PlaceOrderRequest,
  PortfolioControllerService,
  PortfolioSnapshotResponse,
  RiskControllerService,
  RiskWarning,
} from '../../generated/trading-api-client';
import { PriceChartComponent, TradeQuote } from '../../features/trade/price-chart.component';
import { isRejection, orderError } from '../../features/trade/order-error';
import { OrderConfirmationComponent } from '../../features/trade/order-confirmation.component';
import { newUuid } from '../../core/uuid';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-order-form',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    PriceChartComponent,
    OrderConfirmationComponent,
  ],
  templateUrl: './order-form.component.html',
  styleUrl: './order-form.component.css',
})
export class OrderFormComponent {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly accounts = inject(AccountService);
  private readonly api = inject(OrderControllerService);
  private readonly riskApi = inject(RiskControllerService);
  private readonly portfolioApi = inject(PortfolioControllerService);
  private readonly instrumentsApi = inject(InstrumentControllerService);
  private readonly route = inject(ActivatedRoute);
  readonly accountId = this.accounts.currentAccountId;
  readonly instruments = signal<InstrumentResponse[]>([]);
  readonly instrumentError = signal(false);
  readonly instrumentsLoading = signal(true);
  readonly pickerOpen = signal(false);
  readonly submitting = signal(false);
  readonly checking = signal(false);
  readonly warnings = signal<RiskWarning[]>([]);
  readonly error = signal<string | null>(null);
  readonly result = signal<OrderResponse | null>(null);
  readonly review = signal<PlaceOrderRequest | null>(null);
  readonly confirmationOrder = signal<PlaceOrderRequest | null>(null);
  readonly confirmationTotal = signal<number | null>(null);
  readonly confirmationCashAfter = signal<number | null>(null);
  private readonly submissions = new Subject<PlaceOrderRequest>();
  private readonly checks = new Subject<PlaceOrderRequest>();
  private lastFingerprint = '';
  private key = '';
  readonly presets = [10, 25, 50, 100];
  readonly form = inject(FormBuilder).group({
    symbol: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z0-9.-]{1,10}$/)],
    }),
    side: new FormControl('BUY', { nonNullable: true, validators: [Validators.pattern(/^(BUY|SELL)$/)] }),
    quantity: new FormControl<number | null>(25, [
      Validators.required,
      Validators.min(1),
      Validators.max(100000),
      Validators.pattern(/^\d+$/),
    ]),
    orderType: new FormControl('MARKET', { nonNullable: true, validators: [Validators.pattern(/^(MARKET|LIMIT)$/)] }),
    price: new FormControl<number | null>(null),
  });
  readonly values = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())));
  readonly chartSymbol = computed(() => this.values()?.symbol ?? '');
  readonly instrumentName = computed(() => this.instruments().find((i) => i.symbol === this.chartSymbol())?.name ?? '');
  readonly isBuy = computed(() => this.values()?.side !== 'SELL');
  readonly isLimit = computed(() => this.values()?.orderType === 'LIMIT');
  readonly quote = signal<TradeQuote | null>(null);
  readonly referencePrice = computed(() =>
    this.quote()?.symbol === this.chartSymbol() ? (this.quote()?.price ?? null) : null,
  );
  private readonly contextRevision = signal(0);
  private readonly contextRequest = computed(() => ({ id: this.accountId(), revision: this.contextRevision() }));
  readonly context = signal<{
    id: string | null;
    loading: boolean;
    error: boolean;
    portfolio: PortfolioSnapshotResponse | null;
  }>({ id: null, loading: false, error: false, portfolio: null });
  readonly portfolio = computed(() => (this.context().id === this.accountId() ? this.context().portfolio : null));
  readonly cash = computed(() => this.portfolio()?.cashBalance ?? null);
  readonly held = computed(() =>
    this.portfolio()
      ? Number(this.portfolio()!.positions?.find((p) => p.symbol === this.chartSymbol())?.quantity ?? 0)
      : null,
  );
  readonly price = computed(() => (this.isLimit() ? (this.values()?.price ?? null) : this.referencePrice()));
  readonly total = computed(() => {
    const quantity = this.values()?.quantity,
      price = this.price();
    return quantity != null &&
      quantity > 0 &&
      Number.isFinite(quantity) &&
      price != null &&
      price > 0 &&
      Number.isFinite(price)
      ? quantity * price
      : null;
  });
  readonly cashAfter = computed(() =>
    this.cash() != null && this.total() != null
      ? Number(this.cash()) + (this.isBuy() ? -this.total()! : this.total()!)
      : null,
  );
  readonly ready = computed(() => this.portfolio() != null && (this.isLimit() || this.referencePrice() != null));
  readonly maxShares = computed(() =>
    this.cash() != null && this.price() != null && this.price()! > 0
      ? Math.min(100000, Math.floor(Number(this.cash()) / this.price()!))
      : null,
  );
  readonly validationIssue = computed(() => {
    const quantity = this.values()?.quantity;
    if (!this.isBuy() && quantity != null && this.held() != null && quantity > this.held()!)
      return `You hold ${this.held()} ${this.chartSymbol()}, so you can sell up to ${this.held()}.`;
    if (this.isBuy() && this.cashAfter() != null && this.cashAfter()! < 0)
      return 'The estimated cost exceeds available cash. Lower the quantity or deposit cash.';
    return null;
  });
  readonly marker = computed(() => {
    const reference = this.referencePrice(),
      target = this.price();
    if (reference == null || target == null) return 50;
    const low = Math.min(reference, target) * 0.995,
      high = Math.max(reference, target) * 1.005;
    return Math.max(25, Math.min(75, ((target - low) / (high - low)) * 100));
  });

  constructor() {
    this.form.controls.orderType.valueChanges
      .pipe(startWith(this.form.controls.orderType.value), takeUntilDestroyed())
      .subscribe((type) => {
        this.form.controls.price.setValidators(
          type === 'LIMIT' ? [Validators.required, Validators.min(0.01), Validators.pattern(/^\d+(\.\d{1,2})?$/)] : [],
        );
        this.form.controls.price.updateValueAndValidity();
      });
    // A limit price belongs to one instrument; carrying it to another symbol would submit a stale price.
    this.form.controls.symbol.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.form.controls.price.setValue(null));
    this.loadInstruments();
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const symbol = params.get('symbol')?.toUpperCase() || this.form.controls.symbol.value;
      this.form.patchValue({ symbol, side: params.get('side') === 'SELL' ? 'SELL' : 'BUY' });
      this.review.set(null);
      this.result.set(null);
    });
    toObservable(this.contextRequest)
      .pipe(
        switchMap(({ id }) => {
          if (!id) return of({ id, loading: false, error: false, portfolio: null });
          return this.portfolioApi.getPortfolioSnapshot(id).pipe(
            map((portfolio) => ({ id, loading: false, error: false, portfolio })),
            catchError(() => of({ id, loading: false, error: true, portfolio: null })),
            startWith({ id, loading: true, error: false, portfolio: null }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((context) => this.context.set(context));
    effect(() => {
      this.accountId();
      this.review.set(null);
      this.warnings.set([]);
      this.result.set(null);
      this.error.set(null);
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.review.set(null);
      this.warnings.set([]);
      this.error.set(null);
      this.result.set(null);
    });
    // The risk check runs on Review, before the dialog opens. Warnings never block: the user can still place the order.
    this.checks
      .pipe(
        exhaustMap((order) =>
          this.riskApi.check(order).pipe(
            map((warnings) => ({ order, warnings })),
            catchError((error) => {
              if (isRejection(error)) {
                // The check refuses what /orders would refuse, so the order was never placed
                this.lastFingerprint = '';
                this.error.set(orderError(error));
              } else {
                this.error.set("Couldn't run the risk check. Try again.");
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((checked) => {
        this.checking.set(false);
        if (!checked || checked.order.accountId !== this.accountId()) return;
        this.warnings.set(checked.warnings);
        this.review.set(checked.order);
      });
    this.submissions
      .pipe(
        exhaustMap((request) =>
          this.api.placeOrder(request).pipe(
            catchError((error) => {
              // A definitive 4xx rejection must not replay the same key on retry; ambiguous failures keep it so a retry stays safe.
              if (isRejection(error)) this.lastFingerprint = '';
              this.error.set(orderError(error));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((order) => {
        this.submitting.set(false);
        if (order) {
          this.result.set(order);
          this.review.set(null);
          this.lastFingerprint = '';
          this.key = '';
          this.refreshContext();
        }
      });
  }
  loadInstruments(): void {
    this.instrumentError.set(false);
    this.instrumentsLoading.set(true);
    this.instrumentsApi
      .getInstruments(true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (assets) => {
          this.instruments.set(assets);
          this.instrumentsLoading.set(false);
          if (!this.form.controls.symbol.value && assets.length)
            this.form.controls.symbol.setValue(
              assets.find((i) => i.symbol === 'AAPL')?.symbol ?? assets[0].symbol ?? '',
            );
        },
        error: () => {
          this.instrumentsLoading.set(false);
          this.instrumentError.set(true);
        },
      });
  }
  refreshContext(): void {
    this.contextRevision.update((v) => v + 1);
  }
  onQuote(quote: TradeQuote): void {
    this.quote.set(quote);
    if (
      quote.symbol === this.chartSymbol() &&
      this.isLimit() &&
      this.form.controls.price.value == null &&
      quote.price != null
    )
      this.form.controls.price.setValue(Math.round(quote.price * 100) / 100);
  }
  toggleInstrumentPicker(): void {
    this.pickerOpen.update((open) => !open);
    if (this.pickerOpen())
      afterNextRender(() => this.element.nativeElement.querySelector<HTMLInputElement>('#trade-symbol')?.focus(), {
        injector: this.injector,
      });
  }
  normalizeSymbol(): void {
    this.form.controls.symbol.setValue(this.form.controls.symbol.value.trim().toUpperCase());
  }
  setSide(side: 'BUY' | 'SELL'): void {
    this.form.controls.side.setValue(side);
  }
  setOrderType(type: 'MARKET' | 'LIMIT'): void {
    this.form.controls.orderType.setValue(type);
    if (type === 'LIMIT' && this.form.controls.price.value == null && this.referencePrice() != null)
      this.form.controls.price.setValue(Math.round(this.referencePrice()! * 100) / 100);
  }
  stepLimit(direction: -1 | 1): void {
    const current = this.form.controls.price.value ?? this.referencePrice() ?? 0.05;
    this.form.controls.price.setValue(Math.max(0.05, Math.round((current + direction * 0.05) * 100) / 100));
  }
  prepare(): void {
    if (this.checking()) return;
    this.error.set(null);
    this.form.markAllAsTouched();
    const value = this.form.getRawValue(),
      id = this.accountId();
    if (this.form.invalid || !id || value.quantity == null || this.validationIssue() || !this.ready()) return;
    if (value.orderType === 'LIMIT' && (value.price == null || !Number.isFinite(value.price) || value.price <= 0)) {
      this.error.set('Enter a positive limit price.');
      return;
    }
    const request = {
      accountId: id,
      symbol: value.symbol,
      side: value.side,
      quantity: value.quantity,
      orderType: value.orderType,
      ...(value.orderType === 'LIMIT' ? { price: value.price! } : {}),
    };
    const fingerprint = JSON.stringify(request);
    if (fingerprint !== this.lastFingerprint) {
      this.lastFingerprint = fingerprint;
      this.key = newUuid();
    }
    this.confirmationOrder.set({ ...request, idempotencyKey: this.key });
    this.confirmationTotal.set(this.total());
    this.confirmationCashAfter.set(this.cashAfter());
    this.checking.set(true);
    this.checks.next(this.confirmationOrder()!);
  }
  dismissConfirmation(): void {
    this.review.set(null);
    this.warnings.set([]);
    this.result.set(null);
    this.error.set(null);
  }
  confirm(): void {
    const order = this.review();
    if (
      !order ||
      this.submitting() ||
      this.checking() ||
      order.accountId !== this.accountId() ||
      !this.ready() ||
      this.validationIssue() ||
      this.form.invalid
    )
      return;
    this.error.set(null);
    this.submitting.set(true);
    this.submissions.next(order);
  }
}
