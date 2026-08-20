import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const source = readFileSync(
  fileURLToPath(new URL("./PayrollWorkspace.vue", import.meta.url)),
  "utf8",
);

const pricingSource = readFileSync(
  fileURLToPath(new URL("./PricingSettingsCard.vue", import.meta.url)),
  "utf8",
);

describe("PayrollWorkspace ordinary premium explainability", () => {
  it("shows backend-owned ordinary premium money and reference base", () => {
    expect(source).toContain(
      'id="payrollOrdinaryPremiumBreakdown"',
    );
    expect(source).toContain(
      "preview.ordinaryPremiumMinutes",
    );
    expect(source).toContain(
      "preview.ordinaryPremiumReferenceBasePayMinor",
    );
    expect(source).toContain(
      "preview.ordinaryPremiumPayMinor",
    );
    expect(source).toContain(
      'id="payrollGrandTotal">{{ money(preview?.totalPayMinor) }}',
    );
    expect(pricingSource).toContain('id="pricingNightEnabled"');
    expect(pricingSource).toContain('id="pricingHolidayEnabled"');
    expect(pricingSource).toContain('id="pricingOvertimeEnabled"');
    expect(pricingSource).toContain('Math.round(value * 100)');
  });

  it("does not mislabel ordinary pricing failure as settlement failure", () => {
    expect(source).toContain(
      "!preview.value.settlementPricingReady",
    );
    expect(source).toContain(
      "!preview.value.ordinaryPremiumPricingReady",
    );
    expect(source).toContain(
      "!preview.value.ordinaryPremiumPricingReady)return text.value.ordinaryPremium",
    );
    expect(source).not.toContain(
      'id="payrollOrdinaryPremiumPricingStatus"',
    );
    expect(pricingSource).toContain(
      'const MANAGED_CODES = new Set([',
    );
    expect(pricingSource).toContain(
      '.filter(rule => !MANAGED_CODES.has(rule.code))',
    );
  });

  it("shows frozen ordinary premium amount on saved revisions", () => {
    expect(source).toContain(
      "item.ordinaryPremiumPricingFingerprint",
    );
    expect(source).toContain(
      "item.ordinaryPremiumMinutes",
    );
    expect(source).toContain(
      "item.ordinaryPremiumPayMinor",
    );
    expect(source).toContain(
      '<PricingSettingsCard :language="language" />',
    );
    expect(pricingSource).toContain(
      "banked overtime is explicitly settled for cash",
    );
  });
});
