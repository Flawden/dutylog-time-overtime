const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule
} = require('./helpers');

test('task details separate reading from editing and persist a long description', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'task-details' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await openDayModule(page, 'tasks');

  const title = `Task details ${Date.now()}`;
  const description = 'Контекст задачи\nhttps://stage.example.test/task';
  await page.locator('#taskCreateForDay').click();
  await page.locator('#taskEditText').fill(title);
  await page.locator('#taskEditAdvanced').evaluate(element => { element.open = true; });
  await page.locator('#taskEditDescription').fill(description);
  await page.locator('#taskEditSubtasks').evaluate(element => { element.open = true; });
  await page.locator('#taskEditSubtaskAdd').click();
  await page.locator('#taskEditSubtaskList .taskSubtaskEditorRow').first().locator('input[type="text"]').fill('Проверить детали');
  const created = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskEditSave').click();
  const task = await (await created).json();

  const row = page.locator(`#taskList [data-task-id="${task.id}"]`);
  const detailsLoaded = waitForApi(page, 'GET', `/api/tasks/${task.id}`);
  await row.locator('.taskItemBody').click();
  await detailsLoaded;
  await expect(page.locator('#taskDetailsModal')).toBeVisible();
  await expect(page.locator('#taskEditModal')).toBeHidden();
  await expect(page.locator('#taskDetailsTitle')).toHaveText(title);
  await expect(page.locator('#taskDetailsDescriptionText')).toHaveText(description);
  await expect(page.locator('#taskDetailsChecklist')).toContainText('Проверить детали');

  await page.locator('#taskDetailsEdit').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await expect(page.locator('#taskEditDescription')).toHaveValue(description);
  await page.locator('#taskEditDescription').fill(`${description}\nОбновлено`);
  const updated = waitForApi(page, 'PATCH', `/api/tasks/${task.id}`);
  await page.locator('#taskEditSave').click();
  await updated;

  await row.locator('.taskItemBody').click();
  await expect(page.locator('#taskDetailsDescriptionText')).toContainText('Обновлено');
});
