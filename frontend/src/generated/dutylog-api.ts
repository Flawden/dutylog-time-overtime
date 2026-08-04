/* eslint-disable */
/**
 * GENERATED FILE — DO NOT EDIT.
 * Source: src/main/resources/static/openapi/dutylog-v1.yaml
 * SHA-256: 7fb97f9bc762ad0de0ebf122ed8fdc139f1ba60371da2eef89088a4bb0880758
 * Generator: frontend/scripts/generate-openapi-contract.mjs
 */

export const DUTYLOG_OPENAPI_SOURCE_SHA256 = "7fb97f9bc762ad0de0ebf122ed8fdc139f1ba60371da2eef89088a4bb0880758";

export namespace DutyLogApiSchemas {
  export type AbsenceOccurrence = {
    periodId: number;
    typeId: number;
    typeName: string;
    typeColor: string;
    systemCode?: string | null;
    title?: string | null;
    date: string;
    startDate: string;
    endDate: string;
    status: "DRAFT" | "PLANNED" | "SUBMITTED" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED";
    countedDay: boolean;
    shiftConflict: boolean;
    balancePolicy?: "VACATION_DAYS" | "TIME_OFF_HOURS" | "NONE";
    coverage?: "FULL_DAY" | "PARTIAL" | "HOURS_ONLY";
    startTime?: string | null;
    endTime?: string | null;
    chargedMinutes?: number;
    replacesShift?: boolean;
    plannedShiftName?: string | null;
    plannedShiftColor?: string | null;
    plannedShiftMinutes?: number;
    compensationPolicy?: "VACATION_ALLOWANCE" | "OVERTIME_BANK" | "SICK_PAY" | "UNPAID" | "NONE";
    compensatedMinutes?: number;
    linkedOvertimeUsageId?: number | null;
  };

  export type AbsencePeriod = DutyLogApiSchemas.AbsencePeriodInput & {
    id?: number;
    typeName?: string;
    typeColor?: string;
    systemCode?: string | null;
    countsAgainstAllowance?: boolean;
    calendarDays?: number;
    countedDays?: number;
    shiftConflictCount?: number;
    balancePolicy?: "VACATION_DAYS" | "TIME_OFF_HOURS" | "NONE";
    chargedMinutes?: number;
    replacesShift?: boolean;
    compensationPolicy?: "VACATION_ALLOWANCE" | "OVERTIME_BANK" | "SICK_PAY" | "UNPAID" | "NONE";
    compensatedMinutes?: number;
    linkedOvertimeUsageId?: number | null;
  };

  export type AbsencePeriodInput = {
    typeId: number;
    title?: string | null;
    startDate: string;
    endDate: string;
    status?: "DRAFT" | "PLANNED" | "SUBMITTED" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED";
    note?: string | null;
    coverage?: "FULL_DAY" | "PARTIAL";
    startTime?: string | null;
    endTime?: string | null;
    compensationPolicy?: "VACATION_ALLOWANCE" | "OVERTIME_BANK" | "SICK_PAY" | "UNPAID" | "NONE";
  };

  export type AbsencePeriodPatch = DutyLogApiSchemas.AbsencePeriodInput;

  export type AbsencePreview = unknown;

  export type AbsencePreviewInput = {
    typeId: number;
    startDate: string;
    endDate: string;
    excludePeriodId?: number | null;
    coverage?: "FULL_DAY" | "PARTIAL" | "HOURS_ONLY";
    startTime?: string | null;
    endTime?: string | null;
    compensationPolicy?: "VACATION_ALLOWANCE" | "OVERTIME_BANK" | "SICK_PAY" | "UNPAID" | "NONE";
  };

  export type AbsenceType = DutyLogApiSchemas.AbsenceTypeInput & {
    id?: number;
    systemPreset?: boolean;
    systemCode?: "VACATION" | "TIME_OFF" | "SICK" | "UNPAID" | "OTHER" | null;
  };

  export type AbsenceTypeInput = {
    name: string;
    color?: string;
    countsAgainstAllowance?: boolean;
    sortOrder?: number;
    balancePolicy?: "VACATION_DAYS" | "TIME_OFF_HOURS" | "NONE";
    fullDayReplacesShift?: boolean;
  };

  export type AbsenceTypePatch = DutyLogApiSchemas.AbsenceTypeInput;

  export type AccountingPeriod = {
    month: string;
    status: "OPEN" | "CLOSED";
    closedAt?: string | null;
    updatedAt?: string | null;
  };

  export type ActualWorkInterval = DutyLogApiSchemas.ActualWorkIntervalInput & {
    id?: number;
    workedMinutes?: number;
    createdAt?: string | null;
    updatedAt?: string | null;
  };

  export type ActualWorkIntervalInput = {
    workDate: string;
    startTime: string;
    endTime: string;
    note?: string | null;
  };

  export type ApiError = {
    code: string;
    message: string;
    error: string;
    fields?: Record<string, string>;
    moduleKey?: string | null;
    requestId?: string | null;
    timestamp: string;
  };

  export type CalendarLayer = DutyLogApiSchemas.CalendarLayerEntry & {
    id: number;
    name: string;
    color: string;
    timezone: string;
    visible: boolean;
    sortOrder?: number;
    templateId: number;
    templateName?: string;
    anchorDate: string;
    startDate: string;
    endDate?: string | null;
    readOnly: true;
    entries: DutyLogApiSchemas.CalendarLayerEntry;
  };

  export type CalendarLayerEntry = {
    layerId?: number;
    layerName?: string;
    layerColor?: string;
    sourceDate?: string;
    date?: string;
    shiftTypeId?: number;
    shiftTypeName?: string;
    shiftColor?: string;
    sourceTimezone?: string;
    startInstant?: string | null;
    endInstant?: string | null;
    displayStart?: string | null;
    displayEnd?: string | null;
    timed?: boolean;
    dayOff?: boolean;
  };

  export type CalendarLayerInput = {
    name: string;
    color?: string;
    timezone: string;
    visible?: boolean;
    sortOrder?: number;
    templateId: number;
    anchorDate: string;
    startDate: string;
    endDate?: string | null;
  };

  export type CalendarLayerPatch = {
    name?: string;
    color?: string;
    timezone?: string;
    visible?: boolean;
    sortOrder?: number;
    templateId?: number;
    anchorDate?: string;
    startDate?: string;
    endDate?: string | null;
    clearEndDate?: boolean;
  };

  export type CalendarSubscription = DutyLogApiSchemas.CalendarSyncStatus & {
    subscriptionUrl?: string;
  };

