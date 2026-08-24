const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  waitForPayrollReady,
  waitForApi,
} = require('./helpers');

function addDaysIso(value, days) {
  const [year, month, day] =
    value.split('-').map(Number);

  const date =
    new Date(
      Date.UTC(
        year,
        month - 1,
        day,
      ),
    );

  date.setUTCDate(
    date.getUTCDate() + days,
  );

  return date
    .toISOString()
    .slice(0, 10);
}

function componentLine(snapshot, componentId) {
  return (
    snapshot.compensationComponentLines
      .find(
        line =>
          Number(line.componentId)
          === Number(componentId),
      )
    ?? null
  );
}

test(
  'generic compensation components preserve frozen payroll revisions across later configuration changes',
  async ({ page }) => {
    await page.setViewportSize({
      width: 1280,
      height: 960,
    });

    await registerAndOnboard(page, {
      preset: 'full',
      prefix: 'payroll-generic-components',
    });

    const currentDate =
      await currentLocalDateKey(page);

    const date =
      addDaysIso(
        currentDate,
        -2,
      );

    const month =
      date.slice(0, 7);

    const result =
      await page.evaluate(
        async ({ date, month }) => {
          const shift =
            await jfetch(
              '/api/v1/shift-types',
              {
                method: 'POST',
                body: {
                  name: 'Generic compensation E2E',
                  hours: 1,
                  color: '#476582',
                  startTime: '10:00',
                  endTime: '11:00',
                  breakMinutes: 0,
                  plannedHours: 1,
                  notificationsEnabled: false,
                  notificationMinutesBefore: -1,
                },
              },
            );

          await jfetch(
            `/api/v1/days/${date}`,
            {
              method: 'PUT',
              body: {
                shiftTypeId: shift.id,
              },
            },
          );

          await jfetch(
            `/api/v1/payroll/compensation-terms/${month}`,
            {
              method: 'PUT',
              body: {
                payMode: 'HOURLY',
                currencyCode: 'RUB',
                hourlyRateMinor: 100000,
                monthlySalaryMinor: null,
              },
            },
          );

          /*
           * Explicit base-only Pricing policy.
           *
           * The scenario is about generic components, not NIGHT /
           * HOLIDAY / OVERTIME pricing, so missing pricing policy must
           * not become an unrelated blocker.
           */
          await jfetch(
            `/api/v1/payroll/pricing/terms/${date}`,
            {
              method: 'PUT',
              body: {
                rules: [],
              },
            },
          );

          const actual =
            await jfetch(
              '/api/v1/actual-work',
              {
                method: 'POST',
                body: {
                  workDate: date,
                  endDate: null,
                  startTime: '10:00',
                  endTime: '11:00',
                  breakMinutes: 0,
                  note: 'Generic compensation E2E',
                },
              },
            );

          const percent =
            await jfetch(
              '/api/v1/payroll/compensation-components',
              {
                method: 'POST',
                body: {
                  effectiveMonth: month,
                  version: {
                    displayName:
                      'Премия за выживание после ночной смены',
                    calculationType:
                      'PERCENT_OF_BASE',
                    calculationBase:
                      'EARNED_BASE_PAY',
                    rateBps: 1000,
                    enabled: true,
                  },
                },
              },
            );

          const fixed =
            await jfetch(
              '/api/v1/payroll/compensation-components',
              {
                method: 'POST',
                body: {
                  effectiveMonth: month,
                  version: {
                    displayName:
                      'Фиксированная E2E выплата',
                    calculationType:
                      'FIXED_AMOUNT',
                    amountMinor: 5000,
                    currencyCode: 'RUB',
                    enabled: true,
                  },
                },
              },
            );

          /*
           * Deliberately incompatible while enabled:
           * NOMINAL_SALARY has no meaning in HOURLY mode.
           */
          const blocker =
            await jfetch(
              '/api/v1/payroll/compensation-components',
              {
                method: 'POST',
                body: {
                  effectiveMonth: month,
                  version: {
                    displayName:
                      'E2E nominal blocker',
                    calculationType:
                      'PERCENT_OF_BASE',
                    calculationBase:
                      'NOMINAL_SALARY',
                    rateBps: 1000,
                    enabled: true,
                  },
                },
              },
            );

          const blockedPeriod =
            await jfetch(
              `/api/v1/payroll/periods/${month}`,
            );

          /*
           * Disabling is historical state, not deletion.
           */
          await jfetch(
            `/api/v1/payroll/compensation-components/${blocker.componentId}/versions/${month}`,
            {
              method: 'PUT',
              body: {
                displayName:
                  'E2E nominal blocker',
                calculationType:
                  'PERCENT_OF_BASE',
                calculationBase:
                  'NOMINAL_SALARY',
                rateBps: 1000,
                enabled: false,
              },
            },
          );

          const readyPeriod =
            await jfetch(
              `/api/v1/payroll/periods/${month}`,
            );

          const effectiveAfterDisable =
            await jfetch(
              `/api/v1/payroll/compensation-components/effective/${month}`,
            );

          await jfetch(
            `/api/v1/ledger-integrity/periods/${month}/close`,
            {
              method: 'POST',
            },
          );

          const snapshot1 =
            await jfetch(
              `/api/v1/payroll/periods/${month}/calculate`,
              {
                method: 'POST',
              },
            );

          /*
           * Mutate current configuration AFTER revision 1.
           * The same stable component now has a different user-owned
           * label and percentage.
           */
          await jfetch(
            `/api/v1/payroll/compensation-components/${percent.componentId}/versions/${month}`,
            {
              method: 'PUT',
              body: {
                displayName:
                  'Премия за выживание после ночной смены v2',
                calculationType:
                  'PERCENT_OF_BASE',
                calculationBase:
                  'EARNED_BASE_PAY',
                rateBps: 2000,
                enabled: true,
              },
            },
          );

          const changedPeriod =
            await jfetch(
              `/api/v1/payroll/periods/${month}`,
            );

          const snapshot2 =
            await jfetch(
              `/api/v1/payroll/periods/${month}/calculate`,
              {
                method: 'POST',
              },
            );

          const finalPeriod =
            await jfetch(
              `/api/v1/payroll/periods/${month}`,
            );

          const history =
            await jfetch(
              '/api/v1/payroll/compensation-components',
            );

          return {
            actual,
            percent,
            fixed,
            blocker,
            blockedPeriod,
            readyPeriod,
            effectiveAfterDisable,
            snapshot1,
            changedPeriod,
            snapshot2,
            finalPeriod,
            history,
          };
        },
        {
          date,
          month,
        },
      );

    expect(result.actual.workedMinutes)
      .toBe(60);

    /*
     * Fail-closed semantic base proof.
     */
    expect(
      result.blockedPeriod.preview
        .compensationComponentCalculationReady,
    ).toBe(false);

    expect(
      result.blockedPeriod.preview
        .compensationComponentCalculationBlockingReason,
    ).toBe(
      'PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE',
    );

    /*
     * Disabled version remains resolvable but creates no line,
     * fingerprint contribution or money.
     */
    const disabled =
      result.effectiveAfterDisable.find(
        version =>
          Number(version.componentId)
          === Number(result.blocker.componentId),
      );

    expect(disabled).toBeTruthy();
    expect(disabled.enabled).toBe(false);

    const ready =
      result.readyPeriod.preview;

    expect(
      ready.compensationComponentCalculationReady,
    ).toBe(true);

    expect(
      ready.compensationComponentCalculationBlockingReason,
    ).toBeNull();

    expect(ready.workedMinutes).toBe(60);
    expect(ready.basePayMinor).toBe(100000);

    expect(
      ready.compensationComponentCount,
    ).toBe(2);

    expect(
      ready.compensationComponentEarningsMinor,
    ).toBe(15000);

    expect(
      ready.compensationComponentFingerprint,
    ).toHaveLength(64);

    expect(
      ready.compensationComponentLines,
    ).toHaveLength(2);

    expect(
      ready.totalPayMinor,
    ).toBe(115000);

    const readyPercent =
      componentLine(
        ready,
        result.percent.componentId,
      );

    const readyFixed =
      componentLine(
        ready,
        result.fixed.componentId,
      );

    expect(readyPercent).toBeTruthy();

    expect(readyPercent.displayName)
      .toBe(
        'Премия за выживание после ночной смены',
      );

    expect(readyPercent.calculationType)
      .toBe('PERCENT_OF_BASE');

    expect(readyPercent.calculationBase)
      .toBe('EARNED_BASE_PAY');

    expect(readyPercent.rateBps)
      .toBe(1000);

    expect(readyPercent.referenceBaseMinor)
      .toBe(100000);

    expect(readyPercent.amountMinor)
      .toBe(10000);

    expect(readyFixed).toBeTruthy();

    expect(readyFixed.calculationType)
      .toBe('FIXED_AMOUNT');

    expect(readyFixed.configuredAmountMinor)
      .toBe(5000);

    expect(readyFixed.configuredCurrencyCode)
      .toBe('RUB');

    expect(readyFixed.amountMinor)
      .toBe(5000);

    /*
     * Revision 1 freezes the original configuration.
     */
    expect(result.snapshot1.revision)
      .toBe(1);

    expect(
      result.snapshot1.compensationComponentCount,
    ).toBe(2);

    expect(
      result.snapshot1.compensationComponentEarningsMinor,
    ).toBe(15000);

    expect(
      result.snapshot1.compensationComponentFingerprint,
    ).toHaveLength(64);

    expect(
      result.snapshot1.totalPayMinor,
    ).toBe(115000);

    const frozen1 =
      componentLine(
        result.snapshot1,
        result.percent.componentId,
      );

    expect(frozen1).toBeTruthy();

    expect(frozen1.displayName)
      .toBe(
        'Премия за выживание после ночной смены',
      );

    expect(frozen1.rateBps)
      .toBe(1000);

    expect(frozen1.referenceBaseMinor)
      .toBe(100000);

    expect(frozen1.amountMinor)
      .toBe(10000);

    /*
     * Live projection changes after current configuration changes.
     */
    const changed =
      result.changedPeriod.preview;

    expect(
      changed.compensationComponentCalculationReady,
    ).toBe(true);

    expect(
      changed.compensationComponentCount,
    ).toBe(2);

    expect(
      changed.compensationComponentEarningsMinor,
    ).toBe(25000);

    expect(
      changed.totalPayMinor,
    ).toBe(125000);

    const changedPercent =
      componentLine(
        changed,
        result.percent.componentId,
      );

    expect(changedPercent).toBeTruthy();

    expect(changedPercent.displayName)
      .toBe(
        'Премия за выживание после ночной смены v2',
      );

    expect(changedPercent.rateBps)
      .toBe(2000);

    expect(changedPercent.amountMinor)
      .toBe(20000);

    /*
     * Revision 2 freezes the new current configuration.
     */
    expect(result.snapshot2.revision)
      .toBe(2);

    expect(
      result.snapshot2.compensationComponentEarningsMinor,
    ).toBe(25000);

    expect(
      result.snapshot2.totalPayMinor,
    ).toBe(125000);

    expect(
      result.snapshot2.compensationComponentFingerprint,
    ).toHaveLength(64);

    expect(
      result.snapshot2.compensationComponentFingerprint,
    ).not.toBe(
      result.snapshot1.compensationComponentFingerprint,
    );

    expect(
      result.snapshot2.calculationHash,
    ).toHaveLength(64);

    expect(
      result.snapshot2.calculationHash,
    ).not.toBe(
      result.snapshot1.calculationHash,
    );

    const frozen2 =
      componentLine(
        result.snapshot2,
        result.percent.componentId,
      );

    expect(frozen2).toBeTruthy();

    expect(frozen2.displayName)
      .toBe(
        'Премия за выживание после ночной смены v2',
      );

    expect(frozen2.rateBps)
      .toBe(2000);

    expect(frozen2.amountMinor)
      .toBe(20000);

    /*
     * Re-fetch from persistence and prove revision 1 did not
     * retroactively follow mutable component configuration.
     */
    const persisted1 =
      result.finalPeriod.snapshots.find(
        item => item.revision === 1,
      );

    const persisted2 =
      result.finalPeriod.snapshots.find(
        item => item.revision === 2,
      );

    expect(persisted1).toBeTruthy();
    expect(persisted2).toBeTruthy();

    const persisted1Line =
      componentLine(
        persisted1,
        result.percent.componentId,
      );

    const persisted2Line =
      componentLine(
        persisted2,
        result.percent.componentId,
      );

    expect(persisted1Line.displayName)
      .toBe(
        'Премия за выживание после ночной смены',
      );

    expect(persisted1Line.rateBps)
      .toBe(1000);

    expect(persisted1Line.amountMinor)
      .toBe(10000);

    expect(persisted2Line.displayName)
      .toBe(
        'Премия за выживание после ночной смены v2',
      );

    expect(persisted2Line.rateBps)
      .toBe(2000);

    expect(persisted2Line.amountMinor)
      .toBe(20000);

    expect(persisted1.supersededById)
      .toBe(persisted2.id);

    /*
     * Stable component history still contains user configuration
     * history/state including the explicitly disabled component.
     */
    expect(
      result.history.some(
        item =>
          Number(item.componentId)
            === Number(result.blocker.componentId)
          && item.enabled === false,
      ),
    ).toBe(true);

    /*
     * Browser-facing explainability leg.
     */
    await openView(page, 'payroll');
    await waitForPayrollReady(page);

    const monthInput =
      page.locator('#payrollMonth');

    if (
      (await monthInput.inputValue())
      !== month
    ) {
      const refreshed =
        waitForApi(
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

    await expect(
      page.locator(
        '#payrollCompensationComponentBreakdown',
      ),
    ).toBeVisible();

    await expect(
      page.locator(
        '#payrollCompensationComponentBreakdown',
      ),
    ).toContainText(
      'Премия за выживание после ночной смены v2',
    );

    await expect(
      page.locator(
        '#payrollCompensationComponentsTotal',
      ),
    ).toContainText('250');

    await expect(
      page.locator('#payrollSnapshotList'),
    ).toContainText(
      'Премия за выживание после ночной смены',
    );

    await expect(
      page.locator('#payrollSnapshotList'),
    ).toContainText(
      'Премия за выживание после ночной смены v2',
    );

    await expect(
      page.locator('#payrollSnapshotList'),
    ).toContainText(
      /Ревизия 1|Revision 1/,
    );

    await expect(
      page.locator('#payrollSnapshotList'),
    ).toContainText(
      /Ревизия 2|Revision 2/,
    );

    const preset =
      page.locator(
        '#compensationComponentPreset',
      );

    await expect(preset)
      .toBeVisible();

    await expect(
      preset.locator('option'),
    ).toHaveCount(3);
  },
);
