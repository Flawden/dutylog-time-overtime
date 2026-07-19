const base = require('@playwright/test');
const { expect } = base;

function isSameOrigin(url, baseURL) {
  try {
    return new URL(url).origin === new URL(baseURL).origin;
  } catch (_) {
    return false;
  }
}

const test = base.test.extend({
  page: async ({ page, baseURL }, use, testInfo) => {
    const issues = [];

    page.on('console', message => {
      if (message.type() === 'error') issues.push(`console.error: ${message.text()}`);
    });
    page.on('pageerror', error => issues.push(`pageerror: ${error.message}`));
    page.on('requestfailed', request => {
      if (!isSameOrigin(request.url(), baseURL)) return;
      const failure = request.failure()?.errorText || 'unknown failure';
      // A reload or form navigation legitimately cancels obsolete in-flight reads.
      // Treat network failures as regressions, but not browser-initiated cancellation.
      if (/ERR_ABORTED|NS_BINDING_ABORTED|cancelled/i.test(failure)) return;
      issues.push(`requestfailed: ${request.method()} ${request.url()} — ${failure}`);
    });
    page.on('response', response => {
      if (!isSameOrigin(response.url(), baseURL)) return;
      if (response.status() >= 400) {
        issues.push(`http ${response.status()}: ${response.request().method()} ${response.url()}`);
      }
    });

    await use(page);

    if (issues.length) {
      await testInfo.attach('runtime-issues.txt', {
        body: Buffer.from(issues.join('\n'), 'utf8'),
        contentType: 'text/plain'
      });
    }
    expect.soft(issues, 'Browser console, page and same-origin HTTP failures').toEqual([]);
  }
});

module.exports = { test, expect };