  export type CalendarSyncStatus = {
    active: boolean;
    tokenHint?: string | null;
    createdAt?: string | null;
    rotatedAt?: string | null;
    feedPastDays: number;
    feedFutureDays: number;
    entities: Array<"shifts" | "tasks" | "important_events" | "absences">;
  };

  export type Day = DutyLogApiSchemas.DayNote & DutyLogApiSchemas.ShiftInterval & {
    date: string;
    shiftTypeId?: number | null;
    note?: string | null;
    notes?: DutyLogApiSchemas.DayNote;
    dayEmoji?: string | null;
    overtimeHours: number;
    timeOffHours: number;
    overtimeBalanceHours: number;
    version: number;
    updatedAt?: string;
    shiftInterval?: DutyLogApiSchemas.ShiftInterval | null;
  };

  export type DayNote = {
    id: number;
    date: string;
    title?: string | null;
    content: string;
    pinned: boolean;
    sortOrder: number;
    version: number;
    createdAt?: string;
    updatedAt?: string;
  };

  export type DayNoteCreateRequest = {
    date: string;
    title?: string | null;
    content?: string | null;
    pinned?: boolean;
  };

  export type DayNoteMoveRequest = {
    direction: "UP" | "DOWN";
  };

  export type DayNoteUpdateRequest = {
    title?: string | null;
    content?: string | null;
    pinned?: boolean;
  };

  export type ImportantEvent = DutyLogApiSchemas.ImportantEventInput & {
    id?: number;
    startInstant?: string | null;
    endInstant?: string | null;
  };

  export type ImportantEventInput = {
    title: string;
    date: string;
    endDate?: string | null;
    eventType?: "IMPORTANT_DATE" | "EVENT" | "PERIOD";
    allDay?: boolean;
    startTime?: string | null;
    endTime?: string | null;
    sourceTimezone?: string | null;
    place?: string | null;
    description?: string | null;
    icon?: string | null;
    category?: string | null;
    color?: string;
    repeatMode?: "NONE" | "MONTHLY" | "YEARLY";
    reminders?: Array<number>;
  };

  export type InboxConversion = DutyLogApiSchemas.InboxItem & DutyLogApiSchemas.Task & {
    inboxItem: DutyLogApiSchemas.InboxItem;
    task: DutyLogApiSchemas.Task;
  };

  export type InboxCreateRequest = {
    text: string;
    clientOperationId?: string | null;
  };

  export type InboxItem = {
    id: number;
    text: string;
    status: "OPEN" | "ARCHIVED";
    clientOperationId?: string | null;
    createdAt: string;
    updatedAt: string;
    resolvedAt?: string | null;
  };

  export type InboxToTaskRequest = DutyLogApiSchemas.TaskSubtaskInput & {
    date: string;
    description?: string | null;
    category?: string | null;
    tags?: Array<string>;
    priority?: "LOW" | "NORMAL" | "HIGH" | "URGENT";
    dueDate?: string | null;
    dueTime?: string | null;
    reminderEnabled?: boolean;
    reminderMinutesBefore?: number | null;
    subtasks?: DutyLogApiSchemas.TaskSubtaskInput;
  };

  export type InboxUpdateRequest = {
    text?: string;
    archived?: boolean;
  };

  export type LedgerAdjustmentInput = {
    month: string;
    signedMinutes: number;
    reason?: string;
  };

  export type LedgerIntegrity = DutyLogApiSchemas.LedgerIntegrityIssue & DutyLogApiSchemas.TimeLedgerEntry & DutyLogApiSchemas.AccountingPeriod & {
    from: string;
    to: string;
    healthy: boolean;
    reservedMinutes: number;
    postedMinutes: number;
    reversedMinutes: number;
    orphanUsageCount: number;
    allocationMismatchCount: number;
    issues: DutyLogApiSchemas.LedgerIntegrityIssue;
    entries: DutyLogApiSchemas.TimeLedgerEntry;
    periods: DutyLogApiSchemas.AccountingPeriod;
  };

  export type LedgerIntegrityIssue = {
    code: string;
    severity: "ERROR" | "WARNING";
    message: string;
    sourceKind: string;
    sourceId?: number | null;
  };

  export type LegacyOvertimeCredit = {
    id: number;
    workedDate: string;
    startDateTime?: string | null;
    endDateTime?: string | null;
    timeRange?: string | null;
    hours: number;
    minutes: number;
    reason?: string | null;
    migratable: boolean;
    blockedReason?: string | null;
    sourceTimezone: string;
    projectedStart?: string | null;
    projectedEnd?: string | null;
    creditedStart?: string | null;
    creditedEnd?: string | null;
  };

  export type LegacyOvertimeMigrationPreview = DutyLogApiSchemas.LegacyOvertimeCredit & {
    sourceTimezone: string;
    requestedCount: number;
    migratableCount: number;
    blockedCount: number;
    credits: DutyLogApiSchemas.LegacyOvertimeCredit;
  };

  export type LegacyOvertimeMigrationRequest = {
    creditIds?: Array<number>;
    sourceTimezone?: string;
  };

  export type LegacyOvertimeMigrationResult = DutyLogApiSchemas.OvertimeAccount & {
    migratedCount: number;
    skippedCount: number;
    account: DutyLogApiSchemas.OvertimeAccount;
  };

  export type LegacyOvertimeUsageMigrationItem = {
    usageId: number;
    usageDate: string;
    hours: number;
    minutes: number;
    reason?: string | null;
    inferredCoverage: "FULL_DAY" | "HOURS_ONLY";
    plannedShiftPresent: boolean;
    plannedShiftMinutes: number;
    migratable: boolean;
    blockedReason?: string | null;
  };

  export type LegacyOvertimeUsageMigrationPreview = DutyLogApiSchemas.LegacyOvertimeUsageMigrationItem & {
    totalCount: number;
    fullDayCount: number;
    hoursOnlyCount: number;
    blockedCount: number;
    usages: DutyLogApiSchemas.LegacyOvertimeUsageMigrationItem;
  };

  export type LegacyOvertimeUsageMigrationRequest = {
    usageIds?: Array<number>;
  };

  export type LegacyOvertimeUsageMigrationResult = {
    migratedCount: number;
    skippedCount: number;
    absenceIds: Array<number>;
  };

  export type LegacyShiftMigrationPreview = DutyLogApiSchemas.LegacyShiftOccurrence & {
    sourceTimezone: string;
    legacyCount: number;
    occurrences: DutyLogApiSchemas.LegacyShiftOccurrence;
  };

  export type LegacyShiftMigrationRequest = {
    sourceTimezone: string;
    dayEntryIds: Array<number>;
  };

