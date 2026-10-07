const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('queued changes stay with their owner across logout and a second account', async ({ page }) => {
  const first = await registerAndOnboard(page, { preset: 'full', prefix: 'cacheowner' });
  await page.waitForLoadState('networkidle');
  const firstId = await page.evaluate(() => offlineOwner);
  await page.evaluate(async () => {
    await dataLayer.enqueue('putDay', { date:'2026-10-07', day:{note:'private pending account A'} });
  });
  await page.evaluate(() => window.dispatchEvent(new CustomEvent("dutylog:logout-request")));
  await page.waitForURL('**/login.html');
  const second = await registerAndOnboard(page, { preset: 'full', prefix: 'otherowner' });
  await page.waitForLoadState('networkidle');
  const proof = await page.evaluate(async () => ({
    owner:offlineOwner, queue:await dataLayer.getQueueItems(), snapshot:await dataLayer.readSnapshot(),
    databases:typeof indexedDB.databases === 'function' ? await indexedDB.databases() : [],
  }));
  expect(proof.queue).toEqual([]);
  expect(proof.snapshot.owner).toBe(proof.owner);
  expect(proof.owner).not.toBe(firstId);
  expect(JSON.stringify(proof.snapshot)).not.toContain('private pending account A');
  expect(proof.databases.map(db => db.name)).toContain(`dutylog-offline:account:${encodeURIComponent(firstId)}`);
});

test('calendar boot does not load unopened settings sessions and empty queue adds no month read', async ({ page }) => {
  await registerAndOnboard(page, { preset:'full', prefix:'deferredsettings' });
  await openView(page, 'calendar');
  await page.waitForLoadState('networkidle');
  let sessions=0;
  page.on('request', request => { if (new URL(request.url()).pathname === '/api/v1/profile/sessions') sessions++; });
  await page.reload();
  await expect(page.locator('#appBoot')).toBeHidden({ timeout:30000 });
  await page.waitForLoadState('networkidle');
  expect(sessions).toBe(0);
  let monthReads=0;
  page.on('request', request => { if (/\/api\/(v1\/)?calendar$/.test(new URL(request.url()).pathname)) monthReads++; });
  await page.evaluate(() => dataLayer.syncQueue());
  expect(monthReads).toBe(0);
  await openView(page, 'settings');
  await expect.poll(() => sessions).toBeGreaterThan(0);
});
