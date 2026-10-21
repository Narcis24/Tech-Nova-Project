import { Component, computed, input, signal } from '@angular/core';

@Component({
  selector: 'app-symbol-icon',
  templateUrl: './symbol-icon.component.html',
  styleUrl: './symbol-icon.component.css',
})
export class SymbolIconComponent {
  readonly symbol = input.required<string | undefined>();
  readonly failedSymbol = signal<string | undefined>(undefined);
  readonly showImage = computed(() => !!this.symbol() && this.failedSymbol() !== this.symbol());
  readonly initials = computed(() => (this.symbol() ?? '').slice(0, 2));
  readonly url = computed(() => `https://financialmodelingprep.com/image-stock/${this.symbol()}.png`);
}
