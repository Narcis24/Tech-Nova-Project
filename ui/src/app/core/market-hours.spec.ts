import { isMarketOpen } from './market-hours';

describe('isMarketOpen', () => {
  const open = (utc: string) => isMarketOpen(new Date(utc));

  it('is open on a weekday from 09:30 to before 16:00 New York time', () => {
    // October: New York is UTC-4
    expect(open('2026-10-14T13:30:00Z')).toBeTrue(); // Wed 09:30
    expect(open('2026-10-14T19:59:00Z')).toBeTrue(); // Wed 15:59
  });

  it('is closed before the open, from the close, and at the weekend', () => {
    expect(open('2026-10-14T13:29:00Z')).toBeFalse(); // Wed 09:29
    expect(open('2026-10-14T20:00:00Z')).toBeFalse(); // Wed 16:00
    expect(open('2026-10-16T07:24:00Z')).toBeFalse(); // Fri 03:24
    expect(open('2026-10-17T15:00:00Z')).toBeFalse(); // Sat 11:00
  });

  it('follows New York daylight saving', () => {
    // December: New York is UTC-5
    expect(open('2026-12-02T14:00:00Z')).toBeFalse(); // Wed 09:00
    expect(open('2026-12-02T14:30:00Z')).toBeTrue(); // Wed 09:30
  });
});
