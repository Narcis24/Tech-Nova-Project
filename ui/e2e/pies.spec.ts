import { randomBytes } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('creates, edits, links and previews a pie without placing orders', async ({ page, request }) => {
  const username = `pie_${randomBytes(5).toString('hex')}`;
  const password = `Pi1!${randomBytes(8).toString('hex')}`;
  expect(
    (
      await request.post('/auth/v1/auth/register', {
        data: { username, password, email: `${username}@smoke.test` },
      })
    ).status(),
  ).toBe(201);
  const login = await request.post('/auth/v1/auth/login', { data: { username, password } });
  expect(login.ok()).toBeTruthy();
  const { accessToken } = await login.json();
  const headers = { Authorization: `Bearer ${accessToken}` };
  const account = await request.post('/api/v1/accounts', { headers, data: { holderName: 'Pie UI Test' } });
  expect(account.status()).toBe(201);
  const { accountId } = await account.json();
  expect(
    (await request.post(`/api/v1/accounts/${accountId}/deposit`, { headers, data: { amount: 10000 } })).ok(),
  ).toBeTruthy();
  const errors: string[] = [];
  const placements: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (req) => {
    if (req.method() === 'POST' && new URL(req.url()).pathname === '/api/v1/orders') placements.push(req.url());
  });

  await page.goto('/strategies');
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Strategies', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'New strategy' }).click();
  await page.getByLabel('Name', { exact: true }).fill('My test pie');
  await page.getByLabel('Stock 1', { exact: true }).fill('AAPL');
  await page.getByLabel('Weight 1', { exact: true }).fill('50');
  await page.getByRole('button', { name: 'Add stock' }).click();
  await page.getByLabel('Stock 2', { exact: true }).fill('AAPL');
  await page.getByLabel('Weight 2', { exact: true }).fill('30');
  await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  await page.getByLabel('Stock 2', { exact: true }).fill('MSFT');
  await page.getByLabel('Weight 2', { exact: true }).fill('60');
  await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  await page.getByLabel('Weight 2', { exact: true }).fill('30');
  await expect(page.locator('.cash-line')).toHaveText('Cash: 20%');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.locator('.pie-card')).toContainText('My test pie');
  await page.getByRole('link', { name: 'Edit', exact: true }).click();
  await expect(page.getByLabel('Weight 1', { exact: true })).toHaveValue('50');
  await page.getByLabel('Name', { exact: true }).fill('Edited pie');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.locator('.pie-card')).toContainText('Edited pie');
  await page.getByRole('button', { name: 'Use for this account' }).click();
  await expect(page.getByRole('heading', { name: 'Rebalance preview' })).toBeVisible();
  await expect(page.locator('.summary')).toContainText('$10,000.00');
  await expect(page.locator('.summary')).toContainText('20%');
  await expect(page.locator('tbody tr')).toHaveCount(2);
  await expect(page.locator('.buy')).toHaveCount(2);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Rebalance preview' })).toBeVisible();
  const orders = await request.get(`/api/v1/accounts/${accountId}/orders`, { headers });
  expect(orders.ok()).toBeTruthy();
  expect(await orders.json()).toEqual([]);
  await page.getByRole('link', { name: 'Back to strategies' }).click();
  await expect(page.locator('.header')).toContainText('Edited pie');
  await page.getByRole('button', { name: 'Unlink', exact: true }).click();
  await expect(page.locator('.header')).toContainText('No strategy on this account yet');
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.locator('.pie-card')).toHaveCount(0);
  expect(placements).toEqual([]);
  expect(errors).toEqual([]);
});
