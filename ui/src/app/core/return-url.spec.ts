import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps paths inside the app', () => {
    expect(safeReturnUrl('/dashboard')).toBe('/dashboard');
    expect(safeReturnUrl('/dashboard?tab=orders')).toBe('/dashboard?tab=orders');
    expect(safeReturnUrl('/orders/123')).toBe('/orders/123');
  });

  it('falls back to /dashboard for other sites', () => {
    expect(safeReturnUrl('https://evil.com')).toBe('/dashboard');
    expect(safeReturnUrl('//evil.com')).toBe('/dashboard');
    expect(safeReturnUrl('/\\evil.com')).toBe('/dashboard');
    expect(safeReturnUrl('javascript:alert(1)')).toBe('/dashboard');
  });

  it('falls back to /dashboard when missing', () => {
    expect(safeReturnUrl('')).toBe('/dashboard');
    expect(safeReturnUrl(null)).toBe('/dashboard');
    expect(safeReturnUrl(undefined)).toBe('/dashboard');
  });
});
