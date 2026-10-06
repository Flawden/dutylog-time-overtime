const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, selectDate, openView, waitForPayrollReady } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('calendar, filters and selected-day panel remain usable on a phone viewport', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'mobile' });
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
    headerHeight: document.querySelector('.vue-shell-header').getBoundingClientRect().height
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.headerHeight).toBeLessThan(150);

  const date = await currentLocalDateKey(page);
  await selectDate(page, date);
  await expect(page.locator('#panel')).toBeVisible();
  await expect(page.locator('#chips [data-shift-type-id]').first()).toBeVisible();
  await expect(page.locator('[data-vue-shell-navigation]')).toBeHidden();
  await page.locator('#pClose').click();
  await expect(page.locator('#panel')).toBeHidden();
  await expect(page.locator('[data-vue-shell-navigation]')).toBeVisible();
  await expect(page.locator('[data-vue-shell-navigation] .vue-shell-nav__label').first()).toBeHidden();
  await expect(page.locator('[data-vue-shell-navigation] [data-route="today"]')).toHaveAttribute('aria-label', /Сегодня|Today/);
  await expect(page.locator('[data-vue-shell-navigation] [data-route="today"] .ui-icon')).toBeVisible();

  await openView(page, 'tasks');
  await expect(page.locator('#view-tasks')).toBeVisible();
  await expect(page.locator('#taskBoardFiltersToggle')).toBeVisible();
  await expect(page.locator('#taskBoardFilters')).toBeHidden();
  await page.locator('#taskBoardFiltersToggle').click();
  await expect(page.locator('#taskBoardFilters')).toBeVisible();
  await expect(page.locator('#taskBoardPager')).toBeHidden();

  const finalDimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth
  }));
  expect(finalDimensions.content).toBeLessThanOrEqual(finalDimensions.viewport + 1);
});


test('sync dialog keeps its heading inside the phone viewport and footer reachable', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'work', prefix: 'syncmobile' });
  await page.locator('#offlineStatus').click();
  const dialog = page.getByRole('dialog', { name: 'Синхронизация данных' });
  await expect(dialog).toBeVisible();
  const geometry = await dialog.evaluate(element => {
    const rect = element.getBoundingClientRect();
    const heading = element.querySelector('h2').getBoundingClientRect();
    return { top:rect.top, bottom:rect.bottom, height:innerHeight, headingTop:heading.top, scroll:element.scrollWidth, width:element.clientWidth };
  });
  expect(geometry.top).toBeGreaterThanOrEqual(0);
  expect(geometry.headingTop).toBeGreaterThanOrEqual(geometry.top);
  expect(geometry.bottom).toBeLessThanOrEqual(geometry.height);
  expect(geometry.scroll).toBeLessThanOrEqual(geometry.width + 1);
  await page.locator('#offlineSyncClose').click();
  await expect(dialog).toBeHidden();
});


test('payroll manual operation controls have usable touch targets', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'paymobile' });
  const requests = [];
  page.on('request', request => { if (new URL(request.url()).pathname.includes('/payroll/')) requests.push(request.url()); });
  await openView(page, 'payroll');
  await waitForPayrollReady(page);
  await page.waitForLoadState('networkidle');
  expect(requests.filter(url => new URL(url).pathname.includes('/payroll/periods/'))).toHaveLength(1);
  expect(requests.filter(url => new URL(url).pathname.endsWith('/payroll/pricing/terms'))).toHaveLength(1);
  const controls = page.locator('#payrollAdjustmentForm').locator('input, select, button');
  expect(await controls.count()).toBeGreaterThan(3);
  for (const control of await controls.all()) {
    const size = await control.boundingBox();
    expect(size.height).toBeGreaterThanOrEqual(44);
    expect(size.x).toBeGreaterThanOrEqual(0);
    expect(size.x + size.width).toBeLessThanOrEqual(390);
  }
});


test('compact sync dialog supports enlarged text and scrolling to all actions', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await registerAndOnboard(page, { preset: 'work', prefix: 'synclargetext' });
  await page.addStyleTag({ content: '.ui-modal { font-size: 24px; } .ui-modal__header h2 { font-size: 32px; } .ui-modal__header p { font-size: 20px; } .ui-modal .ui-button { font-size: 24px; }' });
  await page.locator('#offlineStatus').click();
  const dialog = page.getByRole('dialog', { name: 'Синхронизация данных' });
  await expect(dialog).toBeVisible();
  const box = await dialog.boundingBox();
  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.y + box.height).toBeLessThanOrEqual(568);
  await expect(dialog.locator('h2')).toBeInViewport();
  await page.locator('#offlineSyncClose').scrollIntoViewIfNeeded();
  await expect(page.locator('#offlineSyncClose')).toBeInViewport();
  const overflow = await dialog.evaluate(el => el.scrollWidth - el.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await page.locator('#offlineSyncClose').click();
  await expect(dialog).toBeHidden();
});
