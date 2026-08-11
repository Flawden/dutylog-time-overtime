const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule,
  openView
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
  const created = waitForApi(page, 'POST', '/api/v1/tasks');
  await page.locator('#taskEditSave').click();
  const task = await (await created).json();

  const row = page.locator(`#taskList [data-task-id="${task.id}"]`);
  const detailsLoaded = waitForApi(page, 'GET', `/api/v1/tasks/${task.id}`);
  await row.locator('.taskItemBody').click();
  await detailsLoaded;
  await expect(page.locator('#taskDetailsModal')).toBeVisible();
  await expect(page.locator('#taskEditModal')).toBeHidden();
  await expect(page.locator('#taskDetailsTitle')).toHaveText(title);
  await expect(page.locator('#taskDetailsDescriptionText')).toHaveText(description);
  await expect(page.locator('#taskDetailsChecklist')).toContainText('Проверить детали');

  await page.locator('#taskDetailsEdit').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  await page.locator('#taskEditAdvanced').evaluate(element => { element.open = true; });
  await expect(page.locator('#taskEditDescription')).toHaveValue(description);
  await page.locator('#taskEditDescription').fill(`${description}\nОбновлено`);
  const updated = waitForApi(page, 'PATCH', `/api/v1/tasks/${task.id}`);
  await page.locator('#taskEditSave').click();
  await updated;

  await row.locator('.taskItemBody').click();
  await expect(page.locator('#taskDetailsDescriptionText')).toContainText('Обновлено');
});

test('timed task deadline and reminder keep one instant across timezone changes', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'task-zone' });

  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Asia/Yekaterinburg');
  let profileSaved = waitForApi(page, 'PUT', '/api/v1/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await expect(page.locator('#timeSettingsStatus')).toContainText(/сохранено|saved/i);

  const sourceDeadline = await page.evaluate(() => {
    const instant = new Date(Date.now() - 10 * 60_000);
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone:'Asia/Yekaterinburg', year:'numeric', month:'2-digit', day:'2-digit',
      hour:'2-digit', minute:'2-digit', hourCycle:'h23'
    }).formatToParts(instant).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
    return { date:`${parts.year}-${parts.month}-${parts.day}`, time:`${parts.hour}:${parts.minute}` };
  });

  const title = `Timezone deadline ${Date.now()}`;
  const task = await page.evaluate(({ title, sourceDeadline }) => jfetch('/api/tasks', {
    method:'POST',
    body:{
      date:sourceDeadline.date,
      text:title,
      dueDate:sourceDeadline.date,
      dueTime:sourceDeadline.time,
      reminderEnabled:true,
      reminderMinutesBefore:0
    }
  }), { title, sourceDeadline });

  expect(task.deadlineAbsolute).toBe(true);
  expect(task.dueDate).toBe(sourceDeadline.date);
  expect(task.dueTime).toBe(sourceDeadline.time);
  expect(task.dueSourceTimezone).toBe('Asia/Yekaterinburg');
  expect(task.overdue).toBe(true);

  await page.evaluate(() => jfetch('/api/notifications/settings', {
    method:'PATCH',
    body:{ taskRemindersEnabled:true, shiftRemindersEnabled:false,
      importantDayRemindersEnabled:false, tomorrowDigestEnabled:false }
  }));
  const before = await page.evaluate(async ({ date, id }) => {
    const reminders = await jfetch(`/api/notifications/upcoming?from=${date}&to=${date}&includePast=true`);
    return reminders.find(item => item.id === `task:${id}`);
  }, { date:sourceDeadline.date, id:task.id });
  expect(before).toBeTruthy();
  expect(before.remindAt).toContain(sourceDeadline.time);
  expect(before.workTimezone).toBe('Asia/Yekaterinburg');

  await page.locator('#workTimezone').selectOption('Europe/Moscow');
  profileSaved = waitForApi(page, 'PUT', '/api/v1/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await expect(page.locator('#timeSettingsStatus')).toContainText(/сохранено|saved/i);

  const expected = await page.evaluate(instantValue => {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone:'Europe/Moscow', year:'numeric', month:'2-digit', day:'2-digit',
      hour:'2-digit', minute:'2-digit', hourCycle:'h23'
    }).formatToParts(new Date(instantValue)).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
    return { date:`${parts.year}-${parts.month}-${parts.day}`, time:`${parts.hour}:${parts.minute}` };
  }, before.remindAtInstant);

  const projected = await page.evaluate(id => jfetch(`/api/tasks/${id}`), task.id);
  expect(projected.dueDate).toBe(expected.date);
  expect(projected.dueTime).toBe(expected.time);
  expect(projected.dueSourceDate).toBe(sourceDeadline.date);
  expect(projected.dueSourceTime).toBe(sourceDeadline.time);
  expect(projected.dueSourceTimezone).toBe('Asia/Yekaterinburg');
  expect(projected.overdue).toBe(true);

  const after = await page.evaluate(async ({ date, id }) => {
    const reminders = await jfetch(`/api/notifications/upcoming?from=${date}&to=${date}&includePast=true`);
    return reminders.find(item => item.id === `task:${id}`);
  }, { date:expected.date, id:task.id });
  expect(after).toBeTruthy();
  expect(after.remindAtInstant).toBe(before.remindAtInstant);
  expect(after.remindAt).toBe(`${expected.date}T${expected.time}`);
  expect(after.workTimezone).toBe('Europe/Moscow');

  await openView(page, 'tasks');
  const row = page.locator('#taskBoardList .taskBoardItem', { hasText:title });
  await expect(row).toBeVisible();
  await expect(row).toContainText(expected.time);
  await expect(row).toHaveClass(/overdue/);
  await row.locator('.taskBoardBody').click();
  await expect(page.locator('#taskDetailsModal')).toBeVisible();
  await expect(page.locator('#taskDetailsFacts')).toContainText(expected.time);
  await expect(page.locator('#taskDetailsFacts')).toContainText('Asia/Yekaterinburg');
  await expect(page.locator('#taskDetailsHint')).toContainText(/просрочена|overdue/i);
});
