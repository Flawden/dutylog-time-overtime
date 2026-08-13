const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test('remember-me restores a fresh browser session and logout revokes the old cookie', async ({ page, browser, baseURL }) => {
  const account = await registerAndOnboard(page, { preset: 'full', prefix: 'remember' });
  const cookies = await page.context().cookies();
  const remember = cookies.find(cookie => cookie.name === 'DUTYLOG_REMEMBER_ME');

  expect(remember, 'persistent login cookie').toBeTruthy();
  expect(remember.httpOnly).toBe(true);
  expect(remember.expires).toBeGreaterThan(Math.floor(Date.now() / 1000) + 28 * 24 * 60 * 60);

  const restoredContext = await browser.newContext({
    baseURL,
    locale: 'ru-RU',
    serviceWorkers: 'block'
  });
  await restoredContext.addCookies([remember]);
  const restoredPage = await restoredContext.newPage();
  await restoredPage.goto('/');
  await expect(restoredPage).not.toHaveURL(/login\.html/);
  await expect(restoredPage.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await expect(restoredPage.locator('[data-vue-shell-profile] > b')).toHaveText(account.username);

  const bootstrapStatuses = await restoredPage.evaluate(async () => {
    const paths = [
      '/api/profile',
      '/api/modules',
      '/api/shift-types',
      '/api/calendar?from=2026-01-01&to=2026-01-31'
    ];
    return Promise.all(paths.map(async path => (await fetch(path, { credentials: 'same-origin' })).status));
  });
  expect(bootstrapStatuses).toEqual([200, 200, 200, 200]);
  await restoredContext.close();

  await page.locator('[data-vue-shell-more]').click();
  await Promise.all([
    page.waitForURL(/login\.html/),
    page.locator('[data-vue-shell-logout]').click()
  ]);

  const revokedContext = await browser.newContext({
    baseURL,
    locale: 'ru-RU',
    serviceWorkers: 'block'
  });
  await revokedContext.addCookies([remember]);
  const revokedPage = await revokedContext.newPage();
  await revokedPage.goto('/');
  await expect(revokedPage).toHaveURL(/login\.html/);
  await revokedContext.close();
});
