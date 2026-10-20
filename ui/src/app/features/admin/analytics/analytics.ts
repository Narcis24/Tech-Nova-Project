/** Shared by the admin analytics components: filter values and labels, metrics, and which chart points to draw. */

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

// --- Metrics

export type MetricKey = 'tradedValue' | 'orders' | 'fillRate' | 'activeTraders';

const MONEY = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  // Currencies default to two decimals, which would show $200.00 next to $7.59M
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});
const COUNT = new Intl.NumberFormat('en-US');

/** Money as the analytics show it everywhere: $200, $3.55K, $7.59M. */
export function formatMoney(value: number): string {
  return MONEY.format(value);
}

export interface Metric {
  key: MetricKey;
  label: string;
  // A CSS colour variable from styles.css; each tab and its chart line share it
  color: string;
  format: (value: number) => string;
  // A rate compares in percentage points ("▲ 1.2 pts"), not as a % of itself
  isRate: boolean;
}

// Tab order; the colours were checked as neighbours in this order (see styles.css)
export const METRICS: readonly Metric[] = [
  {
    key: 'tradedValue',
    label: 'Traded value',
    color: 'var(--metric-traded-value-color)',
    format: formatMoney,
    isRate: false,
  },
  {
    key: 'orders',
    label: 'Orders placed',
    color: 'var(--metric-orders-color)',
    format: (v) => COUNT.format(v),
    isRate: false,
  },
  {
    key: 'fillRate',
    label: 'Fill rate',
    color: 'var(--metric-fill-rate-color)',
    format: (v) => `${(v * 100).toFixed(1)}%`,
    isRate: true,
  },
  {
    key: 'activeTraders',
    label: 'Active traders',
    color: 'var(--metric-active-traders-color)',
    format: (v) => COUNT.format(v),
    isRate: false,
  },
];

export interface Change {
  direction: 'up' | 'down' | 'flat';
  text: string;
}

/** How a figure moved since the previous period, or null when one side is missing (YTD, or no fill rate). */
export function change(
  metric: Metric,
  current: number | null | undefined,
  previous: number | null | undefined,
): Change | null {
  if (current == null || previous == null) {
    return null;
  }
  if (metric.isRate) {
    const points = Math.round((current - previous) * 1000) / 10;
    return points === 0
      ? { direction: 'flat', text: 'No change' }
      : arrow(points, `${Math.abs(points).toFixed(1)} pts`);
  }
  if (previous === 0) {
    return current === 0 ? { direction: 'flat', text: 'No change' } : { direction: 'up', text: 'New' };
  }
  const percent = Math.round(((current - previous) / previous) * 100);
  return percent === 0 ? { direction: 'flat', text: 'No change' } : arrow(percent, `${Math.abs(percent)}%`);
}

function arrow(amount: number, text: string): Change {
  return amount > 0 ? { direction: 'up', text: `▲ ${text}` } : { direction: 'down', text: `▼ ${text}` };
}

// --- Chart points

// Market days and hours are New York time, as the API groups them
const NEW_YORK = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
  hour: 'numeric',
  hourCycle: 'h23',
});
const MARKET_OPEN_HOUR = 9;
const MARKET_CLOSE_HOUR = 16;

export interface DrawnBucket {
  start: string;
  future: boolean;
}

/**
 * The buckets worth drawing: daily charts leave out weekends, when nothing trades, and Today keeps
 * market hours, so lines don't dip to zero every night and weekend. Weekly buckets are all kept.
 */
export function drawnBuckets<T extends DrawnBucket>(buckets: readonly T[], bucketSize: string): T[] {
  return buckets.filter((bucket) => {
    if (bucketSize === 'WEEK') {
      return true;
    }
    const parts = NEW_YORK.formatToParts(new Date(bucket.start));
    const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
    if (bucketSize === 'DAY') {
      return part('weekday') !== 'Sat' && part('weekday') !== 'Sun';
    }
    const hour = Number(part('hour'));
    return hour >= MARKET_OPEN_HOUR && hour < MARKET_CLOSE_HOUR;
  });
}
