const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, selectDate, waitForApi, openView } = require('./helpers');

const plusDays = (key, days) => {
  const value = new Date(`${key}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
};

test('one absence composer routes balances and projects full and partial facts into the calendar', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 960 });
  await registerAndOnboard(page, { preset:'full', prefix:'absence-composer' });
  const today = await currentLocalDateKey(page);
  const sickDay = plusDays(today, 1);

  for (const date of [today, sickDay]) {
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
      body:JSON.stringify({ date, hours:8, reason:'Composer E2E bank' })
    });
    if (!response.ok) throw new Error(await response.text());
  }, today);

  await page.locator('#pClose').click();
  await expect(page.locator('#globalQuickAdd')).toBeVisible();
  await page.locator('#globalQuickAdd').click();
  await page.locator('#quickActionUsage').click();
  await expect(page.locator('#absenceComposerModal')).toBeVisible();
  const timeOffValue = await page.locator('#vacationType option', { hasText:'Отгул' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(timeOffValue);
  await expect(page.locator('#vacationCompensation')).toHaveValue('OVERTIME_BANK');
  await expect(page.locator('#absenceComposerContext')).toContainText('Банк переработок');
  await expect(page.locator('#absenceComposerContext')).toContainText('8 ч');
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await page.locator('#vacationCoverage').selectOption('PARTIAL');
  await page.locator('#vacationTitle').fill('Четыре часа отгула');
  await page.locator('#vacationStart').fill(today);
  await page.locator('#vacationStartTime').fill('09:00');
  await page.locator('#vacationEndTime').fill('13:00');
  const timeOffCreated = waitForApi(page, 'POST', '/api/vacation-planner/absences', 201);
  await page.locator('#vacationSaveBtn').click();
  await timeOffCreated;
  await expect(page.locator('#absenceComposerModal')).toBeHidden();

  await selectDate(page, today);
  const todayCell = page.locator(`#grid [data-date="${today}"]`);
  await expect(todayCell.locator('.shift')).toBeVisible();
  await expect(todayCell.locator('.partialAbsenceBar')).toContainText('09:00–13:00');
  await expect(todayCell.locator('.partialAbsenceBar')).toContainText('◷');

  await openView(page, 'vacation');
  await page.locator('#vacationComposerOpen').click();
  await expect(page.locator('#absenceComposerModal')).toBeVisible();
  const sickValue = await page.locator('#vacationType option', { hasText:'Больничный' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(sickValue);
  await expect(page.locator('#vacationCompensation')).toHaveValue('SICK_PAY');
  await expect(page.locator('#absenceComposerContext')).toContainText('Баланс не используется');
  await page.locator('#vacationStatus').selectOption('APPROVED');
  await page.locator('#vacationCoverage').selectOption('FULL_DAY');
  await page.locator('#vacationTitle').fill('Больничный E2E');
  await page.locator('#vacationStart').fill(sickDay);
  await page.locator('#vacationEnd').fill(sickDay);
  const sickCreated = waitForApi(page, 'POST', '/api/vacation-planner/absences', 201);
  await page.locator('#vacationSaveBtn').click();
  await sickCreated;
  await expect(page.locator('#absenceComposerModal')).toBeHidden();

  await selectDate(page, sickDay);
  const sickCell = page.locator(`#grid [data-date="${sickDay}"]`);
  await expect(sickCell.locator('.absenceFact')).toContainText('✚');
  await expect(sickCell.locator('.absenceFact')).toContainText('Больничный E2E');
  await expect(sickCell.locator('.plannedShiftGhost')).toBeVisible();

  await openView(page, 'vacation');
  await page.locator('#vacationComposerOpen').click();
  const vacationValue = await page.locator('#vacationType option', { hasText:'Отпуск' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(vacationValue);
  await expect(page.locator('#vacationCompensation')).toHaveValue('VACATION_ALLOWANCE');
  await expect(page.locator('#absenceComposerContext')).toContainText('Отпускной баланс');
  const unpaidValue = await page.locator('#vacationType option', { hasText:'Без содержания' }).getAttribute('value');
  await page.locator('#vacationType').selectOption(unpaidValue);
  await expect(page.locator('#vacationCompensation')).toHaveValue('UNPAID');
  await expect(page.locator('#absenceComposerContext')).toContainText('Баланс не используется');
  await page.locator('#absenceComposerClose').click();
  await expect(page.locator('#absenceComposerModal')).toBeHidden();
});
