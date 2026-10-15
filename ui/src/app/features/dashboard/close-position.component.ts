import { CurrencyPipe } from '@angular/common';
import { AfterViewInit, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, exhaustMap, catchError, of } from 'rxjs';
import { newUuid } from '../../core/uuid';
import { OrderControllerService, PositionResponse } from '../../generated/trading-api-client';
@Component({
  selector: 'app-close-position',
  imports: [CurrencyPipe],
  templateUrl: './close-position.component.html',
  styleUrl: './close-position.component.css',
})
export class ClosePositionComponent implements AfterViewInit {
  private readonly api = inject(OrderControllerService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly accountId = input.required<string>();
  readonly position = input.required<PositionResponse>();
  readonly dismiss = output<void>();
  readonly completed = output<string>();
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  private key = newUuid();
  private readonly submissions = new Subject<void>();
  constructor() {
    this.submissions
      .pipe(
        exhaustMap(() =>
          this.api
            .placeOrder({
              accountId: this.accountId(),
              symbol: this.position().symbol!,
              side: 'SELL',
              orderType: 'MARKET',
              quantity: Number(this.position().quantity),
              idempotencyKey: this.key,
            })
            .pipe(
              catchError((error) => {
                if (error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500)
                  this.key = newUuid();
                this.error.set(
                  "Couldn't confirm submission. Try again; retrying uses the same order ID to prevent duplicates.",
                );
                return of(null);
              }),
            ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((order) => {
        this.submitting.set(false);
        if (!order) return;
        if (['REJECTED', 'CANCELLED', 'EXPIRED'].includes(order.status ?? '')) {
          this.key = newUuid();
          this.error.set(order.message ?? `Order ${order.status?.toLowerCase()}. No position was closed.`);
          return;
        }
        this.completed.emit(
          order.status === 'FILLED'
            ? `${order.symbol} sell order filled.`
            : `${order.symbol} sell order submitted (${order.status ?? 'pending'}). Awaiting execution.`,
        );
      });
  }
  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
  }
  onCancel(event: Event): void {
    if (this.submitting()) event.preventDefault();
  }
  confirm(): void {
    if (this.submitting()) return;
    if (
      !this.position().symbol ||
      !Number.isInteger(Number(this.position().quantity)) ||
      Number(this.position().quantity) < 1 ||
      Number(this.position().quantity) > 100000
    ) {
      this.error.set('This position cannot be closed with a single order. Use Sell to choose a quantity.');
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    this.submissions.next();
  }
}
