import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../generated/trading-api-client';
@Component({
  selector: 'app-position-summary',
  imports: [DecimalPipe, PercentPipe, RouterLink],
  templateUrl: './position-summary.component.html',
  styleUrl: './position-summary.component.css',
})
export class PositionSummaryComponent {
  readonly positions = input.required<PositionResponse[]>();
  readonly instruments = input.required<Map<string, { name?: string }>>();
  readonly portfolioValue = input(0);
  readonly pendingSells = input(new Set<string>());
  readonly close = output<PositionResponse>();
  allocation(p: PositionResponse): number | null {
    return this.portfolioValue() > 0 ? Number(p.marketValue ?? 0) / this.portfolioValue() : null;
  }
  positionReturn(p: PositionResponse): number | null {
    const cost = Number(p.quantity) * Number(p.averageCost);
    return cost > 0 && p.unrealizedPnL != null ? Number(p.unrealizedPnL) / cost : null;
  }
}
