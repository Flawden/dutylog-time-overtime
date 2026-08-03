const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

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
  await expect(page.locator('#dutylog-vue-root')).toHaveAttribute('data-vue-version', '27.34.2');
  await expect(page.locator('#dutylog-vue-root')).toHaveAttribute('data-vue-architecture', 'vue-shell-v1');
  await expect(page.locator('#view-calendar')).toBeVisible();
  await expect(page.locator('[data-vue-app-shell]')).toBeVisible();

  expect(platform).toMatchObject({
    version: '27.34.2',
    architecture: 'vue-shell-v1',
    legacyVersion: '27.34.2',
    snapshot: {
      releaseVersion: '27.34.2',
      architecture: 'vue-shell-v1',
      phase: 'ready',
      legacyConnected: true,
      shellReady: true,
    },
  });
});
