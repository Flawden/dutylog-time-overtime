const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForLedgerReady, waitForVacationReady } = require('./helpers');

test('absence remains the event owner while the time bank explains reservations and FIFO', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 960 });
  await registerAndOnboard(page, { preset:'full', prefix:'absence-time-bank' });

  const date = await page.evaluate(() => {
    const value = new Date();
    value.setDate(value.getDate() + 7);
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
  });

  const created = await page.evaluate(async dateKey => {
    await jfetch('/api/overtime/credits', {
      method:'POST',
      body:{ date:new Date().toISOString().slice(0, 10), hours:6, reason:'Experience source' }
    });
    const planner = await jfetch('/api/vacation-planner');
    const type = planner.types.find(item => item.systemCode === 'TIME_OFF');
    return jfetch('/api/vacation-planner/absences', {
      method:'POST',
      body:{
        typeId:type.id,
        title:'Future reserved time off',
        startDate:dateKey,
        endDate:dateKey,
        status:'PLANNED',
        coverage:'PARTIAL',
        startTime:'09:00',
        endTime:'13:00',
        compensationPolicy:'OVERTIME_BANK'
      }
    });
  }, date);

  await openView(page, 'overtime');
  await waitForLedgerReady(page);
  await expect(page.locator('#ledgerEarned')).toContainText('6');
  await expect(page.locator('#ledgerReserved')).toContainText('4');
  await expect(page.locator('#ledgerBalance')).toContainText('2');
  await expect(page.locator('#ledgerBalanceCaption')).toContainText(/зарезервировано|reserved/i);

  await page.locator('#timeBankGuideOpen').click();
  await expect(page.locator('#timeBankGuideModal')).toBeVisible();
  await expect(page.locator('#timeBankGuideModal')).toContainText(/FIFO/i);
  await page.locator('#timeBankGuideDone').click();
  await expect(page.locator('#timeBankGuideModal')).toBeHidden();

  await page.locator('#timeBankTabUsage').click();
  const usage = page.locator(`#ledgerUsageList [data-source-absence-id="${created.id}"]`);
  await expect(usage).toBeVisible();
  await expect(usage).toContainText(/Зарезервировано|Reserved/i);
  await expect(usage).toContainText('Future reserved time off');
  await expect(usage.locator('[data-open-absence]')).toBeVisible();
  await usage.locator('[data-open-absence]').click();
  await expect(page.locator('#absenceComposerModal')).toBeVisible();
  await expect(page.locator('#vacationTitle')).toHaveValue('Future reserved time off');
  await expect(page.locator('#vacationStatus')).toHaveValue('PLANNED');
  await expect(page.locator('#absenceFifoForecast')).toContainText('Experience source');
  await expect(page.locator('#absenceFifoForecast')).not.toContainText(/Недостаточно|Not enough/i);
  await page.locator('#absenceComposerClose').click();

  await openView(page, 'vacation');
  await waitForVacationReady(page);
  const absence = page.locator(`[data-absence-id="${created.id}"]`);
  await expect(absence).toBeVisible();
  await expect(absence).toContainText('Future reserved time off');
  await expect(absence.locator('[data-bank-absence]')).toBeVisible();
  await absence.locator('[data-bank-absence]').click();
  await expect(page.locator('#view-overtime')).toBeVisible();
  await expect(page.locator('#timeBankTabUsage')).toHaveClass(/active/);
  await expect(page.locator(`#ledgerUsageList [data-source-absence-id="${created.id}"]`)).toBeVisible();

  await page.locator('#timeBankTabFifo').click();
  await page.locator('#fifoForecastHours').fill('2');
  await page.locator('#fifoForecastForm button[type="submit"]').click();
  await expect(page.locator('#ledgerFifoForecast')).toContainText('Experience source');
  await expect(page.locator('#ledgerFifoForecast')).toContainText(/После этого останется|Remaining after this/i);
});
