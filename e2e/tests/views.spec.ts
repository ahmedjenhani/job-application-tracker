import { expect, test } from '@playwright/test';
import { loginAsDemo, registerFreshUser } from './helpers';

const API = process.env['API_URL'] ?? 'http://localhost:8080';

test.describe('list, dashboard and notifications', () => {
  test('list page filters by status and by text on the server', async ({ page }) => {
    await loginAsDemo(page);
    await page.getByTestId('nav-list').click();
    await expect(page.getByTestId('list-table').locator('tr.row')).toHaveCount(14);

    await page.getByTestId('status-filter').click();
    await page.getByRole('option', { name: 'Rejected' }).click();
    await expect(page.getByTestId('list-table').locator('tr.row')).toHaveCount(4);

    await page.getByTestId('list-search').fill('wiremind');
    await expect(page.getByTestId('list-table').locator('tr.row')).toHaveCount(1);
    await expect(page.getByTestId('list-table')).toContainText('Backend Engineer');
    await page.screenshot({ path: 'screenshots/06-list.png' });

    await page.getByTestId('list-table').locator('tr.row').first().click();
    await expect(page.getByTestId('detail-company')).toHaveText('Wiremind');
  });

  test('dashboard shows the numbers and three charts', async ({ page }) => {
    await loginAsDemo(page);
    await page.getByTestId('nav-dashboard').click();

    await expect(page.getByTestId('stat-total')).toContainText('14');
    await expect(page.getByTestId('stat-response')).toContainText('63.6%');
    await expect(page.getByTestId('stat-interview')).toContainText('36.4%');
    await expect(page.getByTestId('stat-days')).toContainText('days');
    await expect(page.getByTestId('stat-followups')).toContainText('2');
    await expect(page.locator('app-chart canvas')).toHaveCount(3);
    await expect(page.getByTestId('followup-list').locator('li')).toHaveCount(2);

    // canvases actually painted something
    const painted = await page.locator('app-chart canvas').first().evaluate((c: HTMLCanvasElement) => {
      const data = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
      return data.some((v, i) => i % 4 === 3 && v > 0);
    });
    expect(painted).toBe(true);
    await page.screenshot({ path: 'screenshots/07-dashboard.png', fullPage: true });
  });

  test('dark mode can be switched on and the charts follow', async ({ page }) => {
    await loginAsDemo(page);
    await page.getByTestId('nav-dashboard').click();
    await page.getByRole('button', { name: /dark mode/i }).click();
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'dark');
    await expect(page.locator('app-chart canvas')).toHaveCount(3);
    await page.screenshot({ path: 'screenshots/08-dashboard-dark.png', fullPage: true });
    await page.getByTestId('nav-board').click();
    await page.screenshot({ path: 'screenshots/09-board-dark.png' });
  });

  test('the seeded reminder is listed in the bell menu and opens its application', async ({ page }) => {
    await loginAsDemo(page);
    await page.getByTestId('bell').click();
    const item = page.locator('.notification', { hasText: 'Time to follow up with Ooredoo' });
    await expect(item).toBeVisible();
    await page.screenshot({ path: 'screenshots/10-notifications.png' });

    await item.click();
    await expect(page.getByTestId('detail-company')).toHaveText('Ooredoo');
  });

  /**
   * Needs the backend to run with a fast schedule, e.g. REMINDERS_CRON="*\/5 * * * * *" (every 5 seconds).
   * Proves the whole chain: overdue follow-up -> scheduler -> notification -> bell badge -> opens the application.
   */
  test('the scheduler turns an overdue follow-up into a notification', async ({ page }) => {
    test.skip(!process.env['FAST_REMINDERS'], 'set FAST_REMINDERS=1 and start the backend with a 5-second REMINDERS_CRON');
    test.setTimeout(90_000);

    await registerFreshUser(page);
    const token = await page.evaluate(() => localStorage.getItem('jt_token'));
    const yesterday = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
    const created = await page.request.post(`${API}/api/applications`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { company: 'Hooli', role: 'Platform Engineer', status: 'APPLIED', followUpDate: yesterday },
    });
    expect(created.ok()).toBe(true);

    // the UI polls every minute, so reload until the scheduler's notification shows up
    await expect
      .poll(
        async () => {
          await page.reload();
          return page.getByTestId('bell').locator('.mat-badge-content').textContent({ timeout: 2000 }).catch(() => '');
        },
        { timeout: 40_000, intervals: [3000] },
      )
      .toContain('1');

    await page.getByTestId('bell').click();
    await page.locator('.notification', { hasText: 'Time to follow up with Hooli' }).click();
    await expect(page.getByTestId('detail-company')).toHaveText('Hooli');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('bell').locator('.mat-badge-content')).toBeHidden();
  });
});

test.describe('mobile layout', () => {
  test.use({ viewport: { width: 390, height: 800 } });

  test('board scrolls horizontally and nothing overflows the page', async ({ page }) => {
    await loginAsDemo(page);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    expect(overflow).toBe(false);
    await page.screenshot({ path: 'screenshots/11-mobile-board.png' });
  });

  test('dashboard fits a phone screen too', async ({ page }) => {
    await loginAsDemo(page);
    await page.getByTestId('nav-dashboard').click();
    await expect(page.locator('app-chart canvas')).toHaveCount(3);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    expect(overflow).toBe(false);
    await page.screenshot({ path: 'screenshots/14-mobile-dashboard.png', fullPage: true });
  });
});
