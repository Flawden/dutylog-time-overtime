import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type { PayrollAdjustmentInput, PayrollSettingsInput } from "../types/domain";

export function createPayrollApi(client: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient()) {
  return Object.freeze({
    period(month: string) {
      return client.request("payrollPeriod", { path: { month } });
    },
    async updateSettings(body: PayrollSettingsInput): Promise<void> {
      await client.request("updatePayrollSettings", { body });
    },
    async addAdjustment(body: PayrollAdjustmentInput): Promise<void> {
      await client.request("addPayrollAdjustment", { body });
    },
    async calculate(month: string): Promise<void> {
      await client.request("calculatePayrollRevision", { path: { month } });
    },
  });
}

export type PayrollApi = ReturnType<typeof createPayrollApi>;
