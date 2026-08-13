import { describe, expect, it } from "vitest";
import type { OvertimeAccountReadModel, OvertimeCredit, OvertimeUsage, QuickScenario } from "./domain";
import {
  applyScenarioToCreditDraft,
  creditRowEarnedHours,
  dayCreditTotals,
  fifoForecast,
  ledgerChartColumns,
  newCreditDraft,
  uniqueSourceCredits,
} from "./model";

function credit(overrides: Partial<OvertimeCredit>): OvertimeCredit {
  return {
    id: 1,
    workedDate: "2026-08-01",
    hours: 4,
    usedHours: 0,
    remainingHours: 4,
    projection: {
      sourceWorkedDate: "2026-08-01",
      partIndex: 1,
      partCount: 1,
      dayRowIndex: 1,
      dayRowCount: 1,
      dayEarnedHours: 4,
      dayUsedHours: 0,
      dayRemainingHours: 4,
      sourceCreditHours: 4,
      sourceUsedHours: 0,
      sourceRemainingHours: 4,
      exact: true,
    },
    reason: "Source A",
    ...overrides,
  } as OvertimeCredit;
}

function usage(overrides: Partial<OvertimeUsage>): OvertimeUsage {
  return {
    id: 7,
    usageDate: "2026-08-10",
    hours: 2,
    minutes: 120,
    reason: "Time off",
    sourceKind: "ABSENCE",
    sourceAbsenceId: 91,
    reserved: true,
    allocations: [{ creditId: 1, workedDate: "2026-08-01", minutes: 120 }],
    ...overrides,
  } as OvertimeUsage;
}

function account(overrides: Partial<OvertimeAccountReadModel> = {}): OvertimeAccountReadModel {
  return {
    totalEarnedHours: 7,
    totalUsedHours: 2,
    balanceHours: 5,
    credits: [credit({ id: 1 }), credit({ id: 2, workedDate: "2026-08-02", hours: 3, remainingHours: 3, reason: "Source B", projection: { ...credit({}).projection!, sourceWorkedDate: "2026-08-02", sourceCreditHours: 3, sourceRemainingHours: 3, dayEarnedHours: 3, dayRemainingHours: 3 } })],
    usages: [],
    ...overrides,
  };
}

