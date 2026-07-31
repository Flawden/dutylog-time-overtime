const { test, expect } = require('./fixtures');
const { registerAndOnboard, currentLocalDateKey, openView, openDayModule } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('calendar offers a contextual return to today and keeps important-day controls compact', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'calcomfort' });
  const today = await currentLocalDateKey(page);
  await openView(page, 'calendar');

  await expect(page.locator('#todayBtn')).toBeHidden();
  await page.locator('#next').click();
  await expect(page.locator('#todayBtn')).toBeVisible();
  await expect(page.locator('#calendarLoadStatus')).toBeHidden({ timeout: 30_000 });

  await page.locator('#todayBtn').click();
  await expect(page.locator('#todayBtn')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator(`#grid [data-date="${today}"]`)).toHaveClass(/sel/);

  await page.locator('#next').click();
  await expect(page.locator('#calendarLoadStatus')).toBeHidden({ timeout: 30_000 });
  const selected = await page.evaluate(() => {
    const d = new Date();
    d.setMonth(d.getMonth() + 1, 15);
    const pad = value => String(value).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-15`;
  });
  await page.locator(`#grid [data-date="${selected}"]`).click();
  await expect(page.locator('#panel')).toBeVisible();
  await openDayModule(page, 'important_dates');
  await expect(page.locator('#impDate')).toHaveValue(selected);

  await openView(page, 'important');
  await page.locator('#importantBoardNew').click();
  await expect(page.locator('#importantEditModal')).toBeVisible();
  const checkboxSizes = await page.locator('#importantEditModal input[type="checkbox"]').evaluateAll(inputs =>
    inputs.map(input => {
      const box = input.getBoundingClientRect();
      return { width: box.width, height: box.height };
    })
  );
  expect(checkboxSizes.length).toBeGreaterThan(0);
  for (const box of checkboxSizes) {
    expect(box.width).toBeLessThanOrEqual(20);
    expect(box.height).toBeLessThanOrEqual(20);
  }
});
