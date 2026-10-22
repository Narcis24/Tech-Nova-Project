import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';

import { ASSET_CLASSES, ORDER_TYPES, PERIODS, SIDES } from '../analytics';
import { AnalyticsStore } from '../analytics.store';
import { ChipOption, FilterChipComponent } from '../filter-chip/filter-chip.component';

/** The analytics heading, period buttons, filter chips and CSV export. Needs an AnalyticsStore provided above it. */
@Component({
  selector: 'app-analytics-filters',
  imports: [DatePipe, FilterChipComponent],
  templateUrl: './analytics-filters.component.html',
  styleUrls: ['../../../../shared/admin-page.css', './analytics-filters.component.css'],
})
export class AnalyticsFiltersComponent {
  readonly store = inject(AnalyticsStore);

  readonly periods = PERIODS;
  readonly assetClasses = ASSET_CLASSES;
  readonly sides = SIDES;
  readonly orderTypes = ORDER_TYPES;

  readonly traderOptions = computed<ChipOption[]>(() =>
    this.store.traders().map((trader) => ({ value: trader, label: trader })),
  );
}
