const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForLedgerReady, waitForApi } = require('./helpers');

test('canonical absence ledger owns new time-off while overtime keeps FIFO statistics', async ({ page }) => {
  await registerAndOnboard(page, { preset:'full', prefix:'canonical-absence-ledger' });
  const date = await page.evaluate(() => {
    const value = new Date();
    value.setDate(value.getDate() + 7);
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
  });

  await page.evaluate(() => jfetch('/api/overtime/credits', {
    method:'POST', body:{ date:new Date().toISOString().slice(0, 10), hours:8, reason:'Canonical source' }
  }));

  const retired = await page.evaluate(async date => {
    const token = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)?.[1];
    const response = await fetch('/api/overtime/usages', {
      method:'POST',
      headers:{ 'Content-Type':'application/json', 'X-XSRF-TOKEN':decodeURIComponent(token || '') },
      body:JSON.stringify({ date, hours:4, reason:'must be rejected' })
    });
    return { status:response.status, body:await response.json() };
  }, date);
  expect(retired.status).toBe(409);
  expect(retired.body.code).toBe('DIRECT_USAGE_RETIRED');

  await openView(page, 'overtime');
  await waitForLedgerReady(page);
  await page.locator('#ledgerAddUsage').click();
  await expect(page.locator('#absenceComposerModal')).toBeVisible();
  await expect(page.locator('#vacationType option:checked')).toContainText(/Отгул|Time off/);
  await expect(page.locator('#vacationCompensation')).toHaveValue('OVERTIME_BANK');
  await page.locator('#vacationStart').fill(date);
  await page.locator('#vacationCoverage').selectOption('PARTIAL');
  await page.locator('#vacationStartTime').fill('09:00');
  await page.locator('#vacationEndTime').fill('13:00');
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await page.locator('#vacationTitle').fill('Canonical time off');
  const created = waitForApi(page, 'POST', '/api/vacation-planner/absences', 201);
  await page.locator('#vacationSaveBtn').click();
  const absence = await (await created).json();
  await expect(page.locator('#absenceComposerModal')).toBeHidden();

  const account = await page.evaluate(() => jfetch('/api/overtime/account'));
  expect(account.totalEarnedHours).toBe(8);
  expect(account.totalUsedHours).toBe(4);
  expect(account.balanceHours).toBe(4);
  expect(account.usages).toHaveLength(1);
  expect(account.usages[0].sourceKind).toBe('ABSENCE');
  expect(account.usages[0].sourceAbsenceId).toBe(absence.id);
  expect(account.usages[0].editable).toBe(false);
  expect(account.usages[0].allocations.reduce((sum, row) => sum + row.minutes, 0)).toBe(240);

  await openView(page, 'overtime');
  await page.locator('#ledgerAllTime').click();
  await waitForLedgerReady(page);
  const edit = page.locator(`[data-edit-usage="${account.usages[0].id}"]`).first();
  await expect(edit).toBeVisible();
  await edit.click();
  await expect(page).toHaveURL(/#vacation$/);
  await expect(page.locator('#vacationEditorTitle')).toContainText(/Редактировать|Edit/);
  await expect(page.locator('#vacationTitle')).toHaveValue('Canonical time off');
  await expect(page.locator('#vacationCompensation')).toHaveValue('OVERTIME_BANK');

  const planner = await page.evaluate(() => jfetch('/api/vacation-planner'));
  const unpaid = planner.types.find(item => item.systemCode === 'UNPAID');
  await page.evaluate(async ({ id, unpaidId, date }) => {
    await jfetch(`/api/vacation-planner/absences/${id}`, {
      method:'PATCH',
      body:{
        typeId:unpaidId, title:'Canonical unpaid', startDate:date, endDate:date,
        status:'APPROVED', coverage:'PARTIAL', startTime:'09:00', endTime:'13:00',
        compensationPolicy:'UNPAID'
      }
    });
  }, { id:absence.id, unpaidId:unpaid.id, date });

  const restored = await page.evaluate(() => jfetch('/api/overtime/account'));
  expect(restored.totalEarnedHours).toBe(8);
  expect(restored.totalUsedHours).toBe(0);
  expect(restored.balanceHours).toBe(8);
  expect(restored.usages).toHaveLength(0);

  const updatedPlanner = await page.evaluate(() => jfetch('/api/vacation-planner'));
  const updated = updatedPlanner.absences.find(item => item.id === absence.id);
  expect(updated.systemCode).toBe('UNPAID');
  expect(updated.compensationPolicy).toBe('UNPAID');
});