  export type LegacyShiftOccurrence = {
    dayEntryId?: number;
    sourceDate?: string;
    shiftTypeId?: number;
    shiftName?: string;
    localStart?: string;
    localEnd?: string;
    sourceTimezone?: string;
    projectedStart?: string;
    projectedEnd?: string;
  };

  export type LegacyTaskDeadline = {
    taskId: number;
    text: string;
    sourceDate: string;
    sourceTime: string;
    sourceTimezone: string;
    projectedDate: string;
    projectedTime: string;
    targetTimezone: string;
    dueInstant: string;
  };

  export type LegacyTaskDeadlineMigrationPreview = DutyLogApiSchemas.LegacyTaskDeadline & {
    sourceTimezone: string;
    targetTimezone: string;
    legacyCount: number;
    tasks: DutyLogApiSchemas.LegacyTaskDeadline;
  };

  export type LegacyTaskDeadlineMigrationRequest = {
    sourceTimezone: string;
    taskIds: Array<number>;
  };

  export type MobileBootstrap = DutyLogApiSchemas.MobileUser & {
    apiVersion: "v1";
    serverTime: string;
    user: DutyLogApiSchemas.MobileUser;
    calendar: Record<string, unknown>;
  };

  export type MobileDayPatch = {
    date: string;
    shiftTypeId?: number | null;
    clearShiftType?: boolean;
    note?: string | null;
    clearNote?: boolean;
    dayEmoji?: string | null;
    clearDayEmoji?: boolean;
    overtimeHours?: number;
    timeOffHours?: number;
  };

  export type MobileLoginRequest = {
    username: string;
    password: string;
    deviceName?: string;
  };

  export type MobileRegisterRequest = {
    username: string;
    password: string;
    languagePreference?: "ru" | "en";
    deviceName?: string;
  };

  export type MobileSession = {
    id?: number;
    deviceName?: string;
    createdAt?: string;
    lastUsedAt?: string | null;
    refreshExpiresAt?: string;
    revoked?: boolean;
    active?: boolean;
  };

  export type MobileSyncItemResult = DutyLogApiSchemas.Day & {
    operationId: string;
    status: "APPLIED" | "ALREADY_APPLIED" | "CONFLICT" | "REJECTED";
    entityType: "day";
    entityId: string;
    serverVersion?: number | null;
    entity?: DutyLogApiSchemas.Day;
    errorCode?: "VERSION_CONFLICT" | "NO_CHANGES" | "MODULE_DISABLED" | "NOT_FOUND" | "VALIDATION_FAILED" | "REJECTED" | null;
    message?: string | null;
  };

  export type MobileSyncOperation = DutyLogApiSchemas.MobileDayPatch & {
    operationId: string;
    baseVersion: number;
    day: DutyLogApiSchemas.MobileDayPatch;
  };

  export type MobileSyncRequest = DutyLogApiSchemas.MobileSyncOperation & {
    operations: DutyLogApiSchemas.MobileSyncOperation;
  };

  export type MobileSyncResponse = DutyLogApiSchemas.MobileSyncItemResult & {
    apiVersion: "v1";
    serverTime: string;
    items: DutyLogApiSchemas.MobileSyncItemResult;
  };

  export type MobileTokenResponse = DutyLogApiSchemas.MobileUser & {
    tokenType: "Bearer";
    accessToken: string;
    accessExpiresAt: string;
    refreshToken: string;
    refreshExpiresAt: string;
    user: DutyLogApiSchemas.MobileUser;
  };

  export type MobileUser = {
    username: string;
    workTimezone?: string | null;
    displayTimezone?: string | null;
  };

  export type OvertimeAccount = DutyLogApiSchemas.OvertimeCredit & DutyLogApiSchemas.OvertimeUsage & {
    totalEarnedHours: number;
    totalUsedHours: number;
    balanceHours: number;
    credits: DutyLogApiSchemas.OvertimeCredit;
    usages: DutyLogApiSchemas.OvertimeUsage;
  };

  export type OvertimeAllocation = {
    creditId: number;
    workedDate: string;
    timeRange?: string | null;
    hours: number;
    minutes: number;
    reason?: string | null;
    startInstant?: string | null;
    endInstant?: string | null;
    displayStart?: string | null;
    displayEnd?: string | null;
    sourceTimezone?: string | null;
    exact: boolean;
    reconstructed: boolean;
  };

  export type OvertimeCredit = DutyLogApiSchemas.OvertimeUsageRef & DutyLogApiSchemas.OvertimeDailyProjection & {
    id: number;
    workedDate: string;
    timeRange?: string | null;
    startDateTime?: string | null;
    endDateTime?: string | null;
    breakMinutes?: number;
    plannedHours?: number;
    calculated: boolean;
    hours: number;
    creditedMinutes: number;
    reason?: string | null;
    usedHours: number;
    remainingHours: number;
    usages: DutyLogApiSchemas.OvertimeUsageRef;
    startInstant?: string | null;
    endInstant?: string | null;
    creditedStartInstant?: string | null;
    creditedEndInstant?: string | null;
    sourceTimezone?: string | null;
    displayStart?: string | null;
    displayEnd?: string | null;
    displayTimezone?: string | null;
    creditedDisplayStart?: string | null;
    creditedDisplayEnd?: string | null;
    migratedFromLegacy: boolean;
    legacyTimezoneRequired: boolean;
    projection?: DutyLogApiSchemas.OvertimeDailyProjection;
  };

  export type OvertimeCreditCreateRequest = {
    date?: string | null;
    timeRange?: string | null;
    hours?: number | null;
    reason?: string | null;
    startDateTime?: string | null;
    endDateTime?: string | null;
    breakMinutes?: number | null;
    plannedHours?: number | null;
  };

  export type OvertimeCreditPreview = {
    calculated: boolean;
    elapsedMinutes: number;
    elapsedHours: number;
    breakMinutes: number;
    plannedMinutes: number;
    plannedHours: number;
    creditedMinutes: number;
    creditedHours: number;
    sourceTimezone?: string | null;
    startInstant?: string | null;
    endInstant?: string | null;
  };

  export type OvertimeDailyProjection = {
    sourceWorkedDate: string;
    sourceTimeRange?: string | null;
    partIndex: number;
    partCount: number;
    dayRowIndex: number;
    dayRowCount: number;
    dayEarnedHours: number;
    dayUsedHours: number;
    dayRemainingHours: number;
    sourceCreditHours: number;
    sourceUsedHours: number;
    sourceRemainingHours: number;
    exact: boolean;
  };

