const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');

test('PWA activation removes a previous release cache before claiming the v27.37.2 shell', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ baseURL, locale: 'ru-RU', serviceWorkers: 'allow' });
  const page = await context.newPage();
  try {
    await page.goto('/login.html');
    const previousCache = 'dutylog-shell-v27.36.8-synthetic-previous';
    await page.evaluate(async name => {
      const cache = await caches.open(name);
      await cache.put('/synthetic-previous-release.txt', new Response('old release'));
    }, previousCache);
    await expect.poll(() => page.evaluate(name => caches.has(name), previousCache)).toBe(true);

    await registerAndOnboard(page, { preset: 'minimum', prefix: 'pwa-upgrade' });
    await page.evaluate(() => navigator.serviceWorker.ready);
    await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller)), { timeout: 30_000 }).toBe(true);
    await expect.poll(() => page.evaluate(name => caches.has(name), previousCache), { timeout: 30_000 }).toBe(false);
    const cacheNames = await page.evaluate(() => caches.keys());
    expect(cacheNames.some(name => name.startsWith('dutylog-shell-v27.37.2-'))).toBe(true);
    expect(cacheNames.filter(name => name.startsWith('dutylog-shell-'))).toHaveLength(1);
  } finally {
    await context.close();
  }
});
