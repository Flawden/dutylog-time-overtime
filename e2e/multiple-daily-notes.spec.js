const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule
} = require('./helpers');

function waitForNotePatch(page) {
  return page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'PATCH'
      && /^\/api\/notes\/\d+$/.test(url.pathname)
      && response.status() === 200;
  });
}

test('multiple notes on one day remain independent across pin, reorder, reload and delete', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await registerAndOnboard(page, { preset: 'work', prefix: 'multinote' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await openDayModule(page, 'notes');

  const firstCreated = waitForApi(page, 'POST', '/api/v1/notes', 201);
  await page.locator('#noteAdd').click();
  await firstCreated;

  const firstSaved = waitForNotePatch(page);
  // Fill title and content inside one debounce window. Both values must survive.
  await page.locator('#noteTitle').fill('Первая заметка');
  await page.locator('#noteEdit').fill('alpha body');
  await firstSaved;

  const secondCreated = waitForApi(page, 'POST', '/api/v1/notes', 201);
  await page.locator('#noteAdd').click();
  await secondCreated;
  const secondSaved = waitForNotePatch(page);
  await page.locator('#noteTitle').fill('Вторая заметка');
  await page.locator('#noteEdit').fill('beta body');
  await secondSaved;

  await expect(page.locator('#noteList .dayNoteCard')).toHaveCount(2);
  await expect(page.locator(`#grid [data-date="${date}"] .noteCountBadge`)).toHaveText('2');
  await expect(page.locator('#sumNote')).toContainText('2');

  const noteLayout = await page.evaluate(() => {
    const module = document.querySelector('.dayNotesModule').getBoundingClientRect();
    const list = document.querySelector('#noteList').getBoundingClientRect();
    const editor = document.querySelector('#noteEditorPane').getBoundingClientRect();
    return {
      viewport: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      moduleRight: module.right,
      listBottom: list.bottom,
      editorTop: editor.top,
      editorRight: editor.right,
      editorWidth: editor.width
    };
  });
  expect(noteLayout.scrollWidth).toBeLessThanOrEqual(noteLayout.viewport + 1);
  expect(noteLayout.editorTop).toBeGreaterThanOrEqual(noteLayout.listBottom - 1);
  expect(noteLayout.editorRight).toBeLessThanOrEqual(noteLayout.moduleRight + 1);
  expect(noteLayout.editorWidth).toBeGreaterThan(250);

  const pinned = waitForNotePatch(page);
  await page.locator('#notePin').click();
  await pinned;
  await expect(page.locator('#noteList .dayNoteCard').first()).toContainText('Вторая заметка');
  await expect(page.locator('#noteList .dayNoteCard').first().locator('.dayNoteCardPin')).toHaveText('📌');

  // Return the second note to the regular group and move it above the first one.
  const unpinned = waitForNotePatch(page);
  await page.locator('#notePin').click();
  await unpinned;
  const moved = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'POST'
      && /^\/api\/v1\/notes\/\d+\/move$/.test(url.pathname)
      && response.status() === 200;
  });
  await page.locator('#noteMoveUp').click();
  await moved;
  await expect(page.locator('#noteList .dayNoteCard').first()).toContainText('Вторая заметка');

  const authoritativeReload = page.waitForResponse(response => new URL(response.url()).pathname === '/api/calendar' && response.status() === 200);
  await page.reload();
  await authoritativeReload;
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await selectDate(page, date);
  await openDayModule(page, 'notes');

  await expect(page.locator('#noteList .dayNoteCard')).toHaveCount(2);
  await expect(page.locator('#noteList .dayNoteCard').first()).toContainText('Вторая заметка');
  await expect(page.locator('#noteTitle')).toHaveValue('Вторая заметка');
  await expect(page.locator('#noteEdit')).toHaveValue('beta body');

  await page.locator('#noteList .dayNoteCard').filter({ hasText: 'Первая заметка' }).click();
  await expect(page.locator('#noteTitle')).toHaveValue('Первая заметка');
  await expect(page.locator('#noteEdit')).toHaveValue('alpha body');

  page.once('dialog', dialog => dialog.accept());
  const deleted = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'DELETE'
      && /^\/api\/v1\/notes\/\d+$/.test(url.pathname)
      && response.status() === 204;
  });
  await page.locator('#noteDelete').click();
  await deleted;

  await expect(page.locator('#noteList .dayNoteCard')).toHaveCount(1);
  await expect(page.locator('#noteList .dayNoteCard')).toContainText('Вторая заметка');
  await expect(page.locator('#noteTitle')).toHaveValue('Вторая заметка');
  await expect(page.locator('#noteEdit')).toHaveValue('beta body');
  await expect(page.locator(`#grid [data-date="${date}"] .noteCountBadge`)).toHaveCount(0);
});
