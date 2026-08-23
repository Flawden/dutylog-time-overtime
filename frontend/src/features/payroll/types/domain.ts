import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type PayrollPeriod = DutyLogApiSchemas.PayrollPeriod;
export type PayrollPreview = DutyLogApiSchemas.PayrollPreview;
export type PayPricingRule = DutyLogApiSchemas.PayPricingRule;
export type PayPricingTerm = DutyLogApiSchemas.PayPricingTerm;
export type PayPricingTermInput = DutyLogApiSchemas.PayPricingTermInput;
export type PayrollSettingsInput = DutyLogApiSchemas.PayrollSettingsInput;
export type PayrollCompensationTermInput = DutyLogApiSchemas.PayrollCompensationTermInput;
export type PayrollCompensationTerm = DutyLogApiSchemas.PayrollCompensationTerm;
export type PayrollCompensationComponentVersion = DutyLogApiSchemas.PayrollCompensationComponentVersion;
export type PayrollCompensationComponentVersionInput = DutyLogApiSchemas.PayrollCompensationComponentVersionInput;
export type PayrollCompensationComponentCreateInput = DutyLogApiSchemas.PayrollCompensationComponentCreateInput;
export type PayrollAdjustmentInput = DutyLogApiSchemas.PayrollAdjustmentInput;
export type ProductionCalendarMonth = DutyLogApiSchemas.ProductionCalendarMonth;
export type ProductionCalendarDay = DutyLogApiSchemas.ProductionCalendarDay;
export type ProductionCalendarDayInput = DutyLogApiSchemas.ProductionCalendarDayInput;

export interface DutyLogPayrollDomain { ready(): boolean; refresh(): Promise<void>; }
