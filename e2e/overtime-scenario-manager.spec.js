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

  // A full shift minus its own planned norm is intentionally 0 overtime.
  // Turn the draft into a real reusable “two hours after shift” scenario.
  await page.evaluate(() => {
    const startInput = document.querySelector('#creditStart');
    const endInput = document.querySelector('#creditEnd');
    const breakInput = document.querySelector('#creditBreak');
    const plannedInput = document.querySelector('#creditPlanned');
    const shiftEnd = endInput.value;
    const [datePart, timePart] = shiftEnd.split('T');
    const [year, month, day] = datePart.split('-').map(Number);
    const [hour, minute] = timePart.split(':').map(Number);
    const end = new Date(year, month - 1, day, hour, minute || 0);
    end.setMinutes(end.getMinutes() + 120);
    const pad = value => String(value).padStart(2, '0');
    const endValue = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}`;

    startInput.value = shiftEnd;
    endInput.value = endValue;
    breakInput.value = '0';
    plannedInput.value = '0';
    for (const input of [startInput, endInput, breakInput, plannedInput]) {
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
  });
  await expect(page.locator('#creditHours')).toHaveValue('2');
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
