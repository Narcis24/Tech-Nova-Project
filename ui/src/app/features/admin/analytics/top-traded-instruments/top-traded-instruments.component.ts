import { Component, computed, inject } from '@angular/core';

import { ASSET_CLASSES, formatMoney } from '../analytics';
import { AnalyticsStore } from '../analytics.store';

/**
 * The period's most traded instruments by filled value, after the card's filters: a ranked list with
 * ticker tiles in their asset class's colour and a bar split into buys and sells. Ranked by what
 * TechNova's traders traded, not by price move (that's the market overview's gainers and losers).
 * Needs an AnalyticsStore provided above it.
 */
@Component({
  selector: 'app-top-traded-instruments',
  templateUrl: './top-traded-instruments.component.html',
  styleUrls: ['../../../../shared/admin-page.css', './top-traded-instruments.component.css'],
})
export class TopTradedInstrumentsComponent {
  readonly store = inject(AnalyticsStore);

  readonly rows = computed(() => {
    const instruments = this.store.data()?.topTradedInstruments ?? [];
    // Bars are scaled to the top instrument, so the first always fills its track
    const top = Math.max(1, ...instruments.map((instrument) => instrument.tradedValue));
    return instruments.map((instrument, index) => {
      const buyShare = instrument.tradedValue ? instrument.buyValue / instrument.tradedValue : 0;
      const width = (instrument.tradedValue / top) * 100;
      return {
        ...instrument,
        rank: index + 1,
        value: formatMoney(instrument.tradedValue),
        color: ASSET_CLASSES.find((assetClass) => assetClass.value === instrument.assetClass)?.color ?? 'currentColor',
        // To a tenth of a percent, so 0.67 × 100 doesn't reach the page as 32.99999999999999%
        buyWidth: Math.round(width * buyShare * 10) / 10,
        sellWidth: Math.round(width * (1 - buyShare) * 10) / 10,
        split: `${Math.round(buyShare * 100)}% buy, ${Math.round((1 - buyShare) * 100)}% sell`,
      };
    });
  });
}
