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

describe("PayrollWorkspace generic compensation explainability", () => {
  it("shows generic compensation aggregate and backend-owned preview lines", () => {
    expect(source).toContain(
      'id="payrollCompensationComponentsTotal"',
    );
    expect(source).toContain(
      "preview?.compensationComponentEarningsMinor",
    );
    expect(source).toContain(
      'id="payrollCompensationComponentBreakdown"',
    );
    expect(source).toContain(
      "preview.compensationComponentLines",
    );
    expect(source).toContain(
      "line.displayName",
    );
    expect(source).toContain(
      "line.referenceBaseMinor",
    );
    expect(source).toContain(
      "line.configuredAmountMinor",
    );
    expect(source).toContain(
      "line.rateBps",
    );
  });

  it("explains generic compensation blockers before downstream pricing blockers", () => {
    expect(source).toContain(
      "compensationComponentCalculationReady",
    );
    expect(source).toContain(
      "compensationComponentCalculationBlockingReason",
    );
    expect(source).toContain(
      'case"PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH"',
    );
    expect(source).toContain(
      'case"PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE"',
    );
    expect(source).toContain(
      'case"PAYROLL_COMP_COMPONENT_INVALID"',
    );
    expect(source).toContain(
      "if(preview.value&&!preview.value.compensationComponentCalculationReady)",
    );
    expect(source.indexOf(
      "if(preview.value&&!preview.value.compensationComponentCalculationReady)",
    )).toBeLessThan(source.indexOf(
      "if(preview.value&&!preview.value.settlementPricingReady)",
    ));
  });

  it("renders frozen generic component lines and fingerprint from each snapshot", () => {
    expect(source).toContain(
      "item.compensationComponentEarningsMinor",
    );
    expect(source).toContain(
      "item.compensationComponentLines",
    );
    expect(source).toContain(
      "item.compensationComponentFingerprint",
    );
    expect(source).toContain(
      "componentLineDetail(line,item.currencyCode)",
    );
    expect(source).toContain(
      "snapshot-component-${item.id}-${line.versionId}",
    );
  });
});
