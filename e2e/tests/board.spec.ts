import { expect, test } from '@playwright/test';
import { addApplication, loginAsDemo, registerFreshUser } from './helpers';

test.describe('board', () => {
  test('demo account shows the seeded pipeline in the right columns', async ({ page }) => {
    await loginAsDemo(page);

    await expect(page.getByTestId('count-WISHLIST')).toHaveText('3');
    await expect(page.getByTestId('count-APPLIED')).toHaveText('4');
    await expect(page.getByTestId('count-INTERVIEW')).toHaveText('2');
    await expect(page.getByTestId('count-OFFER')).toHaveText('1');
    await expect(page.getByTestId('count-REJECTED')).toHaveText('4');
    await expect(page.getByTestId('column-OFFER').getByText('Sofrecom')).toBeVisible();
    await page.screenshot({ path: 'screenshots/03-board.png' });
  });

  test('search narrows the cards on the board', async ({ page }) => {
    await loginAsDemo(page);
    await page.getByTestId('board-search').fill('angular');
    await expect(page.getByTestId('column-INTERVIEW').getByText('Vermeg')).toBeVisible();
    await expect(page.getByTestId('column-OFFER').getByText('Sofrecom')).toBeHidden();
    await page.getByTestId('board-search').fill('');
    await expect(page.getByTestId('column-OFFER').getByText('Sofrecom')).toBeVisible();
  });

  test('create, drag to another column, persist, then delete', async ({ page }) => {
    await registerFreshUser(page);
    await addApplication(page, { company: 'Acme Robotics', role: 'Java Developer', location: 'Tunis', status: 'Applied' });

    const applied = page.getByTestId('column-APPLIED');
    await expect(applied.getByText('Acme Robotics')).toBeVisible();
    await expect(page.getByTestId('count-APPLIED')).toHaveText('1');

    // drag the card into the Interview column
    const card = applied.locator('article.card', { hasText: 'Acme Robotics' });
    const target = page.getByTestId('column-INTERVIEW').locator('.cards');
    const from = await card.boundingBox();
    const to = await target.boundingBox();
    await page.mouse.move(from!.x + from!.width / 2, from!.y + from!.height / 2);
    await page.mouse.down();
    await page.mouse.move(from!.x + from!.width / 2 + 15, from!.y + 15, { steps: 4 });
    await page.mouse.move(to!.x + to!.width / 2, to!.y + 40, { steps: 12 });
    await page.mouse.up();

    await expect(page.getByTestId('count-INTERVIEW')).toHaveText('1');
    await expect(page.getByTestId('count-APPLIED')).toHaveText('0');

    // the change was saved on the server
    await page.reload();
    await expect(page.getByTestId('column-INTERVIEW').getByText('Acme Robotics')).toBeVisible();

    // detail dialog shows the history, then delete it
    await page.getByTestId('column-INTERVIEW').getByText('Acme Robotics').click();
    await expect(page.getByTestId('detail-company')).toHaveText('Acme Robotics');
    await expect(page.getByTestId('history').locator('li')).toHaveCount(2);
    await expect(page.getByTestId('history')).toContainText('Applied');
    await expect(page.getByTestId('history')).toContainText('Interview');
    await page.screenshot({ path: 'screenshots/05-detail.png' });

    await page.getByTestId('delete').click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.getByText('No applications yet')).toBeVisible();
  });

  test('status can be changed from the detail dialog', async ({ page }) => {
    await registerFreshUser(page);
    await addApplication(page, { company: 'Globex', role: 'Backend Dev', status: 'Wishlist' });

    await page.getByTestId('column-WISHLIST').getByText('Globex').click();
    await page.getByTestId('toggle-OFFER').click();
    await expect(page.getByTestId('history').locator('li')).toHaveCount(2);
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('column-OFFER').getByText('Globex')).toBeVisible();
  });

  test('editing an application updates the card', async ({ page }) => {
    await registerFreshUser(page);
    await addApplication(page, { company: 'Initech', role: 'Junior Dev', status: 'Applied' });

    await page.getByTestId('column-APPLIED').getByText('Initech').click();
    await page.getByTestId('edit').click();
    await page.getByTestId('role').fill('Senior Dev');
    await page.getByTestId('save').click();
    await expect(page.getByTestId('column-APPLIED').getByText('Senior Dev')).toBeVisible();
  });

  test('form validation blocks an empty submit', async ({ page }) => {
    await registerFreshUser(page);
    await page.getByTestId('add-application').click();
    await page.getByTestId('save').click();
    await expect(page.getByText('Company is required')).toBeVisible();
    await expect(page.getByText('Role is required')).toBeVisible();
    await page.screenshot({ path: 'screenshots/04-form.png' });
  });
});
