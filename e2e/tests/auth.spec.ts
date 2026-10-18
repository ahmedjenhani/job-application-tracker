import { expect, test } from '@playwright/test';
import { DEMO, registerFreshUser } from './helpers';

test.describe('authentication', () => {
  test('protected pages redirect to the login page', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
    await page.screenshot({ path: 'screenshots/01-login.png' });
  });

  test('wrong password shows an error and stays on the page', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('email').fill(DEMO.email);
    await page.getByTestId('password').fill('definitely-wrong');
    await page.getByTestId('login-submit').click();
    await expect(page.getByTestId('login-error')).toHaveText('Invalid email or password');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('validation messages appear for empty or malformed input', async ({ page }) => {
    await page.goto('/register');
    await page.getByTestId('email').fill('not-an-email');
    await page.getByTestId('password').fill('short');
    await page.getByTestId('fullName').click();
    await page.getByTestId('register-submit').click();
    await expect(page.getByText('Enter a valid email address')).toBeVisible();
    await expect(page.getByText('Password must be 8 to 72 characters')).toBeVisible();
    await expect(page).toHaveURL(/\/register$/);
  });

  test('a new user can register, sees the empty state, signs out and signs back in', async ({ page }) => {
    const email = await registerFreshUser(page);
    await expect(page.getByText('No applications yet')).toBeVisible();
    await page.screenshot({ path: 'screenshots/02-empty-state.png' });

    await page.getByTestId('user-menu').click();
    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByTestId('email').fill(email);
    await page.getByTestId('password').fill('Password1!');
    await page.getByTestId('login-submit').click();
    await expect(page.getByRole('heading', { name: 'Applications', exact: true })).toBeVisible();
  });

  test('registering with an existing email is refused', async ({ page }) => {
    await page.goto('/register');
    await page.getByTestId('fullName').fill('Someone');
    await page.getByTestId('email').fill(DEMO.email);
    await page.getByTestId('password').fill('Password1!');
    await page.getByTestId('register-submit').click();
    await expect(page.getByTestId('register-error')).toContainText('already exists');
  });

  test('session survives a page reload', async ({ page }) => {
    await registerFreshUser(page);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Applications', exact: true })).toBeVisible();
  });
});
