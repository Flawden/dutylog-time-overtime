import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type {
  AbsencePeriodInput,
  AbsencePeriodPatch,
  AbsencePreviewInput,
  CreditDraft,
  LedgerRangeMode,
  OvertimeCreditCreateRequest,
  OvertimeCreditUpdateRequest,
  OvertimeSettlementUpsertRequest,
  QuickScenarioCreateRequest,
  QuickScenarioUpdateRequest,
} from "../types/domain";
import {
  asActualWorkIntervals,
  asCalendarRange,
  asLedgerIntegrity,
  asOvertimeAccount,
  asQuickScenarios,
  asVacationPlanner,
  dateRange,
} from "../types/model";

async function optional<T>(request: Promise<T | null>, fallback: T): Promise<T> {
  try {
    return (await request) ?? fallback;
  } catch {
    return fallback;
  }
}

export function createAbsenceTimeBankApi(client: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient()) {
  async function loadPeriod(rangeMode: LedgerRangeMode = "month") {
    const range = dateRange(rangeMode);
    const compensationRequest = client.request("timeCompensationSummary", { query: range });
    const integrityRequest = client.request("inspectLedgerIntegrity", { query: range });
    const actualWorkRequest = optional(client.request("listActualWorkIntervals", { query: range }), []);
    const [compensation, integrity, actualWork] = await Promise.all([
      compensationRequest,
      integrityRequest,
      actualWorkRequest,
    ]);
    return {
      compensation,
      integrity: asLedgerIntegrity(integrity, range),
      actualWork: asActualWorkIntervals(actualWork),
      range,
    };
  }

  return Object.freeze({
    async load(referenceDate?: string, rangeMode: LedgerRangeMode = "month") {
      const plannerRequest = client.request("getVacationPlanner", { query: { referenceDate } });
      const accountRequest = client.request("overtimeAccount");
      const periodRequest = loadPeriod(rangeMode);
      const scenarioRequest = optional(client.request("quickScenarios"), []);
      const settlementRequest = client.request("listOvertimeSettlements");
      const [planner, account, period, scenarios, settlements] = await Promise.all([
        plannerRequest,
        accountRequest,
        periodRequest,
        scenarioRequest,
        settlementRequest,
      ]);
      return {
        planner: asVacationPlanner(planner),
        account: asOvertimeAccount(account),
        ...period,
        scenarios: asQuickScenarios(scenarios),
        settlements: Array.isArray(settlements) ? settlements : [],
      };
    },
    loadPeriod,
    previewAbsence(body: AbsencePreviewInput, signal?: AbortSignal) {
      return client.request("previewAbsence", { body, ...(signal ? { signal } : {}) });
    },
    createAbsence(body: AbsencePeriodInput) {
      return client.request("createAbsencePeriod", { body });
    },
    updateAbsence(id: number, body: AbsencePeriodPatch) {
      return client.request("updateAbsencePeriod", { path: { id }, body });
    },
    deleteAbsence(id: number) {
      return client.request("deleteAbsencePeriod", { path: { id } });
    },
    previewCredit(body: OvertimeCreditCreateRequest, signal?: AbortSignal) {
      return client.request("previewOvertimeCredit", { body, ...(signal ? { signal } : {}) });
    },
    createCredit(body: OvertimeCreditCreateRequest) {
      return client.request("createOvertimeCredit", { body });
    },
    updateCredit(id: number, body: OvertimeCreditUpdateRequest) {
      return client.request("updateOvertimeCredit", { path: { id }, body });
    },
    deleteCredit(id: number) {
      return client.request("deleteOvertimeCredit", { path: { id } });
    },
    createSettlement(body: OvertimeSettlementUpsertRequest) {
      return client.request("createOvertimeSettlement", { body });
    },
    updateSettlement(id: number, body: OvertimeSettlementUpsertRequest) {
      return client.request("updateOvertimeSettlement", { path: { id }, body });
    },
    deleteSettlement(id: number) {
      return client.request("deleteOvertimeSettlement", { path: { id } });
    },
    async shiftForDate(date: string) {
      const calendar = await client.request("calendarRange", { query: { from: date, to: date } });
      return asCalendarRange(calendar).days.find(day => day.date === date)?.shiftInterval ?? null;
    },
    listScenarios() {
      return client.request("quickScenarios");
    },
    createScenario(body: QuickScenarioCreateRequest) {
      return client.request("createQuickScenario", { body });
    },
    updateScenario(id: number, body: QuickScenarioUpdateRequest) {
      return client.request("updateQuickScenario", { path: { id }, body });
    },
    deleteScenario(id: number) {
      return client.request("deleteQuickScenario", { path: { id } });
    },
    creditBody(draft: CreditDraft): OvertimeCreditCreateRequest {
      const body: OvertimeCreditCreateRequest = {
        date: draft.date,
        timeRange: draft.timeRange.trim() || null,
        reason: draft.reason.trim() || null,
        breakMinutes: draft.breakMinutes,
        plannedHours: draft.plannedHours,
      };
      if (draft.startDateTime && draft.endDateTime) {
        body.startDateTime = draft.startDateTime;
        body.endDateTime = draft.endDateTime;
        body.hours = null;
      } else {
        body.startDateTime = null;
        body.endDateTime = null;
        body.hours = draft.hours;
      }
      return body;
    },
  });
}

export type AbsenceTimeBankApi = ReturnType<typeof createAbsenceTimeBankApi>;
