import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type PayrollPeriod = DutyLogApiSchemas.PayrollPeriod;
export type PayrollPreview = DutyLogApiSchemas.PayrollPreview;
export type PayrollSettingsInput = DutyLogApiSchemas.PayrollSettingsInput;
export type PayrollAdjustmentInput = DutyLogApiSchemas.PayrollAdjustmentInput;

export interface DutyLogPayrollDomain {
  ready(): boolean;
  refresh(): Promise<void>;
}
