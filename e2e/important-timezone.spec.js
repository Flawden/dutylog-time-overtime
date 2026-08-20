const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, waitForApi, waitForAppIdle, openView, selectDate } = require('./helpers');

test('important dates stay floating while canonical timezone survives reload', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'important' });

  await openView(page, 'important');
  await expect(page.locator('#importantBoardList')).toBeVisible();

  const date = await currentLocalDateKey(page);
  const originalTitle = `Important ${Date.now()}`;
  await page.locator('#importantBoardNew').click();
  await expect(page.locator('#importantEditModal')).toBeVisible();
  await page.locator('#importantEditName').fill(originalTitle);
  await page.locator('#importantEditStartDate').fill(date);
  await page.locator('#importantEditRepeat').selectOption('YEARLY');

  const created = waitForApi(page, 'POST', '/api/v1/important-days');
  await page.locator('#importantEditSave').click();
  await created;
  await expect(page.locator('#importantEditModal')).toBeHidden();

  const row = page.locator('#importantBoardList .importantBoardRow', { hasText: originalTitle });
  await expect(row).toBeVisible();
  await row.locator('[data-important-edit]').click();
  await expect(page.locator('#importantEditModal')).toBeVisible();
  await expect(page.locator('#importantDetailsModal')).toBeHidden();
  await expect(page.locator('#importantEditName')).toHaveValue(originalTitle);

  const updatedTitle = `${originalTitle} updated`;
  await page.locator('#importantEditName').fill(updatedTitle);
  const updated = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'PATCH' && /^\/api\/v1\/important-days\/\d+$/.test(url.pathname) && response.status() === 200;
  });
  await page.locator('#importantEditSave').click();
  await updated;
  await expect(page.locator('#importantEditModal')).toBeHidden();
  await expect(page.locator('#importantDetailsModal')).toBeHidden();
  await expect(page.locator('#importantBoardList .importantBoardRow', { hasText: updatedTitle })).toBeVisible();

  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await expect(page.locator('#timeSettingsCard')).toHaveClass(/is-open/);
  await expect(page.locator('#workRegionName')).toHaveCount(0);
  await expect(page.locator('#workOffsetMoscow')).toHaveCount(0);
  const timezone = page.locator('#workTimezone');
  const displayTimezone = page.locator('#displayTimezone');
  await expect(timezone).toHaveAttribute('aria-describedby', 'timeZoneHelp');
  await expect(displayTimezone).toHaveAttribute('type', 'hidden');
  await timezone.selectOption('Europe/Chisinau');
  await expect(page.locator('#timeSettingsStatus')).toContainText(/не сохранено|not saved/i);
  await expect(page.locator('#timeNowBox')).toContainText('Europe/Chisinau');
  const profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await page.evaluate(() => Promise.resolve(window.__dutylogTimeSettingsSaveReady));
  await waitForAppIdle(page);
  await expect(timezone).toHaveValue('Europe/Chisinau');
  await expect(displayTimezone).toHaveValue('Europe/Chisinau');
  await expect(page.locator('#timeSettingsStatus')).toHaveText('Часовой пояс сохранён');

  await page.reload();
  await expect(page.locator('[data-vue-shell-profile] > b')).not.toBeEmpty();
  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await expect(page.locator('#workTimezone')).toHaveValue('Europe/Chisinau');
  await expect(page.locator('#displayTimezone')).toHaveValue('Europe/Chisinau');
});


