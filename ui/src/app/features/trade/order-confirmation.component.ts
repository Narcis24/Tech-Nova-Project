import { CurrencyPipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { AfterViewInit, Component, computed, ElementRef, input, OnDestroy, output, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { isMarketOpen } from '../../core/market-hours';
import { timeInForceLabel } from '../../core/time-in-force';
import { OrderResponse, PlaceOrderRequest, RiskWarning } from '../../generated/trading-api-client';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';

@Component({
  selector: 'app-order-confirmation',
  imports: [CurrencyPipe, DecimalPipe, TitleCasePipe, RouterLink, SymbolIconComponent],
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
  // From the risk check run before the dialog opened; they never block the order
  readonly warnings = input<RiskWarning[]>([]);
  readonly confirmOrder = output<void>();
  readonly dismiss = output<void>();
  // Checked when the dialog opens
  readonly marketOpen = isMarketOpen();
  readonly goodFor = computed(() => timeInForceLabel(this.order().timeInForce, this.order().expiresOn));
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
    this.dialog().nativeElement.querySelector<HTMLElement>('h2')?.focus({ preventScroll: true });
  }
  ngOnDestroy(): void {
    this.dialog().nativeElement.close();
  }
  cancel(event: Event): void {
    event.preventDefault();
    this.close();
  }
  close(): void {
    if (this.submitting()) return;
    this.dialog().nativeElement.close();
    this.dismiss.emit();
  }
}
