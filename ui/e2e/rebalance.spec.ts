import { randomBytes } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('confirms a preview, opens the run and preserves its status on reload', async ({ page, request }) => {
  const username = `run_${randomBytes(5).toString('hex')}`;
  const password = `Rn1!${randomBytes(8).toString('hex')}`;
  const registration = await request.post('/auth/v1/auth/register', {
    data: { username, password, email: `${username}@smoke.test` },
  });
  expect(registration.status()).toBe(201);
  const login = await request.post('/auth/v1/auth/login', { data: { username, password } });
  expect(login.ok()).toBeTruthy();
  const { accessToken } = await login.json();
  const headers = { Authorization: `Bearer ${accessToken}` };
  const accountResponse = await request.post('/api/v1/accounts', {
    headers,
    data: { holderName: 'Rebalance UI Test' },
  });
  expect(accountResponse.status()).toBe(201);
  const { accountId } = await accountResponse.json();
  expect(
    (await request.post(`/api/v1/accounts/${accountId}/deposit`, { headers, data: { amount: 10000 } })).ok(),
  ).toBeTruthy();
  const pieResponse = await request.post('/api/v1/pies', {
    headers,
    data: { name: 'Execution test', slices: [{ symbol: 'AAPL', weightPct: 50 }] },
  });
  expect(pieResponse.status()).toBe(201);
  const { pieId } = await pieResponse.json();
  expect((await request.put(`/api/v1/accounts/${accountId}/pie`, { headers, data: { pieId } })).ok()).toBeTruthy();
  await page.goto(`/accounts/${accountId}/rebalance`);
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Rebalance preview' })).toBeVisible();
  const started = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith(`/accounts/${accountId}/rebalance`),
  );
  await page.getByRole('button', { name: 'Confirm rebalance' }).click();
  const response = await started;
  expect(response.status()).toBe(202);
  const { runId } = await response.json();
  await expect(page).toHaveURL(new RegExp(`/rebalance-runs/${runId}$`));
  await expect(page.getByRole('heading', { name: 'Rebalance', exact: true })).toBeVisible();
  await expect(page.locator('.legs')).toContainText('AAPL');
  await page.reload();
  await expect(page.locator('.legs')).toContainText('AAPL');
  await expect(page.getByRole('link', { name: 'Order details' })).toBeVisible();
  const latest = await request.get(`/api/v1/rebalance-runs/${runId}`, { headers });
  expect(latest.ok()).toBeTruthy();
  if (['SELLING', 'BUYING'].includes((await latest.json()).status)) {
    await page.getByRole('button', { name: 'Cancel rebalance' }).click();
    await expect(page.getByRole('heading', { name: 'Rebalance cancelled' })).toBeVisible();
  }
});
