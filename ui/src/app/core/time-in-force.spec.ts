import { timeInForceLabel } from './time-in-force';

describe('timeInForceLabel', () => {
  it('names each choice in plain words', () => {
    expect(timeInForceLabel('DAY')).toBe('Today');
    expect(timeInForceLabel('GTC')).toBe('Until cancelled');
    expect(timeInForceLabel('GTD', '2026-10-30')).toBe('Until Oct 30, 2026');
  });

  it('falls back to a day order, the default', () => {
    expect(timeInForceLabel(undefined)).toBe('Today');
  });
});
