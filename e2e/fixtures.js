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
    const expectedStatusConsoleBudget = [];
    const pruneExpectedStatuses = () => {
      const cutoff = Date.now() - 10_000;
      while (expectedStatusConsoleBudget.length && expectedStatusConsoleBudget[0].createdAt < cutoff) {
        expectedStatusConsoleBudget.shift();
      }
    };
    const consumeExpectedStatusConsole = status => {
      pruneExpectedStatuses();
      const index = expectedStatusConsoleBudget.findIndex(item => item.status === status);
      if (index < 0) return false;
      expectedStatusConsoleBudget.splice(index, 1);
      return true;
    };

    page.on('request', request => {
      if (!isSameOrigin(request.url(), baseURL)) return;
      const expected = Number(request.headers()['x-dutylog-e2e-expected-status']);
      if (Number.isInteger(expected) && expected >= 400) {
        expectedStatusConsoleBudget.push({ status:expected, createdAt:Date.now() });
      }
    });
    page.on('console', message => {
      if (message.type() !== 'error') return;
      const text = message.text();
      const resourceStatus = text.match(/Failed to load resource: the server responded with a status of (\d{3})/i);
      if (resourceStatus && consumeExpectedStatusConsole(Number(resourceStatus[1]))) return;
      issues.push(`console.error: ${text}`);
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
        const expected = Number(response.request().headers()['x-dutylog-e2e-expected-status']);
        if (Number.isInteger(expected) && expected === response.status()) return;
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
