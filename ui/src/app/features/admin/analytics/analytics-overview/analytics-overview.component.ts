import { Component, inject } from '@angular/core';

import { AnalyticsChartComponent } from '../analytics-chart/analytics-chart.component';
import { AnalyticsStore } from '../analytics.store';
import { MetricTabsComponent } from '../metric-tabs/metric-tabs.component';

/**
 * The headline figures as tabs and the chart of the chosen one, with the loading and error states for
 * the analytics request. Needs an AnalyticsStore provided above it.
 */
@Component({
  selector: 'app-analytics-overview',
  imports: [AnalyticsChartComponent, MetricTabsComponent],
  templateUrl: './analytics-overview.component.html',
  styleUrls: ['../../../../shared/admin-page.css', './analytics-overview.component.css'],
  host: { '[attr.aria-busy]': 'store.loading()' },
})
export class AnalyticsOverviewComponent {
  readonly store = inject(AnalyticsStore);
}
