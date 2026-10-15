import { CurrencyPipe, DecimalPipe, DOCUMENT, TitleCasePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, exhaustMap, filter, map, merge, of, startWith, switchMap, timer } from 'rxjs';
import { OrderControllerService, OrderResponse } from '../../generated/trading-api-client';

interface ResultState { id: string; order: OrderResponse | null; loading: boolean; error: boolean; }
@Component({
  selector: 'app-order-result',
  imports: [CurrencyPipe, DecimalPipe, TitleCasePipe, RouterLink],
  templateUrl: './order-result.component.html',
  styleUrl: './order-result.component.css',
})
export class OrderResultComponent {
  private readonly api = inject(OrderControllerService);
  private readonly document = inject(DOCUMENT);
  private readonly route = inject(ActivatedRoute);
  private readonly id = toSignal(this.route.paramMap.pipe(map(params => params.get('orderId') ?? '')), { initialValue: '' });
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ id: this.id(), revision: this.revision() }));
  readonly state = signal<ResultState>({ id: '', order: null, loading: true, error: false });
  readonly order = computed(() => this.state().id === this.id() ? this.state().order : null);
  readonly terminal = computed(() => ['FILLED','REJECTED','CANCELLED','EXPIRED'].includes(this.order()?.status ?? ''));
  readonly value = computed(() => this.order()?.quantity != null && this.order()?.price != null ? this.order()!.quantity! * this.order()!.price! : null);

  constructor() {
    toObservable(this.request).pipe(switchMap(({ id }) => {
      if (!id) return of({ id, order: null, loading: false, error: true });
      return merge(of(0), timer(15_000, 15_000).pipe(filter(() => !this.terminal() && this.document.visibilityState !== 'hidden'))).pipe(
        exhaustMap(() => {
          const previous = this.state().id === id ? this.state().order : null;
          return this.api.getOrder(id).pipe(
            map(order => ({ id, order, loading: false, error: false })),
            catchError(() => of({ id, order: previous, loading: false, error: true })),
            startWith({ id, order: previous, loading: true, error: false }),
          );
        }),
      );
    }), takeUntilDestroyed()).subscribe(state => this.state.set(state));
  }
  refresh(): void { this.revision.update(value => value + 1); }
}
