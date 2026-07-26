const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, waitForApi } = require('./helpers');

test('important dates stay floating while canonical timezone survives reload', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'important' });

  await page.locator('#tabbar a[data-view="important"]').click();
  await expect(page.locator('#view-important')).toBeVisible();
  await expect(page.locator('#importantBoardList')).toBeVisible();

  const date = await currentLocalDateKey(page);
  const originalTitle = `Important ${Date.now()}`;
  await page.locator('#importantBoardTitle').fill(originalTitle);
  await page.locator('#importantBoardDate').fill(date);
  await page.locator('#importantBoardRepeat').selectOption('YEARLY');

  const created = waitForApi(page, 'POST', '/api/important-days');
  await page.locator('#importantBoardSave').click();
  await created;

  const row = page.locator('#importantBoardList .importantBoardRow', { hasText: originalTitle });
  await expect(row).toBeVisible();
  await row.locator('[data-important-edit]').click();
  await expect(page.locator('#importantBoardTitle')).toHaveValue(originalTitle);

  const updatedTitle = `${originalTitle} updated`;
  await page.locator('#importantBoardTitle').fill(updatedTitle);
  const updated = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'PATCH' && /^\/api\/important-days\/\d+$/.test(url.pathname) && response.status() === 200;
  });
  await page.locator('#importantBoardSave').click();
  await updated;
  await expect(page.locator('#importantBoardList .importantBoardRow', { hasText: updatedTitle })).toBeVisible();

  await page.locator('#tabbar a[data-view="settings"]').click();
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
  await expect(timezone).toHaveValue('Europe/Chisinau');
  await expect(displayTimezone).toHaveValue('Europe/Chisinau');
  await expect(page.locator('#timeSettingsStatus')).toContainText(/сохранено|saved/i);

  await page.reload();
  await expect(page.locator('#whoami')).not.toBeEmpty();
  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  await expect(page.locator('#workTimezone')).toHaveValue('Europe/Chisinau');
  await expect(page.locator('#displayTimezone')).toHaveValue('Europe/Chisinau');
});


test('existing dated shift keeps its source zone and reprojects after canonical timezone change', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shift-zone' });

  // First establish the zone in which the real shift is assigned.
  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Asia/Yekaterinburg');
  let profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;

  await page.locator('#tabbar a[data-view="calendar"]').click();
  const day = page.locator('#grid .cell:not(.empty)').first();
  await day.click();
  await expect(page.locator('#panel')).toBeVisible();
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
  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Europe/Kyiv');
  profileSaved = waitForApi(page, 'PUT', '/api/profile');
  const calendarRefreshed = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'GET'
      && url.pathname === '/api/calendar'
      && url.searchParams.has('_')
      && response.status() === 200;
  });
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;
  await calendarRefreshed;

  await page.locator('#tabbar a[data-view="calendar"]').click();
  await day.click();
  const projection = page.locator('#shiftProjection');
  await expect(projection).toBeVisible();
  await expect(projection).toContainText('Europe/Kyiv');
  await expect(projection).toContainText('06:30–15:00');
  await expect(projection).toContainText('Asia/Yekaterinburg');
  await expect(projection).toContainText('08:30–17:00');
  await expect(projection).toContainText(/Рабочее время смены|Shift work time/);
  await expect(projection).toContainText(/Обед в смене|Shift break/);
});

test('a timezone projection can move a late shift to the next calendar date', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shift-next-day' });

  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Europe/Kyiv');
  let profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;

  const lateShift = await page.evaluate(() => jfetch('/api/shift-types', {
    method:'POST',
    body:{
      name:'Поздняя E2E', hours:8, color:'#7B8CE0',
      startTime:'23:00', endTime:'07:00', breakMinutes:0, plannedHours:8,
      notificationsEnabled:false
    }
  }));
  await page.evaluate(({ id }) => jfetch('/api/days/2026-07-03', {
    method:'PUT', body:{ shiftTypeId:id, note:null, dayEmoji:null, overtimeHours:0, timeOffHours:0 }
  }), lateShift);

  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  // Moving +2 hours sends the whole interval to July 4.
  await page.locator('#workTimezone').selectOption('Asia/Yekaterinburg');
  profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await page.locator('#timeSaveTimezone').click();
  await profileSaved;

  await page.locator('#tabbar a[data-view="calendar"]').click();
  const julyFourth = page.locator('#grid [data-date="2026-07-04"]');
  await expect(julyFourth).toContainText('Поздняя E2E');
  await expect(julyFourth).toContainText('01:00–09:00');
  const julyThird = page.locator('#grid [data-date="2026-07-03"]');
  await expect(julyThird).not.toContainText('Поздняя E2E');

  await julyFourth.click();
  await expect(page.locator('#shiftProjection')).toContainText('01:00–09:00');
  await expect(page.locator('#shiftProjection')).toContainText('03.07.2026 23:00–04.07.2026 07:00');
});
