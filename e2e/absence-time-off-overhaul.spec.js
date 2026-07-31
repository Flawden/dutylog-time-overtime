const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, openView, selectDate, waitForApi, openDayModuleById } = require('./helpers');

test('partial time off keeps the planned shift and spends the independent hour balance', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 950 });
  await registerAndOnboard(page, { preset:'full', prefix:'time-off' });
  const date = await currentLocalDateKey(page);

  await selectDate(page, date);
  const shiftSaved = waitForApi(page, 'PUT', `/api/days/${date}`);
  await page.locator('#chips [data-shift-type-id]').first().click();
  await shiftSaved;
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);

  await openView(page, 'vacation');
  await expect(page.locator('#vacationType option')).toHaveCount(5);

  await page.locator('#timeOffBalanceHours').fill('8');
  await page.locator('#defaultTimeOffDayHours').fill('8');
  const settingsSaved = waitForApi(page, 'PATCH', '/api/vacation-planner/settings');
  await page.locator('#vacationSettingsForm button[type="submit"]').click();
  await settingsSaved;
  await expect(page.locator('#timeOffRemaining')).toContainText('8');

  const timeOffOption = page.locator('#vacationType option', { hasText:'Отгул' });
  const timeOffValue = await timeOffOption.getAttribute('value');
  await page.locator('#vacationType').selectOption(timeOffValue);
  await page.locator('#vacationCoverage').selectOption('PARTIAL');
  await expect(page.locator('#vacationPartialTimes')).toBeVisible();
  await expect(page.locator('#vacationEnd')).toBeDisabled();
  await page.locator('#vacationTitle').fill('Врач');
  await page.locator('#vacationStart').fill(date);
  await page.locator('#vacationStartTime').fill('09:00');
  await page.locator('#vacationEndTime').fill('13:00');

  const previewResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/vacation-planner/preview'
    && response.request().method() === 'POST' && response.status() === 200);
  await page.locator('#vacationPreviewBtn').click();
  const preview = await (await previewResponse).json();
  expect(preview.coverage).toBe('PARTIAL');
  expect(preview.durationMinutes).toBe(240);
  expect(preview.timeOffRemainingAfter).toBe(240);
  await expect(page.locator('#vacationPreview')).toContainText('4 ч');

  const createdResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/vacation-planner/absences'
    && response.request().method() === 'POST' && response.status() === 201);
  await page.locator('#vacationSaveBtn').click();
  const created = await (await createdResponse).json();
  expect(created.coverage).toBe('PARTIAL');
  expect(created.chargedMinutes).toBe(240);
  await expect(page.locator('#timeOffRemaining')).toContainText('4');
  await expect(page.locator('#vacationPeriodList .vacationPeriodCard', { hasText:'Врач' })).toContainText('09:00–13:00');

  await selectDate(page, date);
  const cell = page.locator(`#grid [data-date="${date}"]`);
  await expect(cell.locator('.shift')).toBeVisible();
  await expect(cell.locator('.partialAbsenceBar')).toContainText('09:00–13:00');
  await openDayModuleById(page, 'accVacation');
  const item = page.locator('#vacationDayList .vacationDayItem', { hasText:'Врач' });
  await expect(item).toContainText('Фактически');
  await expect(item).toContainText('По графику');

  page.once('dialog', dialog => dialog.accept());
  const deleted = page.waitForResponse(response => new URL(response.url()).pathname === `/api/vacation-planner/absences/${created.id}`
    && response.request().method() === 'DELETE' && response.status() === 204);
  await item.click();
  await page.locator(`[data-delete-absence="${created.id}"]`).click();
  await deleted;
  await expect(page.locator('#timeOffRemaining')).toContainText('8');
});
