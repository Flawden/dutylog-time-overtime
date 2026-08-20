import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type AbsenceType = DutyLogApiSchemas.AbsenceType;
export type AbsencePeriod = DutyLogApiSchemas.AbsencePeriod;
export type AbsenceOccurrence = DutyLogApiSchemas.AbsenceOccurrence;
export type VacationSettings = DutyLogApiSchemas.VacationSettings;
export type VacationSummary = DutyLogApiSchemas.VacationSummary;
export type AbsencePreview = DutyLogApiSchemas.AbsencePreview;
export type AbsencePreviewInput = DutyLogApiSchemas.AbsencePreviewInput;
export type AbsencePeriodInput = DutyLogApiSchemas.AbsencePeriodInput;
export type AbsencePeriodPatch = DutyLogApiSchemas.AbsencePeriodPatch;
export type OvertimeAllocation = DutyLogApiSchemas.OvertimeAllocation;
export type OvertimeUsageRef = DutyLogApiSchemas.OvertimeUsageRef;
export type OvertimeUsage = Omit<DutyLogApiSchemas.OvertimeUsage, "allocations"> & {
  allocations: OvertimeAllocation[];
};
export type OvertimeCredit = Omit<DutyLogApiSchemas.OvertimeCredit, "usages"> & {
  usages: OvertimeUsageRef[];
};
export type OvertimeAccount = Omit<DutyLogApiSchemas.OvertimeAccount, "credits" | "usages"> & {
  credits: OvertimeCredit[];
  usages: OvertimeUsage[];
};
export type OvertimeCreditCreateRequest = DutyLogApiSchemas.OvertimeCreditCreateRequest;
export type OvertimeCreditUpdateRequest = DutyLogApiSchemas.OvertimeCreditUpdateRequest;
export type OvertimeCreditPreview = DutyLogApiSchemas.OvertimeCreditPreview;
export type OvertimeSettlement = DutyLogApiSchemas.OvertimeSettlement;
export type OvertimeSettlementUpsertRequest = DutyLogApiSchemas.OvertimeSettlementUpsertRequest;
export type TimeCompensationSummary = DutyLogApiSchemas.TimeCompensationSummary;
export type LedgerIntegrity = DutyLogApiSchemas.LedgerIntegrity;
export type ActualWorkInterval = DutyLogApiSchemas.ActualWorkInterval;
export type QuickScenario = DutyLogApiSchemas.QuickScenario;
export type QuickScenarioCreateRequest = DutyLogApiSchemas.QuickScenarioCreateRequest;
export type QuickScenarioUpdateRequest = DutyLogApiSchemas.QuickScenarioUpdateRequest;
export type ShiftInterval = DutyLogApiSchemas.ShiftInterval;

export interface VacationPlannerReadModel {
  settings: VacationSettings;
  summary: VacationSummary;
  durationPresets: number[];
  types: AbsenceType[];
  absences: AbsencePeriod[];
  occurrences: AbsenceOccurrence[];
  typeSummaries: Array<DutyLogApiSchemas.AbsenceTypeSummary>;
}

export interface OvertimeAccountReadModel {
  totalEarnedHours: number;
  totalUsedHours: number;
  balanceHours: number;
  credits: OvertimeCredit[];
  usages: OvertimeUsage[];
}

export interface LedgerIntegrityReadModel {
  from: string;
  to: string;
  healthy: boolean;
  reservedMinutes: number;
  postedMinutes: number;
  reversedMinutes: number;
  orphanUsageCount: number;
  allocationMismatchCount: number;
  issues: Array<DutyLogApiSchemas.LedgerIntegrityIssue>;
  entries: Array<DutyLogApiSchemas.TimeLedgerEntry>;
  periods: Array<DutyLogApiSchemas.AccountingPeriod>;
}

export interface CalendarDayReadModel {
  date: string;
  shiftTypeId?: number | null;
  shiftInterval?: ShiftInterval | null;
}

export interface CalendarRangeReadModel {
  days: CalendarDayReadModel[];
}

export type AbsenceStatus = NonNullable<AbsencePeriodInput["status"]>;
export type AbsenceCoverage = NonNullable<AbsencePreviewInput["coverage"]>;
export type CompensationPolicy = NonNullable<AbsencePeriodInput["compensationPolicy"]>;
export type TimeBankTab = "overview" | "credits" | "usage" | "fifo";
export type LedgerRangeMode = "month" | "year";

export interface AbsenceDraft {
  id: number | null;
  typeId: number | null;
  title: string;
  startDate: string;
  endDate: string;
  status: AbsenceStatus;
  note: string;
  coverage: AbsenceCoverage;
  startTime: string;
  endTime: string;
  compensationPolicy: CompensationPolicy;
}

export interface CreditDraft {
  id: number | null;
  date: string;
  timeRange: string;
  startDateTime: string;
  endDateTime: string;
  breakMinutes: number;
  plannedHours: number;
  hours: number;
  reason: string;
}

export interface SettlementDraft {
  id: number | null;
  settlementDate: string;
  minutes: number;
  reason: string;
}

export interface ScenarioDraft {
  id: number | null;
  name: string;
  groupLabel: string;
  description: string;
  startMode: "SHIFT_START" | "SHIFT_END";
  endMode: "SHIFT_END" | "ADD_MINUTES" | "FIXED_TIME";
  endOffsetMinutes: number;
  endFixedTime: string;
  endDayOffset: number;
  breakMode: "ZERO" | "SHIFT" | "CUSTOM";
  customBreakMinutes: number;
  plannedMode: "ZERO" | "SHIFT" | "CUSTOM";
  customPlannedHours: number;
  reasonTemplate: string;
  sortOrder: number;
}

export interface FifoForecastAllocation {
  creditId: number;
  workedDate: string;
  reason: string;
  minutes: number;
  remainingAfterMinutes: number;
}

export interface FifoForecast {
  requestedMinutes: number;
  allocatedMinutes: number;
  shortageMinutes: number;
  freeAfterMinutes: number;
  allocations: FifoForecastAllocation[];
}

export interface LedgerChartColumn {
  key: string;
  earnedHours: number;
  usedHours: number;
  title: string;
}

export interface AbsenceComposerOpenOptions {
  date?: string | null;
  systemCode?: string | null;
  source?: "vacation" | "calendar" | "today" | "time-bank" | "overtime" | "quick-add";
  reason?: string | null;
}

export interface DutyLogAbsenceTimeBankDomain {
  ready(): boolean;
  refresh(): Promise<void>;
  openAbsenceComposer(options?: AbsenceComposerOpenOptions): Promise<void>;
  openAbsenceEditor(id: number): Promise<void>;
  openCreditEditor(date?: string | null): Promise<void>;
  editCredit(id: number): Promise<void>;
  openSettlementEditor(id?: number | null, date?: string | null): Promise<void>;
  openTimeBankUsage(absenceId?: number | null): Promise<void>;
}
