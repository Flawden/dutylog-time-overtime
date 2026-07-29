const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  selectDate,
  waitForApi
} = require('./helpers');

test('Tasks & Inbox Next keeps planning, deadline, project and capture as separate concepts', async ({ page }) => {
  await registerAndOnboard(page, { preset:'full', prefix:'tasks-next' });
  const date = await currentLocalDateKey(page);
  const title = `Planned interval ${Date.now()}`;
  const project = `DutyLog ${Date.now()}`;

  await openView(page, 'tasks');
  await page.locator('#taskBoardCreate').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await page.locator('#taskEditText').fill(title);
  await page.locator('#taskEditDate').fill(date);
  await page.locator('#taskEditAllDay').uncheck();
  await page.locator('#taskEditStartTime').fill('18:33');
  await page.locator('[data-task-duration="45"]').click();
  await expect(page.locator('#taskEditEndTime')).toHaveValue('19:18');
  await expect(page.locator('#taskPlanningSummary')).toContainText('45');
  await page.locator('#taskEditAdvanced').evaluate(element => { element.open = true; });
  await page.locator('#taskEditProject').fill(project);
  await page.locator('#taskEditCategory').fill('release');
  await page.locator('#taskEditDueDate').fill(date);
  await page.locator('#taskEditDueTime').fill('20:00');

  const createdResponse = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskEditSave').click();
  const created = await (await createdResponse).json();
  expect(created.allDay).toBe(false);
  expect(created.scheduledStartTime).toBe('18:33');
  expect(created.scheduledEndTime).toBe('19:18');
  expect(created.scheduledDurationMinutes).toBe(45);
  expect(created.project).toBe(project);
  expect(created.dueTime).toBe('20:00');

  const row = page.locator('#taskBoardList .taskBoardItem', { hasText:title });
  await expect(row).toBeVisible();
  await expect(row).toContainText(project);
  await expect(row).toContainText('18:33');
  await row.locator('.taskBoardBody').click();
  await expect(page.locator('#taskDetailsModal')).toBeVisible();
  await expect(page.locator('#taskDetailsScheduleMain')).toContainText('18:33');
  await expect(page.locator('#taskDetailsScheduleMain')).toContainText('19:18');
  await expect(page.locator('#taskDetailsScheduleMain')).toContainText('45');
  await expect(page.locator('#taskDetailsFacts')).toContainText(project);
  await expect(page.locator('#taskDetailsFacts')).toContainText('20:00');
  await page.locator('#taskDetailsClose').click();

  await expect(page.locator('#taskBoardProject')).toContainText(project);
  await page.locator('#taskBoardProject').selectOption({ label:project });
  await expect(page.locator('#taskBoardList .taskBoardItem')).toHaveCount(1);

  const inbox = page.locator('#taskInboxCard');
  if (!(await inbox.evaluate(element => element.open))) await inbox.locator('summary').click();
  const firstInbox = `Needle ${Date.now()}`;
  const secondInbox = `Haystack ${Date.now()}`;
  await page.locator('#inboxQuickText').fill(firstInbox);
  let saved = waitForApi(page, 'POST', '/api/inbox', 201);
  await page.locator('#inboxQuickSave').click();
  await saved;
  await page.locator('#inboxQuickText').fill(secondInbox);
  saved = waitForApi(page, 'POST', '/api/inbox', 201);
  await page.locator('#inboxQuickSave').click();
  await saved;
  await page.locator('#inboxSearch').fill('Needle');
  await expect(page.locator('#inboxList .inboxItem')).toHaveCount(1);
  await expect(page.locator('#inboxList')).toContainText(firstInbox);
  await expect(page.locator('#inboxList')).not.toContainText(secondInbox);

  await selectDate(page, date);
  await page.locator('[data-calendar-mode="day"]').click();
  const event = page.locator('#calendarTimelineCanvas .calendarTimelineEvent.task', { hasText:title });
  await expect(event).toBeVisible();
  await expect(event).toContainText('18:33–19:18');
  const duration = await event.evaluate(element => Number.parseFloat(getComputedStyle(element).getPropertyValue('--duration')));
  expect(duration).toBeGreaterThan(3);

  await page.setViewportSize({ width:390, height:844 });
  await openView(page, 'tasks');
  await page.locator('#taskBoardCreate').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await expect(page.locator('#taskEditAllDay')).toBeVisible();
  await page.locator('#taskEditAllDay').uncheck();
  await expect(page.locator('#taskEditStartTime')).toBeVisible();
  await expect(page.locator('#taskEditDuration')).toBeVisible();
  await expect(page.locator('[data-task-duration="15"]')).toBeVisible();
});
