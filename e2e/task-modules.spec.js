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
  await page.locator('#taskText').fill(taskText);
  await page.locator('#taskCategory').fill('e2e');
  const taskCreated = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskAdd').click();
  await taskCreated;
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
