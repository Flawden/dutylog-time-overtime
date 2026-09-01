const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, openDayModule } = require('./helpers');

test.use({ viewport: { width: 390, height: 844 } });

test('calendar offers a contextual return to today and keeps important-day controls compact', async ({ page }) => {
  await registerAndOnboard(page, { preset: 'full', prefix: 'calcomfort' });
  await openView(page, 'calendar');

  // "Today" is DutyLog's canonical account work date, not the browser
  // runner's local date. Around timezone midnight those can differ.
  const canonicalToday = page.locator('#grid .todayCell');
  await expect(canonicalToday).toHaveCount(1, { timeout: 30_000 });
  const today = await canonicalToday.getAttribute('data-date');
  expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);

  await expect(page.locator('#todayBtn')).toBeHidden();
  await page.locator('#next').click();
  await expect(page.locator('#todayBtn')).toBeVisible();
  await expect(page.locator('#calendarLoadStatus')).toBeHidden({ timeout: 30_000 });

  await page.locator('#todayBtn').click();
  await expect(page.locator('#todayBtn')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator(`#grid [data-date="${today}"]`)).toHaveClass(/sel/);
  await expect(page.locator('#panel')).toBeVisible();

  // Month mode deliberately opens today's modal day panel. Follow the real
  // mobile route instead of trying to click through its blocking backdrop.
  await page.locator('#pClose').click();
  await expect(page.locator('#panel')).toBeHidden();
  await expect(page.locator('#layout')).not.toHaveClass(/with-panel/);

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
