const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, toggleModule } = require('./helpers');
const { releaseVersion } = require('./release-version');

test('Vue app shell owns navigation chrome after legacy screen retirement', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'vueappshell' });
  await page.evaluate(() => window.__dutylogVueReady);

  const shell = page.locator('[data-vue-app-shell]');
  await expect(shell).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-vue-shell', 'ready');
  await expect(page.locator('#tabbar')).toHaveCount(0);
  await expect(page.locator('#nextTopbar')).toHaveCount(0);
  await expect(page.locator('body > .head')).toHaveCount(0);

  const settings = page.locator('[data-vue-shell-navigation] [data-route="settings"]');
  await expect(settings).toContainText('Настройки');

  const calendar = page.locator('[data-vue-shell-navigation] [data-route="calendar"]');
  await calendar.click();
  await expect(page).toHaveURL(/#calendar$/);
  await expect(page.locator('#view-calendar')).toBeVisible();
  await expect(calendar).toHaveAttribute('aria-current', 'page');

  await openView(page, 'tasks');
  const more = page.locator('[data-vue-shell-more]');
  await expect(more).toContainText('Ещё');
  await expect(more).toHaveAttribute('aria-current', 'page');
  await more.click();
  await expect(page.locator('.vue-shell-more-grid [data-route="tasks"]')).toHaveAttribute('aria-current', 'page');

  const diagnostics = await page.evaluate(() => window.DutyLogVuePlatform?.snapshot());
  expect(diagnostics).toMatchObject({
    releaseVersion,
    architecture: 'vue-shell-v1',
    phase: 'ready',
    legacyConnected: true,
    shellReady: true,
  });
  await page.locator('[data-vue-shell-close]').click();

  await toggleModule(page, 'tasks', false);
  await page.evaluate(() => { window.location.hash = '#tasks'; });
  await expect(page).toHaveURL(/#calendar$/);
  await expect(page.locator('[data-vue-domain-route="calendar"]')).toBeVisible();

  await page.evaluate(() => { window.location.hash = '#admin'; });
  await expect(page).toHaveURL(/#calendar$/);
  await expect(page.locator('[data-vue-domain-route="calendar"]')).toBeVisible();
});
