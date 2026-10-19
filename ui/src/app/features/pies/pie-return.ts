import { formatNumber } from '@angular/common';

export const WINDOWS = ['7D', '30D', '90D', 'ALL'] as const;
export type Window = (typeof WINDOWS)[number];

/** A return like +4.20%, or a dash when the pie has no return for the period yet. */
export function formatReturn(value: number | null | undefined): string {
  if (value == null) return '—';
  return `${value > 0 ? '+' : ''}${formatNumber(value, 'en-US', '1.2-2')}%`;
}

/** Colours a return green or red; zero and missing stay neutral. */
export function returnTone(value: number | null | undefined): 'gain' | 'loss' | null {
  if (!value) return null;
  return value > 0 ? 'gain' : 'loss';
}
