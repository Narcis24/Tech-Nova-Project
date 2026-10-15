import { CurrencyPipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { AfterViewInit, Component, ElementRef, input, OnDestroy, output, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderResponse, PlaceOrderRequest } from '../../generated/trading-api-client';

@Component({
  selector: 'app-order-confirmation',
  imports: [CurrencyPipe, DecimalPipe, TitleCasePipe, RouterLink],
  templateUrl: './order-confirmation.component.html',
  styleUrl: './order-confirmation.component.css',
})
export class OrderConfirmationComponent implements AfterViewInit, OnDestroy {
  readonly order = input.required<PlaceOrderRequest>();
  readonly total = input<number | null>(null);
  readonly cashAfter = input<number | null>(null);
  readonly result = input<OrderResponse | null>(null);
  readonly submitting = input(false);
  readonly error = input<string | null>(null);
  readonly confirmOrder = output<void>();
  readonly dismiss = output<void>();
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
    this.dialog().nativeElement.querySelector<HTMLElement>('h2')?.focus({ preventScroll: true });
  }
  ngOnDestroy(): void { this.dialog().nativeElement.close(); }
  cancel(event: Event): void { event.preventDefault(); this.close(); }
  close(): void {
    if (this.submitting()) return;
    this.dialog().nativeElement.close();
    this.dismiss.emit();
  }
}
