const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('Vue app shell owns navigation chrome while legacy product screens retain behavior', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'vueappshell' });
  await page.evaluate(() => window.__dutylogVueReady);

  const shell = page.locator('[data-vue-app-shell]');
  await expect(shell).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-vue-shell', 'ready');
  await expect(page.locator('#tabbar')).toBeHidden();
  await expect(page.locator('.nextTopbar')).toBeHidden();

  const calendar = page.locator('[data-vue-shell-navigation] [data-route="calendar"]');
  await calendar.click();
  await expect(page).toHaveURL(/#calendar$/);
  await expect(page.locator('#view-calendar')).toBeVisible();
  await expect(calendar).toHaveAttribute('aria-current', 'page');

  await openView(page, 'tasks');
  const more = page.locator('[data-vue-shell-more]');
  await expect(more).toHaveAttribute('aria-current', 'page');
  await more.click();
  await expect(page.locator('.vue-shell-more-grid [data-route="tasks"]')).toHaveAttribute('aria-current', 'page');

  const diagnostics = await page.evaluate(() => window.DutyLogVuePlatform?.snapshot());
  expect(diagnostics).toMatchObject({
    releaseVersion: '27.40.8',
    architecture: 'vue-shell-v1',
    phase: 'ready',
    legacyConnected: true,
    shellReady: true,
  });
});