  export type OvertimeUsage = DutyLogApiSchemas.OvertimeAllocation & {
    id: number;
    usageDate: string;
    hours: number;
    minutes: number;
    reason?: string | null;
    sourceKind: "MANUAL" | "ABSENCE";
    sourceAbsenceId?: number | null;
    editable: boolean;
    postingState: "RESERVED" | "POSTED";
    reserved: boolean;
    allocations: DutyLogApiSchemas.OvertimeAllocation;
  };

  export type OvertimeUsageRef = {
    usageId: number;
    usageDate: string;
    hours: number;
    minutes: number;
    reason?: string | null;
    startInstant?: string | null;
    endInstant?: string | null;
    displayStart?: string | null;
    displayEnd?: string | null;
    sourceTimezone?: string | null;
    allocationPartIndex?: number;
    allocationPartCount?: number;
    exact: boolean;
    reconstructed: boolean;
  };

  export type PayrollAdjustment = DutyLogApiSchemas.PayrollAdjustmentInput & {
    id?: number;
    createdAt?: string;
  };

  export type PayrollAdjustmentInput = {
    month: string;
    adjustmentType: "ADDITION" | "DEDUCTION";
    amountMinor: number;
    title: string;
    note?: string | null;
  };

  export type PayrollPeriod = DutyLogApiSchemas.PayrollSettings & DutyLogApiSchemas.PayrollPreview & DutyLogApiSchemas.PayrollAdjustment & DutyLogApiSchemas.PayrollSnapshot & {
    month: string;
    periodClosed: boolean;
    integrityHealthy: boolean;
    canCalculate: boolean;
    blockingReason?: "PERIOD_OPEN" | "LEDGER_INTEGRITY_FAILED" | "PAYROLL_RATE_REQUIRED" | null;
    settings: DutyLogApiSchemas.PayrollSettings;
    preview: DutyLogApiSchemas.PayrollPreview;
    adjustments: DutyLogApiSchemas.PayrollAdjustment;
    latestSnapshot?: DutyLogApiSchemas.PayrollSnapshot | null;
    snapshots: DutyLogApiSchemas.PayrollSnapshot;
  };

  export type PayrollPreview = {
    month: string;
    currencyCode: string;
    hourlyRateMinor: number;
    plannedMinutes: number;
    workedMinutes: number;
    vacationMinutes: number;
    sickMinutes: number;
    overtimeCompensatedMinutes: number;
    unpaidMinutes: number;
    timeAdjustmentMinutes: number;
    paidAbsenceMinutes: number;
    payableMinutes: number;
    basePayMinor: number;
    additionsMinor: number;
    deductionsMinor: number;
    totalPayMinor: number;
  };

  export type PayrollSettings = DutyLogApiSchemas.PayrollSettingsInput & {
    updatedAt?: string | null;
  };

  export type PayrollSettingsInput = {
    currencyCode: string;
    hourlyRateMinor: number;
  };

  export type PayrollSnapshot = DutyLogApiSchemas.PayrollPreview & {
    id?: number;
    revision?: number;
    sourcePeriodClosedAt?: string;
    sourceIntegrityCheckedAt?: string;
    calculationHash?: string;
    createdAt?: string;
    supersededById?: number | null;
  };

  export type QuickScenario = {
    id: number;
    name: string;
    groupLabel?: string | null;
    description?: string | null;
    startMode: "SHIFT_START" | "SHIFT_END";
    endMode: "SHIFT_END" | "ADD_MINUTES" | "FIXED_TIME";
    endOffsetMinutes: number;
    endFixedTime?: string | null;
    endNextDay: boolean;
    endDayOffset: number;
    breakMode: "ZERO" | "SHIFT" | "CUSTOM";
    customBreakMinutes: number;
    plannedMode: "ZERO" | "SHIFT" | "CUSTOM";
    customPlannedHours: number;
    reasonTemplate?: string | null;
    sortOrder: number;
  };

  export type ScheduleTemplate = DutyLogApiSchemas.ScheduleTemplateStep & {
    id: number;
    name: string;
    description?: string | null;
    alignmentMode: "CYCLE_START" | "WEEKDAY";
    systemPreset: boolean;
    sortOrder: number;
    steps: DutyLogApiSchemas.ScheduleTemplateStep;
    createdAt?: string;
    updatedAt?: string;
  };

  export type ScheduleTemplateApplyRequest = {
    startDate: string;
    endDate: string;
    anchorDate?: string | null;
    overwriteExistingShift?: boolean;
  };

  export type ScheduleTemplateApplyResult = DutyLogApiSchemas.Day & {
    templateId?: number;
    from?: string;
    to?: string;
    appliedCount?: number;
    unchangedCount?: number;
    skippedCount?: number;
    conflictCount?: number;
    days?: DutyLogApiSchemas.Day;
  };

  export type ScheduleTemplateInput = {
    name: string;
    description?: string | null;
    alignmentMode?: "CYCLE_START" | "WEEKDAY";
    shiftTypeIds: Array<number>;
    sortOrder?: number;
  };

  export type ScheduleTemplatePatch = {
    name?: string;
    description?: string | null;
    alignmentMode?: "CYCLE_START" | "WEEKDAY";
    shiftTypeIds?: Array<number>;
    sortOrder?: number;
  };

  export type ScheduleTemplatePreview = DutyLogApiSchemas.ScheduleTemplatePreviewItem & {
    templateId?: number;
    templateName?: string;
    from?: string;
    to?: string;
    anchorDate?: string;
    overwriteExistingShift?: boolean;
    totalDays?: number;
    writeCount?: number;
    unchangedCount?: number;
    skippedCount?: number;
    conflictCount?: number;
    items?: DutyLogApiSchemas.ScheduleTemplatePreviewItem;
  };

  export type ScheduleTemplatePreviewItem = {
    date?: string;
    cyclePosition?: number;
    shiftTypeId?: number;
    shiftTypeName?: string;
    shiftColor?: string;
    existingShiftTypeId?: number | null;
    existingShiftTypeName?: string | null;
    action?: "APPLY" | "OVERWRITE" | "SAME" | "SKIP_CONFLICT";
  };

  export type ScheduleTemplateStep = {
    position: number;
    shiftTypeId: number;
    shiftTypeName: string;
    shiftColor: string;
    dayOff: boolean;
  };

  export type ShiftInterval = {
    startInstant: string;
    endInstant: string;
    workStart: string;
    workEnd: string;
    displayStart: string;
    displayEnd: string;
    workTimezone: string;
    displayTimezone: string;
    breakMinutes: number;
    elapsedMinutes: number;
    netMinutes: number;
    crossesWorkMidnight: boolean;
    crossesDisplayMidnight: boolean;
    sameTimezone: boolean;
    legacyLocal: boolean;
  };

