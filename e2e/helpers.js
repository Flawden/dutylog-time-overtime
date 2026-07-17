const { expect } = require('@playwright/test');

let userSequence = 0;

function credentials(prefix = 'e2e') {
  userSequence += 1;
  const suffix = `${Date.now().toString(36)}${userSequence.toString(36)}`;
  return {
    username: `${prefix}${suffix}`.toLowerCase().slice(0, 40),
    password: 'E2ePass!123'
  };
}

function matchesApi(response, method, path) {
  const url = new URL(response.url());
  return response.request().method() === method && url.pathname === path;
}

async function registerAndOnboard(page, { preset = 'work', language = 'ru', prefix = 'e2e' } = {}) {
  const account = credentials(prefix);
  await page.goto('/login.html');
  await expect(page.locator('#loginForm')).toBeVisible();

  await page.locator(`[data-login-lang="${language}"]`).click();
  await expect(page.locator('html')).toHaveAttribute('lang', language);
  await expect(page.locator('#tabReg')).toBeVisible();
  await page.locator('#tabReg').click();
  await page.locator('#ru').fill(account.username);
  await page.locator('#rp').fill(account.password);

  const registration = page.waitForResponse(response => matchesApi(response, 'POST', '/api/auth/register'));
  await page.locator('#regBtn').click();
  expect((await registration).status()).toBe(201);

  await page.waitForURL(url => !url.pathname.endsWith('/login.html'));
  await expect(page.locator('#firstRunOnboarding')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#appBoot')).toBeHidden({ timeout: 30_000 });

  const presetButton = page.locator(`[data-onboarding-preset="${preset}"]`);
  await presetButton.click();
  await expect(presetButton).toHaveAttribute('aria-pressed', 'true');

  const modulesSaved = page.waitForResponse(response => matchesApi(response, 'PATCH', '/api/modules'));
  const profileSaved = page.waitForResponse(response => matchesApi(response, 'PUT', '/api/profile'));
  await page.locator('#onboardingStart').click();
  expect((await modulesSaved).status()).toBe(200);
  expect((await profileSaved).status()).toBe(200);
  await expect(page.locator('#firstRunOnboarding')).toBeHidden({ timeout: 30_000 });
  await expect(page.locator('#whoami')).toHaveText(account.username);
  return account;
}

async function currentLocalDateKey(page) {
  return page.evaluate(() => {
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  });
}

async function selectDate(page, date) {
  const cell = page.locator(`#grid [data-date="${date}"]`);
  await expect(cell).toBeVisible();
  await cell.click();
  await expect(page.locator('#panel')).toBeVisible();
  return cell;
}

function waitForApi(page, method, path, status = 200) {
  return page.waitForResponse(response => matchesApi(response, method, path) && response.status() === status);
}

async function openDayModule(page, moduleKey) {
  const section = page.locator(`[data-day-module="${moduleKey}"]`);
  await expect(section).toBeVisible();
  if (!(await section.evaluate(element => element.open))) {
    await section.locator('summary').first().click();
  }
  await expect(section).toHaveAttribute('open', '');
  return section;
}

async function toggleModule(page, key, enabled) {
  await page.locator('#tabbar a[data-view="settings"]').click();
  await page.locator('[data-settings-jump="modules"]').click();
  await expect(page.locator('#modulesCard')).toHaveClass(/is-open/);
  await expect(page.locator('#moduleSettingsGrid')).toBeVisible();
  const toggle = page.locator(`[data-module-toggle="${key}"]`);
  await expect(toggle).toBeVisible();
  const response = waitForApi(page, 'PATCH', '/api/modules');
  if (enabled) await toggle.check(); else await toggle.uncheck();
  await response;
  if (enabled) await expect(toggle).toBeChecked();
  else await expect(toggle).not.toBeChecked();
  await expect(page.locator('#modulesMsg')).toContainText(/сохран|saved/i);
}

module.exports = {
  registerAndOnboard,
  currentLocalDateKey,
  selectDate,
  waitForApi,
  openDayModule,
  toggleModule
};
