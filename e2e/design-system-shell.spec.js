const { test, expect } = require('./fixtures');
const { registerAndOnboard, waitForAppIdle } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('UI Core workspace persists in the single DutyLog shell after Classic sunset', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shell' });

  await expect(page.locator('html')).toHaveAttribute('data-shell', 'next');
  await expect(page.locator('html')).toHaveAttribute('data-ui-contract', '1');
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'shift-worker');
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'dashboard');
  await expect(page.locator('#nextTopbar')).toBeVisible();
  await expect(page.locator('#tabbar a:visible .navIcon')).toHaveCount(5);
  await expect(page.locator('#tabbar a[data-view="today"]')).toHaveAttribute('aria-current', 'page');

  const before = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
    navPosition: getComputedStyle(document.querySelector('#tabbar')).position,
  }));
  expect(before.content).toBeLessThanOrEqual(before.viewport + 1);
  expect(before.navPosition).toBe('fixed');

  await page.locator('#tabbar a[data-view="settings"]').click();
  await expect(page.locator('#view-settings')).toBeVisible();
  const appearanceCard = page.locator('#appearanceCard');
  await appearanceCard.locator('.settingsHead').click();
  await expect(page.locator('#uiPlatformStatus')).toContainText('UI Core v1');
  await expect(page.locator('#singleShellNotice')).toBeVisible();
  await expect(page.locator('[data-shell-choice]')).toHaveCount(0);

  await page.locator('#uiWorkspace').selectOption('planner');
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'planner');
  await expect(page.locator('#tabbar a:visible').nth(1)).toHaveAttribute('data-view', 'tasks');

  await page.locator('#uiLayout').selectOption('compact');
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'compact');

  await page.locator('#uiPalette').selectOption('violet');
  await expect(page.locator('html')).toHaveAttribute('data-ui-palette', 'violet');
  await expect(page.locator('#appearanceMsg')).toContainText(/Сохранено автоматически|Saved automatically/);

  await waitForAppIdle(page);
  await page.reload();
  await waitForAppIdle(page);
  await expect(page.locator('html')).toHaveAttribute('data-shell', 'next');
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'planner');
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'compact');
  await expect(page.locator('html')).toHaveAttribute('data-ui-palette', 'violet');
  await expect(appearanceCard).toHaveClass(/is-open/);
  await expect(page.locator('#singleShellNotice')).toBeVisible();
  await expect(page.locator('[data-shell-choice]')).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.getItem('dutylog.settings.openSection'))).toBe('appearance');

  // Simulate a user upgrading from a profile/local cache that still contains
  // the retired shellMode=classic field. Bootstrap and runtime must ignore it.
  await page.evaluate(() => {
    const key = 'dutylog.appearance.v2';
    const appearance = JSON.parse(localStorage.getItem(key) || '{}');
    appearance.themeConfig = { ...(appearance.themeConfig || {}), shellMode: 'classic' };
    localStorage.setItem(key, JSON.stringify(appearance));
  });
  await waitForAppIdle(page);
  await page.reload();
  await waitForAppIdle(page);
  await expect(page.locator('html')).toHaveAttribute('data-shell', 'next');
  await expect(page.locator('#nextTopbar')).toBeVisible();
  await expect(page.locator('#tabbar a[data-view="today"]')).toBeVisible();
  await expect(page.locator('[data-shell-choice]')).toHaveCount(0);
});
