const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('Vue foundation boots the released app shell inside the same-origin Spring session', async ({ page }) => {
  await registerAndOnboard(page, { prefix: 'vuefoundation' });

  const platform = await page.evaluate(async () => {
    const ready = await window.__dutylogVueReady;
    return {
      version: ready.version,
      architecture: ready.architecture,
      snapshot: ready.snapshot(),
      legacyVersion: window.DutyLogLegacyPlatform?.version,
    };
  });

  await expect(page.locator('#dutylog-vue-root')).toHaveAttribute('data-vue-ready', 'true');
  await expect(page.locator('#dutylog-vue-root')).toHaveAttribute('data-vue-version', '27.38.9');
  await expect(page.locator('#dutylog-vue-root')).toHaveAttribute('data-vue-architecture', 'vue-shell-v1');
  await openView(page, 'calendar');
  await expect(page.locator('#view-calendar')).toBeVisible();
  await expect(page.locator('[data-vue-app-shell]')).toBeVisible();

  expect(platform).toMatchObject({
    version: '27.38.9',
    architecture: 'vue-shell-v1',
    legacyVersion: '27.38.9',
    snapshot: {
      releaseVersion: '27.38.9',
      architecture: 'vue-shell-v1',
      phase: 'ready',
      legacyConnected: true,
      shellReady: true,
    },
  });

  const diagnostics = await page.evaluate(() => window.DutyLogVuePlatform?.diagnostics());
  expect(diagnostics).toMatchObject({ releaseVersion: '27.38.9', route: 'calendar', fatal: null });

  await page.evaluate(() => {
    const controlled = Object.assign(new Error('controlled recovery probe'), { requestId: 'e2e-recovery-35' });
    const event = new Event('unhandledrejection');
    Object.defineProperty(event, 'reason', { value: controlled });
    window.dispatchEvent(event);
  });
  await expect(page.locator('[data-vue-recovery-ui]')).toBeVisible();
  await expect(page.locator('[data-vue-recovery-request-id]')).toHaveText('e2e-recovery-35');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    page.locator('[data-vue-recovery-today]').click(),
  ]);
  await page.evaluate(() => window.__dutylogVueReady);
});
