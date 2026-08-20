const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForPayrollReady } = require('./helpers');

test('Payroll Foundation calculates a versioned closed-month snapshot from the canonical time source', async ({ page }) => {
  await page.setViewportSize({ width:1280, height:960 });
  await registerAndOnboard(page, { preset:'full', prefix:'payroll-foundation' });

  const result = await page.evaluate(async () => {
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    const month = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
    const date = `${month}-01`;

    await jfetch('/api/overtime/credits', {
      method:'POST', body:{ date, hours:8, reason:'Payroll Foundation source' }
    });
    await jfetch(`/api/ledger-integrity/periods/${month}/close`, { method:'POST' });
    await jfetch('/api/payroll/settings', {
      method:'PATCH', body:{ currencyCode:'RUB', hourlyRateMinor:100000 }
    });
    await jfetch('/api/payroll/adjustments', {
      method:'POST', body:{ month, adjustmentType:'ADDITION', amountMinor:50000, title:'Премия' }
    });
    const snapshot = await jfetch(`/api/payroll/periods/${month}/calculate`, { method:'POST' });
    const period = await jfetch(`/api/payroll/periods/${month}`);
    return { month, snapshot, period };
  });

  expect(result.snapshot.revision).toBe(1);
  expect(result.snapshot.workedMinutes).toBe(480);
  expect(result.snapshot.payableMinutes).toBe(480);
  expect(result.snapshot.hourlyBasePayableMinutes).toBe(0);
  expect(result.snapshot.basePayMinor).toBe(0);
  expect(result.snapshot.totalPayMinor).toBe(50000);
  expect(result.snapshot.calculationHash).toHaveLength(64);
  expect(result.period.periodClosed).toBe(true);
  expect(result.period.integrityHealthy).toBe(true);
  expect(result.period.canCalculate).toBe(true);

  await openView(page, 'payroll');
  await waitForPayrollReady(page);
  await expect(page.locator('#payrollPeriodStatus')).toContainText(/закрыт|closed/i);
  await expect(page.locator('#payrollIntegrityStatus')).toContainText(/согласован|healthy/i);
  await expect(page.locator('#payrollWorked')).toContainText('8 ч');
  await expect(page.locator('#payrollSnapshotList')).toContainText(/Ревизия 1|Revision 1/);
  await expect(page.locator('#payrollAdjustmentList')).toContainText('Премия');
  await expect(page.locator('#payrollCalculate')).toBeEnabled();
});
