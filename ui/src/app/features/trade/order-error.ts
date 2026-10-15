import { HttpErrorResponse } from '@angular/common/http';

/** Show known API rejections; keep ambiguous network/server failures retry-safe. */
export function orderError(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500) {
    const message = error.error?.message;
    if (typeof message === 'string' && message.trim()) return message;
    if (error.status === 401) return 'Your session has expired. Sign in again before placing an order.';
    if (error.status === 403) return 'You do not have access to trade on this account.';
    if (error.status === 429) return 'Too many requests. Wait a moment and retry this order.';
    return 'The order was rejected. Check the instrument, quantity and available cash.';
  }
  return "Couldn't confirm submission. Retry without changing the order to avoid duplicates.";
}
