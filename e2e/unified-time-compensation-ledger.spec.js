const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, selectDate, waitForApi } = require('./helpers');

test('absence compensation is linked to FIFO overtime and monthly plan-fact summary', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 960 });
  await registerAndOnboard(page, { preset:'full', prefix:'unifiedledger' });

  const dates = await page.evaluate(() => {
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    const prefix = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
    return { timeOff:`${prefix}-10`, unpaid:`${prefix}-11` };
  });

  for (const date of [dates.timeOff, dates.unpaid]) {
    await selectDate(page, date);
    const saved = waitForApi(page, 'PUT', `/api/days/${date}`);
    await page.locator('#chips [data-shift-type-id]').first().click();
    await saved;
  }

  await page.evaluate(async date => {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    const response = await fetch('/api/overtime/credits', {
      method:'POST',
      headers:{ 'Content-Type':'application/json', 'X-XSRF-TOKEN':decodeURIComponent(match[1]) },
      body:JSON.stringify({ date, hours:8, reason:'Unified ledger E2E source' })
    });
    if (!response.ok) throw new Error(await response.text());
  }, dates.timeOff);

  await openView(page, 'vacation');
  const timeOffValue = await page.locator('#vacationType option', { hasText:'Отгул' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(timeOffValue);
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await expect(page.locator('#vacationCompensation')).toHaveValue('OVERTIME_BANK');
  await page.locator('#vacationCoverage').selectOption('PARTIAL');
  await page.locator('#vacationTitle').fill('Отгул за переработку');
  await page.locator('#vacationStart').fill(dates.timeOff);
  await page.locator('#vacationStartTime').fill('09:00');
  await page.locator('#vacationEndTime').fill('13:00');
  const timeOffCreated = page.waitForResponse(response => new URL(response.url()).pathname === '/api/vacation-planner/absences'
    && response.request().method() === 'POST' && response.status() === 201);
  await page.locator('#vacationSaveBtn').click();
  const timeOff = await (await timeOffCreated).json();
  expect(timeOff.status).toBe('APPROVED');
  expect(timeOff.compensationPolicy).toBe('OVERTIME_BANK');
  expect(timeOff.compensatedMinutes).toBe(240);
  expect(timeOff.linkedOvertimeUsageId).toBeTruthy();

  const unpaidValue = await page.locator('#vacationType option', { hasText:'Без содержания' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(unpaidValue);
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await expect(page.locator('#vacationCompensation')).toHaveValue('UNPAID');
  await page.locator('#vacationCoverage').selectOption('FULL_DAY');
  await page.locator('#vacationTitle').fill('День без содержания');
  await page.locator('#vacationStart').fill(dates.unpaid);
  await page.locator('#vacationEnd').fill(dates.unpaid);
  const unpaidCreated = page.waitForResponse(response => new URL(response.url()).pathname === '/api/vacation-planner/absences'
    && response.request().method() === 'POST' && response.status() === 201);
  await page.locator('#vacationSaveBtn').click();
  const unpaid = await (await unpaidCreated).json();
  expect(unpaid.status).toBe('APPROVED');
  expect(unpaid.compensationPolicy).toBe('UNPAID');
  expect(unpaid.linkedOvertimeUsageId).toBeNull();

  await openView(page, 'overtime');
  await expect(page.locator('#timeCompensationCard')).toBeVisible();
  await expect(page.locator('#timeCompUsed')).toContainText('4');
  await expect(page.locator('#timeCompUnpaid')).not.toContainText('0 ч');
  await expect(page.locator('#timeCompDays')).toContainText('Списано из банка переработок');
  await expect(page.locator('#timeCompDays')).toContainText('Неоплачиваемое время');
  await page.locator('#timeBankTabUsage').click();
  const linked = page.locator('#ledgerUsageList .timeBankUsageCard', { hasText:/Управляется отсутствием|Managed by absence/i });
  await expect(linked.first()).toBeVisible();
  await expect(page.locator(`[data-edit-usage="${timeOff.linkedOvertimeUsageId}"]`)).toHaveCount(0);
  await expect(page.locator(`[data-del-usage="${timeOff.linkedOvertimeUsageId}"]`)).toHaveCount(0);

  await openView(page, 'vacation');
  await page.locator(`[data-edit-absence="${timeOff.id}"]`).click();
  page.once('dialog', dialog => dialog.accept());
  const deleted = waitForApi(page, 'DELETE', `/api/vacation-planner/absences/${timeOff.id}`, 204);
  await page.locator(`[data-delete-absence="${timeOff.id}"]`).click();
  await deleted;

  await openView(page, 'overtime');
  await expect(page.locator('#ledgerBalance')).toContainText('8');
  await expect(page.locator('#timeCompUsed')).toContainText('0 ч');
  await page.locator('#timeBankTabUsage').click();
  await expect(page.locator('#ledgerUsageList .timeBankUsageCard')).toHaveCount(0);
});
