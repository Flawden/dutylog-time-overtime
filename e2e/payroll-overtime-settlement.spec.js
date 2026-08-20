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

test('banked overtime becomes Payroll money only after explicit settlement with configured tiers', async ({ page }) => {
  await page.setViewportSize({ width:1280, height:960 });

  await registerAndOnboard(page, {
    preset:'full',
    prefix:'payroll-overtime-settlement',
  });

  const currentDate = await currentLocalDateKey(page);
  const date = addDaysIso(currentDate, -2);
  const month = date.slice(0, 7);

  const result = await page.evaluate(async ({ date, month }) => {
    /*
     * Eight-hour ordinary capacity.
     * 08:00–19:00 factual work therefore produces exactly
     * three canonical overtime hours after 16:00.
     */
    const shift = await jfetch('/api/v1/shift-types', {
      method:'POST',
      body:{
        name:'Overtime settlement E2E',
        hours:8,
        color:'#345678',
        startTime:'08:00',
        endTime:'16:00',
        breakMinutes:0,
        plannedHours:8,
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

    /*
     * User-configured overtime cash pricing:
     *
     * first 120 overtime minutes: +50%
     * remaining overtime minutes: +100%
     *
     * The base 1.00x is implicit in PayPricingEngine.
     */
    await jfetch(`/api/v1/payroll/pricing/terms/${date}`, {
      method:'PUT',
      body:{
        rules:[
          {
            code:'OT_TIER_1',
            dimension:'OVERTIME',
            premiumBps:5000,
            fromMinute:0,
            toMinuteExclusive:120,
            exclusiveGroup:null,
          },
          {
            code:'OT_TIER_2',
            dimension:'OVERTIME',
            premiumBps:10000,
            fromMinute:120,
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
        startTime:'08:00',
        endTime:'19:00',
        breakMinutes:0,
        note:'Overtime settlement E2E',
      },
    });

    /*
     * Bank-first checkpoint.
     *
     * The three overtime hours exist as time only.
     * They must not increase Payroll while no settlement exists.
     */
    const bankBefore =
      await jfetch('/api/v1/overtime/account');

    const payrollBefore =
      await jfetch(`/api/v1/payroll/periods/${month}`);

    /*
     * Explicit user decision to turn all three banked hours into money.
     */
    const settlement = await jfetch('/api/v1/overtime/settlements', {
      method:'POST',
      body:{
        settlementDate:date,
        minutes:180,
        reason:'Overtime settlement E2E',
      },
    });

    const bankAfter =
      await jfetch('/api/v1/overtime/account');

    const payrollAfter =
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
      bankBefore,
      payrollBefore,
      settlement,
      bankAfter,
      payrollAfter,
      snapshot,
      closedPeriod,
    };
  }, { date, month });

  /*
   * Reality:
   * 11 factual hours against an 8-hour ordinary shift.
   */
  expect(result.actual.workedMinutes).toBe(660);

  /*
   * Bank-first invariant BEFORE explicit settlement.
   */
  expect(result.bankBefore.totalEarnedHours).toBe(3);
  expect(result.bankBefore.totalUsedHours).toBe(0);
  expect(result.bankBefore.balanceHours).toBe(3);

  const derivedCredit =
    result.bankBefore.credits.find(
      item => item.sourceKind === 'SYSTEM_ACTUAL_WORK',
    );

  expect(derivedCredit).toBeTruthy();
  expect(derivedCredit.hours).toBe(3);

  const before = result.payrollBefore.preview;

  expect(before.workedMinutes).toBe(660);
  expect(before.hourlyBasePayableMinutes).toBe(480);
  expect(before.basePayMinor).toBe(800000);

  expect(before.settlementPricingReady).toBe(true);
  expect(before.settlementCount).toBe(0);
  expect(before.settlementMinutes).toBe(0);
  expect(before.settlementBasePayMinor).toBe(0);
  expect(before.settlementPremiumPayMinor).toBe(0);
  expect(before.settlementPayMinor).toBe(0);

  expect(before.totalPayMinor).toBe(800000);

  /*
   * Settlement owns the explicit money decision.
   */
  expect(result.settlement.minutes).toBe(180);
  expect(result.settlement.hours).toBe(3);

  /*
   * Same canonical bank, same FIFO.
   * No second overtime-money queue exists.
   */
  expect(result.bankAfter.totalEarnedHours).toBe(3);
  expect(result.bankAfter.totalUsedHours).toBe(3);
  expect(result.bankAfter.balanceHours).toBe(0);

  const settlementUsage =
    result.bankAfter.usages.find(
      item => item.sourceKind === 'SETTLEMENT',
    );

  expect(settlementUsage).toBeTruthy();
  expect(settlementUsage.minutes).toBe(180);
  expect(settlementUsage.sourceSettlementId)
    .toBe(result.settlement.id);

  /*
   * Monetary result:
   *
   * settlement base:
   *   3 h × 1000 RUB = 3000 RUB
   *
   * additive premiums:
   *   first 2 h × +50%  = 1000 RUB
   *   third 1 h × +100% = 1000 RUB
   *
   * settlement total = 5000 RUB
   *
   * ordinary base remains 8000 RUB.
   * Payroll total = 13000 RUB.
   */
  const after = result.payrollAfter.preview;

  expect(after.workedMinutes).toBe(660);
  expect(after.hourlyBasePayableMinutes).toBe(480);
  expect(after.basePayMinor).toBe(800000);

  expect(after.settlementPricingReady).toBe(true);
  expect(after.settlementCount).toBe(1);
  expect(after.settlementMinutes).toBe(180);

  expect(after.settlementBasePayMinor).toBe(300000);
  expect(after.settlementPremiumPayMinor).toBe(200000);
  expect(after.settlementPayMinor).toBe(500000);

  expect(after.settlementPricingFingerprint).toHaveLength(64);

  expect(after.totalPayMinor).toBe(1300000);

  /*
   * Immutable Payroll snapshot freezes the exact same settlement result.
   */
  expect(result.snapshot.revision).toBe(1);

  expect(result.snapshot.workedMinutes).toBe(660);
  expect(result.snapshot.hourlyBasePayableMinutes).toBe(480);
  expect(result.snapshot.basePayMinor).toBe(800000);

  expect(result.snapshot.settlementCount).toBe(1);
  expect(result.snapshot.settlementMinutes).toBe(180);
  expect(result.snapshot.settlementBasePayMinor).toBe(300000);
  expect(result.snapshot.settlementPremiumPayMinor).toBe(200000);
  expect(result.snapshot.settlementPayMinor).toBe(500000);

  expect(result.snapshot.settlementPricingFingerprint).toHaveLength(64);

  expect(result.snapshot.totalPayMinor).toBe(1300000);
  expect(result.snapshot.calculationHash).toHaveLength(64);

  expect(result.closedPeriod.periodClosed).toBe(true);
  expect(result.closedPeriod.integrityHealthy).toBe(true);
  expect(result.closedPeriod.canCalculate).toBe(true);

  /*
   * Browser-facing Payroll result.
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

  await expect(page.locator('#payrollWorked'))
    .toContainText(/11\s*(ч|h)/i);

  await expect(page.locator('#payrollHourlyBasePayable'))
    .toContainText(/8\s*(ч|h)/i);

  await expect(page.locator('#payrollSettlementPricingStatus'))
    .toContainText(/готов|ready/i);

  await expect(page.locator('#payrollSettlementBase'))
    .toContainText(/3[^\d]*000/);

  await expect(page.locator('#payrollSettlementPremium'))
    .toContainText(/2[^\d]*000/);

  await expect(page.locator('#payrollSettlementTotal'))
    .toContainText(/5[^\d]*000/);

  await expect(page.locator('#payrollGrandTotal'))
    .toContainText(/13[^\d]*000/);

  await expect(page.locator('#payrollSnapshotList'))
    .toContainText(/Ревизия 1|Revision 1/);

  await expect(page.locator('#payrollSnapshotList'))
    .toContainText(/5[^\d]*000/);
});
