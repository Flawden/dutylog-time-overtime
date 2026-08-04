const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForApi } = require('./helpers');

test('Vue owns Absence and Time Bank while blocking duplicate domain mutations', async ({ page }) => {
  await registerAndOnboard(page, { preset:'full', prefix:'vue-absence-bank' });

  await page.evaluate(() => jfetch('/api/overtime/credits', {
    method:'POST',
    body:{ date:new Date().toISOString().slice(0, 10), hours:8, reason:'Vue migration source' }
  }));

  await openView(page, 'vacation');
  await expect(page.locator('[data-vue-domain-route="vacation"]')).toBeVisible();
  await expect(page.locator('#view-vacation')).toHaveCount(0);
  await expect(page.locator('#view-overtime')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => Boolean(window.DutyLogVueDomains?.absenceTimeBank?.ready()))).toBe(true);

  await page.locator('#vacationComposerOpen').click();
  const timeOffValue = await page.locator('#vacationType option', { hasText:'Отгул' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(timeOffValue);
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await page.locator('#vacationCoverage').selectOption('PARTIAL');
  await page.locator('#vacationTitle').fill('Vue duplicate-submit guard');
  await page.locator('#vacationStartTime').fill('09:00');
  await page.locator('#vacationEndTime').fill('11:00');

  let createRequests = 0;
  await page.route('**/api/v1/vacation-planner/absences', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    createRequests += 1;
    await new Promise(resolve => setTimeout(resolve, 250));
    await route.continue();
  });
  const created = waitForApi(page, 'POST', '/api/v1/vacation-planner/absences', 201);
  await page.evaluate(() => {
    const button = document.querySelector('#vacationSaveBtn');
    button.click();
    button.click();
  });
  await created;
  expect(createRequests).toBe(1);
  await expect(page.locator('#absenceComposerModal')).toBeHidden();

  const row = page.locator('#vacationPeriodList [data-absence-id]', { hasText:'Vue duplicate-submit guard' });
  await expect(row).toBeVisible();
  await row.locator('[data-bank-absence]').click();
  await expect(page.locator('[data-vue-domain-route="overtime"]')).toBeVisible();
  await expect(page.locator('#timeBankTabUsage')).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#ledgerUsageList [data-source-absence-id]')).toContainText('Vue duplicate-submit guard');
});
