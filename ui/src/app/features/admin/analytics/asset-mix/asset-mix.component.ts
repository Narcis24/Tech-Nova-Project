import { Component, computed, inject } from '@angular/core';

import { ASSET_CLASSES, formatMoney } from '../analytics';
import { AnalyticsStore } from '../analytics.store';

/**
 * How the period's filled value splits across asset classes: one bar in the asset class colours and a
 * legend with each share and value. Classes the asset class filter leaves out are dimmed, not hidden,
 * so the legend keeps its order. Needs an AnalyticsStore provided above it.
 */
@Component({
  selector: 'app-asset-mix',
  templateUrl: './asset-mix.component.html',
  styleUrls: ['../../../../shared/admin-page.css', './asset-mix.component.css'],
})
export class AssetMixComponent {
  readonly store = inject(AnalyticsStore);

  // Always in the legend's order (Equity, ETF, Bond, Commodity), whatever order the API sends
  readonly entries = computed(() => {
    const mix = this.store.data()?.assetMix ?? [];
    const filter = this.store.filters().assetClass;
    return ASSET_CLASSES.map((assetClass) => {
      const entry = mix.find((item) => item.assetClass === assetClass.value);
      const share = entry?.share ?? 0;
      return {
        key: assetClass.value,
        label: assetClass.label,
        color: assetClass.color,
        share,
        percent: `${Math.round(share * 100)}%`,
        value: formatMoney(entry?.tradedValue ?? 0),
        dimmed: filter !== null && filter !== assetClass.value,
      };
    });
  });

  readonly segments = computed(() => this.entries().filter((entry) => entry.share > 0));

  // The bar chart on wider screens: the biggest share fills the height and the others are scaled to it
  readonly columns = computed(() => {
    const biggest = Math.max(...this.entries().map((entry) => entry.share));
    return this.entries().map((entry) => ({
      ...entry,
      height: biggest > 0 ? Math.round((entry.share / biggest) * 1000) / 10 : 0,
    }));
  });

  readonly summary = computed(() =>
    this.segments()
      .map((entry) => `${entry.label} ${entry.percent}`)
      .join(', '),
  );
}
