const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, selectDate, openView } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('calendar, filters and selected-day panel remain usable on a phone viewport', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'mobile' });
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
    headerHeight: document.querySelector('.head').getBoundingClientRect().height
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.headerHeight).toBeLessThan(150);

  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await expect(page.locator('#panel')).toBeVisible();
  await expect(page.locator('#chips [data-shift-type-id]').first()).toBeVisible();
  await expect(page.locator('[data-vue-shell-navigation]')).toBeHidden();
  await page.locator('#pClose').click();
  await expect(page.locator('#panel')).toBeHidden();
  await expect(page.locator('[data-vue-shell-navigation]')).toBeVisible();

  await openView(page, 'tasks');
  await expect(page.locator('#view-tasks')).toBeVisible();
  await expect(page.locator('#taskBoardFiltersToggle')).toBeVisible();
  await expect(page.locator('#taskBoardFilters')).toBeHidden();
  await page.locator('#taskBoardFiltersToggle').click();
  await expect(page.locator('#taskBoardFilters')).toBeVisible();
  await expect(page.locator('#taskBoardPager')).toBeHidden();

  const finalDimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth
  }));
  expect(finalDimensions.content).toBeLessThanOrEqual(finalDimensions.viewport + 1);
});
