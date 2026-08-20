import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const source = readFileSync(
  fileURLToPath(new URL("./PayrollWorkspace.vue", import.meta.url)),
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
  });
});
