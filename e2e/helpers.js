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

async function waitForAppIdle(page) {
  await expect(page.locator('#appBoot')).toBeHidden({ timeout:30_000 });
  await page.waitForFunction(() => {
    const ui = typeof state === 'undefined' ? {} : (state.ui || {});
    return !ui.booting && !ui.loadingCalendar && !ui.loadingTasks && !ui.loadingLedger && !ui.savingTimeSettings;
  }, null, { timeout:30_000 });
  await page.waitForLoadState('networkidle');
}

async function waitForCalendarNavigationReady(page) {
  await page.evaluate(async () => {
    await Promise.resolve(window.__dutylogCalendarNavigationReady);
    await Promise.resolve(window.__dutylogLedgerReady);
  });
  await waitForAppIdle(page);
}

async function waitForCalendarTimelineReady(page, route) {
  await waitForVueShell(page);
  await expect.poll(() => page.evaluate(() => Boolean(window.DutyLogVueDomains?.calendarTimeline?.ready())), { timeout:30_000 }).toBe(true);
  await expect(page.locator(`[data-vue-domain-route="${route}"]`)).toBeVisible();
}

async function waitForVacationReady(page) {
  await waitForVueShell(page);
  await expect.poll(() => page.evaluate(() => Boolean(window.DutyLogVueDomains?.absenceTimeBank?.ready())), { timeout:30_000 }).toBe(true);
  await expect(page.locator('[data-vue-domain-route="vacation"]')).toBeVisible();
}

async function waitForLedgerReady(page) {
  await waitForVueShell(page);
  await expect.poll(() => page.evaluate(() => Boolean(window.DutyLogVueDomains?.absenceTimeBank?.ready())), { timeout:30_000 }).toBe(true);
  await expect(page.locator('[data-vue-domain-route="overtime"]')).toBeVisible();
}

async function waitForPayrollReady(page) {
  await page.evaluate(async () => {
    await Promise.resolve(window.__dutylogPayrollReady);
  });
  await expect.poll(() => page.evaluate(() => ({
    loading:Boolean(typeof state !== 'undefined' && state.payrollLoading),
    period:Boolean(typeof state !== 'undefined' && state.payrollPeriod)
  })), { timeout:30_000 }).toEqual({ loading:false, period:true });
}

async function waitForVueShell(page) {
  await page.evaluate(async () => {
    await Promise.resolve(window.__dutylogVueReady);
  });
  await expect(page.locator('[data-vue-app-shell]')).toBeVisible();
}

async function navigateWithShell(page, view) {
  await waitForVueShell(page);
  await page.evaluate(target => {
    const platform = window.DutyLogVuePlatform;
    if (platform?.navigateLegacy) {
      platform.navigateLegacy(target);
      return;
    }
    window.DutyLogLegacyPlatform?.navigate(target);
  }, view);
}

async function waitForSettingsWorkspaceReady(page) {
  await waitForVueShell(page);
  await expect.poll(() => page.evaluate(() => document.documentElement.dataset.vueSettingsWorkspace === 'ready'), { timeout:30_000 }).toBe(true);
  await expect(page.locator('[data-vue-settings-workspace-view]')).toBeVisible();
}

async function openView(page, view) {
  const vueOwnedSelector = {
    today: '[data-vue-domain-route="today"]',
    calendar: '[data-vue-domain-route="calendar"]',
    vacation: '[data-vue-domain-route="vacation"]',
    overtime: '[data-vue-domain-route="overtime"]',
    settings: '[data-vue-settings-workspace-view]',
  }[view];
  const section = page.locator(vueOwnedSelector || `#view-${view}`);
  if (!(await section.isVisible())) {
    await navigateWithShell(page, view);
    await expect(section).toBeVisible();
  }
  if (view === 'today' || view === 'calendar') await waitForCalendarTimelineReady(page, view);
  if (view === 'vacation') await waitForVacationReady(page);
  if (view === 'overtime') await waitForLedgerReady(page);
  if (view === 'settings') await waitForSettingsWorkspaceReady(page);
  if (view === 'payroll') await waitForPayrollReady(page);
  return section;
}

