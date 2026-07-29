const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

async function openAppearance(page) {
  await page.locator('#tabbar a[data-view="settings"]').click();
  await expect(page.locator('#view-settings')).toBeVisible();
  const card = page.locator('#appearanceCard');
  if (!(await card.getAttribute('class') || '').includes('is-open')) {
    await card.locator('.settingsHead').click();
  }
  await expect(page.locator('#uiPalette')).toBeVisible();
}

async function cssVariable(page, name) {
  return page.evaluate(variable => getComputedStyle(document.documentElement).getPropertyValue(variable).trim().toUpperCase(), name);
}

async function setColor(page, selector, value) {
  await page.locator(selector).evaluate((input, color) => {
    input.value = color;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, value);
}

async function previewStyle(page) {
  return page.locator('[data-preview-variant="primary"]').evaluate(button => {
    const css = getComputedStyle(button);
    return {
      backgroundColor: css.backgroundColor,
      borderColor: css.borderTopColor,
      borderStyle: css.borderTopStyle,
      boxShadow: css.boxShadow,
    };
  });
}

test.use({ viewport: { width: 1280, height: 960 } });

test('theme palette can be restored explicitly and Ghost stays distinct from Outline', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'appearance' });
  await openAppearance(page);

  await page.locator('#appearancePreset').selectOption('forest');
  await expect(page.locator('html')).toHaveAttribute('data-ui-theme', 'forest');
  await expect(page.locator('#uiPalette')).toHaveValue('theme');
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#6FBF73');
  await expect.poll(() => cssVariable(page, '--accent-secondary')).toBe('#A7C957');

  await setColor(page, '#appearanceAccent', '#E05780');
  await expect(page.locator('#uiPalette')).toHaveValue('custom');
  await expect(page.locator('#uiPaletteState')).toContainText(/Изменено пользователем|Customized/);
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#E05780');

  await page.locator('#uiPalette').selectOption('theme');
  await expect(page.locator('#uiPaletteState')).toContainText(/Цвета темы|Theme colors/);
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#6FBF73');
  await expect.poll(() => cssVariable(page, '--accent-secondary')).toBe('#A7C957');

  // Legacy/inconsistent snapshots may still say paletteId=theme while carrying
  // custom colors. The explicit command must re-apply the active theme even
  // when the select value itself does not change.
  await page.locator('#appearanceAccent').evaluate(input => { input.value = '#E05780'; });
  await page.locator('#uiAccentSecondary').evaluate(input => { input.value = '#9B7BE0'; });
  await page.evaluate(() => {
    document.documentElement.style.setProperty('--accent', '#E05780');
    document.documentElement.style.setProperty('--color-accent', '#E05780');
    document.documentElement.style.setProperty('--accent-secondary', '#9B7BE0');
    document.documentElement.style.setProperty('--color-accent-secondary', '#9B7BE0');
  });
  await expect(page.locator('#uiPalette')).toHaveValue('theme');
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#E05780');
  await page.locator('#paletteThemeReset').click();
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#6FBF73');
  await expect.poll(() => cssVariable(page, '--accent-secondary')).toBe('#A7C957');
  await expect(page.locator('#appearanceMsg')).toContainText(/Сохранено автоматически|Saved automatically/);

  await page.reload();
  await openAppearance(page);
  await expect(page.locator('#appearancePreset')).toHaveValue('forest');
  await expect(page.locator('#uiPalette')).toHaveValue('theme');
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#6FBF73');
  await expect.poll(() => cssVariable(page, '--accent-secondary')).toBe('#A7C957');

  await page.locator('#appearancePreset').selectOption('midnight');
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#7B8CE0');
  await expect.poll(() => cssVariable(page, '--accent-secondary')).toBe('#58C6C8');

  await setColor(page, '#appearanceAccent', '#E05780');
  await expect(page.locator('#uiPalette')).toHaveValue('custom');
  await page.locator('#uiWorkspace').selectOption('planner');
  await page.locator('#uiLayout').selectOption('compact');
  await expect.poll(() => cssVariable(page, '--accent')).toBe('#E05780');

  await page.locator('#themeButtonStyle').selectOption('outline');
  await expect(page.locator('html')).toHaveAttribute('data-button-style', 'outline');
  const outline = await previewStyle(page);
  expect(outline.borderStyle).toBe('solid');
  expect(outline.borderColor).not.toBe('rgba(0, 0, 0, 0)');

  await page.locator('#themeButtonStyle').selectOption('ghost');
  await expect(page.locator('html')).toHaveAttribute('data-button-style', 'ghost');
  const ghost = await previewStyle(page);
  expect(ghost.borderStyle).toBe('solid');
  expect(ghost.borderColor).toBe('rgba(0, 0, 0, 0)');
  expect(ghost.boxShadow).toBe('none');

  const ghostButton = page.locator('[data-preview-variant="primary"]');
  const beforeHover = ghost.backgroundColor;
  await ghostButton.hover();
  await expect.poll(async () => (await previewStyle(page)).backgroundColor).not.toBe(beforeHover);

  await expect(page.locator('[data-preview-variant]')).toHaveCount(7);
  await expect(page.locator('[data-preview-variant="icon"]')).toHaveAttribute('aria-label', /Ещё действия|More actions/);
});