test('existing dated shift keeps its source zone and reprojects after canonical timezone change', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shift-zone' });

  // First establish the zone in which the real shift is assigned.
  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Asia/Yekaterinburg');
  let profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await page.evaluate(() => Promise.resolve(window.__dutylogTimeSettingsSaveReady));
  await waitForAppIdle(page);

  // The user defines the real local shift while living in UTC+5. Future
  // timezone changes must project this template instead of reinterpreting it.
  await page.locator('#defDayStart').fill('08:30');
  await page.locator('#defDayEnd').fill('17:00');
  const builtinsSaved = new Promise(resolve => {
    let count = 0;
    const handler = response => {
      const url = new URL(response.url());
      if (response.request().method() === 'PATCH'
          && /^\/api\/v1\/shift-types\/\d+$/.test(url.pathname)
          && response.status() === 200
          && ++count === 2) {
        page.off('response', handler);
        resolve();
      }
    };
    page.on('response', handler);
  });
  await page.locator('#timeApplyBuiltins').click();
  await builtinsSaved;
  await expect(page.locator('#defDayStart')).toHaveValue('08:30');
  await expect(page.locator('#defDayEnd')).toHaveValue('17:00');

  await openView(page, 'calendar');
  const firstDay = page.locator('#grid .cell:not(.outside)').first();
  const shiftDate = await firstDay.getAttribute('data-date');
  expect(shiftDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  await selectDate(page, shiftDate);
  const dayShift = page.locator('#chips .chip').filter({ hasText: /Дневная|Day shift/ }).first();
  const saved = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'PUT'
      && /^\/api\/days\/\d{4}-\d{2}-\d{2}$/.test(url.pathname)
      && response.status() === 200;
  });
  await dayShift.click();
  await saved;

  // Moving the account must not reinterpret 08:30 as 08:30 in the new zone.
  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Europe/Kyiv');
  profileSaved = waitForApi(page, 'PUT', '/api/profile');
  const calendarRefreshed = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'GET'
      && url.pathname === '/api/v1/calendar'
      && response.status() === 200;
  });
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await page.evaluate(() => Promise.resolve(window.__dutylogTimeSettingsSaveReady));
  await waitForAppIdle(page);
  await calendarRefreshed;
  await expect(page.locator('#defDayStart')).toHaveValue('06:30');
  await expect(page.locator('#defDayEnd')).toHaveValue('15:00');
  await expect(page.locator('#shiftTemplateZoneHint')).toContainText('Europe/Kyiv');

  await page.locator('[data-settings-jump="notifications"]').click();
  await page.locator('#notifShift').check();
  await page.locator('#notifShiftBefore').fill('30');
  const notificationsSaved = waitForApi(page, 'PATCH', '/api/v1/notifications/settings');
  await page.locator('#notifSave').click();
  await notificationsSaved;
  await expect(page.locator('#notifyList')).toContainText('Начало 06:30 Europe/Kyiv');
  await expect(page.locator('#notifyList')).toContainText('06:00');

  await selectDate(page, shiftDate);
  const projectedCellAfterTimezoneMove = page.locator(`#grid [data-date="${shiftDate}"]`);
  await expect(projectedCellAfterTimezoneMove).toContainText('06:30–15:00');
  await expect(projectedCellAfterTimezoneMove).not.toContainText('08:30–17:00');
  await expect(page.locator('#panel')).toBeVisible();
});

test('a timezone projection can move a late shift to the next calendar date', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shift-next-day' });
  const dates = await page.evaluate(() => {
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    const month = pad(now.getMonth() + 1);
    const prefix = `${now.getFullYear()}-${month}`;
    return {
      source:`${prefix}-03`,
      projected:`${prefix}-04`,
      sourceDisplay:`03.${month}`,
      projectedDisplay:`04.${month}`
    };
  });

  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('UTC');
  let profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await page.evaluate(() => Promise.resolve(window.__dutylogTimeSettingsSaveReady));
  await waitForAppIdle(page);

  const lateShift = await page.evaluate(() => jfetch('/api/shift-types', {
    method:'POST',
    body:{
      name:'Поздняя E2E', hours:8, color:'#7B8CE0',
      startTime:'23:00', endTime:'07:00', breakMinutes:0, plannedHours:8,
      notificationsEnabled:false
    }
  }));
  await page.evaluate(({ id, source }) => jfetch(`/api/days/${source}`, {
    method:'PUT', body:{ shiftTypeId:id, note:null, dayEmoji:null, overtimeHours:0, timeOffHours:0 }
  }), { id:lateShift.id, source:dates.source });

  await openView(page, 'settings');
  await page.locator('[data-settings-jump="time"]').click();
  // Moving from UTC to fixed UTC+5 sends the whole interval to the next local calendar date.
  await page.locator('#workTimezone').selectOption('Asia/Yekaterinburg');
  profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await page.evaluate(() => Promise.resolve(window.__dutylogTimeSettingsSaveReady));
  await waitForAppIdle(page);

  const projectedTemplate = await page.evaluate(({ id }) => jfetch('/api/shift-types')
    .then(items => items.find(item => Number(item.id) === Number(id))), lateShift);
  expect(projectedTemplate.startTime).toBe('04:00');
  expect(projectedTemplate.endTime).toBe('12:00');

  await openView(page, 'calendar');
  const projectedCell = page.locator(`#grid [data-date="${dates.projected}"]`);
  await expect(projectedCell).toContainText('Поздняя E2E');
  await expect(projectedCell).toContainText('04:00–12:00');
  const sourceCell = page.locator(`#grid [data-date="${dates.source}"]`);
  await expect(sourceCell).not.toContainText('Поздняя E2E');

  await projectedCell.click();
  await expect(projectedCell).toHaveClass(/sel/);
  await expect(page.locator('#panel')).toBeVisible();
});
