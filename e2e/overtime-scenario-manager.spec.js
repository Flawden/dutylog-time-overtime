const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule
} = require('./helpers');

test('overtime scenarios are created and edited inside the shared credit modal', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'scenarioeditor' });
  const date = await currentLocalDateKey(page);
  await selectDate(page, date);

  const shiftButton = page.locator('#chips [data-shift-type-id]').first();
  await expect(shiftButton).toBeVisible();
  const shiftAssigned = waitForApi(page, 'PUT', `/api/days/${date}`);
  await shiftButton.click();
  await shiftAssigned;

  await openDayModule(page, 'overtime');
  await page.locator('#dayAddCredit').click();
  await expect(page.locator('#overtimeCreditModal')).toBeVisible();
  await page.locator('#creditTimeByShift').click();
  await expect(page.locator('#creditStart')).not.toHaveValue('');
  await expect(page.locator('#creditEnd')).not.toHaveValue('');
  await page.locator('#creditReason').fill('Scenario manager E2E');

  await page.locator('#creditScenarioSaveCurrent').click();
  await expect(page.locator('#scenarioManagerView')).toBeVisible();
  await expect(page.locator('#scenarioManagerForm')).toBeVisible();
  await expect(page.locator('#overtimeCreditForm')).toBeHidden();

  const name = `Saved scenario ${Date.now()}`;
  await page.locator('#scName').fill(name);
  const created = waitForApi(page, 'POST', '/api/quick-scenarios', 201);
  await page.locator('#scSave').click();
  const createdResponse = await created;
  const createdBody = await createdResponse.json();

  await expect(page.locator('#overtimeCreditForm')).toBeVisible();
  await expect(page.locator('#scenarioManagerView')).toBeHidden();
  await expect(page.locator('#creditScenarioSelect')).toContainText(name);

  await page.locator('#creditScenarioManage').click();
  await expect(page.locator('#scenarioManagerView')).toBeVisible();
  const row = page.locator(`[data-scenario-row="${createdBody.id}"]`);
  await expect(row).toContainText(name);
  await row.locator(`[data-scenario-edit="${createdBody.id}"]`).first().click();
  await expect(page.locator('#scenarioManagerForm')).toBeVisible();

  const editedName = `${name} edited`;
  await page.locator('#scName').fill(editedName);
  const updated = waitForApi(page, 'PATCH', `/api/quick-scenarios/${createdBody.id}`);
  await page.locator('#scSave').click();
  await updated;

  await expect(page.locator('#scenarioManagerListPane')).toBeVisible();
  await expect(page.locator(`[data-scenario-row="${createdBody.id}"]`)).toContainText(editedName);
  await page.locator('#scenarioManagerBack').click();
  await expect(page.locator('#overtimeCreditForm')).toBeVisible();
  await expect(page.locator('#creditScenarioSelect')).toContainText(editedName);

  await page.locator('#creditCancel').click();
  await page.locator('#tabbar a[data-view="settings"]').click();
  await expect(page.locator('[data-settings-jump="scenarios"]')).toHaveCount(0);
  await expect(page.locator('#quickScenarioSettingsCard')).toHaveCount(0);
});
