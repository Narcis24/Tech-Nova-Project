/** Shared by the admin analytics components: the filter values the API takes, and their labels. */

/** The values the API accepts. A typo here is a compile error, not a 400 at runtime. */
export type Period = 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_90_DAYS' | 'YEAR_TO_DATE';
export type AssetClass = 'EQUITY' | 'ETF' | 'BOND' | 'COMMODITY';
export type Side = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT';

/** What the card shows; null means "all" for every filter. */
export interface AnalyticsFilters {
  period: Period;
  assetClass: AssetClass | null;
  side: Side | null;
  orderType: OrderType | null;
  trader: string | null;
}

export const DEFAULT_PERIOD: Period = 'LAST_30_DAYS';

export interface Option<T extends string> {
  value: T;
  label: string;
}

export const PERIODS: readonly Option<Period>[] = [
  { value: 'TODAY', label: 'Today' },
  { value: 'LAST_7_DAYS', label: '7D' },
  { value: 'LAST_30_DAYS', label: '30D' },
  { value: 'LAST_90_DAYS', label: '90D' },
  { value: 'YEAR_TO_DATE', label: 'YTD' },
];

// Legend order: the colours were checked for colour-blind separation as neighbours in this order
export const ASSET_CLASSES: readonly (Option<AssetClass> & { color: string })[] = [
  { value: 'EQUITY', label: 'Equity', color: 'var(--asset-equity-color)' },
  { value: 'ETF', label: 'ETF', color: 'var(--asset-etf-color)' },
  { value: 'BOND', label: 'Bond', color: 'var(--asset-bond-color)' },
  { value: 'COMMODITY', label: 'Commodity', color: 'var(--asset-commodity-color)' },
];

export const SIDES: readonly Option<Side>[] = [
  { value: 'BUY', label: 'Buy' },
  { value: 'SELL', label: 'Sell' },
];

export const ORDER_TYPES: readonly Option<OrderType>[] = [
  { value: 'MARKET', label: 'Market' },
  { value: 'LIMIT', label: 'Limit' },
];

/** The value if it's one of the options, otherwise null; so a hand-edited URL can't break the card. */
export function oneOf<T extends string>(value: string | null, options: readonly Option<T>[]): T | null {
  return options.find((option) => option.value === value)?.value ?? null;
}
