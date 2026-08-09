const { test, expect } = require('./fixtures');
const { registerAndOnboard, waitForApi, openView } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('Today Dashboard composes the day and opens existing feature flows', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'today' });

  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('[data-vue-shell-navigation] [data-route="today"]')).toHaveAttribute('aria-current', 'page');
  await expect(page.locator('#todayDateStrip .todayDateChip')).toHaveCount(7);
  await expect(page.locator('#todayShiftCard')).toBeVisible();
  await expect(page.locator('#todayOvertimeBalance')).toBeVisible();
  await expect(page.locator('#todayTaskList')).toBeVisible();

  await page.locator('#todayQuickTask').click();
  await expect(page.locator('#taskEditModal')).toBeVisible();
  const date = await page.locator('#taskEditDate').inputValue();
  expect(date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  await page.locator('#taskEditText').fill('Проверить Today Dashboard');
  const created = waitForApi(page, 'POST', '/api/v1/tasks');
  await page.locator('#taskEditSave').click();
  await created;
  await expect(page.locator('#taskEditModal')).toBeHidden();
  await expect(page.locator('#todayTaskList')).toContainText('Проверить Today Dashboard');

  await page.locator('#todayOpenCalendar').click();
  await expect(page.locator('#view-calendar')).toBeVisible();
  await expect(page.locator('#calendarDayExperience')).toBeVisible();
  await expect(page.locator('[data-calendar-mode="day"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#calendarDayTitle')).not.toHaveText('—');

  await page.locator('[data-vue-shell-brand]').click();
  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('[data-vue-shell-navigation] [data-route="today"]')).toHaveAttribute('aria-current', 'page');
});


test('Today opens the neutral absence composer directly', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'today-absence' });

  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('#todayQuickAbsence')).toBeVisible();
  await expect(page.locator('#todayQuickAbsence')).toContainText('Оформить отсутствие');
  await page.locator('#todayQuickAbsence').click();

  await expect(page.locator('#absenceComposerModal')).toBeVisible();
  const date = await page.locator('#vacationStart').inputValue();
  expect(date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  await expect(page.locator('#vacationCompensation')).not.toHaveValue('OVERTIME_BANK');
  await page.locator('#absenceComposerClose').click();
  await expect(page.locator('#absenceComposerModal')).toBeHidden();

  await page.locator('#todayQuickMore').click();
  await expect(page.locator('#quickActionUsage')).toBeVisible();
  await expect(page.locator('#quickActionUsage')).toContainText('Оформить отсутствие');
});
