const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  waitForAppIdle,
  waitForCalendarNavigationReady,
  openDayModule
} = require('./helpers');

test('shift, emoji and note survive month navigation and full reload', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'work', prefix: 'calendar' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);

  const shiftSaved = waitForApi(page, 'PUT', `/api/days/${date}`);
  await page.locator('#chips [data-shift-type-id]').first().click();
  await shiftSaved;
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);

  await openDayModule(page, 'core');
  const emojiSaved = waitForApi(page, 'PUT', `/api/days/${date}`);
  await page.locator('#dayEmojiCustom').fill('🧪');
  await page.locator('#dayEmojiApply').click();
  await emojiSaved;

  await openDayModule(page, 'notes');
  const note = `# E2E ${Date.now()}
Calendar persistence check`;

  // Multiple Daily Notes keeps the editor hidden until a concrete note exists.
  // Create it through the dedicated notes API, then wait for the debounced PATCH
  // instead of the removed legacy PUT /api/days/{date} note contract.
  const noteCreated = waitForApi(page, 'POST', '/api/v1/notes', 201);
  await page.locator('#noteAdd').click();
  await noteCreated;

  const noteSaved = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'PATCH'
      && /^\/api\/notes\/\d+$/.test(url.pathname)
      && response.status() === 200;
  });
  await page.locator('#noteEdit').fill(note);
  await noteSaved;

  await expect(page.locator(`#grid [data-date="${date}"] .dayEmoji`)).toHaveText('🧪');
  await expect(page.locator(`#grid [data-date="${date}"] .ear`)).toHaveCount(1);

  // Await the product-level navigation readiness promise instead of coupling
  // persistence to one calendar transport alias. The grid assertion proves
  // that we really left the source month before navigating back.
  await page.locator('#next').click();
  await waitForCalendarNavigationReady(page);
  await expect(page.locator(`#grid [data-date="${date}"]`)).toHaveCount(0);
  await page.locator('#prev').click();
  await waitForCalendarNavigationReady(page);
  await expect(page.locator(`#grid [data-date="${date}"]`)).toHaveCount(1);

  await expect(page.locator(`#grid [data-date="${date}"] .dayEmoji`)).toHaveText('🧪');
  await expect(page.locator(`#grid [data-date="${date}"] .ear`)).toHaveCount(1);

  await waitForAppIdle(page);
  const authoritativeReload = page.waitForResponse(response => new URL(response.url()).pathname === '/api/calendar' && response.status() === 200);
  await page.reload();
  await authoritativeReload;
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await selectDate(page, date);
  await openDayModule(page, 'notes');
  await openDayModule(page, 'core');
  await expect(page.locator('#noteEdit')).toHaveValue(note);
  await expect(page.locator('#dayEmojiPreview')).toContainText('🧪');
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);
});

test('a shift can be deleted and assigned again while a note save is pending', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'work', prefix: 'reassign' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);

  const shift = page.locator('#chips [data-shift-type-id]').first();
  const created = waitForApi(page, 'PUT', `/api/days/${date}`);
  await shift.click();
  await created;
  await expect(shift).toHaveAttribute('aria-pressed', 'true');

  await openDayModule(page, 'notes');
  const noteCreated = waitForApi(page, 'POST', '/api/v1/notes', 201);
  await page.locator('#noteAdd').click();
  await noteCreated;
  const note = `pending note ${Date.now()}`;

  // A note now saves through its own PATCH endpoint. Shift deletion must remain
  // independent even when the debounced note write is flushed immediately first.
  const noteSaved = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'PATCH'
      && /^\/api\/notes\/\d+$/.test(url.pathname)
      && response.status() === 200;
  });
  const shiftDeleted = waitForApi(page, 'PUT', `/api/days/${date}`);
  await page.locator('#noteEdit').fill(note);
  await shift.click();
  await noteSaved;
  await shiftDeleted;
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(0);

  await waitForAppIdle(page);
  const reloadAfterDelete = page.waitForResponse(response => new URL(response.url()).pathname === '/api/calendar' && response.status() === 200);
  await page.reload();
  await reloadAfterDelete;
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await selectDate(page, date);
  await openDayModule(page, 'notes');
  await expect(page.locator('#noteEdit')).toHaveValue(note);
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(0);

  const recreated = waitForApi(page, 'PUT', `/api/days/${date}`);
  await page.locator('#chips [data-shift-type-id]').first().click();
  await recreated;
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);
});
