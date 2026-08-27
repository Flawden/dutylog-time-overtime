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

    expect(card).toContain(
      'id="compensationComponentEarningKind"',
    );

    for (const kind of [
      "UNCLASSIFIED",
      "HARMFUL_CONDITIONS",
      "COMBINATION",
      "MONTHLY_BONUS",
      "ONE_TIME_BONUS",
      "REGIONAL_COEFFICIENT",
    ]) {
      expect(card).toContain(
        `value="${kind}"`,
      );
    }

    expect(card).toContain(
      'ref<GenericEarningKind>("UNCLASSIFIED")',
    );

    expect(card).toContain(
      "version.earningKind == null",
    );

    expect(card).toContain(
      "earningKind: earningKind.value",
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

  it("offers only neutral presets backed by existing formula families", () => {
    expect(card).toContain(
      "data-compensation-preset-helper",
    );

    expect(card).toContain(
      'id="compensationComponentPreset"',
    );

    expect(card).toContain(
      'value="earned"',
    );

    expect(card).toContain(
      'value="nominal"',
    );

    expect(card).toContain(
      'value="fixed"',
    );

    const presetStart =
      card.indexOf(
        "data-compensation-preset-helper",
      );

    const presetEnd =
      card.indexOf(
        "</div>",
        presetStart,
      );

    const presetRegion =
      card.slice(
        presetStart,
        presetEnd,
      );

    expect(presetRegion).not.toContain(
      "HARMFUL_CONDITIONS",
    );

    expect(presetRegion).not.toContain(
      "REGIONAL_COEFFICIENT",
    );

    expect(presetRegion).not.toContain(
      "COMBINATION",
    );
  });

  it("keeps presets as ephemeral form helpers rather than persisted payroll semantics", () => {
    expect(card).toContain(
      'v-model="preset"',
    );

    expect(card).toContain(
      'value === "fixed"',
    );

    expect(card).toContain(
      'value === "nominal"',
    );

    expect(api).not.toContain(
      "compensationPreset",
    );

    expect(store).not.toContain(
      "compensationPreset",
    );

    const presetModelStart =
      card.indexOf(
        "const preset = computed<",
      );

    const presetModelEnd =
      card.indexOf(
        "const message = ref",
        presetModelStart,
      );

    expect(
      card.slice(
        presetModelStart,
        presetModelEnd,
      ),
    ).not.toContain(
      "earningKind",
    );
  });

});
