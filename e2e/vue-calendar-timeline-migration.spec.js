const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('Vue owns Today, Month, Week and Day while the selected-day editor remains compatible', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'vue-calendar' });

  await expect(page.locator('[data-vue-domain-route="today"][data-vue-domain-owner="calendar-timeline"]')).toBeVisible();
  await expect(page.locator('#view-today')).toHaveCount(1);
  await expect.poll(() => page.evaluate(() => Boolean(window.DutyLogVueDomains?.calendarTimeline?.ready())), { timeout: 30_000 }).toBe(true);

  await openView(page, 'calendar');
  const calendar = page.locator('[data-vue-domain-route="calendar"][data-vue-domain-owner="calendar-timeline"]');
  await expect(calendar).toBeVisible();
  await expect(page.locator('#view-calendar')).toHaveCount(1);
  await expect(page.locator('[data-calendar-mode="month"]')).toHaveAttribute('aria-pressed', 'true');
  await expect.poll(() => page.locator('#grid [data-date]').count()).toBeGreaterThanOrEqual(35);

  await page.locator('[data-calendar-mode="week"]').click();
  await expect(page.locator('[data-calendar-mode="week"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#calendarWeekStrip [data-date]')).toHaveCount(7);
  await expect(page.locator('#calendarWeekAgenda article')).toHaveCount(7);

  await page.locator('[data-calendar-mode="day"]').click();
  await expect(page.locator('[data-calendar-mode="day"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#calendarTimelineHours span')).toHaveCount(13);
  await expect(page.locator('#calendarDayOpenDetails')).toBeVisible();

  await page.locator('#calendarDayOpenDetails').click();
  await expect(page.locator('[data-calendar-mode="month"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#calendarLegacyPanelHost > #panel')).toBeVisible();
  await expect(page.locator('#calendarLegacyPanelHost > #panel')).toHaveCount(1);

  const snapshot = await page.evaluate(() => window.DutyLogVueDomains?.calendarTimeline?.snapshot());
  expect(snapshot).toMatchObject({ mode: 'month' });
  expect(snapshot.focusDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
});