describe("absence and time-bank domain model", () => {
  it("forecasts FIFO from the oldest source and reports the free remainder", () => {
    const result = fifoForecast(account(), 300);
    expect(result.allocations).toEqual([
      expect.objectContaining({ creditId: 1, minutes: 240, remainingAfterMinutes: 0 }),
      expect.objectContaining({ creditId: 2, minutes: 60, remainingAfterMinutes: 120 }),
    ]);
    expect(result.shortageMinutes).toBe(0);
    expect(result.freeAfterMinutes).toBe(0);
  });

  it("restores the current absence allocation while editing its FIFO forecast", () => {
    const data = account({
      balanceHours: 3,
      credits: [credit({ remainingHours: 2, projection: { ...credit({}).projection!, sourceRemainingHours: 2, dayRemainingHours: 2 } })],
      usages: [usage({})],
    });
    const result = fifoForecast(data, 180, 91);
    expect(result.allocations[0]).toMatchObject({ creditId: 1, minutes: 180, remainingAfterMinutes: 60 });
    expect(result.shortageMinutes).toBe(0);
  });

  it("deduplicates split daily projections while keeping earned chart totals by visible day", () => {
    const split = account({
      credits: [
        credit({ id: 5, workedDate: "2026-08-03", hours: 1, usedHours: 1, remainingHours: 0, projection: { ...credit({}).projection!, partIndex: 1, partCount: 2, dayEarnedHours: 1, dayUsedHours: 1, dayRemainingHours: 0 } }),
        credit({ id: 5, workedDate: "2026-08-04", hours: 3, usedHours: 2, remainingHours: 1, projection: { ...credit({}).projection!, partIndex: 2, partCount: 2, dayEarnedHours: 3, dayUsedHours: 2, dayRemainingHours: 1 } }),
      ],
      usages: [],
    });
    expect(uniqueSourceCredits(split.credits)).toHaveLength(1);
    expect(ledgerChartColumns(split, "month")).toEqual([
      expect.objectContaining({ key: "2026-08-03", earnedHours: 1, usedHours: 0 }),
      expect.objectContaining({ key: "2026-08-04", earnedHours: 3, usedHours: 0 }),
    ]);
  });

  it("recovers canonical server day totals when a historical visible row reports zero hours", () => {
    const historical = credit({
      id: 8,
      workedDate: "2026-08-06",
      hours: 0,
      usedHours: 0,
      remainingHours: 0,
      projection: {
        ...credit({}).projection!,
        partIndex: 1,
        partCount: 1,
        dayEarnedHours: 3,
        dayUsedHours: 1,
        dayRemainingHours: 2,
        sourceCreditHours: 3,
        sourceUsedHours: 1,
        sourceRemainingHours: 2,
      },
    });
    const data = account({ credits: [historical], usages: [] });

    expect(creditRowEarnedHours(historical)).toBe(3);
    expect(dayCreditTotals(data.credits).get("2026-08-06")).toEqual({ earned: 3, used: 1, remaining: 2 });
    expect(ledgerChartColumns(data, "month")).toEqual([
      expect.objectContaining({ key: "2026-08-06", earnedHours: 3, usedHours: 0 }),
    ]);
  });

  it("plots time-bank usage on the actual usage date without double-counting credit usedHours", () => {
    const data = account({
      credits: [
        credit({ id: 1, workedDate: "2026-08-01", hours: 3, usedHours: 3, remainingHours: 0, projection: { ...credit({}).projection!, dayEarnedHours: 3, dayUsedHours: 3, dayRemainingHours: 0, sourceCreditHours: 3, sourceUsedHours: 3, sourceRemainingHours: 0 } }),
        credit({ id: 2, workedDate: "2026-08-02", hours: 2, usedHours: 1, remainingHours: 1, projection: { ...credit({}).projection!, sourceWorkedDate: "2026-08-02", dayEarnedHours: 2, dayUsedHours: 1, dayRemainingHours: 1, sourceCreditHours: 2, sourceUsedHours: 1, sourceRemainingHours: 1 } }),
      ],
      usages: [usage({ usageDate: "2026-08-03", hours: 4, minutes: 240 })],
    });

    expect(ledgerChartColumns(data, "month")).toEqual([
      expect.objectContaining({ key: "2026-08-01", earnedHours: 3, usedHours: 0 }),
      expect.objectContaining({ key: "2026-08-02", earnedHours: 2, usedHours: 0 }),
      expect.objectContaining({ key: "2026-08-03", earnedHours: 0, usedHours: 4, title: "2026-08-03: +0 ч · −4 ч" }),
    ]);
  });

  it("folds earned work dates and actual usage dates into the same yearly month bucket", () => {
    const data = account({
      credits: [
        credit({ id: 1, workedDate: "2026-08-01", hours: 3, projection: { ...credit({}).projection!, dayEarnedHours: 3, dayRemainingHours: 3, sourceCreditHours: 3, sourceRemainingHours: 3 } }),
        credit({ id: 2, workedDate: "2026-08-02", hours: 2, remainingHours: 2, projection: { ...credit({}).projection!, sourceWorkedDate: "2026-08-02", dayEarnedHours: 2, dayRemainingHours: 2, sourceCreditHours: 2, sourceRemainingHours: 2 } }),
      ],
      usages: [usage({ usageDate: "2026-08-03", hours: 4, minutes: 240 })],
    });

    expect(ledgerChartColumns(data, "year")).toEqual([
      expect.objectContaining({ key: "2026-08", earnedHours: 5, usedHours: 4, title: "2026-08: +5 ч · −4 ч" }),
    ]);
  });

  it("applies a reusable scenario to an exact shift interval", () => {
    const scenario = {
      id: 2,
      name: "Two hours after shift",
      startMode: "SHIFT_END",
      endMode: "ADD_MINUTES",
      endOffsetMinutes: 120,
      endDayOffset: 0,
      endNextDay: false,
      breakMode: "ZERO",
      plannedMode: "ZERO",
      sortOrder: 0,
      reasonTemplate: "Scenario source",
    } as QuickScenario;
    const result = applyScenarioToCreditDraft(newCreditDraft("2026-08-05"), scenario, {
      workStart: "2026-08-05T09:00",
      workEnd: "2026-08-05T17:00",
      breakMinutes: 60,
      netMinutes: 420,
    });
    expect(result).toMatchObject({
      startDateTime: "2026-08-05T17:00",
      endDateTime: "2026-08-05T19:00",
      breakMinutes: 0,
      plannedHours: 0,
      hours: 2,
      reason: "Scenario source",
    });
  });
});
