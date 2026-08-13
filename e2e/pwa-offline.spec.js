const { test, expect } = require('@playwright/test');
const { registerAndOnboard, currentLocalDateKey, selectDate, waitForApi, openDayModule } = require('./helpers');

async function queuedNoteUpdates(page) {
  return page.evaluate(async () => {
    const items = await dataLayer.getQueueItems();
    return items.filter(item => item.type === 'updateNote').length;
  });
}

test('installed web shell preserves and synchronizes an existing note edited offline', async ({ browser, baseURL }) => {
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

    await openDayModule(page, 'notes');
    const noteCreated = waitForApi(page, 'POST', '/api/v1/notes', 201);
    await page.locator('#noteAdd').click();
    await noteCreated;
    const note = `Offline snapshot ${Date.now()}`;
    const noteSaved = page.waitForResponse(response => {
      const url = new URL(response.url());
      return response.request().method() === 'PATCH'
        && /^\/api\/notes\/\d+$/.test(url.pathname)
        && response.status() === 200;
    });
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
    await expect(page.locator('#offlineStatus')).toHaveAttribute('data-network-state', 'offline');
    await expect(page.locator(`#grid [data-date="${date}"] .ear`)).toHaveCount(1);
    await selectDate(page, date);
    await openDayModule(page, 'notes');
    await expect(page.locator('#noteEdit')).toHaveValue(note);
    await expect(page.locator('#noteEdit')).toBeEditable();
    await expect(page.locator('#noteAdd')).toBeDisabled();

    const offlineEdit = `${note} · queued offline edit`;
    await page.locator('#noteEdit').fill(offlineEdit);
    await expect.poll(() => queuedNoteUpdates(page)).toBe(1);
    await expect(page.locator('#offlineStatus')).toContainText(/1 не отправлено|1 pending/i);

    // The optimistic snapshot and the queue must both survive a full offline reload.
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
    await selectDate(page, date);
    await openDayModule(page, 'notes');
    await expect(page.locator('#noteEdit')).toHaveValue(offlineEdit);
    await expect.poll(() => queuedNoteUpdates(page)).toBe(1);

    const synchronized = page.waitForResponse(response => {
      const url = new URL(response.url());
      return response.request().method() === 'PATCH'
        && /^\/api\/notes\/\d+$/.test(url.pathname)
        && response.status() === 200;
    });
    await context.setOffline(false);
    await synchronized;
    await expect.poll(() => queuedNoteUpdates(page)).toBe(0);
    await expect(page.locator('#offlineStatus')).not.toContainText(/не отправлено|pending/i);

    // Finally verify the server-authoritative value after reconnect and reload.
    await page.reload();
    await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
    await selectDate(page, date);
    await openDayModule(page, 'notes');
    await expect(page.locator('#noteEdit')).toHaveValue(offlineEdit);
  } finally {
    await context.setOffline(false).catch(() => {});
    await context.close();
  }
});
