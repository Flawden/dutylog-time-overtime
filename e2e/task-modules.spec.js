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
  await page.locator('#quickActionText').fill(thought);
  const captured = waitForApi(page, 'POST', '/api/inbox', 201);
  await page.locator('#quickActionInbox').click();
  await captured;

  await page.locator('#tabbar a[data-view="tasks"]').click();
  await page.locator('#taskInboxCard > summary').click();
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

test('task subtasks keep order, update progress and require explicit parent completion', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'basic', prefix: 'subtasks' });
  await toggleModule(page, 'tasks', true);

  await page.locator('#tabbar a[data-view="calendar"]').click();
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  const tasksSection = page.locator('#accTasks');
  if (!(await tasksSection.evaluate(element => element.open))) {
    await tasksSection.locator('summary').click();
  }

  const taskText = `Subtask release ${Date.now()}`;
  await page.locator('#taskCreateForDay').click();
  await page.locator('#taskEditText').fill(taskText);
  await page.locator('#taskEditSubtasks').evaluate(element => { element.open = true; });
  await page.locator('#taskEditSubtaskAdd').click();
  await page.locator('#taskEditSubtaskList .taskSubtaskEditorRow').nth(0).locator('input[type="text"]').fill('Проверить CI');
  await page.locator('#taskEditSubtaskAdd').click();
  await page.locator('#taskEditSubtaskList .taskSubtaskEditorRow').nth(1).locator('input[type="text"]').fill('Проверить staging');

  const created = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskEditSave').click();
  await created;

  const task = page.locator('#taskList .taskItem', { hasText: taskText });
  await expect(task).toBeVisible();
  await expect(task.locator('.taskSubtaskProgress')).toContainText('0/2');
  await task.locator('.taskSubtasksInline > summary').click();
  await expect(task.locator('.taskSubtaskInlineText').nth(0)).toHaveText('Проверить CI');
  await expect(task.locator('.taskSubtaskInlineText').nth(1)).toHaveText('Проверить staging');

  const taskId = await task.getAttribute('data-task-id');
  const firstCheckbox = task.locator('.taskSubtaskInlineRow').nth(0).locator('input[type="checkbox"]');
  const childUpdated = page.waitForResponse(response => response.request().method() === 'PATCH'
    && new RegExp(`/api/tasks/${taskId}/subtasks/\\d+$`).test(new URL(response.url()).pathname)
    && response.status() === 200);
  await firstCheckbox.check();
  await childUpdated;
  await expect(task.locator('.taskSubtaskProgress')).toContainText('1/2');

  page.once('dialog', dialog => dialog.accept());
  const parentUpdated = waitForApi(page, 'PATCH', `/api/tasks/${taskId}`);
  await task.locator(':scope > input[type="checkbox"]').check();
  await parentUpdated;
  await expect(task).toHaveClass(/done/);
  await expect(task.locator('.taskSubtaskProgress')).toContainText('2/2');
});

test('task polish validates deadlines, persists subtask dates and keeps completed tasks below open tasks', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await registerAndOnboard(page, { preset: 'basic', prefix: 'task-polish' });
  await toggleModule(page, 'tasks', true);

  await page.locator('#tabbar a[data-view="calendar"]').click();
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  const tasksSection = page.locator('#accTasks');
  if (!(await tasksSection.evaluate(element => element.open))) {
    await tasksSection.locator('summary').click();
  }

  const previousDate = await page.evaluate(value => {
    const [year, month, day] = value.split('-').map(Number);
    const instant = new Date(Date.UTC(year, month - 1, day));
    instant.setUTCDate(instant.getUTCDate() - 1);
    return instant.toISOString().slice(0, 10);
  }, date);

  const completedText = `Polished task ${Date.now()}`;
  await page.locator('#taskCreateForDay').click();
  await page.locator('#taskEditText').fill(completedText);
  await page.locator('#taskEditAdvanced').evaluate(element => { element.open = true; });
  await page.locator('#taskEditDueDate').fill(previousDate);
  await page.locator('#taskEditSave').click();
  await expect(page.locator('#taskEditMessage')).toHaveText('Дедлайн не может быть раньше окончания запланированного интервала.');
  await expect(page.locator('#taskEditModal')).toBeVisible();

  await page.locator('#taskEditDueDate').fill(date);
  await page.locator('#taskEditSubtasks').evaluate(element => { element.open = true; });
  await page.locator('#taskEditSubtaskAdd').click();
  const subtaskRow = page.locator('#taskEditSubtaskList .taskSubtaskEditorRow').first();
  await subtaskRow.locator('input[type="text"]').fill('Проверить срок');
  await subtaskRow.locator('input[type="date"]').fill(date);
  const created = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskEditSave').click();
  await created;

  const completedTask = page.locator('#taskList .taskItem', { hasText: completedText });
  await expect(completedTask.locator('.taskSubtaskProgress')).toContainText('0/1');
  await expect(completedTask.locator('.taskSubtaskProgress')).toHaveAttribute('role', 'progressbar');
  await completedTask.locator('.taskSubtasksInline > summary').click();
  await expect(completedTask.locator('.taskSubtaskInlineDue')).toContainText(date.split('-').reverse().join('.'));

  const openText = `Still open ${Date.now()}`;
  await page.locator('#taskCreateForDay').click();
  await page.locator('#taskEditText').fill(openText);
  const openCreated = waitForApi(page, 'POST', '/api/tasks');
  await page.locator('#taskEditSave').click();
  await openCreated;

  page.once('dialog', dialog => dialog.accept());
  const completedId = await completedTask.getAttribute('data-task-id');
  const parentUpdated = waitForApi(page, 'PATCH', `/api/tasks/${completedId}`);
  await completedTask.locator(':scope > input[type="checkbox"]').check();
  await parentUpdated;

  const rows = page.locator('#taskList .taskItem');
  await expect(rows.first()).toContainText(openText);
  await expect(rows.last()).toContainText(completedText);
  await expect(page.locator('#taskList .taskCompletionDivider')).toBeVisible();
});
