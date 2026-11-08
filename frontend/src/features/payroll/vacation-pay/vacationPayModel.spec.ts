import { describe, expect, it } from "vitest";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import { formatVacationMoney, isEligibleVacation, localIsoDate, parsePositiveMinorAmount, provenVacationGross, type VacationPayAbsenceSummary } from "./vacationPayModel";

function absence(overrides: Partial<DutyLogApiSchemas.AbsencePeriod> = {}): DutyLogApiSchemas.AbsencePeriod {
  return {
    id: 41, typeId: 1, typeName: "Vacation", typeColor: "#fff",
    startDate: "2026-11-10", endDate: "2026-11-20",
    status: "APPROVED", coverage: "FULL_DAY", systemCode: "VACATION",
    balancePolicy: "VACATION_DAYS", compensationPolicy: "VACATION_ALLOWANCE",
    calendarDays: 11, countedDays: 11, shiftConflictCount: 0,
    ...overrides,
  };
}

describe("vacation pay transfer input", () => {
  it("accepts cents without floating-point rounding", () => {
    expect(parsePositiveMinorAmount("48 000")).toBeNull();
    expect(parsePositiveMinorAmount("48000,25")).toBe(4_800_025);
    expect(parsePositiveMinorAmount("0.01")).toBe(1);
    expect(parsePositiveMinorAmount("5")).toBe(500);
  });

  it("rejects fractional fractions, negative and unsafe amounts", () => {
    for (const input of ["", "-1", "0", "0.00", "1.999", "10.2.3", "foo", "900719925474100"]) {
      expect(parsePositiveMinorAmount(input)).toBeNull();
    }
  });

  it("selects only canonical approved annual leave", () => {
    expect(isEligibleVacation(absence())).toBe(true);
    expect(isEligibleVacation(absence({ status: "COMPLETED" }))).toBe(true);
    expect(isEligibleVacation(absence({ status: "PLANNED" }))).toBe(false);
    expect(isEligibleVacation(absence({ systemCode: "SICK" }))).toBe(false);
    expect(isEligibleVacation(absence({ coverage: "PARTIAL" }))).toBe(false);
    expect(isEligibleVacation(absence({ balancePolicy: "NONE" }))).toBe(false);
    expect(isEligibleVacation(absence({ compensationPolicy: "NONE" }))).toBe(false);
  });

  it("displays gross evidence only for a ready, safe and currency-identified snapshot", () => {
    const summary: VacationPayAbsenceSummary = {
      absencePeriodId: 1, vacationFrom: "2026-09-01", vacationTo: "2026-09-28", currencyTotals: [],
      calculationSnapshot: {
        status: "READY", blockingReason: null,
        evidence: { calculatedGrossAmountMinor: 5370000, currencyCode: "RUB" },
      },
    };
    expect(provenVacationGross(summary)).toEqual({ amountMinor: 5370000, currencyCode: "RUB" });
    expect(provenVacationGross({ ...summary, calculationSnapshot: null })).toBeNull();
    expect(provenVacationGross({ ...summary, calculationSnapshot: { ...summary.calculationSnapshot!, status: "BLOCKED" } })).toBeNull();
    expect(provenVacationGross({ ...summary, calculationSnapshot: { ...summary.calculationSnapshot!, evidence: null } })).toBeNull();
    expect(provenVacationGross({ ...summary, calculationSnapshot: { ...summary.calculationSnapshot!, evidence: { calculatedGrossAmountMinor: Number.MAX_SAFE_INTEGER + 1, currencyCode: "RUB" } } })).toBeNull();
  });

  it("formats money by currency and generates local calendar dates", () => {
    expect(formatVacationMoney(10050, "USD", "en")).toContain("100.50");
    expect(localIsoDate(new Date(2026, 10, 8, 23, 59))).toBe("2026-11-08");
  });
});
