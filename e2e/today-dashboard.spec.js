const { test, expect } = require('./fixtures');
const { registerAndOnboard, waitForApi, openView, selectDate } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('Today Dashboard composes the day and opens existing feature flows', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'today' });

  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('[data-vue-shell-navigation] [data-route="today"]')).toHaveAttribute('aria-current', 'page');
  await expect(page.locator('#todayDateStrip .todayDateChip')).toHaveCount(7);
  await expect(page.locator('#todayShiftCard')).toBeVisible();
  await expect(page.locator('#todayOvertimeBalance')).toBeVisible();
  await expect(page.locator('#todayTaskList')).toBeVisible();

  const workDate = await page.evaluate(async () => (await jfetch('/api/v1/time/context')).workDate);

  await page.evaluate(async date => {
    await jfetch('/api/overtime/credits', {
      method:'POST',
      body:{ date, hours:5, reason:'Today visual parity E2E' }
    });
  }, workDate);
  await page.reload();
  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('#todayOvertimeEarned')).toHaveText('5 ч');
  await expect(page.locator('#todayOvertimeUsed')).toHaveText('0 ч');
  await expect(page.locator('#todayOvertimeBalance')).toHaveText('5 ч');
  const balanceProgress = page.locator('#todayOvertimeProgress');
  await expect(balanceProgress).toHaveAttribute('data-balance-percent', '100.0');
  expect(await balanceProgress.evaluate(node => node.getBoundingClientRect().width)).toBeGreaterThan(0);

  await openView(page, 'calendar');
  await selectDate(page, workDate);
  const shiftSaved = waitForApi(page, 'PUT', `/api/days/${workDate}`);
  await page.locator('#chips [data-shift-type-id]').first().click();
  await shiftSaved;
  await page.locator('#pClose').click();
  await openView(page, 'today');
  const todayChip = page.locator(`#todayDateStrip .todayDateChip[data-date="${workDate}"]`);
  await expect(todayChip).toHaveClass(/hasShift/);
  await expect(todayChip).toHaveAttribute('style', /--shift-color:/);
  await expect(todayChip.locator('.todayDateShiftLabel')).toBeVisible();
  const freeChip = page.locator('#todayDateStrip .todayDateChip.isScheduleFree').first();
  await expect(freeChip).toBeVisible();
  await expect(freeChip.locator('.todayDatePalm')).toBeVisible();

  const tomorrow = new Date(`${workDate}T00:00:00Z`);
  tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
  const tomorrowKey = tomorrow.toISOString().slice(0, 10);
  const importantTitle = `Parity tomorrow ${Date.now()}`;
  await openView(page, 'important');
  await page.locator('#importantBoardNew').click();
  await expect(page.locator('#importantEditModal')).toBeVisible();
  await page.locator('#importantEditName').fill(importantTitle);
  await page.locator('#importantEditStartDate').fill(tomorrowKey);
  const importantCreated = waitForApi(page, 'POST', '/api/v1/important-days');
  await page.locator('#importantEditSave').click();
  await importantCreated;
  await openView(page, 'today');
  const upcomingTomorrow = page.locator('#todayUpcomingList .todayUpcomingRow', { hasText: importantTitle });
  await expect(upcomingTomorrow).toBeVisible();
  await expect(upcomingTomorrow.locator('strong')).toHaveText('завтра');
  await expect(page.locator(`#todayDateStrip .todayDateChip[data-date="${tomorrowKey}"] .todayDateImportantGlyph`)).toBeVisible();

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
  await expect(page.locator(`#todayDateStrip .todayDateChip[data-date="${workDate}"] .todayDateTaskCount`)).toHaveText('1');

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
