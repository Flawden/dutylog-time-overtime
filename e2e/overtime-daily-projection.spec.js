const { test, expect } = require('./fixtures');
const { registerAndOnboard, waitForApi, openView } = require('./helpers');

async function setTimezone(page, zone) {
  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption(zone);
  const saved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await saved;
}

async function account(page) {
  return page.evaluate(() => jfetch('/api/overtime/account'));
}

function sumForDate(rows, date, field) {
  return rows
    .filter(row => row.workedDate === date)
    .reduce((sum, row) => sum + Number(row[field] || 0), 0);
}

test('overtime and FIFO are redistributed by current timezone day without moving absolute minutes', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'overtime-projection' });
  await setTimezone(page, 'Europe/Moscow');

  await page.evaluate(() => jfetch('/api/overtime/credits', {
    method:'POST',
    body:{
      date:'2026-07-03',
      startDateTime:'2026-07-03T22:00',
      endDateTime:'2026-07-04T02:00',
      breakMinutes:0,
      plannedHours:0,
      hours:null,
      reason:'Daily projection E2E'
    }
  }));
  await page.evaluate(async () => {
    const planner = await jfetch('/api/vacation-planner');
    const type = planner.types.find(item => item.systemCode === 'TIME_OFF');
    await jfetch('/api/vacation-planner/absences', {
      method:'POST',
      body:{
        typeId:type.id, title:'Projection FIFO', startDate:'2026-07-05', endDate:'2026-07-05',
        status:'APPROVED', coverage:'PARTIAL', startTime:'09:00', endTime:'12:00',
        compensationPolicy:'OVERTIME_BANK'
      }
    });
  });

  let data = await account(page);
  expect(sumForDate(data.credits, '2026-07-03', 'hours')).toBe(2);
  expect(sumForDate(data.credits, '2026-07-04', 'hours')).toBe(2);
  expect(data.totalUsedHours).toBe(3);
  expect(data.balanceHours).toBe(1);

  await setTimezone(page, 'Asia/Tbilisi');
  data = await account(page);
  expect(sumForDate(data.credits, '2026-07-03', 'hours')).toBe(1);
  expect(sumForDate(data.credits, '2026-07-04', 'hours')).toBe(3);
  expect(sumForDate(data.credits, '2026-07-03', 'usedHours')).toBe(1);
  expect(sumForDate(data.credits, '2026-07-04', 'usedHours')).toBe(2);
  expect(data.usages[0].allocations.reduce((sum, row) => sum + row.minutes, 0)).toBe(180);
  expect(data.balanceHours).toBe(1);

  await openView(page, 'overtime');
  await page.evaluate(() => loadLedgerPage(true));
  await page.locator('#timeBankTabCredits').click();
  await expect(page.locator('#ledgerRows')).toContainText('итого за день');
  await expect(page.locator('#ledgerRows')).toContainText('+3 ч');

  await setTimezone(page, 'Asia/Yekaterinburg');
  data = await account(page);
  expect(sumForDate(data.credits, '2026-07-03', 'hours')).toBe(0);
  expect(sumForDate(data.credits, '2026-07-04', 'hours')).toBe(4);
  expect(sumForDate(data.credits, '2026-07-04', 'usedHours')).toBe(3);
  expect(data.usages[0].allocations.reduce((sum, row) => sum + row.minutes, 0)).toBe(180);
  expect(data.totalEarnedHours).toBe(4);
  expect(data.totalUsedHours).toBe(3);
  expect(data.balanceHours).toBe(1);

  await setTimezone(page, 'Europe/Moscow');
  data = await account(page);
  expect(sumForDate(data.credits, '2026-07-03', 'hours')).toBe(2);
  expect(sumForDate(data.credits, '2026-07-04', 'hours')).toBe(2);
  expect(data.usages[0].allocations.reduce((sum, row) => sum + row.minutes, 0)).toBe(180);
  expect(data.balanceHours).toBe(1);
});
