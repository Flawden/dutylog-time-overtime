const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  toggleModule
} = require('./helpers');

test('task data survives disabling and re-enabling the Tasks module', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'basic', prefix: 'tasks' });
  await toggleModule(page, 'tasks', true);
  await expect(page.locator('#tabbar a[data-view="tasks"]')).toBeVisible();

  await page.locator('#tabbar a[data-view="calendar"]').click();
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  const tasksSection = page.locator('#accTasks');
  await expect(tasksSection).toBeVisible();
  if (!(await tasksSection.evaluate(element => element.open))) {
    await tasksSection.locator('summary').click();
  }

  const taskText = `Browser task ${Date.now()}`;
  await page.locator('#taskCreateForDay').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await page.locator('#taskEditText').fill(taskText);
  await page.locator('#taskEditAdvanced').evaluate(element => { element.open = true; });
  await page.locator('#taskEditCategory').fill('E2E');
  await page.locator('#taskEditTags').fill('Browser, Regression, browser');
  const taskCreated = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskEditSave').click();
  await taskCreated;
  await expect(page.locator('#taskEditModal')).toBeHidden();
  const row = page.locator('#taskList .taskItem', { hasText: taskText });
  await expect(row).toBeVisible();

  const taskId = await row.getAttribute('data-task-id');
  expect(taskId).toBeTruthy();
  const taskCompleted = waitForApi(page, 'PATCH', `/api/tasks/${taskId}`);
  await row.locator('input[type="checkbox"]').check();
  await taskCompleted;
  await expect(row).toHaveClass(/done/);

  await toggleModule(page, 'tasks', false);
  await expect(page.locator('#tabbar a[data-view="tasks"]')).toBeHidden();
  await toggleModule(page, 'tasks', true);
  await expect(page.locator('#tabbar a[data-view="tasks"]')).toBeVisible();

  await page.locator('#tabbar a[data-view="calendar"]').click();
  await selectDate(page, date);
  const reopenedTasks = page.locator('#accTasks');
  if (!(await reopenedTasks.evaluate(element => element.open))) {
    await reopenedTasks.locator('summary').click();
  }
  const restored = page.locator(`#taskList [data-task-id="${taskId}"]`);
  await expect(restored).toContainText(taskText);
  await expect(restored).toHaveClass(/done/);
  await expect(restored.locator('input[type="checkbox"]')).toBeChecked();
});


test('quick capture survives the fast Inbox flow and converts into a task', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await registerAndOnboard(page, { preset: 'basic', prefix: 'inbox' });
  await toggleModule(page, 'tasks', true);

  const thought = `Capture thought ${Date.now()}`;
  await page.locator('#globalQuickAdd').click();
  await expect(page.locator('#quickActionsModal')).toBeVisible();
  await page.locator('#quickActionCapture').click();
  await expect(page.locator('#quickCaptureModal')).toBeVisible();
  await page.locator('#quickCaptureText').fill(thought);
  const captured = waitForApi(page, 'POST', '/api/inbox', 201);
  await page.locator('#quickCaptureSave').click();
  await captured;

  await page.locator('#tabbar a[data-view="tasks"]').click();
  const inboxRow = page.locator('#inboxList .inboxItem', { hasText: thought });
  await expect(inboxRow).toBeVisible();
  await inboxRow.getByRole('button', { name: 'В задачу' }).click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await expect(page.locator('#taskEditText')).toHaveValue(thought);
  const converted = page.waitForResponse(response => response.request().method() === 'POST' && /\/api\/inbox\/\d+\/task$/.test(new URL(response.url()).pathname) && response.status() === 200);
  await page.locator('#taskEditSave').click();
  await converted;
  await expect(page.locator('#taskBoardList .taskBoardItem', { hasText: thought })).toBeVisible();
});
