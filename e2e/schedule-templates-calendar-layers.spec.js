const { test, expect } = require('./fixtures');
const {
  registerAndOnboard,
  currentLocalDateKey,
  openView,
  selectDate,
  openDayModuleById,
  waitForApi
} = require('./helpers');

test('schedule templates preview safely and people profiles switch the whole calendar context', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await registerAndOnboard(page, { preset:'full', prefix:'schedule-layers' });
  const date = await currentLocalDateKey(page);
  const endDate = await page.evaluate(key => {
    const [y,m,d] = key.split('-').map(Number);
    const value = new Date(Date.UTC(y, m - 1, d + 3));
    return `${value.getUTCFullYear()}-${String(value.getUTCMonth()+1).padStart(2,'0')}-${String(value.getUTCDate()).padStart(2,'0')}`;
  }, date);

  await selectDate(page, date);
  await openDayModuleById(page, 'accSched');
  await expect(page.locator('#tplPreset option')).toHaveCount(5);
  const ownTemplateValue = await page.locator('#tplPreset option', { hasText:'2 через 2' }).getAttribute('value');
  await page.locator('#tplPreset').selectOption(ownTemplateValue);
  await page.locator('#tplDays').fill('4');
  await expect(page.locator('#tplOverwrite')).not.toBeChecked();

  const previewResponse = page.waitForResponse(response => /\/api\/v1\/schedule-templates\/\d+\/preview$/.test(new URL(response.url()).pathname)
    && response.request().method() === 'POST' && response.status() === 200);
  await page.locator('#tplPreviewBtn').click();
  const preview = await (await previewResponse).json();
  expect(preview.totalDays).toBe(4);
  expect(preview.writeCount).toBe(4);
  expect(preview.overwriteExistingShift).toBe(false);
  await expect(page.locator('#tplPreview')).toBeVisible();
  await expect(page.locator('#tplPreview')).toContainText('4 будет записано');

  const applyResponse = page.waitForResponse(response => /\/api\/v1\/schedule-templates\/\d+\/apply$/.test(new URL(response.url()).pathname)
    && response.request().method() === 'POST' && response.status() === 200);
  await page.locator('#tplApply').click();
  const applied = await (await applyResponse).json();
  expect(applied.appliedCount).toBe(4);

  await openView(page, 'settings');
  await page.locator('[data-settings-jump="schedule"]').click();
  await expect(page.locator('#scheduleSettingsCard')).toHaveClass(/is-open/);
  await expect(page.locator('#scheduleTemplateList .scheduleTemplateCard')).toHaveCount(5);

  await page.locator('#calendarLayerNew').click();
  await expect(page.locator('#calendarLayerForm')).toBeVisible();
  await page.locator('#calendarLayerName').fill('Напарник');
  const layerTemplateValue = await page.locator('#calendarLayerTemplate option', { hasText:'День / Ночь / 48' }).getAttribute('value');
  await page.locator('#calendarLayerTemplate').selectOption(layerTemplateValue);
  await page.locator('#calendarLayerAnchor').fill(date);
  await page.locator('#calendarLayerStart').fill(date);
  await page.locator('#calendarLayerEnd').fill(endDate);

  const layerCreated = waitForApi(page, 'POST', '/api/v1/calendar-layers', 201);
  await page.locator('#calendarLayerForm button[type="submit"]').click();
  const layer = await (await layerCreated).json();
  expect(layer.readOnly).toBe(true);
  expect(layer.visible).toBe(true);
  await expect(page.locator('#calendarLayerList .calendarLayerCard', { hasText:'Напарник' })).toBeVisible();

  await openView(page, 'calendar');
  await selectDate(page, date);
  const selfProfile = page.locator('#calendarProfileBar .calendarProfileToggle', { hasText:'Я' });
  const companionProfile = page.locator('#calendarProfileBar .calendarProfileToggle', { hasText:'Напарник' });
  await expect(selfProfile).toHaveAttribute('aria-pressed', 'true');
  await expect(companionProfile).toBeVisible();
  await companionProfile.click();
  await expect(companionProfile).toHaveAttribute('aria-pressed', 'true');
  await expect(selfProfile).toHaveAttribute('aria-pressed', 'false');
  await expect(page.locator(`#grid [data-date="${date}"] .calendarLayerChip`)).toHaveCount(0);
  await expect(page.locator(`#grid [data-date="${date}"] .shift`)).toBeVisible();
  await expect(page.locator('#summary')).toContainText('Напарник');

  await page.locator('[data-calendar-mode="day"]').click();
  await expect(page.locator('.calendarProfileReadOnly')).toContainText('только для просмотра');
  await expect(page.locator('#calendarDayOpenDetails')).toHaveCount(0);
  await expect(page.locator('#calendarTimelineCanvas .calendarTimelineEvent.layer')).toBeVisible();

  await selfProfile.click();
  await expect(selfProfile).toHaveAttribute('aria-pressed', 'true');
});
