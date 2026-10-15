import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  outputDir: '../test-results',
  forbidOnly: !!process.env['CI'],
  workers: 1,
  reporter: [['list'], ['junit', { outputFile: '../test-results/smoke.xml' }]],
  use: {
    baseURL: process.env['UI_BASE_URL'] || 'http://localhost:8091',
    trace: 'retain-on-failure',
    launchOptions: process.env['CHROME_BIN'] ? { executablePath: process.env['CHROME_BIN'] } : {},
  },
  projects: [
    { name: 'desktop', use: devices['Desktop Chrome'] },
    { name: 'mobile', use: devices['Pixel 7'] },
  ],
});
