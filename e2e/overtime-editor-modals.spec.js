const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule
} = require('./helpers');

test('overtime credit and usage editors work from calendar and ledger', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'overtimeedit' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await openDayModule(page, 'overtime');

  await page.locator('#dayAddCredit').click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await expect(page.locator('#creditDate')).toHaveValue(date);
  await expect(page.locator('#creditTimeRange')).toHaveCount(0);
  await page.locator('#creditStart').fill(`${date}T17:00`);
  const nextDate = new Date(`${date}T00:00:00Z`);
  nextDate.setUTCDate(nextDate.getUTCDate() + 1);
  const nextDateKey = nextDate.toISOString().slice(0, 10);
  await page.locator('#creditEnd').fill(`${nextDateKey}T01:00`);
  await page.locator('#creditReason').fill('E2E modal overtime');
  const creditCreated = waitForApi(page, 'POST', '/api/overtime/credits');
  await page.locator('#creditAdd').click();
  await creditCreated;
  await expect(page.locator('#overtimeCreditModal')).toBeHidden();
  await expect(page.locator('#otDayDetails')).toContainText('+8');

  await page.locator('#dayAddUsage').click();
  await expect(page.locator('#overtimeUsageModal')).toBeVisible();
  await expect(page.locator('#usageDate')).toHaveValue(date);
  await page.locator('#usageHours').fill('8');
  await page.locator('#usageReason').fill('E2E modal time off');
  await expect(page.locator('#usageBalanceAfter')).toContainText('0');
  const usageCreated = waitForApi(page, 'POST', '/api/overtime/usages');
  await page.locator('#usageAdd').click();
  await usageCreated;
  await expect(page.locator('#overtimeUsageModal')).toBeHidden();
  await expect(page.locator('#otDayDetails')).toContainText('−8');
  await expect(page.locator('#otDayDetails')).toContainText('24:00');
  await expect(page.locator('#otDayDetails')).toContainText('00:00–01:00');

  await page.locator('#tabbar a[data-view="overtime"]').click();
  await expect(page.locator('#ledgerAddCredit')).toBeVisible();
  await page.locator('#ledgerAddCredit').click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await page.locator('#creditCancel').click();
  await expect(page.locator('#overtimeCreditModal')).toBeHidden();

  const editCredit = page.locator('#ledgerRows [data-edit-credit]').first();
  await expect(editCredit).toBeVisible();
  await editCredit.click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await expect(page.locator('#overtimeCreditTitle')).toContainText(/Редактировать|Edit/i);
  await expect(page).toHaveURL(/#overtime$/);
});
