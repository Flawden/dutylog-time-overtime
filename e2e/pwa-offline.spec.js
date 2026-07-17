const { test, expect } = require('@playwright/test');
const { registerAndOnboard, currentLocalDateKey, selectDate, waitForApi } = require('./helpers');

test('installed web shell reopens from IndexedDB snapshot while offline', async ({ browser, baseURL }) => {
  const context = await browser.newContext({
    baseURL,
    locale: 'ru-RU',
    serviceWorkers: 'allow'
  });
  const page = await context.newPage();
  try {
    await registerAndOnboard(page, { preset: 'work', prefix: 'offline' });
    const date = await currentLocalDateKey(page);
    await selectDate(page, date);

    const note = `Offline snapshot ${Date.now()}`;
    const noteSaved = waitForApi(page, 'PUT', `/api/days/${date}`);
    await page.locator('#noteEdit').fill(note);
    await noteSaved;

    await page.evaluate(async () => {
      const registration = await navigator.serviceWorker.ready;
      if (!registration.active) throw new Error('Service worker is not active');
    });
    await page.reload();
    await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
    await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);

    await context.setOffline(true);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
    await expect(page.locator('body')).toHaveClass(/offline/);
    await expect(page.locator('#offlineStatus')).toContainText(/оффлайн|offline/i);
    await expect(page.locator(`#grid [data-date="${date}"] .ear`)).toHaveCount(1);
  } finally {
    await context.setOffline(false).catch(() => {});
    await context.close();
  }
});
