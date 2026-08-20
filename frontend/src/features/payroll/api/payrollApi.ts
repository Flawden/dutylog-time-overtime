import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type {
  PayPricingTermInput,
  PayrollAdjustmentInput,
  PayrollCompensationTermInput,
  PayrollSettingsInput,
  ProductionCalendarDayInput,
} from "../types/domain";

export function createPayrollApi(client: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient()) {
  return Object.freeze({
    period(month: string) { return client.request("payrollPeriod", { path: { month } }); },
    pricingTerms() { return client.request("listPayrollPricingTerms"); },
    async upsertPricingTerm(effectiveFrom: string, body: PayPricingTermInput): Promise<void> {
      await client.request("upsertPayrollPricingTerm", { path: { effectiveFrom }, body });
    },
    async deletePricingTerm(effectiveFrom: string): Promise<void> {
      await client.request("deletePayrollPricingTerm", { path: { effectiveFrom } });
    },
    async updateSettings(body: PayrollSettingsInput): Promise<void> { await client.request("updatePayrollSettings", { body }); },
    async upsertCompensationTerm(month: string, body: PayrollCompensationTermInput): Promise<void> {
      await client.request("upsertPayrollCompensationTerm", { path: { month }, body });
    },
    async deleteCompensationTerm(month: string): Promise<void> {
      await client.request("deletePayrollCompensationTerm", { path: { month } });
    },
    async addAdjustment(body: PayrollAdjustmentInput): Promise<void> { await client.request("addPayrollAdjustment", { body }); },
    async calculate(month: string): Promise<void> { await client.request("calculatePayrollRevision", { path: { month } }); },
    async upsertProductionDay(date: string, body: ProductionCalendarDayInput): Promise<void> { await client.request("upsertProductionCalendarDay", { path: { date }, body }); },
    async deleteProductionDay(date: string): Promise<void> { await client.request("deleteProductionCalendarDayOverride", { path: { date } }); },
  });
}
export type PayrollApi = ReturnType<typeof createPayrollApi>;
