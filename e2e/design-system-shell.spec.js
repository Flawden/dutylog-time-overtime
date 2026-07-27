const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('DutyLog Next mobile shell stays usable and Classic remains an instant fallback', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'shell' });

  await expect(page.locator('html')).toHaveAttribute('data-shell', 'next');
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
  await page.locator('#appearanceCard .settingsHead').click();
  await expect(page.locator('[data-shell-choice="classic"]')).toBeVisible();

  await page.locator('[data-shell-choice="classic"]').click();
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
