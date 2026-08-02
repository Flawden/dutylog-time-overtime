const { test, expect } = require('./fixtures');
const { registerAndOnboard, waitForAppIdle } = require('./helpers');

async function openAppearance(page) {
  await page.locator('#tabbar a[data-view="settings"]').click();
  await expect(page.locator('#view-settings')).toBeVisible();
  const card = page.locator('#appearanceCard');
  if (!(await card.getAttribute('class') || '').includes('is-open')) {
    await card.locator('.settingsHead').click();
  }
  await expect(page.locator('#workspaceStudio')).toBeVisible();
}

function studioRow(page, kind, id) {
  return page.locator(`[data-studio-kind="${kind}"][data-studio-id="${id}"]`);
}

test.use({ viewport: { width: 1280, height: 960 } });

test('Workspace Studio persists custom navigation, Today cards, layout and calendar presentation', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'studio' });
  await openAppearance(page);

  await expect(page.locator('html')).toHaveAttribute('data-ui-contract', '2');
  await expect(page.locator('#uiPlatformStatus')).toContainText('UI Core v2');

  await page.locator('#uiLayout').selectOption('sidebar');
  await page.locator('#uiDecoration').selectOption('grid');
  await page.locator('#uiCalendarDensity').selectOption('compact');
  await page.locator('#uiCalendarLayerStyle').selectOption('dots');

  await page.locator('#workspaceCustomize').click();
  await expect(page.locator('#uiWorkspace')).toHaveValue('custom');
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'custom');

  await studioRow(page, 'navigation', 'vacation').locator('[data-studio-visible]').uncheck();
  await studioRow(page, 'navigation', 'tasks').locator('[data-studio-visible]').check();
  for (let i = 0; i < 4; i += 1) {
    await studioRow(page, 'navigation', 'tasks').locator('[data-studio-move="-1"]').click();
  }

  await studioRow(page, 'widget', 'overtime').locator('[data-studio-visible]').uncheck();
  await studioRow(page, 'widget', 'tasks').locator('[data-studio-move="-1"]').click();

  await expect(page.locator('#appearanceMsg')).toContainText(/Сохранено автоматически|Saved automatically/);
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'sidebar');
  await expect(page.locator('html')).toHaveAttribute('data-ui-decoration', 'grid');
  await expect(page.locator('html')).toHaveAttribute('data-ui-calendar-density', 'compact');
  await expect(page.locator('html')).toHaveAttribute('data-ui-calendar-layers', 'dots');
  await expect(page.locator('#tabbar a[data-view="tasks"]')).toBeVisible();
  await expect(page.locator('#tabbar a[data-view="vacation"]')).toBeHidden();
  await expect(page.locator('#tabbar a:visible')).toHaveCount(5);

  await page.locator('#tabbar a[data-view="today"]').click();
  await expect(page.locator('#view-today')).toBeVisible();
  await expect(page.locator('#todayOvertimeCard')).toBeHidden();
  await expect(page.locator('#todayTasksCard')).toBeVisible();
  await expect(page.locator('#todayShiftCard')).toBeVisible();
  expect(await page.evaluate(() => {
    const tasks = document.querySelector('#todayTasksCard');
    const shift = document.querySelector('#todayShiftCard');
    return !!(tasks.compareDocumentPosition(shift) & Node.DOCUMENT_POSITION_FOLLOWING);
  })).toBe(true);

  await waitForAppIdle(page);
  await page.reload();
  await waitForAppIdle(page);

  await expect(page.locator('html')).toHaveAttribute('data-ui-contract', '2');
  await expect(page.locator('html')).toHaveAttribute('data-ui-workspace', 'custom');
  await expect(page.locator('html')).toHaveAttribute('data-ui-layout', 'sidebar');
  await expect(page.locator('html')).toHaveAttribute('data-ui-decoration', 'grid');
  await expect(page.locator('html')).toHaveAttribute('data-ui-calendar-density', 'compact');
  await expect(page.locator('html')).toHaveAttribute('data-ui-calendar-layers', 'dots');
  await expect(page.locator('#tabbar a[data-view="tasks"]')).toBeVisible();
  await expect(page.locator('#tabbar a[data-view="vacation"]')).toBeHidden();
  await expect(page.locator('#todayOvertimeCard')).toBeHidden();

  await openAppearance(page);
  await expect(page.locator('#uiWorkspace')).toHaveValue('custom');
  await expect(page.locator('#uiLayout')).toHaveValue('sidebar');
  await expect(page.locator('#uiDecoration')).toHaveValue('grid');
  await expect(page.locator('#uiCalendarDensity')).toHaveValue('compact');
  await expect(page.locator('#uiCalendarLayerStyle')).toHaveValue('dots');
  await expect(studioRow(page, 'navigation', 'tasks').locator('[data-studio-visible]')).toBeChecked();
  await expect(studioRow(page, 'navigation', 'vacation').locator('[data-studio-visible]')).not.toBeChecked();
  await expect(studioRow(page, 'widget', 'overtime').locator('[data-studio-visible]')).not.toBeChecked();
});
