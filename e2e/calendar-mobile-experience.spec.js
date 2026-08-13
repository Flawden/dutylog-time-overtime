const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, openView, selectDate, waitForApi, openDayModule } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('calendar switches month week and hourly day while preserving the focused date', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'calexp' });
  const today = await currentLocalDateKey(page);
  await openView(page, 'calendar');

  await expect(page.locator('.calendarModeSwitch')).toBeVisible();
  await expect(page.locator('[data-calendar-mode="month"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#calendarMonthExperience')).toBeVisible();

  // A fresh account has no assigned days. Create the shift fact explicitly so
  // this test verifies shift-color rendering instead of depending on ambient data.
  await selectDate(page, today);
  const shiftSaved = waitForApi(page, 'PUT', `/api/days/${today}`);
  await page.locator('#chips [data-shift-type-id]').first().click();
  await shiftSaved;
  await page.locator('#pClose').click();
  await expect(page.locator('#panel')).toBeHidden();

  const shiftCell = page.locator(`#grid .cell[data-date="${today}"]`);
  await expect(shiftCell).toHaveClass(/hasShift/);
  await expect(shiftCell).toHaveAttribute('style', /--shift-color:/);
  await expect(shiftCell).toHaveAttribute('aria-label', /Смена|Shift/);
  await expect(shiftCell.locator('.shift')).toBeHidden();

  const freeCell = page.locator('#grid .cell.isScheduleFree:not(.outside)').first();
  await expect(freeCell).toBeVisible();
  await expect(freeCell.locator('.calendarDayOffWatermark')).toBeVisible();
  await expect(freeCell).toHaveAttribute('aria-label', /свободный день|day off/i);

  await selectDate(page, today);
  await openDayModule(page, 'important_dates');
  await expect(page.locator('#impDate')).toHaveValue(today);
  const importantTitle = `Calendar all-day ${Date.now()}`;
  await page.locator('#impTitle').fill(importantTitle);
  await page.locator('#impRepeat').selectOption('NONE');
  const importantCreated = waitForApi(page, 'POST', '/api/v1/important-days');
  await page.locator('#impAdd').click();
  await importantCreated;
  await page.locator('#pClose').click();
  await expect(page.locator('#panel')).toBeHidden();
  await expect(shiftCell.locator('.calendarCellImportantZone')).toBeVisible();
  await expect(shiftCell.locator('.calendarCellImportantZone')).toContainText('★');

  await page.locator('[data-calendar-mode="week"]').click();
  await expect(page.locator('#calendarWeekExperience')).toBeVisible();
  await expect(page.locator('#calendarWeekStrip .calendarWeekDay')).toHaveCount(7);
  const freeWeekDay = page.locator('#calendarWeekStrip .calendarWeekDay.isScheduleFree').first();
  await expect(freeWeekDay.locator('.calendarWeekPalm')).toBeVisible();
  await page.locator(`#calendarWeekStrip [data-date="${today}"]`).click();
  await expect(page.locator(`#calendarWeekStrip [data-date="${today}"]`)).toHaveClass(/isSelected/);

  await page.locator('[data-calendar-mode="day"]').click();
  await expect(page.locator('#calendarDayExperience')).toBeVisible();
  await expect(page.locator('#calendarTimelineHours span')).toHaveCount(13);
  await expect(page.locator('#calendarDayTitle')).not.toHaveText('—');
  await expect(page.locator('#calendarAllDay')).toBeVisible();
  await expect(page.locator('#calendarAllDay .calendarAllDayHead')).toContainText(/Весь день|All day/i);
  await expect(page.locator('#calendarAllDay .calendarAllDayItem.important')).toContainText(importantTitle);
  await expect(page.locator('#calendarTimelineCanvas .calendarTimelineEvent.reminder', { hasText: importantTitle })).toHaveCount(0);

  await page.reload();
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator('#view-calendar')).toBeVisible();
  await expect(page.locator('[data-calendar-mode="day"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#calendarDayExperience')).toBeVisible();
  await expect(page.locator('#calendarDayTitle')).not.toHaveText('—');

  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport + 1);
});
