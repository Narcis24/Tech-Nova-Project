/** How long a limit order stays open, as the API takes it (TimeInForce.java). */
export type TimeInForce = 'DAY' | 'GTC' | 'GTD';

/** The longest a limit order stays open, in days: GTC orders and the furthest GTD date. */
export const MAX_DAYS_OPEN = 90;

export const TIME_IN_FORCE_OPTIONS: { value: TimeInForce; label: string; hint: string }[] = [
  { value: 'DAY', label: 'Today', hint: 'Expires at the next market close' },
  { value: 'GTC', label: 'Until cancelled', hint: `Up to ${MAX_DAYS_OPEN} days` },
  { value: 'GTD', label: 'Until a date', hint: 'Expires at the close on that date' },
];

/** How long an order is good for, in words: "Today", "Until cancelled" or "Until Oct 30, 2026". */
export function timeInForceLabel(timeInForce?: string | null, expiresOn?: string | null): string {
  if (timeInForce === 'GTD' && expiresOn) {
    const date = new Date(`${expiresOn}T12:00:00Z`).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      timeZone: 'UTC',
    });
    return `Until ${date}`;
  }
  return TIME_IN_FORCE_OPTIONS.find((option) => option.value === timeInForce)?.label ?? 'Today';
}
