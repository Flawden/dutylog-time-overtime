const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForApi } = require('./helpers');

test('Vue Settings owns profile/modules/integrations and retired Settings islands natively', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'vue-settings' });
  await openView(page, 'settings');

  await expect(page.locator('html')).toHaveAttribute('data-vue-settings-workspace', 'ready');
  await expect(page.locator('[data-vue-settings-workspace-view]')).toBeVisible();
  await expect(page.locator('[data-vue-domain-owner="settings-workspace"]')).toHaveCount(1);
  await expect(page.locator('#view-settings')).toHaveCount(0);

  await expect(page.locator('#profileCard')).toHaveCount(1);
  await expect(page.locator('#languageCard')).toHaveCount(1);
  await expect(page.locator('#modulesCard')).toHaveCount(1);
  await expect(page.locator('#appearanceCard')).toHaveCount(1);
  await expect(page.locator('#settingsLegacyHost')).toHaveCount(0);
  await expect(page.locator('[data-vue-settings-workspace-view] #timeSettingsCard')).toHaveCount(1);
  await expect(page.locator('[data-vue-settings-workspace-view] #scheduleSettingsCard')).toHaveCount(1);
  await expect(page.locator('[data-vue-settings-workspace-view] #notifyCard')).toHaveCount(1);

  await page.locator('[data-settings-jump="profile"]').click();
  await expect(page.locator('#profileCard')).toHaveClass(/is-open/);
  const profileResponse = waitForApi(page, 'PUT', '/api/v1/profile');
  await page.locator('#profileName').fill('Vue Settings E2E');
  await page.locator('#profileSave').click();
  await profileResponse;
  await expect(page.locator('#profileMsg')).toContainText(/Сохранено|Saved/);

  await page.locator('[data-settings-jump="modules"]').click();
  await expect(page.locator('#moduleSettingsGrid')).toBeVisible();
  await expect(page.locator('[data-module-toggle="tasks"]')).toBeVisible();

  await page.locator('[data-settings-jump="appearance"]').click();
  await expect(page.locator('#workspaceStudio')).toBeVisible();
  await expect(page.locator('#uiWorkspace')).toBeVisible();

  await page.locator('[data-settings-jump="time"]').click();
  await expect(page.locator('[data-vue-settings-workspace-view] #timeSettingsCard')).toBeVisible();
  await page.locator('[data-settings-jump="schedule"]').click();
  await expect(page.locator('[data-vue-settings-workspace-view] #scheduleSettingsCard')).toBeVisible();
  await expect(page.locator('[data-vue-settings-native-section="schedule"]')).toBeVisible();
});
