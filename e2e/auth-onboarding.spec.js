const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('registration keeps login language and Minimum onboarding survives reload', async ({ page }) => {
  const account = await registerAndOnboard(page, {
    preset: 'basic',
    language: 'en',
    prefix: 'auth'
  });

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#tabbar a[data-view="tasks"]')).toBeHidden();
  await expect(page.locator('#tabbar a[data-view="overtime"]')).toBeHidden();
  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('#tabbar a[data-view="today"]')).toHaveAttribute('aria-current', 'page');

  await page.reload();
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator('#firstRunOnboarding')).toBeHidden();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#whoami')).toHaveText(account.username);
  await expect(page.locator('#view-today')).toBeVisible();
  await openView(page, 'calendar');
  await expect(page.locator('#grid [data-date]')).not.toHaveCount(0);
});
