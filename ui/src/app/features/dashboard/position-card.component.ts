import { CurrencyPipe, DecimalPipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { PositionResponse } from '../../generated/trading-api-client';
import { ActionButtonsComponent } from './action-buttons.component';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';
@Component({
  selector: 'app-position-card',
  imports: [CurrencyPipe, DecimalPipe, PercentPipe, TitleCasePipe, ActionButtonsComponent, SymbolIconComponent],
  templateUrl: './position-card.component.html',
  styleUrl: './position-card.component.css',
})
export class PositionCardComponent {
  readonly position = input.required<PositionResponse>();
  readonly allocation = input<number | null>(null);
  readonly assetClass = input<string>();
  readonly closeDisabled = input(false);
  readonly closePosition = output<PositionResponse>();
}
