import { randomBytes } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('publishes a pie, finds it on Discover, then unpublishes it', async ({ page, request }) => {
  const suffix = randomBytes(4).toString('hex');
  const username = `pub_${suffix}`;
  const password = `Pi1!${randomBytes(8).toString('hex')}`;
  const pieName = `Public pie ${suffix}`;
  expect(
    (
      await request.post('/auth/v1/auth/register', {
        data: { username, password, email: `${username}@smoke.test` },
      })
    ).status(),
  ).toBe(201);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto('/pies');
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByRole('link', { name: 'New pie' }).click();
  await page.getByLabel('Name', { exact: true }).fill(pieName);
  await page.getByLabel('Stock 1', { exact: true }).fill('AAPL');
  await page.getByLabel('Weight 1', { exact: true }).fill('60');
  await page.getByRole('button', { name: 'Save', exact: true }).click();

  // The demo pies share the page with mine, so find this pie by its unique name
  await page.locator('.pie-card', { hasText: pieName }).getByRole('link', { name: pieName }).click();
  await expect(page.getByRole('heading', { name: pieName, level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Private', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Public', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Unpublish', exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'View public page' }).click();
  await expect(page.getByRole('heading', { name: pieName, level: 1 })).toBeVisible();
  await expect(page.locator('.byline')).toContainText(`by ${username}`);
  await expect(page.locator('.stats')).toContainText('Followers');
  await expect(page.locator('.holdings')).toContainText('AAPL');
  await expect(page.locator('.holdings')).toContainText('Cash');

  await page.getByRole('link', { name: 'Back to Discover' }).click();
  await expect(page.getByRole('link', { name: pieName, exact: true })).toBeVisible();
  await expect(page.locator('.entry', { hasText: pieName })).toContainText(`by ${username}`);

  await page.getByRole('navigation', { name: 'Pies' }).getByRole('link', { name: 'My pies' }).click();
  await page.locator('.pie-card', { hasText: pieName }).getByRole('link', { name: pieName }).click();
  await page.getByRole('button', { name: 'Unpublish', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Private', exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'Back to pies' }).click();
  await page.getByRole('navigation', { name: 'Pies' }).getByRole('link', { name: 'Discover' }).click();
  await expect(page.locator('.entry').first()).toBeVisible();
  await expect(page.getByRole('link', { name: pieName, exact: true })).toHaveCount(0);

  // Clean up so reruns leave no pie behind
  await page.getByRole('navigation', { name: 'Pies' }).getByRole('link', { name: 'My pies' }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.locator('.pie-card', { hasText: pieName }).getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.locator('.pie-card', { hasText: pieName })).toHaveCount(0);
  expect(errors).toEqual([]);
});
