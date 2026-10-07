const { test, expect } = require('./fixtures');
const { registerAndOnboard } = require('./helpers');
const { releaseVersion } = require('./release-version');

test(`PWA activation removes a previous release cache before claiming the v${releaseVersion} shell`, async ({ browser, baseURL }) => {
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
    expect(cacheNames.some(name => name.startsWith(`dutylog-shell-v${releaseVersion}-`))).toBe(true);
    expect(cacheNames.filter(name => name.startsWith('dutylog-shell-'))).toHaveLength(1);
  } finally {
    await context.close();
  }
});

test('PWA uses cached immutable chunks but revalidates mutable code and authenticated reads', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ baseURL, locale: 'ru-RU', serviceWorkers: 'allow' });
  const page = await context.newPage();
  try {
    const account = await registerAndOnboard(page, { preset: 'basic', prefix: 'pwa-cache' });
    await page.evaluate(() => navigator.serviceWorker.ready);
    await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller)), { timeout: 30_000 }).toBe(true);
    const result = await page.evaluate(async () => {
      const names = await caches.keys();
      const cache = await caches.open(names.find(name => name.startsWith('dutylog-shell-')));
      const chunk = '/vue/chunks/startup-12345678.js';
      const entry = '/vue/dutylog-vue-app-shell.js';
      await cache.put(chunk, new Response('cached immutable code'));
      await cache.put(entry, new Response('obsolete mutable code'));
      await cache.put('/api/auth/me', new Response('{"username":"obsolete cached account"}'));
      const immutable = await (await fetch(chunk)).text();
      const mutable = await (await fetch(entry)).text();
      const identity = await (await fetch('/api/auth/me')).json();
      return { immutable, mutable, username: identity.username };
    });
    expect(result.immutable).toBe('cached immutable code');
    expect(result.mutable).not.toBe('obsolete mutable code');
    expect(result.username).toBe(account.username);
  } finally { await context.close(); }
});
