const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule
} = require('./helpers');

test('overtime credit and usage editors work from calendar and ledger', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'overtimeedit' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await openDayModule(page, 'overtime');

  await page.locator('#dayAddCredit').click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await expect(page.locator('#creditDate')).toHaveValue(date);
  await expect(page.locator('#creditTimeRange')).toHaveCount(0);
  await page.locator('#creditStart').fill(`${date}T17:00`);
  const nextDate = new Date(`${date}T00:00:00Z`);
  nextDate.setUTCDate(nextDate.getUTCDate() + 1);
  const nextDateKey = nextDate.toISOString().slice(0, 10);
  await page.locator('#creditEnd').fill(`${nextDateKey}T01:00`);
  await page.locator('#creditBreak').fill('0');
  await page.locator('#creditPlanned').fill('0');
  await expect(page.locator('#creditBreak')).toHaveValue('0');
  await expect(page.locator('#creditPlanned')).toHaveValue('0');
  await page.locator('#creditReason').fill('E2E modal overtime');
  const creditCreated = waitForApi(page, 'POST', '/api/overtime/credits');
  await page.locator('#creditAdd').click();
  await creditCreated;
  await expect(page.locator('#overtimeCreditModal')).toBeHidden();
  // The interval is split at midnight: the selected calendar day owns seven
  // hours, while the account balance still contains the full eight hours.
  await expect(page.locator('#otDayDetails')).toContainText('+7');
  await expect(page.locator('#otBalance')).toContainText('+8');

  await page.locator('#dayAddUsage').click();
  await expect(page.locator('#absenceComposerModal')).toBeVisible();
  await expect(page.locator('#vacationType option:checked')).toContainText('Отгул');
  await expect(page.locator('#vacationCompensation')).toHaveValue('OVERTIME_BANK');
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await page.locator('#vacationTitle').fill('E2E modal time off');
  await expect(page.locator('#absenceComposerContext')).toContainText('8 ч');
  const usageCreated = waitForApi(page, 'POST', '/api/vacation-planner/absences', 201);
  await page.locator('#vacationSaveBtn').click();
  await usageCreated;
  await expect(page.locator('#absenceComposerModal')).toBeHidden();
  await expect(page.locator('#otDayDetails')).toContainText('−8');
  await expect(page.locator('#otDayDetails')).toContainText('24:00');
  await expect(page.locator('#otDayDetails')).toContainText('00:00–01:00');

  await page.locator('#tabbar a[data-view="overtime"]').click();
  await expect(page.locator('#ledgerAddCredit')).toBeVisible();
  await page.locator('#ledgerAddCredit').click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await page.locator('#creditCancel').click();
  await expect(page.locator('#overtimeCreditModal')).toBeHidden();

  const editCredit = page.locator('#ledgerRows [data-edit-credit]').first();
  await expect(editCredit).toBeVisible();
  await editCredit.click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await expect(page.locator('#overtimeCreditTitle')).toContainText(/Редактировать|Edit/i);
  await expect(page).toHaveURL(/#overtime$/);
});

test('deleting one split time-off keeps every credit and the other usage', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'overtimeintegrity' });
  const base = await currentLocalDateKey(page);
  const plusDays = (key, days) => {
    const value = new Date(`${key}T12:00:00Z`);
    value.setUTCDate(value.getUTCDate() + days);
    return value.toISOString().slice(0, 10);
  };
  const dates = [0, 1, 2, 3].map(days => plusDays(base, days));

  const callApi = async (path, method, body) => page.evaluate(async ({ path, method, body }) => {
    const headers = body ? { 'Content-Type': 'application/json' } : {};
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
      if (match) headers['X-XSRF-TOKEN'] = decodeURIComponent(match[1]);
    }
    const response = await fetch(path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    });
    const text = await response.text();
    if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${text}`);
    return text ? JSON.parse(text) : null;
  }, { path, method, body });

  let account = await callApi('/api/overtime/credits', 'POST', {
    date: dates[0], hours: 3, reason: 'Integrity source A'
  });
  const firstCreditId = account.credits[0].id;
  account = await callApi('/api/overtime/credits', 'POST', {
    date: dates[1], hours: 5, reason: 'Integrity source B'
  });
  const secondCreditId = account.credits.find(row => row.id !== firstCreditId).id;
  account = await callApi('/api/overtime/usages', 'POST', {
    date: dates[2], hours: 4, reason: 'Split time-off'
  });
  const firstUsageId = account.usages[0].id;
  account = await callApi('/api/overtime/usages', 'POST', {
    date: dates[3], hours: 3, reason: 'Surviving time-off'
  });
  const secondUsageId = account.usages.find(row => row.id !== firstUsageId).id;
  expect(account.usages.find(row => row.id === firstUsageId).allocations).toHaveLength(2);

  await page.locator('#tabbar a[data-view="overtime"]').click();
  await page.locator('#ledgerAllTime').click();
  await expect(page.locator('.allocationPartBadge')).toContainText(['часть 1/2', 'часть 2/2']);

  const deleted = waitForApi(page, 'DELETE', `/api/overtime/usages/${firstUsageId}`);
  page.once('dialog', dialog => dialog.accept());
  const firstUsageDesktopDelete = page
    .locator(`#ledgerRows [data-del-usage="${firstUsageId}"]`)
    .first();
  await expect(firstUsageDesktopDelete).toBeVisible();
  await firstUsageDesktopDelete.click();
  await deleted;

  const rebuilt = await callApi('/api/overtime/account', 'GET');
  expect(rebuilt.credits.map(row => row.id)).toEqual([firstCreditId, secondCreditId]);
  expect(rebuilt.usages).toHaveLength(1);
  expect(rebuilt.usages[0].id).toBe(secondUsageId);
  expect(rebuilt.usages[0].allocations).toHaveLength(1);
  expect(rebuilt.usages[0].allocations[0].creditId).toBe(firstCreditId);

  await expect(page.locator(`#ledgerRows [data-del-usage="${firstUsageId}"]`)).toHaveCount(0);
  await expect(page.locator(`#ledgerRows [data-del-usage="${secondUsageId}"]`)).toHaveCount(1);
  await expect(page.locator('#ledgerRows tr[data-credit-id]')).toHaveCount(2);
});
