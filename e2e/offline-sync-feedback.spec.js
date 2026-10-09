const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test('manual synchronization shows progress and a final result', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'work', prefix: 'syncfeedback' });

  await page.locator('#offlineStatus').click();
  await expect(page.locator('#offlineSyncDialog')).toBeVisible();

  await page.evaluate(() => {
    const platform = window.DutyLogLegacyPlatform;
    if (typeof platform?.offlineSync !== 'function') {
      throw new Error('Offline sync bridge is unavailable');
    }

    let release;
    const gate = new Promise(resolve => { release = resolve; });
    window.__syncFeedbackControl = { started: false, release };

    const replacement = Object.freeze({
      ...platform,
      async offlineSync() {
        window.__syncFeedbackControl.started = true;
        await gate;
        await platform.offlineSync.call(platform);
      },
    });

    if (!Reflect.set(window, 'DutyLogLegacyPlatform', replacement) ||
        window.DutyLogLegacyPlatform !== replacement) {
      throw new Error('Could not replace offline sync bridge');
    }
  });

  const button = page.locator('#offlineSyncNow');
  const feedback = page.locator('#offlineSyncFeedback');

  try {
    await button.click();
    await expect.poll(() => page.evaluate(
      () => window.__syncFeedbackControl.started
    )).toBe(true);

    await expect(button).toBeDisabled();
    await expect(button).toHaveAttribute('aria-busy', 'true');
    await expect(button).toContainText(/Синхронизация|Syncing/i);
    await expect(feedback).toContainText(/Синхронизация|Syncing/i);
  } finally {
    await page.evaluate(() => window.__syncFeedbackControl.release());
  }

  await expect(button).toBeEnabled({ timeout: 15_000 });
  await expect(button).toHaveAttribute('aria-busy', 'false');
  await expect(feedback).toContainText(/Нет изменений|No changes/i);
});
