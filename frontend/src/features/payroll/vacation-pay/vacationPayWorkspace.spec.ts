import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const parent = readFileSync(fileURLToPath(new URL("../components/PayrollWorkspace.vue", import.meta.url)), "utf8");
const component = readFileSync(fileURLToPath(new URL("./VacationPayReceiptsCard.vue", import.meta.url)), "utf8");

describe("vacation ledger UI integration", () => {
  it("lives in a dedicated Payroll tab without changing salary calculations", () => {
    expect(parent).toContain('key: "vacation-pay", ru: "Отпускные"');
    expect(parent).toContain('<VacationPayReceiptsCard :language="language" />');
    expect(parent).toContain('id="payroll-panel-vacation-pay"');
  });

  it("labels net receipts separately from gross evidence and presents history", () => {
    expect(component).toContain("Расчётные отпускные до удержаний");
    expect(component).toContain("Полученная сумма (после удержаний)");
    expect(component).toContain("selectedSummary.currencyTotals");
    expect(component).toContain("item.adjustments");
    expect(component).toContain("calculatedGross.amountMinor");
    expect(component).toContain('v-else-if="historyError"');
    expect(component).toContain('v-else-if="!selectedReceipts.length"');
    expect(component).toContain('type="date"');
  });
});
