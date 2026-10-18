import { expect, test } from '@playwright/test';
import { registerFreshUser } from './helpers';

const POSTING = `Wiremind is looking for a Junior Backend Engineer to join our team in Paris (hybrid).
You will design REST APIs in Java with Spring Boot, work with PostgreSQL and Kafka, and deploy on Kubernetes.
Nice to have: Docker and CI/CD. Salary: 42-48k EUR per year.`;

test.describe('AI features (mocked provider responses, so no quota is used)', () => {
  test('pasting a posting fills the form, and the application can be saved', async ({ page }) => {
    await page.route('**/api/ai/parse-job', (route) =>
      route.fulfill({
        json: {
          company: 'Wiremind',
          role: 'Junior Backend Engineer',
          location: 'Paris',
          salaryRange: '42-48k EUR per year',
          skills: ['Java', 'Spring Boot', 'PostgreSQL'],
        },
      }),
    );
    await registerFreshUser(page);
    await page.getByTestId('add-application').click();
    await page.getByTestId('ai-text').fill(POSTING);
    await page.getByTestId('ai-extract').click();

    await expect(page.getByTestId('ai-result')).toContainText('Form filled');
    await expect(page.getByTestId('company')).toHaveValue('Wiremind');
    await expect(page.getByTestId('role')).toHaveValue('Junior Backend Engineer');
    await expect(page.getByTestId('location')).toHaveValue('Paris');
    await expect(page.getByRole('row').or(page.locator('mat-chip-row')).filter({ hasText: 'Spring Boot' }).first()).toBeVisible();
    await page.screenshot({ path: 'screenshots/12-ai-autofill.png' });

    await page.getByTestId('save').click();
    await expect(page.getByTestId('column-APPLIED').getByText('Wiremind')).toBeVisible();
  });

  test('when the AI is unavailable the user is told and can continue by hand', async ({ page }) => {
    await page.route('**/api/ai/parse-job', (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        json: { title: 'Service Unavailable', status: 503, detail: 'The AI service is not reachable right now.' },
      }),
    );
    await registerFreshUser(page);
    await page.getByTestId('add-application').click();
    await page.getByTestId('ai-text').fill(POSTING);
    await page.getByTestId('ai-extract').click();

    await expect(page.getByTestId('ai-result')).toContainText('not reachable');
    await expect(page.getByTestId('ai-result')).toContainText('fill the form by hand');
    await page.getByTestId('company').fill('Manual Co');
    await page.getByTestId('role').fill('Dev');
    await page.getByTestId('save').click();
    await expect(page.getByTestId('column-APPLIED').getByText('Manual Co')).toBeVisible();
  });

  test('rate limit errors are explained', async ({ page }) => {
    await page.route('**/api/ai/parse-job', (route) =>
      route.fulfill({
        status: 429,
        headers: { 'retry-after': '30' },
        contentType: 'application/problem+json',
        json: { title: 'Too Many Requests', status: 429, detail: 'Too many AI requests, please wait a moment' },
      }),
    );
    await registerFreshUser(page);
    await page.getByTestId('add-application').click();
    await page.getByTestId('ai-text').fill(POSTING);
    await page.getByTestId('ai-extract').click();
    await expect(page.getByTestId('ai-result')).toContainText('Too many AI requests');
  });

  test('too little text is rejected before calling the server', async ({ page }) => {
    await registerFreshUser(page);
    await page.getByTestId('add-application').click();
    await page.getByTestId('ai-text').fill('hello');
    await page.getByTestId('ai-extract').click();
    await expect(page.getByTestId('ai-result')).toContainText('Paste the whole job posting');
  });
});

test.describe('AI features against the real Gemini API', () => {
  test.skip(!process.env['LIVE_AI'], 'set LIVE_AI=1 to run the tests that call Gemini for real');

  test('real posting extraction and interview questions', async ({ page }) => {
    test.setTimeout(120_000);
    await registerFreshUser(page);
    await page.getByTestId('add-application').click();
    await page.getByTestId('ai-text').fill(POSTING);
    await page.getByTestId('ai-extract').click();

    await expect(page.getByTestId('ai-result')).toContainText('Form filled', { timeout: 60_000 });
    await expect(page.getByTestId('company')).toHaveValue(/Wiremind/i);
    await expect(page.getByTestId('role')).toHaveValue(/Backend/i);
    await page.getByTestId('save').click();

    await page.getByTestId('column-APPLIED').getByText('Wiremind').click();
    await page.getByTestId('prep').click();
    await expect(page.getByTestId('questions').locator('li').first()).toBeVisible({ timeout: 60_000 });
    expect(await page.getByTestId('questions').locator('li').count()).toBeGreaterThanOrEqual(5);
    await page.screenshot({ path: 'screenshots/13-interview-prep.png' });
  });
});
