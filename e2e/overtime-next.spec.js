const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey } = require('./helpers');

function plusDays(key, days) {
  const value = new Date(`${key}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

test('Overtime Next keeps the professional desktop ledger and replaces it with detailed mobile cards', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'overtime-next' });
  const today = await currentLocalDateKey(page);
  const firstDate = plusDays(today, -2);
  const secondDate = plusDays(today, -1);

  await page.evaluate(async ({ firstDate, secondDate, today }) => {
    await jfetch('/api/overtime/credits', {
      method:'POST',
      body:{ date:firstDate, hours:3, reason:'Overtime Next first credit' }
    });
    await jfetch('/api/overtime/credits', {
      method:'POST',
      body:{ date:secondDate, hours:2, reason:'Overtime Next second credit' }
    });
    await jfetch('/api/overtime/usages', {
      method:'POST',
      body:{ date:today, hours:4, reason:'Overtime Next FIFO usage' }
    });
  }, { firstDate, secondDate, today });

  await page.locator('#tabbar a[data-view="overtime"]').click();
  await page.evaluate(() => loadLedgerPage(true));

  await expect(page.locator('#ledgerBalance')).toContainText('+1');
  await expect(page.locator('#ledgerEarned')).toContainText('+5');
  await expect(page.locator('#ledgerUsed')).toContainText('−4');
  await expect(page.locator('#ledgerUsageRatio')).toContainText('80%');
  await expect(page.locator('#ledgerOldestCredit')).toContainText('1 ч');
  await expect(page.locator('#ledgerFifoQueue')).toContainText('Overtime Next second credit');
  expect(await page.locator('#ledgerChart .overtimeChartColumn').count()).toBeGreaterThan(0);
  await expect(page.locator(`#ledgerChart .overtimeChartColumn[data-series-key="${today}"]`)).toHaveAttribute('title', /−4/);

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
