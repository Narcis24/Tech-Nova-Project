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

test('copies a public pie and trades an author update only after confirmation', async ({ page, request }) => {
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
  await expect(
    page.getByText('Later author updates need your review and confirmation', { exact: false }),
  ).toBeVisible();
  await expect(page.locator('.summary')).toContainText('$10,000.00');
  const started = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith(`/accounts/${accountId}/pie/copy`),
  );
  await page.getByRole('button', { name: 'Confirm copy' }).click();
  const response = await started;
  expect(response.status()).toBe(202);
  const { runId } = await response.json();
  await expect(page).toHaveURL(new RegExp(`/rebalance-runs/${runId}$`));

  // Fills depend on the local market feed, so cancel active runs before continuing.
  const endRun = async (id: string) => {
    const runStatus = async () => {
      const run = await request.get(`/api/v1/rebalance-runs/${id}`, { headers: copier.headers });
      expect(run.ok()).toBeTruthy();
      return (await run.json()).status;
    };
    if (['SELLING', 'BUYING'].includes(await runStatus())) {
      const cancelled = await request.post(`/api/v1/rebalance-runs/${id}/cancel`, { headers: copier.headers });
      expect(cancelled.ok()).toBeTruthy();
    }
    await expect.poll(runStatus, { timeout: 30_000 }).toMatch(/COMPLETED|PARTIAL|CANCELLED/);
  };
  const orders = async () => {
    const response = await request.get(`/api/v1/accounts/${accountId}/orders`, { headers: copier.headers });
    expect(response.ok()).toBeTruthy();
    const rows: { orderId: string }[] = await response.json();
    return rows.map((order) => order.orderId).sort();
  };
  const linkedPie = async () => {
    const response = await request.get(`/api/v1/accounts/${accountId}/pie`, { headers: copier.headers });
    expect(response.ok()).toBeTruthy();
    return response.json();
  };
  await endRun(runId);

  await page.goto('/pies');
  const header = page.locator('.header');
  await expect(header).toContainText(`This account copies ${pieName}`);
  await expect(page.getByRole('switch')).toHaveCount(0);
  const ordersBeforeEdit = await orders();

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

  expect((await linkedPie()).activeRunId).toBeFalsy();
  expect(await orders()).toEqual(ordersBeforeEdit);
  await header.getByRole('link', { name: 'Review', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Rebalance preview' })).toBeVisible();
  expect(await orders()).toEqual(ordersBeforeEdit);
  expect((await linkedPie()).activeRunId).toBeFalsy();

  const updateStarted = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().endsWith(`/accounts/${accountId}/rebalance`),
  );
  await page.getByRole('button', { name: 'Confirm rebalance', exact: true }).click();
  const updateResponse = await updateStarted;
  expect(updateResponse.status()).toBe(202);
  const { runId: updateRunId } = await updateResponse.json();
  expect(updateRunId).not.toBe(runId);
  await expect(page).toHaveURL(new RegExp(`/rebalance-runs/${updateRunId}$`));
  await endRun(updateRunId);
  await page.goto('/pies');
  await expect(header).toContainText(`This account copies ${pieName}`);

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Stop copying' }).click();
  await expect(header).toContainText('No strategy on this account yet');

  // Clean up so reruns leave no public pie behind
  expect((await request.post(`/api/v1/pies/${pieId}/unpublish`, { headers: author.headers })).ok()).toBeTruthy();
  expect((await request.delete(`/api/v1/pies/${pieId}`, { headers: author.headers })).ok()).toBeTruthy();
  expect(errors).toEqual([]);
});
