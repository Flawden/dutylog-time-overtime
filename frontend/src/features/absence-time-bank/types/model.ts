import type {
  AbsenceCoverage,
  AbsenceDraft,
  AbsencePeriod,
  AbsencePeriodInput,
  AbsencePeriodPatch,
  AbsenceType,
  ActualWorkInterval,
  CalendarRangeReadModel,
  CompensationPolicy,
  CreditDraft,
  FifoForecast,
  LedgerChartColumn,
  LedgerIntegrityReadModel,
  LedgerRangeMode,
  OvertimeAccountReadModel,
  OvertimeCredit,
  OvertimeUsage,
  QuickScenario,
  QuickScenarioCreateRequest,
  QuickScenarioUpdateRequest,
  ScenarioDraft,
  VacationPlannerReadModel,
} from "./domain";

export function todayIso(now: Date = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function dateRange(mode: LedgerRangeMode = "month", now: Date = new Date()): { from: string; to: string } {
  const from = mode === "year"
    ? new Date(now.getFullYear(), 0, 1)
    : new Date(now.getFullYear(), now.getMonth(), 1);
  const to = mode === "year"
    ? new Date(now.getFullYear(), 11, 31)
    : new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return { from: todayIso(from), to: todayIso(to) };
}

export function currentMonthRange(now: Date = new Date()): { from: string; to: string } {
  return dateRange("month", now);
}

export function asVacationPlanner(value: unknown): VacationPlannerReadModel {
  const raw = (value ?? {}) as Partial<VacationPlannerReadModel>;
  return {
    settings: (raw.settings ?? {}) as VacationPlannerReadModel["settings"],
    summary: (raw.summary ?? {}) as VacationPlannerReadModel["summary"],
    durationPresets: Array.isArray(raw.durationPresets) ? raw.durationPresets.map(Number) : [],
    types: Array.isArray(raw.types) ? raw.types : [],
    absences: Array.isArray(raw.absences) ? raw.absences : [],
    occurrences: Array.isArray(raw.occurrences) ? raw.occurrences : [],
    typeSummaries: Array.isArray(raw.typeSummaries) ? raw.typeSummaries : [],
  };
}

export function asOvertimeAccount(value: unknown): OvertimeAccountReadModel {
  const raw = (value ?? {}) as Partial<OvertimeAccountReadModel>;
  return {
    totalEarnedHours: Number(raw.totalEarnedHours ?? 0),
    totalUsedHours: Number(raw.totalUsedHours ?? 0),
    balanceHours: Number(raw.balanceHours ?? 0),
    credits: Array.isArray(raw.credits) ? raw.credits : [],
    usages: Array.isArray(raw.usages) ? raw.usages : [],
  };
}

export function asLedgerIntegrity(value: unknown, range: { from: string; to: string }): LedgerIntegrityReadModel {
  const raw = (value ?? {}) as Partial<LedgerIntegrityReadModel>;
  return {
    from: String(raw.from ?? range.from),
    to: String(raw.to ?? range.to),
    healthy: Boolean(raw.healthy),
    reservedMinutes: Number(raw.reservedMinutes ?? 0),
    postedMinutes: Number(raw.postedMinutes ?? 0),
    reversedMinutes: Number(raw.reversedMinutes ?? 0),
    orphanUsageCount: Number(raw.orphanUsageCount ?? 0),
    allocationMismatchCount: Number(raw.allocationMismatchCount ?? 0),
    issues: Array.isArray(raw.issues) ? raw.issues : [],
    entries: Array.isArray(raw.entries) ? raw.entries : [],
    periods: Array.isArray(raw.periods) ? raw.periods : [],
  };
}

export function asActualWorkIntervals(value: unknown): ActualWorkInterval[] {
  return Array.isArray(value) ? value as ActualWorkInterval[] : [];
}

export function asQuickScenarios(value: unknown): QuickScenario[] {
  return Array.isArray(value)
    ? [...value as QuickScenario[]].sort((left, right) => Number(left.sortOrder ?? 0) - Number(right.sortOrder ?? 0) || Number(left.id) - Number(right.id))
    : [];
}

export function asCalendarRange(value: unknown): CalendarRangeReadModel {
  const raw = (value ?? {}) as Partial<CalendarRangeReadModel>;
  return { days: Array.isArray(raw.days) ? raw.days : [] };
}

export function defaultCompensation(type: AbsenceType | null | undefined): CompensationPolicy {
  switch (String(type?.systemCode ?? "").toUpperCase()) {
    case "VACATION": return "VACATION_ALLOWANCE";
    case "TIME_OFF": return "OVERTIME_BANK";
    case "SICK": return "SICK_PAY";
    case "UNPAID": return "UNPAID";
    default: return "NONE";
  }
}

export function compensationContext(policy: CompensationPolicy, planner: VacationPlannerReadModel | null): string {
  const summary = planner?.summary;
  switch (policy) {
    case "VACATION_ALLOWANCE":
      return `Отпускной баланс: доступно ${Number(summary?.availableDays ?? 0)} дн., после запланированного останется ${Number(summary?.remainingDays ?? 0)} дн.`;
    case "OVERTIME_BANK":
      return `Банк переработок: свободно ${formatMinutes(summary?.timeOffRemainingMinutes)}. Списание выполняется по FIFO.`;
    case "SICK_PAY":
      return "Баланс не используется. Больничный учитывается отдельно в plan/fact.";
    case "UNPAID":
      return "Баланс не используется. Время попадёт в неоплачиваемую компенсацию.";
    default:
      return "Баланс не используется. Backend сохранит отсутствие без списания.";
  }
}

export function newAbsenceDraft(date: string = todayIso()): AbsenceDraft {
  return {
    id: null,
    typeId: null,
    title: "",
    startDate: date,
    endDate: date,
    status: "PLANNED",
    note: "",
    coverage: "FULL_DAY",
    startTime: "09:00",
    endTime: "18:00",
    compensationPolicy: "NONE",
  };
}

export function draftFromPeriod(period: AbsencePeriod): AbsenceDraft {
  return {
    id: Number(period.id ?? 0) || null,
    typeId: Number(period.typeId ?? 0) || null,
    title: String(period.title ?? ""),
    startDate: String(period.startDate ?? todayIso()),
    endDate: String(period.endDate ?? period.startDate ?? todayIso()),
    status: period.status ?? "PLANNED",
    note: String(period.note ?? ""),
    coverage: (period.coverage ?? "FULL_DAY") as AbsenceCoverage,
    startTime: String(period.startTime ?? "09:00"),
    endTime: String(period.endTime ?? "18:00"),
    compensationPolicy: period.compensationPolicy ?? "NONE",
  };
}

export function absenceCreateBody(draft: AbsenceDraft): AbsencePeriodInput {
  const body: AbsencePeriodInput = {
    typeId: Number(draft.typeId),
    title: draft.title.trim() || null,
    startDate: draft.startDate,
    endDate: draft.coverage === "PARTIAL" ? draft.startDate : draft.endDate,
    status: draft.status,
    note: draft.note.trim() || null,
    coverage: draft.coverage === "HOURS_ONLY" ? "PARTIAL" : draft.coverage,
    compensationPolicy: draft.compensationPolicy,
  };
  if (draft.coverage === "PARTIAL") {
    body.startTime = draft.startTime;
    body.endTime = draft.endTime;
  }
  return body;
}

export function absencePatchBody(draft: AbsenceDraft): AbsencePeriodPatch {
  return {
    typeId: Number(draft.typeId),
    title: draft.title.trim() || null,
    clearTitle: !draft.title.trim(),
    startDate: draft.startDate,
    endDate: draft.coverage === "PARTIAL" ? draft.startDate : draft.endDate,
    status: draft.status,
    note: draft.note.trim() || null,
    clearNote: !draft.note.trim(),
    coverage: draft.coverage,
    startTime: draft.coverage === "PARTIAL" ? draft.startTime : null,
    endTime: draft.coverage === "PARTIAL" ? draft.endTime : null,
    clearTimes: draft.coverage !== "PARTIAL",
    compensationPolicy: draft.compensationPolicy,
  };
}

export function newCreditDraft(date: string = todayIso()): CreditDraft {
  return {
    id: null,
    date,
    timeRange: "",
    startDateTime: "",
    endDateTime: "",
    breakMinutes: 0,
    plannedHours: 0,
    hours: 1,
    reason: "",
  };
}

export function creditDraftFromRow(row: OvertimeCredit): CreditDraft {
  return {
    id: Number(row.id),
    date: row.workedDate,
    timeRange: String(row.timeRange ?? ""),
    startDateTime: toLocalDateTimeInput(row.startDateTime ?? row.displayStart),
    endDateTime: toLocalDateTimeInput(row.endDateTime ?? row.displayEnd),
    breakMinutes: Number(row.breakMinutes ?? 0),
    plannedHours: Number(row.plannedHours ?? 0),
    hours: Number(row.projection?.sourceCreditHours ?? row.hours ?? 0),
    reason: String(row.reason ?? ""),
  };
}

export function toLocalDateTimeInput(value: string | null | undefined): string {
  return value ? String(value).slice(0, 16) : "";
}

export function addMinutesToLocalDateTime(value: string, minutes: number): string {
  const [datePart, timePart] = value.split("T");
  if (!datePart || !timePart) return "";
  const [year, month, day] = datePart.split("-").map(Number);
  const [hour, minute] = timePart.split(":").map(Number);
  if (![year, month, day, hour, minute].every(Number.isFinite)) return "";
  const next = new Date(year!, month! - 1, day!, hour!, minute!);
  next.setMinutes(next.getMinutes() + minutes);
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())}T${pad(next.getHours())}:${pad(next.getMinutes())}`;
}

export function creditHoursFromInterval(draft: CreditDraft): number {
  if (!draft.startDateTime || !draft.endDateTime) return Math.max(0, Number(draft.hours || 0));
  const start = new Date(draft.startDateTime);
  const end = new Date(draft.endDateTime);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) return 0;
  const elapsedMinutes = Math.round((end.getTime() - start.getTime()) / 60_000);
  const credited = Math.max(0, elapsedMinutes - Math.max(0, Number(draft.breakMinutes || 0)) - Math.round(Math.max(0, Number(draft.plannedHours || 0)) * 60));
  return Math.round((credited / 60) * 100) / 100;
}

export function newScenarioDraft(): ScenarioDraft {
  return {
    id: null,
    name: "",
    groupLabel: "",
    description: "",
    startMode: "SHIFT_END",
    endMode: "ADD_MINUTES",
    endOffsetMinutes: 120,
    endFixedTime: "",
    endDayOffset: 0,
    breakMode: "ZERO",
    customBreakMinutes: 0,
    plannedMode: "ZERO",
    customPlannedHours: 0,
    reasonTemplate: "",
    sortOrder: 0,
  };
}

export function scenarioDraftFromRow(row: QuickScenario): ScenarioDraft {
  return {
    id: Number(row.id),
    name: row.name,
    groupLabel: String(row.groupLabel ?? ""),
    description: String(row.description ?? ""),
    startMode: row.startMode,
    endMode: row.endMode,
    endOffsetMinutes: Number(row.endOffsetMinutes ?? 0),
    endFixedTime: String(row.endFixedTime ?? ""),
    endDayOffset: Number(row.endDayOffset ?? (row.endNextDay ? 1 : 0)),
    breakMode: row.breakMode,
    customBreakMinutes: Number(row.customBreakMinutes ?? 0),
    plannedMode: row.plannedMode,
    customPlannedHours: Number(row.customPlannedHours ?? 0),
    reasonTemplate: String(row.reasonTemplate ?? ""),
    sortOrder: Number(row.sortOrder ?? 0),
  };
}

export function scenarioCreateBody(draft: ScenarioDraft): QuickScenarioCreateRequest {
  return {
    name: draft.name.trim(),
    groupLabel: draft.groupLabel.trim() || null,
    description: draft.description.trim() || null,
    startMode: draft.startMode,
    endMode: draft.endMode,
    endOffsetMinutes: draft.endOffsetMinutes,
    endFixedTime: draft.endFixedTime || null,
    endNextDay: draft.endDayOffset > 0,
    endDayOffset: draft.endDayOffset,
    breakMode: draft.breakMode,
    customBreakMinutes: draft.customBreakMinutes,
    plannedMode: draft.plannedMode,
    customPlannedHours: draft.customPlannedHours,
    reasonTemplate: draft.reasonTemplate.trim() || null,
    sortOrder: draft.sortOrder,
  };
}

export function scenarioUpdateBody(draft: ScenarioDraft): QuickScenarioUpdateRequest {
  return scenarioCreateBody(draft);
}

export function applyScenarioToCreditDraft(draft: CreditDraft, scenario: QuickScenario, shift: { workStart: string; workEnd: string; breakMinutes: number; netMinutes: number }): CreditDraft {
  const shiftStart = toLocalDateTimeInput(shift.workStart);
  const shiftEnd = toLocalDateTimeInput(shift.workEnd);
  const start = scenario.startMode === "SHIFT_START" ? shiftStart : shiftEnd;
  let end = shiftEnd;
  if (scenario.endMode === "ADD_MINUTES") end = addMinutesToLocalDateTime(start, Number(scenario.endOffsetMinutes ?? 0));
  if (scenario.endMode === "FIXED_TIME") {
    const base = new Date(`${draft.date}T12:00:00`);
    base.setDate(base.getDate() + Number(scenario.endDayOffset ?? (scenario.endNextDay ? 1 : 0)));
    const date = todayIso(base);
    end = scenario.endFixedTime ? `${date}T${scenario.endFixedTime}` : "";
  }
  const breakMinutes = scenario.breakMode === "SHIFT" ? Number(shift.breakMinutes ?? 0)
    : scenario.breakMode === "CUSTOM" ? Number(scenario.customBreakMinutes ?? 0) : 0;
  const plannedHours = scenario.plannedMode === "SHIFT" ? Number(shift.netMinutes ?? 0) / 60
    : scenario.plannedMode === "CUSTOM" ? Number(scenario.customPlannedHours ?? 0) : 0;
  const next: CreditDraft = {
    ...draft,
    startDateTime: start,
    endDateTime: end,
    breakMinutes,
    plannedHours: Math.round(plannedHours * 100) / 100,
    reason: draft.reason.trim() || String(scenario.reasonTemplate ?? scenario.name),
  };
  next.hours = creditHoursFromInterval(next);
  return next;
}

export function formatHours(value: number | null | undefined): string {
  const safe = Number.isFinite(Number(value)) ? Number(value) : 0;
  return `${safe.toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ч`;
}

export function formatSignedHours(value: number | null | undefined, negative = false): string {
  const safe = Math.abs(Number.isFinite(Number(value)) ? Number(value) : 0);
  const sign = negative ? "−" : "+";
  return `${sign}${safe.toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ч`;
}

export function formatMinutes(value: number | null | undefined): string {
  const raw = Math.round(Number(value ?? 0));
  const sign = raw < 0 ? "−" : "";
  const minutes = Math.abs(raw);
  if (minutes % 60 === 0) return `${sign}${formatHours(minutes / 60)}`;
  return `${sign}${Math.floor(minutes / 60)} ч ${minutes % 60} мин`;
}

export function absenceDisplayTitle(period: AbsencePeriod): string {
  return String(period.title ?? "").trim() || String(period.typeName ?? "Отсутствие");
}

export function usageIsAbsenceOwned(usage: OvertimeUsage): boolean {
  return usage.sourceKind === "ABSENCE" && Number(usage.sourceAbsenceId ?? 0) > 0;
}

export function uniqueSourceCredits(credits: OvertimeCredit[]): OvertimeCredit[] {
  const byId = new Map<number, OvertimeCredit>();
  for (const credit of credits) {
    const id = Number(credit.id);
    const previous = byId.get(id);
    if (!previous || Number(credit.projection?.partIndex ?? 1) < Number(previous.projection?.partIndex ?? 1)) byId.set(id, credit);
  }
  return [...byId.values()].sort((left, right) => String(left.workedDate).localeCompare(String(right.workedDate)) || Number(left.id) - Number(right.id));
}

export function fifoOpenCredits(account: OvertimeAccountReadModel): OvertimeCredit[] {
  return uniqueSourceCredits(account.credits)
    .filter(credit => Number(credit.projection?.sourceRemainingHours ?? credit.remainingHours ?? 0) > 0.0001)
    .sort((left, right) => String(left.creditedStartInstant ?? left.startInstant ?? left.workedDate).localeCompare(String(right.creditedStartInstant ?? right.startInstant ?? right.workedDate)) || Number(left.id) - Number(right.id));
}

export function reservedUsageMinutes(account: OvertimeAccountReadModel): number {
  return account.usages.filter(usage => usage.reserved).reduce((sum, usage) => sum + Number(usage.minutes ?? 0), 0);
}

export function fifoForecast(account: OvertimeAccountReadModel, requestedMinutes: number, excludeAbsenceId: number | null = null): FifoForecast {
  const restored = new Map<number, number>();
  if (excludeAbsenceId !== null) {
    const usage = account.usages.find(item => Number(item.sourceAbsenceId ?? 0) === excludeAbsenceId);
    for (const allocation of usage?.allocations ?? []) {
      restored.set(Number(allocation.creditId), (restored.get(Number(allocation.creditId)) ?? 0) + Number(allocation.minutes ?? 0));
    }
  }
  let remaining = Math.max(0, Math.round(requestedMinutes));
  const allocations = [] as FifoForecast["allocations"];
  for (const credit of fifoOpenCredits(account)) {
    if (remaining <= 0) break;
    const available = Math.max(0, Math.round(Number(credit.projection?.sourceRemainingHours ?? credit.remainingHours ?? 0) * 60)) + (restored.get(Number(credit.id)) ?? 0);
    const minutes = Math.min(available, remaining);
    if (minutes > 0) {
      remaining -= minutes;
      allocations.push({
        creditId: Number(credit.id),
        workedDate: credit.workedDate,
        reason: String(credit.reason ?? credit.timeRange ?? "Переработка"),
        minutes,
        remainingAfterMinutes: Math.max(0, available - minutes),
      });
    }
  }
  const allocatedMinutes = Math.max(0, Math.round(requestedMinutes)) - remaining;
  const restoredMinutes = [...restored.values()].reduce((sum, value) => sum + value, 0);
  return {
    requestedMinutes: Math.max(0, Math.round(requestedMinutes)),
    allocatedMinutes,
    shortageMinutes: remaining,
    freeAfterMinutes: Math.max(0, Math.round(account.balanceHours * 60) + restoredMinutes - Math.max(0, Math.round(requestedMinutes))),
    allocations,
  };
}

export function usageRatio(account: OvertimeAccountReadModel): number {
  return account.totalEarnedHours > 0 ? Math.round((account.totalUsedHours / account.totalEarnedHours) * 100) : 0;
}

export function oldestCreditRemainingMinutes(account: OvertimeAccountReadModel): number {
  const oldest = fifoOpenCredits(account)[0];
  return oldest ? Math.round(Number(oldest.projection?.sourceRemainingHours ?? oldest.remainingHours ?? 0) * 60) : 0;
}

function periodKey(date: string, mode: LedgerRangeMode): string {
  return mode === "year" ? date.slice(0, 7) : date;
}

function finiteHours(value: unknown): number | null {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function creditRowEarnedHours(credit: OvertimeCredit): number {
  const rowHours = finiteHours(credit.hours) ?? 0;
  if (Math.abs(rowHours) > 0.0001) return rowHours;
  const projection = credit.projection;
  if (!projection || Number(projection.partCount ?? 1) !== 1) return rowHours;
  return finiteHours(projection.sourceCreditHours) ?? finiteHours(projection.dayEarnedHours) ?? rowHours;
}

export function dayCreditTotals(credits: OvertimeCredit[]): Map<string, { earned: number; used: number; remaining: number }> {
  const rowsByDay = new Map<string, OvertimeCredit[]>();
  for (const credit of credits) {
    const rows = rowsByDay.get(credit.workedDate) ?? [];
    rows.push(credit);
    rowsByDay.set(credit.workedDate, rows);
  }

  const result = new Map<string, { earned: number; used: number; remaining: number }>();
  for (const [date, rows] of rowsByDay) {
    const serverProjection = rows.map(row => row.projection).find(projection => projection != null);
    const fallback = rows.reduce((totals, row) => ({
      earned: totals.earned + creditRowEarnedHours(row),
      used: totals.used + (finiteHours(row.usedHours) ?? 0),
      remaining: totals.remaining + (finiteHours(row.remainingHours) ?? 0),
    }), { earned: 0, used: 0, remaining: 0 });
    result.set(date, {
      earned: finiteHours(serverProjection?.dayEarnedHours) ?? fallback.earned,
      used: finiteHours(serverProjection?.dayUsedHours) ?? fallback.used,
      remaining: finiteHours(serverProjection?.dayRemainingHours) ?? fallback.remaining,
    });
  }
  return result;
}

export function ledgerChartColumns(account: OvertimeAccountReadModel, mode: LedgerRangeMode): LedgerChartColumn[] {
  const rows = new Map<string, { earnedHours: number; usedHours: number }>();
  const rowFor = (date: string) => {
    const key = periodKey(date, mode);
    const current = rows.get(key) ?? { earnedHours: 0, usedHours: 0 };
    rows.set(key, current);
    return current;
  };

  for (const [date, totals] of dayCreditTotals(account.credits)) {
    rowFor(date).earnedHours += totals.earned;
  }
  for (const usage of account.usages) {
    rowFor(usage.usageDate).usedHours += Number(usage.hours ?? 0);
  }

  return [...rows.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([key, value]) => ({
    key,
    earnedHours: Math.round(value.earnedHours * 100) / 100,
    usedHours: Math.round(value.usedHours * 100) / 100,
    title: `${key}: +${formatHours(value.earnedHours)} · −${formatHours(value.usedHours)}`,
  }));
}

export function scenarioDescription(scenario: QuickScenario): string {
  const start = scenario.startMode === "SHIFT_START" ? "от начала смены" : "от конца смены";
  const end = scenario.endMode === "ADD_MINUTES"
    ? `+${scenario.endOffsetMinutes} мин`
    : scenario.endMode === "FIXED_TIME"
      ? `${scenario.endFixedTime ?? "--:--"} · день ${scenario.endDayOffset >= 0 ? "+" : ""}${scenario.endDayOffset}`
      : "до конца смены";
  return `${start} → ${end}`;
}
