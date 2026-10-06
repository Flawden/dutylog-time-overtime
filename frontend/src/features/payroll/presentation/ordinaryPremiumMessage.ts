/** Translate server readiness; never calculate money or infer legal facts. */
export function ordinaryPremiumMessage(code: string | null | undefined, language: string): string {
  const groups: Record<string, number> = {
    CONFIGURATION_CHANGED: 0, REVIEW_DRIFT: 0,
    SOURCE_REQUIRED: 1, SOURCE_BLOCKED: 1,
    LEGACY_RECONCILIATION_REQUIRED: 2, RULES_REQUIRED: 3,
    CURRENCY_MISMATCH: 4, ORDINARY_PREMIUM_CURRENCY_MISMATCH: 4,
    COMPENSATION_REQUIRED: 5, PRODUCTION_NORM_INCOMPLETE: 6, PRODUCTION_NORM_REQUIRED: 6,
  };
  const messages = language === "en" ? [
    "Settings changed. Renew the review.",
    "Holiday pay needs complete evidence and compensation choice.",
    "Pay rules conflict with the review. Check the rules.",
    "Payment rules are missing for some worked time.",
    "Payment currency differs from payroll.",
    "Set how you are paid first.",
    "Salary needs a full schedule and positive norm.",
    "Check the source data and pay rules.",
  ] : [
    "Условия оплаты изменились. Обнови подтверждение.",
    "Нужны подтверждения работы и выбора компенсации.",
    "Правила не согласуются с подтверждением. Проверь правила.",
    "Не хватает правил оплаты.",
    "Валюта доплаты не совпадает с расчётной.",
    "Сначала укажи условия оплаты.",
    "Для оклада нужны график и положительная норма.",
    "Доплата недоступна. Проверь исходные данные и правила.",
  ];
  return messages[groups[(code ?? "").replace(/^(PAYROLL_(ARTICLE153_)?|PAY_PRICING_)/, "")] ?? 7]!;
}
