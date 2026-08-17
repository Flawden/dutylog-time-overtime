import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type PayrollPeriod = DutyLogApiSchemas.PayrollPeriod;
export type PayrollPreview = DutyLogApiSchemas.PayrollPreview;
export type PayrollSettingsInput = DutyLogApiSchemas.PayrollSettingsInput;
export type PayrollAdjustmentInput = DutyLogApiSchemas.PayrollAdjustmentInput;
export type ProductionCalendarMonth = DutyLogApiSchemas.ProductionCalendarMonth;
export type ProductionCalendarDay = DutyLogApiSchemas.ProductionCalendarDay;
export type ProductionCalendarDayInput = DutyLogApiSchemas.ProductionCalendarDayInput;

export interface DutyLogPayrollDomain {
  ready(): boolean;
  refresh(): Promise<void>;
}
