import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests drive the real UI against the real API.
 * Start the stack first (backend on :8080, frontend on :4200) - see the README.
 * Uses the locally installed Chrome so no browser download is needed (set BROWSER=chromium to use Playwright's).
 */
const channel = process.env['BROWSER'] === 'chromium' ? undefined : (process.env['BROWSER'] ?? 'chrome');

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:4200',
    channel,
    viewport: { width: 1360, height: 860 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  outputDir: './test-results',
});
