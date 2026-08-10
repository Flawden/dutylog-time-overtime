const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  selectDate,
  waitForApi,
  openDayModule
} = require('./helpers');

test('task and shift type editors use complete modal forms', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'editors' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await openDayModule(page, 'tasks');

  const original = `Modal task ${Date.now()}`;
  await page.locator('#taskCreateForDay').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await page.locator('#taskEditText').fill(original);
  const created = waitForApi(page, 'POST', '/api/v1/tasks');
  await page.locator('#taskEditSave').click();
  await created;
  await expect(page.locator('#taskEditModal')).toBeHidden();

  const row = page.locator('#taskList .taskItem', { hasText: original });
  await expect(row).toBeVisible();
  const taskId = await row.getAttribute('data-task-id');
  await row.locator('.taskItemBody').click();
  await expect(page.locator('#taskDetailsModal')).toBeVisible();
  await page.locator('#taskDetailsEdit').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await page.locator('#taskEditAdvanced').evaluate(element => { element.open = true; });

  const edited = `${original} edited`;
  await page.locator('#taskEditText').fill(edited);
  await page.locator('#taskEditCategory').fill('modal-e2e');
  await page.locator('#taskEditPriority').selectOption('HIGH');
  await page.locator('#taskEditAllDay').uncheck();
  await page.locator('#taskEditStartTime').fill('17:41');
  await page.locator('#taskEditDuration').fill('45');
  await page.locator('#taskEditDuration').dispatchEvent('input');
  await page.locator('#taskEditDueDate').fill(date);
  await expect(page.locator('#taskEditDueTime')).toHaveAttribute('step', '60');
  await page.locator('#taskEditDueTime').fill('18:30');
  expect(await page.locator('#taskEditDueTime').evaluate(input => input.checkValidity())).toBe(true);
  await page.locator('#taskEditReminderEnabled').check();
  await page.locator('#taskEditReminderBefore').fill('3');
  const updated = waitForApi(page, 'PATCH', `/api/v1/tasks/${taskId}`);
  await page.locator('#taskEditSave').click();
  await updated;
  await expect(page.locator('#taskEditModal')).toBeHidden();
  await expect(page.locator(`#taskList [data-task-id="${taskId}"]`)).toContainText(edited);
  await page.locator(`#taskList [data-task-id="${taskId}"] .taskItemBody`).click();
  await expect(page.locator('#taskDetailsModal')).toBeVisible();
  await page.locator('#taskDetailsEdit').click();
  await expect(page.locator('#taskEditCategory')).toHaveValue('modal-e2e');
  await expect(page.locator('#taskEditPriority')).toHaveValue('HIGH');
  await expect(page.locator('#taskEditDueDate')).toHaveValue(date);
  await expect(page.locator('#taskEditStartTime')).toHaveValue('17:41');
  await expect(page.locator('#taskEditEndTime')).toHaveValue('18:26');
  await expect(page.locator('#taskEditDueTime')).toHaveValue('18:30');
  await expect(page.locator('#taskEditReminderEnabled')).toBeChecked();
  await expect(page.locator('#taskEditReminderBefore')).toHaveValue('3');
  await page.locator('#taskEditCancel').click();

  await page.locator('#chips .plus').click();
  await expect(page.locator('#shiftTypeModal')).toBeVisible();
  const shiftName = `E2E shift ${Date.now()}`;
  await page.locator('#nsName').fill(shiftName);
  await page.locator('#nsStart').fill('09:00');
  await page.locator('#nsEnd').fill('18:00');
  await page.locator('#nsBreak').fill('60');
  const shiftCreated = waitForApi(page, 'POST', '/api/shift-types', 201);
  await page.locator('#shiftTypeSave').click();
  const shift = await (await shiftCreated).json();
  const shiftRow = page.locator('#customList > div', { hasText: shiftName });
  await expect(shiftRow).toBeVisible();
  await shiftRow.getByRole('button', { name: /настроить|configure/i }).click();
  await expect(page.locator('#nsName')).toHaveValue(shiftName);
  await page.locator('#nsEnd').fill('19:00');
  const shiftUpdated = waitForApi(page, 'PATCH', `/api/shift-types/${shift.id}`);
  await page.locator('#shiftTypeSave').click();
  await shiftUpdated;
  await expect(page.locator('#customList > div', { hasText: shiftName })).toContainText('19:00');
  await page.locator('#shiftTypeClose').click();
  await expect(page.locator('#shiftTypeModal')).toBeHidden();

  const shiftChip = page.locator(`#chips [data-shift-type-id="${shift.id}"]`);
  await expect(shiftChip).toBeVisible();
  const assigned = waitForApi(page, 'PUT', `/api/days/${date}`);
  const calendarReloaded = waitForApi(page, 'GET', '/api/v1/calendar');
  await shiftChip.click();
  await assigned;
  await calendarReloaded;
  await page.reload();
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator(`#grid [data-date="${date}"]`)).toContainText(shiftName);

  await openView(page, 'calendar');
  await page.locator('[data-calendar-mode="day"]').click();
  const timelineTask = page.locator('#calendarTimelineCanvas .calendarTimelineEvent.task', { hasText: edited });
  await expect(timelineTask).toBeVisible();
  await expect(timelineTask).toContainText('17:41');
  const eventLayout = await timelineTask.evaluate(element => {
    const box = element.getBoundingClientRect();
    const title = element.querySelector('b')?.getBoundingClientRect();
    const detail = element.querySelector('span')?.getBoundingClientRect();
    return {
      height: box.height,
      titleInside: Boolean(title && title.top >= box.top - 0.5 && title.bottom <= box.bottom + 0.5),
      detailInside: Boolean(detail && detail.top >= box.top - 0.5 && detail.bottom <= box.bottom + 0.5)
    };
  });
  expect(eventLayout.height).toBeGreaterThanOrEqual(47);
  expect(eventLayout.titleInside).toBe(true);
  expect(eventLayout.detailInside).toBe(true);
});
