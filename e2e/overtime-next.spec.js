const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForLedgerReady } = require('./helpers');

test('Overtime Next keeps the professional desktop ledger and replaces it with detailed mobile cards', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'overtime-next' });
  const dates = await page.evaluate(() => {
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    const prefix = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
    return { firstDate:`${prefix}-01`, secondDate:`${prefix}-02`, usageDate:`${prefix}-03` };
  });
  const { firstDate, secondDate, usageDate } = dates;

  await page.evaluate(async ({ firstDate, secondDate, usageDate }) => {
    await jfetch('/api/overtime/credits', {
      method:'POST',
      body:{ date:firstDate, hours:3, reason:'Overtime Next first credit' }
    });
    await jfetch('/api/overtime/credits', {
      method:'POST',
      body:{ date:secondDate, hours:2, reason:'Overtime Next second credit' }
    });
    const planner = await jfetch('/api/vacation-planner');
    const type = planner.types.find(item => item.systemCode === 'TIME_OFF');
    await jfetch('/api/vacation-planner/absences', {
      method:'POST',
      body:{
        typeId:type.id, title:'Overtime Next FIFO usage', startDate:usageDate, endDate:usageDate,
        status:'APPROVED', coverage:'PARTIAL', startTime:'09:00', endTime:'13:00',
        compensationPolicy:'OVERTIME_BANK'
      }
    });
  }, { firstDate, secondDate, usageDate });

  await openView(page, 'overtime');
  await waitForLedgerReady(page);
  await expect.poll(() => page.evaluate(() => state.overtimeAccount?.usages?.length || 0)).toBe(1);
  await expect.poll(() => page.evaluate(() => state.overtimeAccount?.usages?.[0]?.hours || 0)).toBe(4);

  await expect(page.locator('#ledgerBalance')).toContainText('+1');
  await expect(page.locator('#ledgerEarned')).toContainText('+5');
  await expect(page.locator('#ledgerUsed')).toContainText('−4');
  await expect(page.locator('#ledgerUsageRatio')).toContainText('80%');
  await expect(page.locator('#ledgerOldestCredit')).toContainText('1 ч');
  await expect(page.locator('#ledgerFifoQueue')).toContainText('Overtime Next second credit');
  expect(await page.locator('#ledgerChart .overtimeChartColumn').count()).toBeGreaterThan(0);
  const monthKey = usageDate.slice(0, 7);
  await expect(page.locator(`#ledgerChart .overtimeChartColumn[data-series-key="${monthKey}"]`)).toHaveAttribute('title', /−4/);

  await page.locator('#ledgerThisMonth').click();
  await expect(page.locator('#ledgerThisMonth')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator(`#ledgerChart .overtimeChartColumn[data-series-key="${usageDate}"]`)).toHaveAttribute('title', /−4/);

  await expect(page.locator('.ledgerTableWrap')).toBeVisible();
  await expect(page.locator('#ledgerRows tr[data-credit-id]')).toHaveCount(2);
  await expect(page.locator('#ledgerCards')).toBeHidden();

  await page.locator('#ledgerThisYear').click();
  await expect(page.locator('#ledgerThisYear')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#ledgerPeriodLabel')).toContainText(/Год|Year/);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.ledgerTableWrap')).toBeHidden();
  await expect(page.locator('#ledgerCards')).toBeVisible();
  await expect(page.locator('.overtimeLedgerCard')).toHaveCount(2);

  const card = page.locator('.overtimeLedgerCard').filter({ hasText:'Overtime Next second credit' });
  await expect(card).toBeVisible();
  await card.locator('summary').click();
  await expect(card).toContainText('Overtime Next FIFO usage');
  await expect(card.locator('[data-edit-credit]')).toBeVisible();
});
