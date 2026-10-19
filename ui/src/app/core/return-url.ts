const DEFAULT_URL = '/dashboard';

/**
 * Returns returnUrl if it is a path inside this app, otherwise fallback (the user's home page; /dashboard by default).
 * Blocks open redirects: a crafted /login?returnUrl=https://evil.com link must not send users off-site after login.
 */
export function safeReturnUrl(returnUrl: string | null | undefined, fallback = DEFAULT_URL): string {
  if (!returnUrl || !returnUrl.startsWith('/') || returnUrl.startsWith('//') || returnUrl.startsWith('/\\')) {
    return fallback;
  }
  return returnUrl;
}
