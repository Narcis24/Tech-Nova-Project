import { randomBytes } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('container serves deep links and proxies login, account data and order review', async ({ page, request }) => {
  const username = `ui_${randomBytes(5).toString('hex')}`;
  const password = `Ui1!${randomBytes(8).toString('hex')}`;
  const registration = await request.post('/auth/v1/auth/register', {
    data: { username, password, email: `${username}@smoke.test` },
  });
  expect(registration.status()).toBe(201);
  const login = await request.post('/auth/v1/auth/login', { data: { username, password } });
  expect(login.ok()).toBeTruthy();
  const { accessToken } = await login.json();
  const headers = { Authorization: `Bearer ${accessToken}` };
  const account = await request.post('/api/v1/accounts', { headers, data: { holderName: 'UI Smoke Test' } });
  expect(account.status()).toBe(201);
  const { accountId } = await account.json();
  const deposit = await request.post(`/api/v1/accounts/${accountId}/deposit`, { headers, data: { amount: 10000 } });
  expect(deposit.ok()).toBeTruthy();

  const pageErrors: string[] = [];
  const placements: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/orders')
      placements.push(request.url());
  });
  // A fresh navigation exercises Nginx's SPA fallback, then the auth guard.
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Portfolio', exact: true })).toBeVisible();
  await expect(page.locator('app-balance-card')).toContainText('$10,000.00');
  await page.goto('/trade?symbol=AAPL&side=BUY');
  await page.getByRole('group', { name: 'Order type', exact: true }).getByRole('button', { name: /Limit/ }).click();
  const quantity = page.locator('#trade-quantity');
  const price = page.locator('#trade-price');
  const submit = page.locator('fieldset > .submit-order');
  await quantity.fill('1.5');
  await expect(submit).toBeDisabled();
  await expect(page.locator('#quantity-error')).toContainText('whole shares');
  await quantity.fill('1');
  await price.fill('1.234');
  await expect(submit).toBeDisabled();
  await expect(page.locator('#price-error')).toContainText('2 decimal places');
  await price.fill('1.00');
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog')).toContainText('Buy 1 AAPL');
  await page.getByRole('button', { name: 'Edit order', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(quantity).toHaveValue('1');
  await expect(price).toHaveValue('1.00');
  expect(placements).toEqual([]);
  expect(pageErrors).toEqual([]);
});
