const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
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
  const note = `# E2E ${Date.now()}\nCalendar persistence check`;
  const noteSaved = waitForApi(page, 'PUT', `/api/days/${date}`);
  await page.locator('#noteEdit').fill(note);
  await noteSaved;

  await expect(page.locator(`#grid [data-date="${date}"] .dayEmoji`)).toHaveText('🧪');
  await expect(page.locator(`#grid [data-date="${date}"] .ear`)).toHaveCount(1);

  const nextMonth = page.waitForResponse(response => new URL(response.url()).pathname === '/api/calendar' && response.status() === 200);
  await page.locator('#next').click();
  await nextMonth;
  const previousMonth = page.waitForResponse(response => new URL(response.url()).pathname === '/api/calendar' && response.status() === 200);
  await page.locator('#prev').click();
  await previousMonth;

  await expect(page.locator(`#grid [data-date="${date}"] .dayEmoji`)).toHaveText('🧪');
  await expect(page.locator(`#grid [data-date="${date}"] .ear`)).toHaveCount(1);

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
  const note = `pending note ${Date.now()}`;
  let writes = 0;
  const countWrite = response => {
    const url = new URL(response.url());
    if (response.request().method() === 'PUT' && url.pathname === `/api/days/${date}` && response.status() === 200) writes += 1;
  };
  page.on('response', countWrite);

  // The note uses a debounced full-day snapshot. Deleting the shift immediately
  // after typing used to let that older snapshot restore the deleted shift.
  await page.locator('#noteEdit').fill(note);
  await shift.click();
  await expect.poll(() => writes, { timeout: 15_000 }).toBeGreaterThanOrEqual(2);
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(0);

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
  page.off('response', countWrite);
});
