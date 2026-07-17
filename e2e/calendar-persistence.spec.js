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
  await expect(page.locator('#noteEdit')).toHaveValue(note);
  await expect(page.locator('#dayEmojiPreview')).toContainText('🧪');
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);
});
