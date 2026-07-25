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


test('existing dated shift refreshes after canonical timezone change', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shift-zone' });

  // Create the dated shift first. The bug reproduced only for an existing cached
  // month: settings changed, but the day panel kept the previous projection.
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

  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  await page.locator('#workTimezone').selectOption('Asia/Yekaterinburg');
  const profileSaved = waitForApi(page, 'PUT', '/api/profile');
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
  await expect(projection).toContainText('Asia/Yekaterinburg');
  await expect(projection).toContainText('08:30');
  await expect(projection).toContainText(/Рабочее время смены|Shift work time/);
  await expect(projection).toContainText(/Обед в смене|Shift break/);
  await expect(projection).not.toContainText('Europe/Kyiv');
});
