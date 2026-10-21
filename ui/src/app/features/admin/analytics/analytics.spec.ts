import { ASSET_CLASSES, change, drawnBuckets, METRICS, oneOf, PERIODS } from './analytics';

const [tradedValue, orders, fillRate] = METRICS;

describe('analytics helpers', () => {
  describe('oneOf', () => {
    it('keeps a value that is one of the options', () => {
      expect(oneOf('ETF', ASSET_CLASSES)).toBe('ETF');
      expect(oneOf('LAST_7_DAYS', PERIODS)).toBe('LAST_7_DAYS');
    });

    it('turns anything else into null, so a hand-edited URL is ignored', () => {
      expect(oneOf('etf', ASSET_CLASSES)).toBeNull();
      expect(oneOf('FOREVER', PERIODS)).toBeNull();
      expect(oneOf(null, PERIODS)).toBeNull();
    });
  });

  describe('metric formats', () => {
    it('shows money compactly, counts with separators and rates as percentages', () => {
      expect(tradedValue.format(7588936.61)).toBe('$7.59M');
      expect(tradedValue.format(3550)).toBe('$3.55K');
      expect(orders.format(3940)).toBe('3,940');
      expect(fillRate.format(0.819)).toBe('81.9%');
    });
  });

  describe('change', () => {
    it('compares counts and money as a percentage of the previous period', () => {
      expect(change(tradedValue, 7588936, 6830000)).toEqual({ direction: 'up', text: '▲ 11%' });
      expect(change(orders, 50, 100)).toEqual({ direction: 'down', text: '▼ 50%' });
    });

    it('compares rates in percentage points', () => {
      expect(change(fillRate, 0.819, 0.813)).toEqual({ direction: 'up', text: '▲ 0.6 pts' });
      expect(change(fillRate, 0.7, 0.75)).toEqual({ direction: 'down', text: '▼ 5.0 pts' });
    });

    it('says "No change" when the rounded change is zero', () => {
      expect(change(orders, 1000, 1001)).toEqual({ direction: 'flat', text: 'No change' });
      expect(change(fillRate, 0.8001, 0.8)).toEqual({ direction: 'flat', text: 'No change' });
    });

    it('says "New" rather than an infinite rise when the previous period had none', () => {
      expect(change(orders, 5, 0)).toEqual({ direction: 'up', text: 'New' });
      expect(change(orders, 0, 0)).toEqual({ direction: 'flat', text: 'No change' });
    });

    it('has nothing to say when either side is missing', () => {
      expect(change(orders, 5, undefined)).toBeNull(); // year to date: no previous period
      expect(change(fillRate, null, 0.8)).toBeNull(); // nothing finished yet
    });
  });

  describe('drawnBuckets', () => {
    // Times are UTC; New York is 4 hours behind in October (EDT)
    const bucket = (start: string, future = false) => ({ start, future });

    it('leaves weekends out of daily charts, by New York date', () => {
      const days = [
        bucket('2026-10-16T04:00:00Z'), // Fri
        bucket('2026-10-17T04:00:00Z'), // Sat
        bucket('2026-10-18T04:00:00Z'), // Sun
        bucket('2026-10-19T04:00:00Z'), // Mon
      ];

      expect(drawnBuckets(days, 'DAY').map((b) => b.start)).toEqual(['2026-10-16T04:00:00Z', '2026-10-19T04:00:00Z']);
    });

    it('keeps 9:00 to the 15:00 hour, New York time, for Today', () => {
      const hours = Array.from({ length: 24 }, (_, h) => bucket(new Date(Date.UTC(2026, 9, 20, 4 + h)).toISOString()));

      const kept = drawnBuckets(hours, 'HOUR');

      expect(kept).toHaveSize(7);
      expect(kept[0].start).toBe('2026-10-20T13:00:00.000Z'); // 09:00 in New York
      expect(kept[6].start).toBe('2026-10-20T19:00:00.000Z'); // 15:00
    });

    it('keeps every weekly bucket', () => {
      const weeks = [bucket('2026-10-12T04:00:00Z'), bucket('2026-10-19T04:00:00Z')];

      expect(drawnBuckets(weeks, 'WEEK')).toEqual(weeks);
    });
  });
});
