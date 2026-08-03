const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('private calendar feed exports .ics, rotates safely and revokes the old secret', async ({ page, request }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await registerAndOnboard(page, { preset: 'full', prefix: 'calendar-sync' });
  await openView(page, 'settings');
  await page.locator('[data-settings-jump="calendar-sync"]').click();
  await expect(page.locator('#calendarSyncCard')).toHaveClass(/is-open/);
  await expect(page.locator('#calendarSyncStatus')).toContainText(/не настроено|not configured/i);

  const issuedResponse = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'POST'
      && url.pathname === '/api/calendar-sync/subscription'
      && response.status() === 200;
  });
  await page.locator('#calendarSyncIssue').click();
  const issued = await (await issuedResponse).json();
  expect(issued.subscriptionUrl).toContain('/calendar-feed.ics?token=');
  expect(issued.tokenHint).toMatch(/^.{11}$/u);
  await expect(page.locator('#calendarSyncSecret')).toBeVisible();
  await expect(page.locator('#calendarSyncUrl')).toHaveValue(issued.subscriptionUrl);
  await expect(page.locator('#calendarSyncStatus')).toContainText(/активна|active/i);

  const feed = await request.get(issued.subscriptionUrl);
  expect(feed.status()).toBe(200);
  expect(feed.headers()['content-type']).toContain('text/calendar');
  const feedBody = await feed.text();
  expect(feedBody).toContain('BEGIN:VCALENDAR\r\n');
  expect(feedBody).toContain('PRODID:-//DutyLog//Time and Overtime 27.32.1//RU');
  expect(feedBody).toContain('END:VCALENDAR\r\n');

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('#calendarExportRange').click()
  ]);
  expect(download.suggestedFilename()).toMatch(/^dutylog-calendar-\d{4}-\d{2}-\d{2}-\d{4}-\d{2}-\d{2}\.ics$/);
  const downloadBody = await require('fs/promises').readFile(await download.path(), 'utf8');
  expect(downloadBody).toContain('BEGIN:VCALENDAR\r\n');

  const rotatedResponse = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'POST'
      && url.pathname === '/api/calendar-sync/subscription'
      && response.status() === 200;
  });
  await page.locator('#calendarSyncIssue').click();
  const rotated = await (await rotatedResponse).json();
  expect(rotated.subscriptionUrl).not.toBe(issued.subscriptionUrl);
  await expect(page.locator('#calendarSyncUrl')).toHaveValue(rotated.subscriptionUrl);
  expect((await request.get(issued.subscriptionUrl)).status()).toBe(404);
  expect((await request.get(rotated.subscriptionUrl)).status()).toBe(200);

  page.once('dialog', dialog => dialog.accept());
  const revokedResponse = page.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'DELETE'
      && url.pathname === '/api/calendar-sync/subscription'
      && response.status() === 204;
  });
  await page.locator('#calendarSyncRevoke').click();
  await revokedResponse;
  await expect(page.locator('#calendarSyncStatus')).toContainText(/не настроено|not configured/i);
  await expect(page.locator('#calendarSyncSecret')).toBeHidden();

  const revokedFeed = await request.get(rotated.subscriptionUrl);
  expect(revokedFeed.status()).toBe(404);
});
