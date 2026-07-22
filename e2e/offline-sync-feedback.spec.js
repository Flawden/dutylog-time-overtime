const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test('manual synchronization shows progress and a final result', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'work', prefix: 'syncfeedback' });

  await page.locator('#offlineStatus').click();
  await expect(page.locator('#offlineSyncDialog')).toBeVisible();

  await page.route('**/api/calendar?**', async route => {
    await new Promise(resolve => setTimeout(resolve, 500));
    await route.continue();
  });

  await page.locator('#offlineSyncNow').click();
  await expect(page.locator('#offlineSyncNow')).toBeDisabled();
  await expect(page.locator('#offlineSyncNow')).toContainText(/Синхронизация|Syncing/i);
  await expect(page.locator('#offlineSyncFeedback')).toContainText(/Синхронизация|Syncing/i);

  await expect(page.locator('#offlineSyncNow')).toBeEnabled({ timeout: 15_000 });
  await expect(page.locator('#offlineSyncFeedback')).toContainText(/Нет изменений|No changes/i);
});
