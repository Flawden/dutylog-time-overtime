import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

/** Bank credits are net cash amounts. Calculation evidence, when present, is gross. */
export interface VacationPayReceipt {
  id: number;
  absencePeriodId: number;
  vacationFrom: string;
  vacationTo: string;
  receivedDate: string;
  actualReceivedAmountMinor: number;
  currencyCode: string;
  calculationStatus: string;
  calculatedGrossAmountMinor: number | null;
  calculationCurrencyCode: string | null;
}

export interface VacationPayAdjustment {
  id: number;
  adjustmentType: "VOID" | "REFUND";
  amountMinor: string;
  currencyCode: string;
  eventDate: string;
  reason: string;
  replacementReceiptId: number | null;
}

export interface VacationPayReceiptHistory {
  receipt: VacationPayReceipt;
  adjustments: VacationPayAdjustment[];
  status: "ACTIVE" | "VOIDED" | "PARTIALLY_REFUNDED" | "REFUNDED";
  refundedAmountMinor: string;
  remainingAmountMinor: string;
}

export interface VacationPayCurrencyTotal {
  currencyCode: string;
  receivedBeforeRefundsAmountMinor: string;
  refundedAmountMinor: string;
  netRetainedAmountMinor: string;
}

export interface VacationPayAbsenceSummary {
  absencePeriodId: number;
  vacationFrom: string;
  vacationTo: string;
  currencyTotals: VacationPayCurrencyTotal[];
  calculationSnapshot: {
    status: string;
    blockingReason: string | null;
    evidence: { calculatedGrossAmountMinor?: number | null; currencyCode?: string | null } | null;
  } | null;
}

export interface VacationPayReceiptCreate {
  absencePeriodId: number;
  receivedDate: string;
  actualReceivedAmountMinor: number;
  currencyCode: string;
  // Omitting calculation is intentional: recording real net cash must not require payroll evidence.
}

/** Never infer a gross vacation-pay figure from bank credits or blocked evidence. */
export function provenVacationGross(summary: VacationPayAbsenceSummary | null): { amountMinor: number; currencyCode: string } | null {
  const snapshot = summary?.calculationSnapshot;
  if (snapshot?.status !== "READY") return null;
  const amountMinor = snapshot.evidence?.calculatedGrossAmountMinor;
  const currencyCode = snapshot.evidence?.currencyCode;
  if (typeof amountMinor !== "number" || !Number.isSafeInteger(amountMinor) || amountMinor < 0
    || typeof currencyCode !== "string" || !/^[A-Z]{3}$/.test(currencyCode)) return null;
  return { amountMinor, currencyCode };
}

export function isEligibleVacation(absence: DutyLogApiSchemas.AbsencePeriod): boolean {
  return (absence.status === "APPROVED" || absence.status === "COMPLETED")
    && absence.systemCode === "VACATION"
    && absence.balancePolicy === "VACATION_DAYS"
    && (absence.coverage ?? "FULL_DAY") === "FULL_DAY"
    && absence.compensationPolicy === "VACATION_ALLOWANCE";
}

/** Strict two-decimal conversion; rejects rounding and unsafe JS integers. */
export function parsePositiveMinorAmount(value: string): number | null {
  const normalized = value.trim().replace(",", ".");
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(normalized)) return null;
  const [whole = "0", fractional = ""] = normalized.split(".");
  const minor = Number(whole) * 100 + Number(fractional.padEnd(2, "0"));
  return Number.isSafeInteger(minor) && minor > 0 ? minor : null;
}

export function formatVacationMoney(minor: string | number, code: string, language: string): string {
  const numeric = Number(minor);
  if (!Number.isSafeInteger(numeric)) return `${String(minor)} ${code}`;
  const value = numeric / 100;
  try {
    return new Intl.NumberFormat(language === "en" ? "en-US" : "ru-RU", {
      style: "currency", currency: code, minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${value.toFixed(2)} ${code}`;
  }
}

export function localIsoDate(now: Date): string {
  const year = now.getFullYear();
  return `${year}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}
