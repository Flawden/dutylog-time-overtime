const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  selectDate,
  waitForApi,
  openDayModuleById
} = require('./helpers');

test('vacation planner previews allowance and composes absence without replacing a shift', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 950 });
  await registerAndOnboard(page, { preset:'full', prefix:'vacation-planner' });
  const start = await currentLocalDateKey(page);
  const end = await page.evaluate(key => {
    const [y,m,d] = key.split('-').map(Number);
    const value = new Date(Date.UTC(y, m - 1, d + 13));
    return `${value.getUTCFullYear()}-${String(value.getUTCMonth()+1).padStart(2,'0')}-${String(value.getUTCDate()).padStart(2,'0')}`;
  }, start);

  await selectDate(page, start);
  const shiftSaved = waitForApi(page, 'PUT', `/api/days/${start}`);
  await page.locator('#chips [data-shift-type-id]').first().click();
  await shiftSaved;
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);

  await openView(page, 'vacation');
  await expect(page.locator('#vacationAvailable')).toContainText('28');
  await page.locator('#vacationComposerOpen').click();
  await expect(page.locator('#vacationType option')).toHaveCount(5);
  await page.locator('#vacationTitle').fill('E2E отпуск');
  await page.locator('#vacationStart').fill(start);
  await page.locator('[data-vacation-days="14"]').click();
  await expect(page.locator('#vacationEnd')).toHaveValue(end);

  const previewResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/vacation-planner/preview'
    && response.request().method() === 'POST' && response.status() === 200);
  await page.locator('#vacationPreviewBtn').click();
  const preview = await (await previewResponse).json();
  expect(preview.calendarDays).toBe(14);
  expect(preview.countedDays).toBe(14);
  expect(preview.remainingAfter).toBe(14);
  await expect(page.locator('#vacationPreview')).toBeVisible();
  await expect(page.locator('#vacationPreview')).toContainText('14');

  const createdResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/vacation-planner/absences'
    && response.request().method() === 'POST' && response.status() === 201);
  await page.locator('#vacationSaveBtn').click();
  const created = await (await createdResponse).json();
  expect(created.title).toBe('E2E отпуск');
  expect(created.calendarDays).toBe(14);
  await expect(page.locator('#vacationPeriodList .vacationPeriodCard', { hasText:'E2E отпуск' })).toBeVisible();
  await expect(page.locator('#vacationPlanned')).toContainText('14');
  await expect(page.locator('#vacationRemaining')).toContainText('14');

  await selectDate(page, start);
  await openDayModuleById(page, 'accVacation');
  await expect(page.locator('#vacationDayList .vacationDayItem', { hasText:'E2E отпуск' })).toBeVisible();
  await expect(page.locator(`#grid [data-date="${start}"] .absenceFact`)).toContainText('E2E отпуск');
  await expect(page.locator(`#grid [data-date="${start}"] .plannedShiftGhost`)).toContainText('По графику');

  await page.locator('#vacationDayList .vacationDayItem', { hasText:'E2E отпуск' }).click();
  await expect(page.locator('[data-vue-domain-route="vacation"]')).toBeVisible();
  await expect(page.locator('#vacationEditorTitle')).toContainText('Редактировать');
  await expect(page.locator('#vacationStart')).toHaveValue(start);
  await expect(page.locator('#vacationEnd')).toHaveValue(end);

  page.once('dialog', dialog => dialog.accept());
  const deletedResponse = page.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/vacation-planner/absences/${created.id}`
    && response.request().method() === 'DELETE' && response.status() === 204);
  await page.locator(`[data-delete-absence="${created.id}"]`).click();
  await deletedResponse;
  await expect(page.locator('#vacationPeriodList .vacationPeriodCard', { hasText:'E2E отпуск' })).toHaveCount(0);
  await expect(page.locator('#vacationPlanned')).toContainText('0');

  await selectDate(page, start);
  await expect(page.locator('#chips [data-shift-type-id][aria-pressed="true"]')).toHaveCount(1);
});
