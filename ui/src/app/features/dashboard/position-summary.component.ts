import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../generated/trading-api-client';
import { ActionButtonsComponent } from './action-buttons.component';
@Component({
  selector: 'app-position-summary',
  imports: [DecimalPipe, PercentPipe, RouterLink, ActionButtonsComponent],
  templateUrl: './position-summary.component.html',
  styleUrl: './position-summary.component.css',
})
export class PositionSummaryComponent {
  readonly positions = input.required<PositionResponse[]>();
  readonly instruments = input.required<Map<string, { name?: string }>>();
  readonly pendingSells = input(new Set<string>());
  readonly close = output<PositionResponse>();
  positionReturn(p: PositionResponse): number | null {
    const cost = Number(p.quantity) * Number(p.averageCost);
    return cost > 0 && p.unrealizedPnL != null ? Number(p.unrealizedPnL) / cost : null;
  }
}
