const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('UI Core workspace stays persistent while Classic remains an instant fallback', async ({ page }) => {
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
  const classicChoice = appearanceCard.locator('[data-shell-choice="classic"]');
  await appearanceCard.locator('.settingsHead').click();
  await expect(classicChoice).toBeVisible();
  await expect(page.locator('#uiPlatformStatus')).toContainText('UI Core v1');

  await page.locator('#uiWorkspace').selectOption('planner');
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'planner');
  await expect(page.locator('#tabbar a:visible').nth(1)).toHaveAttribute('data-view', 'tasks');

  await page.locator('#uiLayout').selectOption('compact');
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'compact');

  await page.locator('#uiPalette').selectOption('violet');
  await expect(page.locator('html')).toHaveAttribute('data-ui-palette', 'violet');
  await expect(page.locator('#appearanceMsg')).toContainText(/Сохранено автоматически|Saved automatically/);

  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'planner');
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'compact');
  await expect(page.locator('html')).toHaveAttribute('data-ui-palette', 'violet');
  await expect(appearanceCard).toHaveClass(/is-open/);
  await expect(classicChoice).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('dutylog.settings.openSection'))).toBe('appearance');

  await classicChoice.click();
  await expect(page.locator('html')).toHaveAttribute('data-shell', 'classic');
  await expect(page.locator('#nextTopbar')).toBeHidden();
  await expect(page.locator('#tabbar a[data-view="today"]')).toBeHidden();
  await expect(page.locator('#tabbar a[data-view="important"]')).toBeVisible();
  await expect(page.locator('[data-shell-choice="classic"]')).toHaveAttribute('aria-pressed', 'true');

  await page.locator('[data-shell-choice="next"]').click();
  await expect(page.locator('html')).toHaveAttribute('data-shell', 'next');
  await expect(page.locator('#nextTopbar')).toBeVisible();
  await expect(page.locator('#tabbar a[data-view="today"]')).toBeVisible();
  await expect(page.locator('#tabbar a[data-view="important"]')).toBeHidden();
  await expect(page.locator('[data-shell-choice="next"]')).toHaveAttribute('aria-pressed', 'true');
});