async function selectDate(page, date) {
  await openView(page, 'calendar');
  const cell = page.locator(`#grid [data-date="${date}"]`);
  const panel = page.locator('#panel');
  const activeModeButton = page.locator('[data-calendar-mode][aria-pressed="true"]');
  const originalMode = await activeModeButton.count()
    ? await activeModeButton.first().getAttribute('data-calendar-mode')
    : 'month';

  const cellExists = await cell.count() > 0;
  const alreadySelected = cellExists
    ? await cell.first().evaluate(element => element.classList.contains('sel'))
    : false;
  const panelVisible = await panel.isVisible();

  // Day and week modes intentionally hide the month grid. When the requested
  // day is already focused, selecting it again must be idempotent and must not
  // force a hidden month cell to become visible.
  if (alreadySelected && panelVisible) return cell.first();

  if (!cellExists || !(await cell.first().isVisible())) {
    const monthButton = page.locator('[data-calendar-mode="month"]');
    await monthButton.click();
    await expect(monthButton).toHaveAttribute('aria-pressed', 'true');
    await expect(cell).toBeVisible();
  }

  const selected = await cell.evaluate(element => element.classList.contains('sel'));
  if (selected && !(await panel.isVisible())) {
    // Vue Calendar keeps the focused date selected. Clicking an already-focused
    // day is idempotent and reopens the selected-day compatibility island.
    await cell.click();
  } else if (!selected) {
    await cell.click();
  }

  await expect(cell).toHaveClass(/sel/);
  await expect(panel).toBeVisible();

  if (originalMode && originalMode !== 'month') {
    const restoreMode = page.locator(`[data-calendar-mode="${originalMode}"]`);
    await restoreMode.click();
    await expect(restoreMode).toHaveAttribute('aria-pressed', 'true');
  }
  return cell;
}

function waitForApi(page, method, path, status = 200) {
  return page.waitForResponse(response => matchesApi(response, method, path) && response.status() === status);
}

async function openSelectedDayDetails(page) {
  await openView(page, 'calendar');
  const monthButton = page.locator('[data-calendar-mode="month"]');
  const dayButton = page.locator('[data-calendar-mode="day"]');
  const dayDetailsButton = page.locator('#calendarDayOpenDetails');
  const panel = page.locator('#panel');

  if (await monthButton.getAttribute('aria-pressed') === 'true' && await panel.isVisible()) {
    return panel;
  }

  // "Все детали дня" is the product route from the focused Day view back to
  // the full selected-day panel. Reuse it instead of reaching into a hidden
  // Month grid or changing calendar state directly in the test.
  if (!(await dayDetailsButton.isVisible())) {
    await dayButton.click();
    await expect(dayButton).toHaveAttribute('aria-pressed', 'true');
    await expect(dayDetailsButton).toBeVisible();
  }
  await dayDetailsButton.click();

  await expect(monthButton).toHaveAttribute('aria-pressed', 'true');
  await expect(panel).toBeVisible();
  return panel;
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

async function openDayModuleById(page, id) {
  const section = page.locator(`#${id}`);
  await expect(section).toHaveCount(1);
  await expect(section).toBeVisible();
  if (!(await section.evaluate(element => element.open))) {
    await section.locator(':scope > summary').click();
  }
  await expect(section).toHaveAttribute('open', '');
  return section;
}

async function toggleModule(page, key, enabled) {
  await openView(page, 'settings');
  await page.locator('[data-settings-jump="modules"]').click();
  await expect(page.locator('#modulesCard')).toHaveClass(/is-open/);
  await expect(page.locator('#moduleSettingsGrid')).toBeVisible();
  const toggle = page.locator(`[data-module-toggle="${key}"]`);
  await expect(toggle).toBeVisible();
  const alreadyEnabled = await toggle.isChecked();
  if (alreadyEnabled === enabled) {
    if (enabled) await expect(toggle).toBeChecked();
    else await expect(toggle).not.toBeChecked();
    return;
  }
  const savedMessage = expect(page.locator('#modulesMsg')).toContainText(
    /модули сохранены|modules saved/i,
    { timeout: 15_000 }
  );
  const response = waitForApi(page, 'PATCH', '/api/v1/modules');
  if (enabled) await toggle.check(); else await toggle.uncheck();
  await response;
  await savedMessage;
  if (enabled) await expect(toggle).toBeChecked();
  else await expect(toggle).not.toBeChecked();
}

module.exports = {
  registerAndOnboard,
  currentLocalDateKey,
  waitForAppIdle,
  waitForCalendarNavigationReady,
  waitForCalendarTimelineReady,
  waitForVacationReady,
  waitForLedgerReady,
  waitForPayrollReady,
  waitForVueShell,
  navigateWithShell,
  openView,
  selectDate,
  waitForApi,
  openSelectedDayDetails,
  openDayModule,
  openDayModuleById,
  toggleModule
};
