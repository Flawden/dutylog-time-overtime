const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, selectDate } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('calendar and selected-day panel remain usable on a phone viewport', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'basic', prefix: 'mobile' });
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport + 1);

  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await expect(page.locator('#panel')).toBeVisible();
  await expect(page.locator('#chips [data-shift-type-id]').first()).toBeVisible();
  await page.locator('#pClose').click();
  await expect(page.locator('#panel')).toBeHidden();
});
