import { Component, computed, input } from '@angular/core';

import { IndexPoint } from '../../generated/trading-api-client';
import { PriceChartComponent } from '../trade/price-chart.component';

@Component({
  selector: 'app-index-chart',
  imports: [PriceChartComponent],
  templateUrl: './index-chart.component.html',
  styleUrl: './index-chart.component.css',
})
export class IndexChartComponent {
  readonly points = input<IndexPoint[]>([]);
  readonly history = computed(() => this.points().map((point) => ({ date: point.date, close: point.value })));
}
