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
  const updated = waitForApi(page, 'PATCH', `/api/tasks/${taskId}`);
  await page.locator('#taskEditSave').click();
  await updated;
  await expect(page.locator('#taskEditModal')).toBeHidden();
  await expect(page.locator(`#taskList [data-task-id="${taskId}"]`)).toContainText(edited);

  await page.locator('#chips .plus').click();
  await expect(page.locator('#shiftTypeModal')).toBeVisible();
  const shiftName = `E2E shift ${Date.now()}`;
  await page.locator('#nsName').fill(shiftName);
  await page.locator('#nsStart').fill('09:00');
  await page.locator('#nsEnd').fill('18:00');
  await page.locator('#nsBreak').fill('60');
  const shiftCreated = waitForApi(page, 'POST', '/api/shift-types');
  await page.locator('#shiftTypeSave').click();
  await shiftCreated;
  await expect(page.locator('#customList')).toContainText(shiftName);
  await page.locator('#shiftTypeClose').click();
  await expect(page.locator('#shiftTypeModal')).toBeHidden();
  await expect(page.locator('#chips')).toContainText(shiftName);
});
