import { expect, Page } from '@playwright/test';

export const DEMO = { email: 'demo@example.com', password: 'Demo@1234' };

export async function loginAsDemo(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('demo-login').click();
  await expect(page.getByRole('heading', { name: 'Applications', exact: true })).toBeVisible();
}

/** Creates a brand-new account so the test owns its data and never touches the demo account. */
export async function registerFreshUser(page: Page): Promise<string> {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@test.dev`;
  await page.goto('/register');
  await page.getByTestId('fullName').fill('E2E Tester');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill('Password1!');
  await page.getByTestId('register-submit').click();
  await expect(page.getByRole('heading', { name: 'Applications', exact: true })).toBeVisible();
  return email;
}

export async function addApplication(
  page: Page,
  values: { company: string; role: string; location?: string; status?: string },
): Promise<void> {
  await page.getByTestId('add-application').click();
  await page.getByTestId('company').fill(values.company);
  await page.getByTestId('role').fill(values.role);
  if (values.location) {
    await page.getByTestId('location').fill(values.location);
  }
  if (values.status) {
    await page.getByTestId('status').click();
    await page.getByRole('option', { name: values.status, exact: true }).click();
  }
  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
}
