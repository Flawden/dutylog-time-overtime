const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  selectDate,
  openSelectedDayDetails,
  openDayModule,
  waitForApi
} = require('./helpers');

test('Notes and Important Events Next combine searchable notes with read-first timed events', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await registerAndOnboard(page, { preset:'full', prefix:'notes-events-next' });
  const date = await currentLocalDateKey(page);
  const eventTitle = `DutyLog event ${Date.now()}`;

  await openView(page, 'important');
  await page.locator('#importantBoardNew').click();
  await expect(page.locator('#importantEditModal')).toBeVisible();
  await page.locator('#importantEditName').fill(eventTitle);
  await page.locator('#importantEditType').selectOption('EVENT');
  await page.locator('#importantEditAllDay').uncheck();
  await page.locator('#importantEditStartDate').fill(date);
  await page.locator('#importantEditEndDate').fill(date);
  await page.locator('#importantEditStartTime').fill('18:10');
  await page.locator('#importantEditEndTime').fill('19:20');
  await page.locator('#importantEditPlace').fill('Дом сообщества');
  await page.locator('#importantEditCategory').fill('DutyLog');
  await page.locator('input[name="importantReminder"][value="30"]').check();

  const createdResponse = waitForApi(page, 'POST', '/api/v1/important-days');
  await page.locator('#importantEditSave').click();
  const created = await (await createdResponse).json();
  expect(created.eventType).toBe('EVENT');
  expect(created.allDay).toBe(false);
  expect(created.startInstant).toBeTruthy();
  expect(created.reminders).toContain(30);

  const row = page.locator('#importantBoardList .importantBoardRow', { hasText:eventTitle });
  await expect(row).toBeVisible();
  await expect(row).toContainText('18:10');
  await row.click();
  await expect(page.locator('#importantDetailsModal')).toBeVisible();
  await expect(page.locator('#importantDetailsTitle')).toContainText(eventTitle);
  await expect(page.locator('#importantDetailsBody')).toContainText('Дом сообщества');
  await page.locator('#importantDetailsClose').click();
  await expect(page.locator('#importantDetailsModal')).toBeHidden();

  await openView(page, 'calendar');
  await selectDate(page, date);
  await page.locator('[data-calendar-mode="day"]').click();
  const event = page.locator('#calendarTimelineCanvas .calendarTimelineEvent.important', { hasText:eventTitle });
  await expect(event).toBeVisible();
  await expect(event).toContainText('Дом сообщества');
  await event.click();
  await expect(page.locator('#importantDetailsModal')).toBeVisible();
  await page.locator('#importantDetailsClose').click();
  await expect(page.locator('#importantDetailsModal')).toBeHidden();

  await openSelectedDayDetails(page);
  await openDayModule(page, 'notes');
  const createdNote = waitForApi(page, 'POST', '/api/v1/notes', 201);
  await page.locator('#noteAdd').click();
  await createdNote;
  const patch = page.waitForResponse(response => /^\/api\/notes\/\d+$/.test(new URL(response.url()).pathname)
    && response.request().method() === 'PATCH' && response.status() === 200);
  await page.locator('#noteTitle').fill('Результаты DutyLog');
  await page.locator('#noteEdit').fill('Уникальная поисковая фраза: Nebula-27120');
  await patch;

  await page.locator('#noteFocusMode').click();
  await expect(page.locator('[data-vue-selected-notes]')).toHaveClass(/is-focus-mode/);
  await expect(page.locator('body')).toHaveClass(/note-focus-mode/);
  await expect(page.locator('#noteEdit')).toHaveValue('Уникальная поисковая фраза: Nebula-27120');
  await page.locator('#noteFocusMode').click();
  await expect(page.locator('[data-vue-selected-notes]')).not.toHaveClass(/is-focus-mode/);
  await expect(page.locator('body')).not.toHaveClass(/note-focus-mode/);

  await page.locator('#noteSearch').fill('Nebula-27120');
  await expect(page.locator('#noteSearchResults')).toBeVisible();
  await expect(page.locator('#noteSearchResults .noteSearchResult')).toHaveCount(1);
  await expect(page.locator('#noteSearchResults')).toContainText('Результаты DutyLog');
  await expect(page.locator('#noteExport')).toHaveAttribute('href', '/api/export/notes');
});
