const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForPayrollReady } = require('./helpers');

// Presentation fixtures deliberately isolate the browser's rendering contract.
// Article153PayrollScenarioTest independently exercises the real HTTP/domain/freeze chain.
async function fixture(page, modify) {
  await registerAndOnboard(page, { preset:'full', prefix:'article153-display' });
  await page.route('**/api/v1/payroll/periods/????-??', async route => {
    const response = await route.fetch();
    const body = await response.json();
    modify(body);
    await route.fulfill({ response, json:body });
  });
  await openView(page, 'payroll');
  await waitForPayrollReady(page);
}

test('reviewed holiday explanation comes from each preview and frozen revision without browser repricing', async ({ page }) => {
  await fixture(page, body => {
    const reviewed = { status:'REVIEWED', blockingReason:null, qualifiedMinutes:60,
      tariffPremiumMinor:50000, componentPremiumMinor:12500, preservedNightPremiumMinor:2500 };
    Object.assign(body.preview, { currencyCode:'RUB', article153:reviewed, ordinaryPremiumPricingReady:true,
      ordinaryPremiumPricingIdentityRequired:true, ordinaryPremiumPayMinor:65000, totalPayMinor:127500 });
    body.snapshots = [{ ...body.preview, id:91, revision:1, createdAt:'2026-05-31T12:00:00Z',
      ordinaryPremiumPricingFingerprint:'a'.repeat(64), supersededById:null,
      article153:{ ...reviewed, tariffPremiumMinor:40000, componentPremiumMinor:0, preservedNightPremiumMinor:1000 }, totalPayMinor:91000 }];
  });
  const preview = page.locator('.payrollMoneyCard [data-testid="article153-reviewed"]');
  await expect(preview).toContainText('Подтверждённые минуты: 60');
  await expect(preview).toContainText(/Праздничная: тариф: 500/);
  await expect(preview).toContainText(/Праздничная: компоненты: 125/);
  await expect(preview).toContainText(/Ночная: 25/);
  const history=page.locator('#payrollSnapshotList [data-testid="article153-reviewed"]');
  await expect(history).toContainText(/Праздничная: тариф: 400/);
  await expect(history).toContainText(/Праздничная: компоненты: 0/);
  await expect(history).toContainText(/Ночная: 10/);
  await expect(page.locator('#payrollGrandTotal')).toContainText(/1\s*275/);
});

test('stale source review explains blocked saving and does not display invented holiday money', async ({ page }) => {
  await fixture(page, body => {
    body.periodClosed=true; body.integrityHealthy=true; body.canCalculate=false;
    body.blockingReason='PAYROLL_ARTICLE153_CONFIGURATION_CHANGED';
    Object.assign(body.preview, { compensationComponentCalculationReady:true, settlementPricingReady:true,
      ordinaryPremiumPricingReady:false, ordinaryPremiumPricingBlockingReason:body.blockingReason,
      article153:{ status:'REVIEW_BLOCKED', blockingReason:body.blockingReason, qualifiedMinutes:null,
        tariffPremiumMinor:null, componentPremiumMinor:null, preservedNightPremiumMinor:null } });
  });
  await expect(page.locator('#payrollCalculate')).toBeDisabled();
  await expect(page.locator('#payrollBlocking')).toContainText('Обнови подтверждение');
  await expect(page.getByTestId('article153-review_blocked')).toContainText('Условия оплаты изменились');
  await expect(page.getByTestId('article153-reviewed')).toHaveCount(0);
});
