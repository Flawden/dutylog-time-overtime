import { describe, expect, it } from "vitest";
import { ordinaryPremiumMessage } from "./ordinaryPremiumMessage";
describe("ordinary premium readiness explanations", () => {
  it("explains changed review and source evidence separately", () => {
    expect(ordinaryPremiumMessage("PAYROLL_ARTICLE153_CONFIGURATION_CHANGED", "ru")).toContain("изменились");
    expect(ordinaryPremiumMessage("PAYROLL_ARTICLE153_REVIEW_DRIFT", "en")).toContain("Renew the review");
    expect(ordinaryPremiumMessage("PAYROLL_ARTICLE153_SOURCE_BLOCKED", "ru")).toContain("подтверждения работы");
    expect(ordinaryPremiumMessage("PAYROLL_ARTICLE153_SOURCE_REQUIRED", "en")).toContain("complete evidence");
  });
  it("explains reconciliation without promising automatic fallback", () => {
    expect(ordinaryPremiumMessage("PAYROLL_ARTICLE153_LEGACY_RECONCILIATION_REQUIRED", "ru")).toContain("Проверь правила");
  });
  it("explains ordinary pricing blockers without referring to overtime", () => {
    for (const code of ["PAY_PRICING_RULES_REQUIRED", "PAY_PRICING_CURRENCY_MISMATCH", "PAYROLL_ORDINARY_PREMIUM_CURRENCY_MISMATCH", "PAYROLL_COMPENSATION_REQUIRED", "PAYROLL_PRODUCTION_NORM_REQUIRED", "PAYROLL_PRODUCTION_NORM_INCOMPLETE"]) {
      expect(ordinaryPremiumMessage(code, "ru")).not.toContain("переработ");
      expect(ordinaryPremiumMessage(code, "en")).not.toContain("overtime");
    }
  });
  it("unknown reasons remain blocked with a useful generic explanation", () => {
    expect(ordinaryPremiumMessage("FUTURE_REASON", "ru")).toContain("Проверь исходные данные");
    expect(ordinaryPremiumMessage(undefined, "en")).toContain("Check the source data");
  });
});
