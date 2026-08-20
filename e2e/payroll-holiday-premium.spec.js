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

test('native HOLIDAY pricing adds configured premium from production calendar into Payroll and snapshot', async ({ page }) => {
  await page.setViewportSize({ width:1280, height:960 });

  await registerAndOnboard(page, {
    preset:'full',
    prefix:'payroll-holiday-premium',
  });

  const currentDate = await currentLocalDateKey(page);
  const date = addDaysIso(currentDate, -2);
  const month = date.slice(0, 7);

  const result = await page.evaluate(async ({ date, month }) => {
    const shift = await jfetch('/api/v1/shift-types', {
      method:'POST',
      body:{
        name:'Holiday premium E2E',
        hours:1,
        color:'#654321',
        startTime:'10:00',
        endTime:'11:00',
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

    /*
     * Payroll holiday semantics are independent from schedule/norm semantics.
     * Keep the scheduled shift untouched and mark only the pay dimension.
     */
    const productionDay =
      await jfetch(`/api/v1/production-calendar/days/${date}`, {
        method:'PUT',
        body:{
          dayKind:'HOLIDAY',
          scheduleEffect:'NONE',
          normMinutesOverride:null,
          payrollEffect:'HOLIDAY',
          label:'Holiday premium E2E',
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
            code:'HOLIDAY_E2E_100',
            dimension:'HOLIDAY',
            premiumBps:10000,
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
        startTime:'10:00',
        endTime:'11:00',
        breakMinutes:0,
        note:'Holiday premium E2E',
      },
    });

    /*
     * 60 min × 1000.00 RUB/h = 1000.00 base
     * HOLIDAY +100%          = 1000.00 premium
     * total                  = 2000.00
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
      productionDay,
      actual,
      openPeriod,
      snapshot,
      closedPeriod,
    };
  }, { date, month });

  expect(result.productionDay.payrollEffect).toBe('HOLIDAY');
  expect(result.productionDay.scheduleEffect).toBe('NONE');

  expect(result.actual.workedMinutes).toBe(60);

  const preview = result.openPeriod.preview;

  expect(preview.workedMinutes).toBe(60);
  expect(preview.payableMinutes).toBe(60);
  expect(preview.hourlyBasePayableMinutes).toBe(60);
  expect(preview.basePayMinor).toBe(100000);

  expect(preview.ordinaryPremiumPricingReady).toBe(true);
  expect(preview.ordinaryPremiumPricingIdentityRequired).toBe(true);

  expect(preview.ordinaryPremiumMinutes).toBe(60);
  expect(preview.ordinaryPremiumReferenceBasePayMinor).toBe(100000);
  expect(preview.ordinaryPremiumPayMinor).toBe(100000);

  expect(preview.settlementCount).toBe(0);
  expect(preview.settlementPayMinor).toBe(0);

  expect(preview.totalPayMinor).toBe(200000);

  expect(result.snapshot.revision).toBe(1);
  expect(result.snapshot.workedMinutes).toBe(60);
  expect(result.snapshot.hourlyBasePayableMinutes).toBe(60);
  expect(result.snapshot.basePayMinor).toBe(100000);

  expect(result.snapshot.ordinaryPremiumMinutes).toBe(60);
  expect(result.snapshot.ordinaryPremiumReferenceBasePayMinor).toBe(100000);
  expect(result.snapshot.ordinaryPremiumPayMinor).toBe(100000);
  expect(result.snapshot.ordinaryPremiumPricingFingerprint).toHaveLength(64);

  expect(result.snapshot.settlementCount).toBe(0);
  expect(result.snapshot.settlementPayMinor).toBe(0);

  expect(result.snapshot.totalPayMinor).toBe(200000);
  expect(result.snapshot.calculationHash).toHaveLength(64);

  expect(result.closedPeriod.periodClosed).toBe(true);
  expect(result.closedPeriod.integrityHealthy).toBe(true);
  expect(result.closedPeriod.canCalculate).toBe(true);

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

  await expect(page.locator('#payrollWorked'))
    .toContainText(/1\s*(ч|h)/i);

  await expect(page.locator('#payrollOrdinaryPremiumBreakdown'))
    .toBeVisible();

  await expect(page.locator('#payrollOrdinaryPremium'))
    .toContainText(/1[^\d]*000/);

  await expect(page.locator('#payrollSettlementTotal'))
    .toContainText('0');

  await expect(page.locator('#payrollGrandTotal'))
    .toContainText(/2[^\d]*000/);

  await expect(page.locator('#payrollSnapshotList'))
    .toContainText(/Ревизия 1|Revision 1/);

  await expect(page.locator('#payrollSnapshotList'))
    .toContainText(/1[^\d]*000/);
});
