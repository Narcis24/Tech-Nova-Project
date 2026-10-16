/** Date-only trading sessions must not shift when DatePipe applies a timezone. */
export function sessionDate(value: string | null | undefined): string | null {
  return value ? `${value}T00:00:00Z` : null;
}
