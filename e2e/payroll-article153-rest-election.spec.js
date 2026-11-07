const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForPayrollReady } = require('./helpers');
const HASH = 'a'.repeat(64);

async function setup(page, blocked = false) {
  await registerAndOnboard(page, { preset: 'full', prefix: 'article153-rest-election' });
  let elected = false;
  let received = null;
  await page.route('**/api/v1/payroll/periods/*/rest-day-elections', async route => {
    if (route.request().method() === 'POST') {
      received = route.request().postDataJSON();
      elected = true;
    }
    const result = blocked
      ? { month: '2026-05', ready: false, blockers: ['2026-05-09:SOURCE:UNKNOWN'], choices: [] }
      : { month: '2026-05', ready: true, blockers: [], choices: [
        { workDate: '2026-05-09', sourceKind: 'EXPLICIT', sourceEntityId: 31,
          qualifiedMinutes: 120, state: elected ? 'ELECTED' : 'NONE',
          sourceFingerprint: HASH, blockingReason: null, electedAt: elected ? '2026-05-09T12:00:00Z' : null }
      ] };
    await route.fulfill({ json: route.request().method() === 'POST'
      ? { electionId: 1, ...received, status: 'ELECTED', sourceEventFingerprint: HASH }
      : result });
  });
  await openView(page, 'payroll');
  await waitForPayrollReady(page);
  await page.locator('#payrollMonthTrigger').click();
  await page.locator('#payrollMonth').fill('2026-05');
  const card = page.getByTestId('article153-rest-election');
  await card.getByRole('button', { name: 'Показать подходящую работу' }).click();
  return { card, received: () => received };
}

test('eligible event is elected with the exact source fingerprint and updates status', async ({ page }) => {
  const { card, received } = await setup(page);
  await expect(card.getByText('Другой день отдыха пока не выбран')).toBeVisible();
  page.once('dialog', dialog => dialog.accept());
  await card.getByRole('button', { name: 'Выбрать другой день отдыха' }).click();
  await expect(card.getByText('Выбран другой день отдыха')).toBeVisible();
  await expect(card).toContainText('Выбор другого дня отдыха сохранён');
  expect(received()).toEqual({ workDate: '2026-05-09', sourceKind: 'EXPLICIT',
    sourceEntityId: 31, expectedSourceFingerprint: HASH });
  await expect(card.getByRole('button', { name: 'Выбрать другой день отдыха' })).toHaveCount(0);
});

test('blocked source evidence never exposes a rest-day election action', async ({ page }) => {
  const { card, received } = await setup(page, true);
  await expect(card.getByTestId('article153-rest-blocked')).toBeVisible();
  await expect(card.getByRole('button', { name: 'Выбрать другой день отдыха' })).toHaveCount(0);
  expect(received()).toBe(null);
});
