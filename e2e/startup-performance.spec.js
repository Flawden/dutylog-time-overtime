const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('calendar startup overlaps metadata reads and leaves time-bank work until its route opens', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'startup' });
  await openView(page, 'calendar');
  await page.waitForLoadState('networkidle');
  let inFlight = 0, peak = 0, metadataReads = 0, bankReads = 0;
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/v1/time-compensation') bankReads++;
  });
  await page.route(/\/api\/(shift-types|quick-scenarios|schedule-templates|calendar-layers)$/, async route => {
    metadataReads++;
    peak = Math.max(peak, ++inFlight);
    await new Promise(resolve => setTimeout(resolve, 350));
    await route.continue();
    inFlight--;
  });
  await page.reload();
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 15_000 });
  await expect(page.locator('#grid')).toBeVisible();
  await page.waitForLoadState('networkidle');
  expect(metadataReads).toBeGreaterThanOrEqual(4);
  expect(peak).toBeGreaterThanOrEqual(4);
  expect(bankReads).toBe(0);
  await openView(page, 'overtime');
  await expect.poll(() => bankReads).toBeGreaterThan(0);
  await expect(page.locator('[data-vue-domain-route="overtime"]')).toBeVisible();
});