  export type ShiftOccurrence = {
    dayEntryId?: number | null;
    sourceDate: string;
    shiftTypeId?: number | null;
    startInstant: string;
    endInstant: string;
    sourceStart: string;
    sourceEnd: string;
    displayStart: string;
    displayEnd: string;
    sourceTimezone: string;
    displayTimezone: string;
    breakMinutes: number;
    elapsedMinutes: number;
    netMinutes: number;
    legacyLocal: boolean;
  };

  export type Task = DutyLogApiSchemas.TaskSubtask & {
    id: number;
    date: string;
    text: string;
    description?: string | null;
    done: boolean;
    category?: string | null;
    tags: Array<string>;
    priority: "LOW" | "NORMAL" | "HIGH" | "URGENT";
    dueDate?: string | null;
    dueTime?: string | null;
    deadlineAbsolute: boolean;
    dueSourceTimezone?: string | null;
    dueSourceDate?: string | null;
    dueSourceTime?: string | null;
    reminderEnabled: boolean;
    reminderMinutesBefore?: number | null;
    overdue: boolean;
    subtasks: DutyLogApiSchemas.TaskSubtask;
  };

  export type TaskCreateRequest = DutyLogApiSchemas.TaskSubtaskInput & {
    date: string;
    text: string;
    description?: string | null;
    category?: string | null;
    tags?: Array<string>;
    priority?: "LOW" | "NORMAL" | "HIGH" | "URGENT";
    dueDate?: string | null;
    dueTime?: string | null;
    reminderEnabled?: boolean;
    reminderMinutesBefore?: number | null;
    subtasks?: DutyLogApiSchemas.TaskSubtaskInput;
  };

  export type TaskMetadata = {
    categories: Array<string>;
    tags: Array<string>;
  };

  export type TaskSubtask = {
    id: number;
    text: string;
    done: boolean;
    sortOrder: number;
    dueDate?: string | null;
  };

  export type TaskSubtaskInput = {
    id?: number | null;
    text: string;
    done?: boolean;
    sortOrder?: number;
    dueDate?: string | null;
  };

  export type TimeCompensationDay = {
    date: string;
    plannedMinutes: number;
    workedMinutes: number;
    absenceMinutes: number;
    overtimeEarnedMinutes: number;
    overtimeUsedMinutes: number;
    compensatedMinutes: number;
    vacationDays: number;
    sickMinutes: number;
    unpaidMinutes: number;
    factLabel: string;
    compensationLabel: string;
    absenceIds: Array<number>;
    actualSource?: "PLAN_DERIVED" | "EXPLICIT";
    actualWorkIntervalIds?: Array<number>;
  };

  export type TimeCompensationSummary = DutyLogApiSchemas.TimeCompensationDay & {
    from: string;
    to: string;
    plannedMinutes: number;
    workedMinutes: number;
    absenceMinutes: number;
    overtimeEarnedMinutes: number;
    overtimeUsedMinutes: number;
    overtimeBalanceMinutes: number;
    compensatedMinutes: number;
    vacationDays: number;
    sickMinutes: number;
    unpaidMinutes: number;
    overtimeReservedMinutes?: number;
    overtimePostedMinutes?: number;
    integrityHealthy?: boolean;
    periodClosed?: boolean;
    days: DutyLogApiSchemas.TimeCompensationDay;
  };

  export type TimeContext = {
    nowInstant: string;
    workTimezone: string;
    displayTimezone: string;
    workLocalDateTime: string;
    displayLocalDateTime: string;
    workDate: string;
    displayDate: string;
    workOffset: string;
    displayOffset: string;
    sameTimezone: true;
  };

  export type TimeLedgerEntry = {
    id: number;
    entryKind: string;
    sourceKind: string;
    sourceId?: number | null;
    effectiveDate: string;
    signedMinutes: number;
    postingState: "RESERVED" | "POSTED" | "REVERSED";
    reversalOfId?: number | null;
    reason?: string | null;
    createdAt: string;
  };

  export type VacationPlanner = DutyLogApiSchemas.VacationSettings & DutyLogApiSchemas.VacationSummary & DutyLogApiSchemas.AbsenceType & DutyLogApiSchemas.AbsencePeriod & DutyLogApiSchemas.AbsenceOccurrence & {
    settings: DutyLogApiSchemas.VacationSettings;
    summary: DutyLogApiSchemas.VacationSummary;
    durationPresets: Array<14 | 28 | 35>;
    types: DutyLogApiSchemas.AbsenceType;
    absences: DutyLogApiSchemas.AbsencePeriod;
    occurrences: DutyLogApiSchemas.AbsenceOccurrence;
    typeSummaries: Array<unknown>;
    type?: Record<string, unknown>;
    properties?: unknown;
  };

  export type VacationSettings = DutyLogApiSchemas.VacationSettingsInput & {
    updatedAt?: string;
  };

  export type VacationSettingsInput = {
    annualAllowanceDays?: number;
    carryoverDays?: number;
    countMode?: "CALENDAR_DAYS" | "WEEKDAYS";
    workYearStartMonth?: number;
    workYearStartDay?: number;
    timeOffBalanceHours?: number;
    defaultTimeOffDayHours?: number;
  };

  export type VacationSummary = {
    workYearStart: string;
    workYearEnd: string;
    annualAllowanceDays?: number;
    carryoverDays?: number;
    availableDays: number;
    plannedDays: number;
    remainingDays: number;
    countMode: "CALENDAR_DAYS" | "WEEKDAYS";
    timeOffAvailableMinutes?: number;
    timeOffPlannedMinutes?: number;
    timeOffRemainingMinutes?: number;
  };
}

