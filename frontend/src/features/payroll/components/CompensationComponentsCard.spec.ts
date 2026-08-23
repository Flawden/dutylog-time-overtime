import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

function source(relative: string): string {
  return readFileSync(
    fileURLToPath(
      new URL(relative, import.meta.url),
    ),
    "utf8",
  );
}

const card =
  source("./CompensationComponentsCard.vue");

const workspace =
  source("./PayrollWorkspace.vue");

const api =
  source("../api/payrollApi.ts");

const store =
  source("../stores/payrollStore.ts");

describe("Generic compensation settings UI", () => {
  it("keeps display names user-owned and formula semantics separate", () => {
    expect(card).toContain(
      "премия за выживание после ночной смены",
    );

    expect(card).toContain(
      "Фиксированная сумма",
    );

    expect(card).toContain(
      "Процент от базы",
    );

    expect(card).toContain(
      "Фактически начисленная базовая оплата",
    );

    expect(card).toContain(
      "Оклад",
    );

    expect(card).not.toContain(
      "HARMFULNESS",
    );

    expect(card).not.toContain(
      "componentIdentity",
    );

    expect(card).not.toContain(
      "#{{ editingComponentId }}",
    );
  });

  it("maps human money and percentage inputs to the typed API shape", () => {
    expect(card).toContain(
      'calculationType: "PERCENT_OF_BASE"',
    );

    expect(card).toContain(
      'calculationType: "FIXED_AMOUNT"',
    );

    expect(card).toContain(
      "Math.round(value * 100)",
    );

    expect(card).toContain(
      "calculationBase:",
    );

    expect(card).toContain(
      "amountMinor:",
    );

    expect(card).toContain(
      "enabled:",
    );

    expect(card).toContain(
      "Previous versions are not deleted.",
    );
  });

  it("uses generated operations through the payroll API and Pinia store", () => {
    expect(api).toContain(
      'client.request("listPayrollCompensationComponentHistory"',
    );

    expect(api).toContain(
      'client.request("createPayrollCompensationComponent"',
    );

    expect(api).toContain(
      '"upsertPayrollCompensationComponentVersion"',
    );

    expect(store).toContain(
      "compensationComponentHistory",
    );

    expect(store).toContain(
      "await this.loadCompensationComponents()",
    );

    expect(workspace).toContain(
      "<CompensationComponentsCard",
    );

    expect(workspace).toContain(
      ':currency-code="currency"',
    );
  });
});
