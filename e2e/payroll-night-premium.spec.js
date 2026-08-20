const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  waitForPayrollReady,
  waitForApi,
} = require('./helpers');

function addDaysIso(value, days) {
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

test('native NIGHT pricing adds configured premium from factual work into Payroll and snapshot', async ({ page }) => {
  await page.setViewportSize({ width:1280, height:960 });

  await registerAndOnboard(page, {
    preset:'full',
    prefix:'payroll-night-premium',
  });

  /*
   * Always use an already-completed factual date.
   * The month-switch below keeps the UI leg valid around month boundaries.
   */
  const currentDate = await currentLocalDateKey(page);
  const date = addDaysIso(currentDate, -2);
  const month = date.slice(0, 7);

  const result = await page.evaluate(async ({ date, month }) => {
    const shift = await jfetch('/api/v1/shift-types', {
      method:'POST',
      body:{
        name:'Night premium E2E',
        hours:1,
        color:'#123456',
        startTime:'22:00',
        endTime:'23:00',
        breakMinutes:0,
        plannedHours:1,
        notificationsEnabled:false,
        notificationMinutesBefore:-1,
      },
    });

    await jfetch(`/api/v1/days/${date}`, {
      method:'PUT',
      body:{
        shiftTypeId:shift.id,
      },
    });

    await jfetch(`/api/v1/payroll/compensation-terms/${month}`, {
      method:'PUT',
      body:{
        payMode:'HOURLY',
        currencyCode:'RUB',
        hourlyRateMinor:100000,
        monthlySalaryMinor:null,
      },
    });

    await jfetch(`/api/v1/payroll/pricing/terms/${date}`, {
      method:'PUT',
      body:{
        rules:[
          {
            code:'NIGHT_E2E_20',
            dimension:'NIGHT',
            premiumBps:2000,
            fromMinute:0,
            toMinuteExclusive:null,
            exclusiveGroup:null,
          },
        ],
      },
    });

    const actual = await jfetch('/api/v1/actual-work', {
      method:'POST',
      body:{
        workDate:date,
        endDate:null,
        startTime:'22:00',
        endTime:'23:00',
        breakMinutes:0,
        note:'Night premium E2E',
      },
    });

    /*
     * 60 min × 1000.00 RUB/h = 1000.00 ordinary base
     * NIGHT +20%             =  200.00 additive premium
     * Payroll total          = 1200.00
     */
    const openPeriod =
      await jfetch(`/api/v1/payroll/periods/${month}`);

    await jfetch(
      `/api/v1/ledger-integrity/periods/${month}/close`,
      { method:'POST' },
    );

    const snapshot =
      await jfetch(
        `/api/v1/payroll/periods/${month}/calculate`,
        { method:'POST' },
      );

    const closedPeriod =
      await jfetch(`/api/v1/payroll/periods/${month}`);

    return {
      actual,
      openPeriod,
      snapshot,
      closedPeriod,
    };
  }, { date, month });

  /*
   * Reality / classification-facing evidence.
   */
  expect(result.actual.workedMinutes).toBe(60);

  /*
   * Live Payroll projection.
   */
  const preview = result.openPeriod.preview;

  expect(preview.workedMinutes).toBe(60);
  expect(preview.payableMinutes).toBe(60);
  expect(preview.hourlyBasePayableMinutes).toBe(60);
  expect(preview.basePayMinor).toBe(100000);

  expect(preview.ordinaryPremiumPricingReady).toBe(true);
  expect(preview.ordinaryPremiumPricingIdentityRequired).toBe(true);

  expect(preview.ordinaryPremiumMinutes).toBe(60);
  expect(preview.ordinaryPremiumReferenceBasePayMinor).toBe(100000);
  expect(preview.ordinaryPremiumPayMinor).toBe(20000);

  /*
   * Bank-first invariant:
   * there is no overtime settlement involved in this money.
   */
  expect(preview.settlementCount).toBe(0);
  expect(preview.settlementMinutes).toBe(0);
  expect(preview.settlementPayMinor).toBe(0);

  expect(preview.totalPayMinor).toBe(120000);

  /*
   * Immutable snapshot keeps the same premium identity and money.
   */
  expect(result.snapshot.revision).toBe(1);

  expect(result.snapshot.workedMinutes).toBe(60);
  expect(result.snapshot.payableMinutes).toBe(60);
  expect(result.snapshot.hourlyBasePayableMinutes).toBe(60);
  expect(result.snapshot.basePayMinor).toBe(100000);

  expect(result.snapshot.ordinaryPremiumMinutes).toBe(60);
  expect(result.snapshot.ordinaryPremiumReferenceBasePayMinor).toBe(100000);
  expect(result.snapshot.ordinaryPremiumPayMinor).toBe(20000);

  expect(result.snapshot.ordinaryPremiumPricingFingerprint).toHaveLength(64);

  expect(result.snapshot.settlementCount).toBe(0);
  expect(result.snapshot.settlementMinutes).toBe(0);
  expect(result.snapshot.settlementPayMinor).toBe(0);

  expect(result.snapshot.totalPayMinor).toBe(120000);
  expect(result.snapshot.calculationHash).toHaveLength(64);

  expect(result.closedPeriod.periodClosed).toBe(true);
  expect(result.closedPeriod.integrityHealthy).toBe(true);
  expect(result.closedPeriod.canCalculate).toBe(true);

  /*
   * Browser/UI leg.
   */
  await openView(page, 'payroll');
  await waitForPayrollReady(page);

  const monthInput = page.locator('#payrollMonth');

  if ((await monthInput.inputValue()) !== month) {
    const refreshed = waitForApi(
      page,
      'GET',
      `/api/v1/payroll/periods/${month}`,
      200,
    );

    await monthInput.fill(month);
    await monthInput.dispatchEvent('change');

    await refreshed;
    await waitForPayrollReady(page);
  }

  await expect(page.locator('#payrollPeriodStatus'))
    .toContainText(/закрыт|closed/i);

  await expect(page.locator('#payrollIntegrityStatus'))
    .toContainText(/согласован|healthy/i);

  await expect(page.locator('#payrollWorked'))
    .toContainText(/1\s*(ч|h)/i);

  await expect(page.locator('#payrollOrdinaryPremiumBreakdown'))
    .toBeVisible();

  await expect(page.locator('#payrollOrdinaryPremium'))
    .toContainText('200');

  await expect(page.locator('#payrollSettlementTotal'))
    .toContainText('0');

  await expect(page.locator('#payrollGrandTotal'))
    .toContainText(/1[^\d]*200/);

  await expect(page.locator('#payrollSnapshotList'))
    .toContainText(/Ревизия 1|Revision 1/);

  await expect(page.locator('#payrollSnapshotList'))
    .toContainText('200');
});