export const dutyLogOperations = {
  "addClosedPeriodAdjustment": { method: "POST", path: "/api/v1/ledger-integrity/adjustments" },
  "addPayrollAdjustment": { method: "POST", path: "/api/v1/payroll/adjustments" },
  "applyScheduleTemplate": { method: "POST", path: "/api/v1/schedule-templates/{id}/apply" },
  "calculatePayrollRevision": { method: "POST", path: "/api/v1/payroll/periods/{month}/calculate" },
  "calendarRange": { method: "GET", path: "/api/v1/calendar" },
  "captureInboxItem": { method: "POST", path: "/api/v1/inbox" },
  "closeAccountingPeriod": { method: "POST", path: "/api/v1/ledger-integrity/periods/{month}/close" },
  "convertInboxItemToTask": { method: "POST", path: "/api/v1/inbox/{id}/task" },
  "createAbsencePeriod": { method: "POST", path: "/api/v1/vacation-planner/absences" },
  "createAbsenceType": { method: "POST", path: "/api/v1/vacation-planner/types" },
  "createActualWorkInterval": { method: "POST", path: "/api/v1/actual-work" },
  "createCalendarLayer": { method: "POST", path: "/api/v1/calendar-layers" },
  "createDayNote": { method: "POST", path: "/api/v1/notes" },
  "createImportantDay": { method: "POST", path: "/api/v1/important-days" },
  "createScheduleTemplate": { method: "POST", path: "/api/v1/schedule-templates" },
  "createTask": { method: "POST", path: "/api/v1/tasks" },
  "deleteAbsencePeriod": { method: "DELETE", path: "/api/v1/vacation-planner/absences/{id}" },
  "deleteAbsenceType": { method: "DELETE", path: "/api/v1/vacation-planner/types/{id}" },
  "deleteActualWorkInterval": { method: "DELETE", path: "/api/v1/actual-work/{id}" },
  "deleteCalendarLayer": { method: "DELETE", path: "/api/v1/calendar-layers/{id}" },
  "deleteDayNote": { method: "DELETE", path: "/api/v1/notes/{id}" },
  "deleteInboxItem": { method: "DELETE", path: "/api/v1/inbox/{id}" },
  "deleteLegacyManualOvertimeUsage": { method: "DELETE", path: "/api/v1/overtime/usages/{id}" },
  "deleteScheduleTemplate": { method: "DELETE", path: "/api/v1/schedule-templates/{id}" },
  "deleteTask": { method: "DELETE", path: "/api/v1/tasks/{taskId}" },
  "exportCalendarRange": { method: "GET", path: "/api/v1/calendar-sync/export" },
  "exportImportantEventIcs": { method: "GET", path: "/api/v1/calendar-sync/events/{id}.ics" },
  "exportNotes": { method: "GET", path: "/api/v1/export/notes" },
  "getCalendarSyncStatus": { method: "GET", path: "/api/v1/calendar-sync/status" },
  "getTaskDetails": { method: "GET", path: "/api/v1/tasks/{taskId}" },
  "getTimeContext": { method: "GET", path: "/api/v1/time/context" },
  "getVacationPlanner": { method: "GET", path: "/api/v1/vacation-planner" },
  "inspectLedgerIntegrity": { method: "GET", path: "/api/v1/ledger-integrity" },
  "listAbsenceTypes": { method: "GET", path: "/api/v1/vacation-planner/types" },
  "listActualWorkIntervals": { method: "GET", path: "/api/v1/actual-work" },
  "listCalendarLayers": { method: "GET", path: "/api/v1/calendar-layers" },
  "listDayNotes": { method: "GET", path: "/api/v1/notes" },
  "listImportantDayOccurrences": { method: "GET", path: "/api/v1/important-days/occurrences" },
  "listImportantDays": { method: "GET", path: "/api/v1/important-days" },
  "listInbox": { method: "GET", path: "/api/v1/inbox" },
  "listMobileSessions": { method: "GET", path: "/api/v1/mobile/auth/sessions" },
  "listModules": { method: "GET", path: "/api/v1/modules" },
  "listScheduleTemplates": { method: "GET", path: "/api/v1/schedule-templates" },
  "listTasks": { method: "GET", path: "/api/v1/tasks" },
  "loginMobile": { method: "POST", path: "/api/v1/mobile/auth/login" },
  "logoutMobile": { method: "POST", path: "/api/v1/mobile/auth/logout" },
  "migrateLegacyOvertimeCredits": { method: "POST", path: "/api/v1/overtime/legacy-credits/migrate" },
  "migrateLegacyShifts": { method: "POST", path: "/api/v1/shifts/legacy-migration" },
  "migrateLegacyTaskDeadlines": { method: "POST", path: "/api/v1/tasks/legacy-deadline-migration" },
  "mobileBootstrap": { method: "GET", path: "/api/v1/mobile/bootstrap" },
  "mobileMe": { method: "GET", path: "/api/v1/mobile/auth/me" },
  "moveDayNote": { method: "POST", path: "/api/v1/notes/{id}/move" },
  "overtimeAccount": { method: "GET", path: "/api/v1/overtime/account" },
  "overtimeLedger": { method: "GET", path: "/api/v1/overtime/ledger" },
  "overtimeSummary": { method: "GET", path: "/api/v1/overtime/summary" },
  "payrollPeriod": { method: "GET", path: "/api/v1/payroll/periods/{month}" },
  "previewAbsence": { method: "POST", path: "/api/v1/vacation-planner/preview" },
  "previewLegacyOvertimeMigration": { method: "POST", path: "/api/v1/overtime/legacy-credits/preview" },
  "previewLegacyOvertimeUsagePromotion": { method: "POST", path: "/api/v1/overtime/legacy-usages/preview" },
  "previewLegacyShiftMigration": { method: "GET", path: "/api/v1/shifts/legacy-migration/preview" },
  "previewLegacyTaskDeadlineMigration": { method: "GET", path: "/api/v1/tasks/legacy-deadline-migration/preview" },
  "previewOvertimeCredit": { method: "POST", path: "/api/v1/overtime/preview" },
  "previewScheduleTemplate": { method: "POST", path: "/api/v1/schedule-templates/{id}/preview" },
  "promoteLegacyOvertimeUsages": { method: "POST", path: "/api/v1/overtime/legacy-usages/migrate" },
  "quickScenarios": { method: "GET", path: "/api/v1/quick-scenarios" },
  "readPrivateCalendarFeed": { method: "GET", path: "/api/v1/calendar-sync/events/{id}.ics" },
  "refreshMobileToken": { method: "POST", path: "/api/v1/mobile/auth/refresh" },
  "registerMobileUser": { method: "POST", path: "/api/v1/mobile/auth/register" },
  "registrationStatus": { method: "GET", path: "/api/v1/mobile/auth/registration-status" },
  "reopenAccountingPeriod": { method: "POST", path: "/api/v1/ledger-integrity/periods/{month}/reopen" },
  "retiredCreateManualOvertimeUsage": { method: "POST", path: "/api/v1/overtime/usages" },
  "retiredUpdateManualOvertimeUsage": { method: "PATCH", path: "/api/v1/overtime/usages/{id}" },
  "revokeCalendarSubscription": { method: "DELETE", path: "/api/v1/calendar-sync/subscription" },
  "revokeMobileSession": { method: "DELETE", path: "/api/v1/mobile/auth/sessions/{id}" },
  "rotateCalendarSubscription": { method: "POST", path: "/api/v1/calendar-sync/subscription" },
  "searchDayNotes": { method: "GET", path: "/api/v1/notes/search" },
  "syncMobileQueue": { method: "POST", path: "/api/v1/mobile/sync" },
  "taskMetadata": { method: "GET", path: "/api/v1/tasks/metadata" },
  "timeCompensationSummary": { method: "GET", path: "/api/v1/time-compensation" },
  "updateAbsencePeriod": { method: "PATCH", path: "/api/v1/vacation-planner/absences/{id}" },
  "updateAbsenceType": { method: "PATCH", path: "/api/v1/vacation-planner/types/{id}" },
  "updateActualWorkInterval": { method: "PUT", path: "/api/v1/actual-work/{id}" },
  "updateCalendarLayer": { method: "PATCH", path: "/api/v1/calendar-layers/{id}" },
  "updateDayNote": { method: "PATCH", path: "/api/v1/notes/{id}" },
  "updateInboxItem": { method: "PATCH", path: "/api/v1/inbox/{id}" },
  "updateModules": { method: "PATCH", path: "/api/v1/modules" },
  "updatePayrollSettings": { method: "PATCH", path: "/api/v1/payroll/settings" },
  "updateScheduleTemplate": { method: "PATCH", path: "/api/v1/schedule-templates/{id}" },
  "updateSubtask": { method: "PATCH", path: "/api/v1/tasks/{taskId}/subtasks/{subtaskId}" },
  "updateTask": { method: "PATCH", path: "/api/v1/tasks/{taskId}" },
  "updateVacationSettings": { method: "PATCH", path: "/api/v1/vacation-planner/settings" },
  "upsertDay": { method: "PUT", path: "/api/v1/days/{date}" },
} as const;

