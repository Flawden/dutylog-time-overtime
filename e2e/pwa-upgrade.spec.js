const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test('PWA activation removes a previous release cache before claiming the v27.40.17 shell', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ baseURL, locale: 'ru-RU', serviceWorkers: 'allow' });
  const page = await context.newPage();
  try {
    // Seed the synthetic previous cache before authentication. Service-worker
    // ownership starts from the authenticated app so first-run onboarding cannot race an initial claim.
    await page.goto('/actuator/health');
    const previousCache = 'dutylog-shell-v27.38.15-synthetic-previous';
    await page.evaluate(async name => {
      const cache = await caches.open(name);
      await cache.put('/synthetic-previous-release.txt', new Response('old release'));
    }, previousCache);
    await expect.poll(() => page.evaluate(name => caches.has(name), previousCache)).toBe(true);

    await registerAndOnboard(page, { preset: 'basic', prefix: 'pwa-upgrade' });
    await page.evaluate(() => navigator.serviceWorker.ready);
    await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller)), { timeout: 30_000 }).toBe(true);
    await expect.poll(() => page.evaluate(name => caches.has(name), previousCache), { timeout: 30_000 }).toBe(false);
    const cacheNames = await page.evaluate(() => caches.keys());
    expect(cacheNames.some(name => name.startsWith('dutylog-shell-v27.40.17-'))).toBe(true);
    expect(cacheNames.filter(name => name.startsWith('dutylog-shell-'))).toHaveLength(1);
  } finally {
    await context.close();
  }
});
