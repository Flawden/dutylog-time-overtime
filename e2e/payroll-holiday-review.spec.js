const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView, waitForPayrollReady } = require('./helpers');

async function fillSource(locator) {
  await locator.locator('select').selectOption('LOCAL_NORMATIVE_ACT');
  await locator.locator('input').nth(0).fill('Положение об оплате, п. 5');
  await locator.locator('input').nth(1).fill('2026-05');
  await locator.locator('textarea').fill('Проверено по документу');
}
async function setup(page, conflict = false) {
  await registerAndOnboard(page, { preset: 'full', prefix: 'holiday-review' });
  let saved = null;
  const base = { configurationToken: 'a'.repeat(64), currencyCode: 'RUB', revision: 0,
    pricingEffectiveFrom: '2026-05-01', localSourceRequired: false, localSource: null,
    components: [{ versionId: 1, name: 'Надбавка по документу', fingerprint: 'b'.repeat(64), currencyCode: 'RUB', decision: null, source: null, locked: false }],
    savedReview: null, ready: false, blockingReason: 'SOURCE_REQUIRED' };
  await page.route('**/api/v1/payroll/periods/*/holiday-review', async route => {
    const month = new URL(route.request().url()).pathname.split('/').at(-2);
    if (route.request().method() === 'PUT') {
      if (conflict) { await route.fulfill({ status: 409, json: { code: 'ARTICLE153_REVIEW_STALE', message: 'Настройки изменились. Обнови форму перед сохранением' } }); return; }
      saved = route.request().postDataJSON();
    }
    await route.fulfill({ json: { ...base, month, ...(saved ? { revision: 1, savedReview: saved.review, ready: true, blockingReason: null, components: base.components.map(c => ({ ...c, decision: 'INCLUDE', source: saved.components[0].source, locked: true })) } : {}) } });
  });
  await openView(page, 'payroll');
  await waitForPayrollReady(page);
  const card = page.getByTestId('holiday-review');
  await card.getByRole('button', { name: 'Проверить основания', exact: true }).click();
  await expect(card.getByTestId('holiday-system-source')).toBeVisible();
  return { card, saved: () => saved };
}
async function complete(card) {
  await fillSource(card.getByTestId('holiday-system-source'));
  await card.getByLabel(/^Правило по документу/).selectOption('NOT_APPLICABLE');
  await fillSource(card.getByTestId('holiday-rest-source'));
  await card.getByLabel(/^Учитывать в праздничной оплате/).selectOption('INCLUDE');
  await fillSource(card.getByTestId('holiday-component-source'));
  await card.getByLabel('Сумма за период в единицах валюты').fill('125,01');
  await card.getByLabel(/^Период/).selectOption('HOUR');
  await card.getByLabel('Дополнительно при усиленной оплате, %').fill('100');
  await card.getByLabel('Дополнительно при выборе другого дня отдыха, %').fill('0');
  await fillSource(card.getByTestId('holiday-rule-source'));
}
test('holiday source form submits explicit classification, exact money and additional percentages', async ({ page }) => {
  const fixture = await setup(page);
  await complete(fixture.card);
  await fixture.card.getByRole('button', { name: 'Сохранить проверенные основания', exact: true }).click();
  await expect(fixture.card).toContainText('Основания сохранены');
  expect(fixture.saved().review.rules[0]).toMatchObject({ period: 'HOUR', periodMinutes: 60, periodAmountMinor: 12501, enhancedAdditionalBps: 10000, restDayAdditionalBps: 0 });
  expect(fixture.saved().components[0].decision).toBe('INCLUDE');
  await expect(fixture.card.getByLabel(/^Учитывать в праздничной оплате/)).toBeDisabled();
});
test('stale form preserves entered sources and explains reload without success', async ({ page }) => {
  const { card } = await setup(page, true);
  await page.setExtraHTTPHeaders({ 'X-DutyLog-E2E-Expected-Status': '409' });
  await complete(card);
  await card.getByRole('button', { name: 'Сохранить проверенные основания', exact: true }).click();
  await expect(card).toContainText('Настройки изменились');
  await expect(card.getByLabel('Сумма за период в единицах валюты')).toHaveValue('125,01');
  await expect(card).not.toContainText('Основания сохранены');
});
