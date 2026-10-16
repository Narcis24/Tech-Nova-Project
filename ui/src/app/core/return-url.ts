const DEFAULT_URL = '/dashboard';

/**
 * Returns returnUrl if it is a path inside this app, otherwise /dashboard.
 * Blocks open redirects: a crafted /login?returnUrl=https://evil.com link must not send users off-site after login.
 */
export function safeReturnUrl(returnUrl: string | null | undefined): string {
  if (!returnUrl || !returnUrl.startsWith('/') || returnUrl.startsWith('//') || returnUrl.startsWith('/\\')) {
    return DEFAULT_URL;
  }
  return returnUrl;
}
