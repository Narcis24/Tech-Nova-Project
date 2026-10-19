import { randomBytes } from 'node:crypto';
import { APIRequestContext, expect, test } from '@playwright/test';

async function signUp(request: APIRequestContext, prefix: string) {
  const username = `${prefix}_${randomBytes(5).toString('hex')}`;
  const password = `Cp1!${randomBytes(8).toString('hex')}`;
  const registration = await request.post('/auth/v1/auth/register', {
    data: { username, password, email: `${username}@smoke.test` },
  });
  expect(registration.status()).toBe(201);
  const login = await request.post('/auth/v1/auth/login', { data: { username, password } });
  expect(login.ok()).toBeTruthy();
  const { accessToken } = await login.json();
  return { username, password, headers: { Authorization: `Bearer ${accessToken}` } };
}

test("copies someone else's public pie, then is offered the author's update", async ({ page, request }) => {
  const pieName = `Copied pie ${randomBytes(4).toString('hex')}`;
  const author = await signUp(request, 'auth');
  const created = await request.post('/api/v1/pies', {
    headers: author.headers,
    data: { name: pieName, slices: [{ symbol: 'AAPL', weightPct: 40 }] },
  });
  expect(created.status()).toBe(201);
  const { pieId } = await created.json();
  expect((await request.post(`/api/v1/pies/${pieId}/publish`, { headers: author.headers })).ok()).toBeTruthy();

  const copier = await signUp(request, 'copy');
  const account = await request.post('/api/v1/accounts', {
    headers: copier.headers,
    data: { holderName: 'Copy UI Test' },
  });
  expect(account.status()).toBe(201);
  const { accountId } = await account.json();
  expect(
    (
      await request.post(`/api/v1/accounts/${accountId}/deposit`, { headers: copier.headers, data: { amount: 10000 } })
    ).ok(),
  ).toBeTruthy();
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto(`/discover/${pieId}`);
  await page.getByLabel('Username', { exact: true }).fill(copier.username);
  await page.getByLabel('Password', { exact: true }).fill(copier.password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: pieName, level: 1 })).toBeVisible();
  await expect(page.locator('.copy')).toContainText(`Into account ${accountId}`);
  await page.getByRole('button', { name: 'Copy into account' }).click();

  await expect(page.getByRole('heading', { name: 'Rebalance preview' })).toBeVisible();
  await expect(page.getByText('auto-sync on')).toBeVisible();
  await expect(page.locator('.summary')).toContainText('$10,000.00');
  const started = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith(`/accounts/${accountId}/pie/copy`),
  );
  await page.getByRole('button', { name: 'Confirm copy' }).click();
  const response = await started;
  expect(response.status()).toBe(202);
  const { runId } = await response.json();
  await expect(page).toHaveURL(new RegExp(`/rebalance-runs/${runId}$`));

  // Fills depend on the local market feed, so end the run rather than wait on it
  const runStatus = async () =>
    (await (await request.get(`/api/v1/rebalance-runs/${runId}`, { headers: copier.headers })).json()).status;
  if (['SELLING', 'BUYING'].includes(await runStatus())) {
    await request.post(`/api/v1/rebalance-runs/${runId}/cancel`, { headers: copier.headers });
  }
  await expect.poll(runStatus, { timeout: 30_000 }).toMatch(/COMPLETED|PARTIAL|CANCELLED/);

  await page.goto('/pies');
  const header = page.locator('.header');
  await expect(header).toContainText(`This account copies ${pieName}`);
  const autoSync = page.getByRole('switch', { name: 'Auto-sync' });
  await expect(autoSync).toBeChecked();
  await autoSync.click();
  await expect(autoSync).not.toBeChecked();

  const edited = await request.put(`/api/v1/pies/${pieId}`, {
    headers: author.headers,
    data: { name: pieName, slices: [{ symbol: 'AAPL', weightPct: 60 }] },
  });
  expect(edited.ok()).toBeTruthy();
  await page.reload();
  await expect(header.locator('.status')).toHaveText('Update available');
  await expect(header.getByRole('link', { name: 'Review', exact: true })).toHaveAttribute(
    'href',
    `/accounts/${accountId}/rebalance`,
  );

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Stop copying' }).click();
  await expect(header).toContainText('No pie on this account yet');

  // Clean up so reruns leave no public pie behind
  expect((await request.post(`/api/v1/pies/${pieId}/unpublish`, { headers: author.headers })).ok()).toBeTruthy();
  expect((await request.delete(`/api/v1/pies/${pieId}`, { headers: author.headers })).ok()).toBeTruthy();
  expect(errors).toEqual([]);
});
