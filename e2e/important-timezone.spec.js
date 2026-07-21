const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, waitForApi } = require('./helpers');

test('important dates have a standalone workspace and timezone survives reload', async ({ page }) => {
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
  const timezone = page.locator('#workTimezone');
  await timezone.fill('Europe/Chisinau');
  const profileSaved = waitForApi(page, 'PUT', '/api/profile');
  await timezone.press('Tab');
  await profileSaved;
  await expect(timezone).toHaveValue('Europe/Chisinau');

  await page.reload();
  await expect(page.locator('#whoami')).not.toBeEmpty();
  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="time"]').click();
  await expect(page.locator('#workTimezone')).toHaveValue('Europe/Chisinau');
});
