const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
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
  await page.locator('#taskText').fill(original);
  const created = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskAdd').click();
  await created;

  const row = page.locator('#taskList .taskItem', { hasText: original });
  await expect(row).toBeVisible();
  const taskId = await row.getAttribute('data-task-id');
  await row.getByTitle(/Редактировать задачу|Edit task/i).click();
  await expect(page.locator('#taskEditModal')).toBeVisible();

  const edited = `${original} edited`;
  await page.locator('#taskEditText').fill(edited);
  await page.locator('#taskEditCategory').fill('modal-e2e');
  await page.locator('#taskEditPriority').selectOption('HIGH');
  await page.locator('#taskEditDueDate').fill(date);
  await page.locator('#taskEditDueTime').fill('18:45');
  await page.locator('#taskEditReminderEnabled').check();
  await page.locator('#taskEditReminderBefore').fill('3');
  const updated = waitForApi(page, 'PATCH', `/api/tasks/${taskId}`);
  await page.locator('#taskEditSave').click();
  await updated;
  await expect(page.locator('#taskEditModal')).toBeHidden();
  await expect(page.locator(`#taskList [data-task-id="${taskId}"]`)).toContainText(edited);
  await page.locator(`#taskList [data-task-id="${taskId}"]`).getByTitle(/Редактировать задачу|Edit task/i).click();
  await expect(page.locator('#taskEditCategory')).toHaveValue('modal-e2e');
  await expect(page.locator('#taskEditPriority')).toHaveValue('HIGH');
  await expect(page.locator('#taskEditDueDate')).toHaveValue(date);
  await expect(page.locator('#taskEditDueTime')).toHaveValue('18:45');
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
  await shiftChip.click();
  await assigned;
  await page.reload();
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator(`#grid [data-date="${date}"]`)).toContainText(shiftName);
});
