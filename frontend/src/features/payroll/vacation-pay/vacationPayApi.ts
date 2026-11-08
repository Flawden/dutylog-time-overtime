import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import { createDutyLogHttpClient } from "@/platform/api/httpClient";
import type {
  VacationPayAbsenceSummary,
  VacationPayReceipt,
  VacationPayReceiptCreate,
  VacationPayReceiptHistory,
} from "./vacationPayModel";

const BASE = "/api/v1/payroll/vacation-pay/receipts";

/**
 * Phase 5B.1 ledger endpoints are not yet represented in the generated OpenAPI client.
 * Reuse the shared HTTP transport (CSRF, request ID, cookies and error handling) until
 * they are added to the contract generator. Never issue raw fetch here.
 */
export function createVacationPayApi(
  json = createDutyLogHttpClient(),
  generated: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient(),
) {
  return Object.freeze({
    planner() { return generated.request("getVacationPlanner"); },
    receipts() { return json<VacationPayReceipt[]>(BASE); },
    summary() { return json<VacationPayAbsenceSummary[]>(`${BASE}/summary`); },
    history(receiptId: number) {
      return json<VacationPayReceiptHistory>(`${BASE}/${receiptId}/history`);
    },
    register(body: VacationPayReceiptCreate) {
      return json<VacationPayReceipt, VacationPayReceiptCreate>(BASE, { method: "POST", body });
    },
  });
}
