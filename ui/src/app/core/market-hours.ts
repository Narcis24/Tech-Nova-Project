const NEW_YORK_TIME = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/**
 * The same hours as the execution engine (MarketHours.java): Monday to Friday, 09:30 to 16:00
 * New York time, holidays not handled. Orders placed outside them wait for the open.
 */
export function isMarketOpen(now: Date = new Date()): boolean {
  const parts = NEW_YORK_TIME.formatToParts(now);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  if (part('weekday') === 'Sat' || part('weekday') === 'Sun') {
    return false;
  }
  const minutes = Number(part('hour')) * 60 + Number(part('minute'));
  return minutes >= 9 * 60 + 30 && minutes < 16 * 60;
}

const NEW_YORK_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/New_York',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** The New York date, as YYYY-MM-DD, some days after now: the calendar limit orders expire on. */
export function newYorkDate(now: Date = new Date(), plusDays = 0): string {
  const date = new Date(`${NEW_YORK_DATE.format(now)}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + plusDays);
  return date.toISOString().slice(0, 10);
}