export type DutyLogOperationId = keyof typeof dutyLogOperations;

export interface DutyLogOperationTypes {
  "addClosedPeriodAdjustment": {
    requestBody: DutyLogApiSchemas.LedgerAdjustmentInput;
    response: unknown;
  };
  "addPayrollAdjustment": {
    requestBody: DutyLogApiSchemas.PayrollAdjustmentInput;
    response: unknown;
  };
  "applyScheduleTemplate": {
    requestBody: DutyLogApiSchemas.ScheduleTemplateApplyRequest;
    response: DutyLogApiSchemas.ScheduleTemplateApplyResult;
  };
  "calculatePayrollRevision": {
    requestBody: undefined;
    response: unknown;
  };
  "calendarRange": {
    requestBody: undefined;
    response: unknown;
  };
  "captureInboxItem": {
    requestBody: DutyLogApiSchemas.InboxCreateRequest;
    response: DutyLogApiSchemas.InboxItem;
  };
  "closeAccountingPeriod": {
    requestBody: undefined;
    response: unknown;
  };
  "convertInboxItemToTask": {
    requestBody: DutyLogApiSchemas.InboxToTaskRequest;
    response: DutyLogApiSchemas.InboxConversion;
  };
  "createAbsencePeriod": {
    requestBody: DutyLogApiSchemas.AbsencePeriodInput;
    response: DutyLogApiSchemas.AbsencePeriod;
  };
  "createAbsenceType": {
    requestBody: DutyLogApiSchemas.AbsenceTypeInput;
    response: unknown;
  };
  "createActualWorkInterval": {
    requestBody: DutyLogApiSchemas.ActualWorkIntervalInput;
    response: unknown;
  };
  "createCalendarLayer": {
    requestBody: DutyLogApiSchemas.CalendarLayerInput;
    response: DutyLogApiSchemas.CalendarLayer;
  };
  "createDayNote": {
    requestBody: DutyLogApiSchemas.DayNoteCreateRequest;
    response: DutyLogApiSchemas.DayNote;
  };
  "createImportantDay": {
    requestBody: DutyLogApiSchemas.ImportantEventInput;
    response: DutyLogApiSchemas.ImportantEvent;
  };
  "createScheduleTemplate": {
    requestBody: DutyLogApiSchemas.ScheduleTemplateInput;
    response: DutyLogApiSchemas.ScheduleTemplate;
  };
  "createTask": {
    requestBody: DutyLogApiSchemas.TaskCreateRequest;
    response: DutyLogApiSchemas.Task;
  };
  "deleteAbsencePeriod": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteAbsenceType": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteActualWorkInterval": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteCalendarLayer": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteDayNote": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteInboxItem": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteLegacyManualOvertimeUsage": {
    requestBody: undefined;
    response: unknown;
  };
  "deleteScheduleTemplate": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteTask": {
    requestBody: undefined;
    response: undefined;
  };
  "exportCalendarRange": {
    requestBody: undefined;
    response: string;
  };
  "exportImportantEventIcs": {
    requestBody: undefined;
    response: string;
  };
  "exportNotes": {
    requestBody: undefined;
    response: string;
  };
  "getCalendarSyncStatus": {
    requestBody: undefined;
    response: DutyLogApiSchemas.CalendarSyncStatus;
  };
  "getTaskDetails": {
    requestBody: undefined;
    response: DutyLogApiSchemas.Task;
  };
  "getTimeContext": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TimeContext;
  };
  "getVacationPlanner": {
    requestBody: undefined;
    response: DutyLogApiSchemas.VacationPlanner;
  };
  "inspectLedgerIntegrity": {
    requestBody: undefined;
    response: DutyLogApiSchemas.LedgerIntegrity;
  };
  "listAbsenceTypes": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.AbsenceType>;
  };
  "listActualWorkIntervals": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.ActualWorkInterval>;
  };
  "listCalendarLayers": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.CalendarLayer>;
  };
  "listDayNotes": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.DayNote>;
  };
  "listImportantDayOccurrences": {
    requestBody: undefined;
    response: unknown;
  };
  "listImportantDays": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.ImportantEvent>;
  };
  "listInbox": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.InboxItem>;
  };
  "listMobileSessions": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.MobileSession>;
  };
  "listModules": {
    requestBody: undefined;
    response: unknown;
  };
  "listScheduleTemplates": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.ScheduleTemplate>;
  };
  "listTasks": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.Task>;
  };
  "loginMobile": {
    requestBody: DutyLogApiSchemas.MobileLoginRequest;
    response: DutyLogApiSchemas.MobileTokenResponse;
  };
  "logoutMobile": {
    requestBody: {
      refreshToken?: string | null;
    };
    response: undefined;
  };
  "migrateLegacyOvertimeCredits": {
    requestBody: DutyLogApiSchemas.LegacyOvertimeMigrationRequest;
    response: DutyLogApiSchemas.LegacyOvertimeMigrationResult;
  };
  "migrateLegacyShifts": {
    requestBody: DutyLogApiSchemas.LegacyShiftMigrationRequest;
    response: DutyLogApiSchemas.LegacyShiftMigrationPreview;
  };
  "migrateLegacyTaskDeadlines": {
    requestBody: DutyLogApiSchemas.LegacyTaskDeadlineMigrationRequest;
    response: DutyLogApiSchemas.LegacyTaskDeadlineMigrationPreview;
  };
  "mobileBootstrap": {
    requestBody: undefined;
    response: DutyLogApiSchemas.MobileBootstrap;
  };
  "mobileMe": {
    requestBody: undefined;
    response: DutyLogApiSchemas.MobileUser;
  };
  "moveDayNote": {
    requestBody: DutyLogApiSchemas.DayNoteMoveRequest;
    response: Array<DutyLogApiSchemas.DayNote>;
  };
  "overtimeAccount": {
    requestBody: undefined;
    response: DutyLogApiSchemas.OvertimeAccount;
  };
  "overtimeLedger": {
    requestBody: undefined;
    response: unknown;
  };
  "overtimeSummary": {
    requestBody: undefined;
    response: unknown;
  };
  "payrollPeriod": {
    requestBody: undefined;
    response: DutyLogApiSchemas.PayrollPeriod;
  };
  "previewAbsence": {
    requestBody: DutyLogApiSchemas.AbsencePreviewInput;
    response: DutyLogApiSchemas.AbsencePreview;
  };
  "previewLegacyOvertimeMigration": {
    requestBody: DutyLogApiSchemas.LegacyOvertimeMigrationRequest;
    response: DutyLogApiSchemas.LegacyOvertimeMigrationPreview;
  };
  "previewLegacyOvertimeUsagePromotion": {
    requestBody: DutyLogApiSchemas.LegacyOvertimeUsageMigrationRequest;
    response: DutyLogApiSchemas.LegacyOvertimeUsageMigrationPreview;
  };
  "previewLegacyShiftMigration": {
    requestBody: undefined;
    response: DutyLogApiSchemas.LegacyShiftMigrationPreview;
  };
  "previewLegacyTaskDeadlineMigration": {
    requestBody: undefined;
    response: DutyLogApiSchemas.LegacyTaskDeadlineMigrationPreview;
  };
  "previewOvertimeCredit": {
    requestBody: DutyLogApiSchemas.OvertimeCreditCreateRequest;
    response: DutyLogApiSchemas.OvertimeCreditPreview;
  };
  "previewScheduleTemplate": {
    requestBody: DutyLogApiSchemas.ScheduleTemplateApplyRequest;
    response: DutyLogApiSchemas.ScheduleTemplatePreview;
  };
  "promoteLegacyOvertimeUsages": {
    requestBody: DutyLogApiSchemas.LegacyOvertimeUsageMigrationRequest;
    response: DutyLogApiSchemas.LegacyOvertimeUsageMigrationResult;
  };
  "quickScenarios": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.QuickScenario>;
  };
  "readPrivateCalendarFeed": {
    requestBody: undefined;
    response: string;
  };
  "refreshMobileToken": {
    requestBody: {
      refreshToken: string;
    };
    response: DutyLogApiSchemas.MobileTokenResponse;
  };
  "registerMobileUser": {
    requestBody: DutyLogApiSchemas.MobileRegisterRequest;
    response: DutyLogApiSchemas.MobileTokenResponse;
  };
  "registrationStatus": {
    requestBody: undefined;
    response: Record<string, unknown>;
  };
  "reopenAccountingPeriod": {
    requestBody: undefined;
    response: unknown;
  };
  "retiredCreateManualOvertimeUsage": {
    requestBody: undefined;
    response: unknown;
  };
  "retiredUpdateManualOvertimeUsage": {
    requestBody: undefined;
    response: unknown;
  };
  "revokeCalendarSubscription": {
    requestBody: undefined;
    response: undefined;
  };
  "revokeMobileSession": {
    requestBody: undefined;
    response: undefined;
  };
  "rotateCalendarSubscription": {
    requestBody: undefined;
    response: DutyLogApiSchemas.CalendarSubscription;
  };
  "searchDayNotes": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.DayNote>;
  };
  "syncMobileQueue": {
    requestBody: DutyLogApiSchemas.MobileSyncRequest;
    response: DutyLogApiSchemas.MobileSyncResponse;
  };
  "taskMetadata": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TaskMetadata;
  };
  "timeCompensationSummary": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TimeCompensationSummary;
  };
  "updateAbsencePeriod": {
    requestBody: DutyLogApiSchemas.AbsencePeriodPatch;
    response: unknown;
  };
  "updateAbsenceType": {
    requestBody: DutyLogApiSchemas.AbsenceTypePatch;
    response: unknown;
  };
  "updateActualWorkInterval": {
    requestBody: DutyLogApiSchemas.ActualWorkIntervalInput;
    response: unknown;
  };
  "updateCalendarLayer": {
    requestBody: DutyLogApiSchemas.CalendarLayerPatch;
    response: unknown;
  };
  "updateDayNote": {
    requestBody: DutyLogApiSchemas.DayNoteUpdateRequest;
    response: DutyLogApiSchemas.DayNote;
  };
  "updateInboxItem": {
    requestBody: DutyLogApiSchemas.InboxUpdateRequest;
    response: unknown;
  };
  "updateModules": {
    requestBody: undefined;
    response: unknown;
  };
  "updatePayrollSettings": {
    requestBody: DutyLogApiSchemas.PayrollSettingsInput;
    response: unknown;
  };
  "updateScheduleTemplate": {
    requestBody: DutyLogApiSchemas.ScheduleTemplatePatch;
    response: unknown;
  };
  "updateSubtask": {
    requestBody: {
      done: boolean;
    };
    response: DutyLogApiSchemas.Task;
  };
  "updateTask": {
    requestBody: undefined;
    response: unknown;
  };
  "updateVacationSettings": {
    requestBody: DutyLogApiSchemas.VacationSettingsInput;
    response: unknown;
  };
  "upsertDay": {
    requestBody: undefined;
    response: unknown;
  };
}

export type DutyLogOperationRequest<T extends DutyLogOperationId> = DutyLogOperationTypes[T]["requestBody"];
export type DutyLogOperationResponse<T extends DutyLogOperationId> = DutyLogOperationTypes[T]["response"];
