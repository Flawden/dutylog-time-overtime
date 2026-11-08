import { describe, expect, it, vi } from "vitest";
import type { DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type { createDutyLogHttpClient } from "@/platform/api/httpClient";
import { createVacationPayApi } from "./vacationPayApi";

describe("Phase 5B.1 vacation pay API transport", () => {
  it("routes reads and register through the shared authenticated transport", async () => {
    const json = vi.fn().mockResolvedValue([]);
    const generatedRequest = vi.fn().mockResolvedValue({ absences: [] });
    const api = createVacationPayApi(
      json as unknown as ReturnType<typeof createDutyLogHttpClient>,
      { request: generatedRequest } as unknown as DutyLogGeneratedApiClient,
    );

    await api.planner();
    await api.receipts();
    await api.summary();
    await api.history(42);
    await api.register({
      absencePeriodId: 18, receivedDate: "2026-11-08",
      actualReceivedAmountMinor: 4800000, currencyCode: "RUB",
    });

    expect(generatedRequest).toHaveBeenCalledWith("getVacationPlanner");
    expect(json).toHaveBeenNthCalledWith(1, "/api/v1/payroll/vacation-pay/receipts");
    expect(json).toHaveBeenNthCalledWith(2, "/api/v1/payroll/vacation-pay/receipts/summary");
    expect(json).toHaveBeenNthCalledWith(3, "/api/v1/payroll/vacation-pay/receipts/42/history");
    expect(json).toHaveBeenNthCalledWith(4, "/api/v1/payroll/vacation-pay/receipts", {
      method: "POST",
      body: { absencePeriodId: 18, receivedDate: "2026-11-08", actualReceivedAmountMinor: 4800000, currencyCode: "RUB" },
    });
  });
});
