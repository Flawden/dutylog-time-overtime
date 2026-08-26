/* eslint-disable */
/**
 * GENERATED FILE — DO NOT EDIT.
 * Source: src/main/resources/static/openapi/dutylog-v1.yaml
 * SHA-256: 447d43d98cfb27f405a1784a088b842a427886fc2992002772a07bf3991f65ef
 * Generator: frontend/scripts/generate-openapi-contract.mjs
 * Contract: 145 operations, 151 schemas
 */

export const DUTYLOG_OPENAPI_SOURCE_SHA256 = "447d43d98cfb27f405a1784a088b842a427886fc2992002772a07bf3991f65ef";

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
    id: number;
    typeName: string;
    typeColor: string;
    systemCode?: string | null;
    countsAgainstAllowance?: boolean;
    calendarDays: number;
    countedDays: number;
    shiftConflictCount: number;
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

  export type AbsencePeriodPatch = {
    typeId?: number;
    title?: string | null;
    startDate?: string;
    endDate?: string;
    status?: "DRAFT" | "PLANNED" | "SUBMITTED" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED";
    note?: string | null;
    clearTitle?: boolean;
    clearNote?: boolean;
    coverage?: "FULL_DAY" | "PARTIAL" | "HOURS_ONLY";
    startTime?: string | null;
    endTime?: string | null;
    clearTimes?: boolean;
    compensationPolicy?: "VACATION_ALLOWANCE" | "OVERTIME_BANK" | "SICK_PAY" | "UNPAID" | "NONE";
  };

  export type AbsencePreview = {
    typeId: number;
    typeName: string;
    startDate: string;
    endDate: string;
    calendarDays: number;
    countedDays: number;
    shiftConflictCount: number;
    absenceConflictCount: number;
    workYearStart: string;
    workYearEnd: string;
    availableDays: number;
    plannedBefore: number;
    projectedPlanned: number;
    remainingAfter: number;
    exceedsAllowance: boolean;
    exceededBy: number;
    items: Array<DutyLogApiSchemas.AbsencePreviewItem>;
    balancePolicy: "VACATION_DAYS" | "TIME_OFF_HOURS" | "NONE";
    coverage: "FULL_DAY" | "PARTIAL" | "HOURS_ONLY";
    durationMinutes: number;
    timeOffAvailableMinutes: number;
    timeOffPlannedBefore: number;
    timeOffProjected: number;
    timeOffRemainingAfter: number;
    compensationPolicy: "VACATION_ALLOWANCE" | "OVERTIME_BANK" | "SICK_PAY" | "UNPAID" | "NONE";
  };

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

  export type AbsencePreviewItem = {
    date: string;
    weekend: boolean;
    counted: boolean;
    shiftConflict: boolean;
    existingAbsenceId?: number | null;
    existingAbsenceTitle?: string | null;
    action: string;
    plannedShiftName?: string | null;
    plannedShiftColor?: string | null;
    plannedShiftMinutes?: number;
    replacesShift: boolean;
  };

  export type AbsenceType = DutyLogApiSchemas.AbsenceTypeInput & {
    id: number;
    systemPreset: boolean;
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

  export type AbsenceTypeSummary = {
    typeId?: number;
    typeName?: string;
    typeColor?: string;
    systemCode?: string | null;
    balancePolicy?: "VACATION_DAYS" | "TIME_OFF_HOURS" | "NONE";
    fullDays?: number;
    partialMinutes?: number;
    chargedMinutes?: number;
  };

  export type AccountingPeriod = {
    month: string;
    status: "OPEN" | "CLOSED";
    closedAt?: string | null;
    updatedAt?: string | null;
  };

  export type ActualWorkInterval = DutyLogApiSchemas.ActualWorkIntervalInput & {
    id: number;
    workedMinutes: number;
    breakMinutes: number;
    createdAt?: string | null;
    updatedAt?: string | null;
    sourceTimezone?: string | null;
    startInstant?: string | null;
    endInstant?: string | null;
    identityReconstructed: boolean;
  };

  export type ActualWorkIntervalInput = {
    workDate: string;
    endDate?: string | null;
    startTime: string;
    endTime: string;
    breakMinutes?: number | null;
    note?: string | null;
  };

  export type AdminDatabaseStatus = {
    ok: boolean;
    error?: string | null;
  };

  export type AdminRegistrationSettings = {
    enabled: boolean;
    mode: "open" | "closed";
    source: "database" | "default";
    updatedAt?: string | null;
    updatedBy?: string | null;
  };

  export type AdminRegistrationSettingsRequest = {
    enabled: boolean;
  };

  export type AdminSystemStatus = {
    app: string;
    version: string;
    admin: string;
    serverTime: string;
    serverTimezone: string;
    profiles: Array<string>;
    database: DutyLogApiSchemas.AdminDatabaseStatus;
    users: DutyLogApiSchemas.AdminUserManagementStatus;
    registration: DutyLogApiSchemas.AdminRegistrationSettings;
    telegram: DutyLogApiSchemas.AdminTelegramStatus;
  };

  export type AdminTelegramStatus = {
    enabled: boolean;
    tokenConfigured: boolean;
    pollingEnabled: boolean;
    notificationsEnabled: boolean;
    configured?: boolean;
    linked: boolean;
    accountNotificationsEnabled?: boolean;
    botUsername?: string | null;
  };

  export type AdminUser = {
    id: number;
    username: string;
    displayName: string;
    role: "USER" | "ADMIN";
    accountTier: string;
    bootstrapAdmin: boolean;
    currentUser: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
  };

  export type AdminUserManagementStatus = {
    total: number;
    admins: number;
    rolesAllowed: Array<string>;
    accountTiersReserved: Array<string>;
  };

  export type AdminUserPage = {
    items: Array<DutyLogApiSchemas.AdminUser>;
    page: number;
    size: number;
    total: number;
    totalPages: number;
    hasPrevious: boolean;
    hasNext: boolean;
  };

  export type AdminUserPasswordResetRequest = {
    newPassword: string;
  };

  export type AdminUserRoleRequest = {
    role: "USER" | "ADMIN";
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

  export type CalendarLayer = {
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
    scheduleEditable: true;
    entries: Array<DutyLogApiSchemas.CalendarLayerEntry>;
  };

  export type CalendarLayerEntry = {
    layerId?: number;
    layerName?: string;
    layerColor?: string;
    sourceDate?: string;
    date?: string;
    shiftTypeId?: number | null;
    shiftTypeName?: string | null;
    shiftColor?: string | null;
    sourceTimezone?: string;
    startInstant?: string | null;
    endInstant?: string | null;
    displayStart?: string | null;
    displayEnd?: string | null;
    timed?: boolean;
    dayOff?: boolean;
    sourceStartTime?: string | null;
    sourceEndTime?: string | null;
    plannedShiftTypeId?: number | null;
    plannedShiftTypeName?: string | null;
    overrideKind?: "WORK" | "OFF" | null;
    overrideReason?: "TIME_OFF" | "VACATION" | "SICK" | "OTHER" | null;
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

  export type CalendarLayerOverride = {
    id: number;
    layerId: number;
    sourceDate: string;
    kind: "WORK" | "OFF";
    reason?: "TIME_OFF" | "VACATION" | "SICK" | "OTHER" | null;
    shiftTypeId?: number | null;
    shiftTypeName?: string | null;
    startTime?: string | null;
    endTime?: string | null;
  };

  export type CalendarLayerOverrideInput = {
    kind: "WORK" | "OFF";
    reason?: "TIME_OFF" | "VACATION" | "SICK" | "OTHER" | null;
    shiftTypeId?: number | null;
    startTime?: string | null;
    endTime?: string | null;
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
    subscriptionUrl: string;
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

  export type Day = {
    date: string;
    shiftTypeId?: number | null;
    note?: string | null;
    notes?: Array<DutyLogApiSchemas.DayNote>;
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
    id: number;
    startInstant: string | null;
    endInstant: string | null;
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

  export type ImportantEventOccurrence = {
    id: number;
    date: string;
    title: string;
    repeatMode: "NONE" | "MONTHLY" | "YEARLY";
    color: string;
    eventType: "IMPORTANT_DATE" | "EVENT" | "PERIOD";
    startDate: string;
    endDate?: string | null;
    allDay: boolean;
    startTime?: string | null;
    endTime?: string | null;
    startInstant?: string | null;
    endInstant?: string | null;
    sourceTimezone?: string | null;
    displayTimezone?: string | null;
    place?: string | null;
    description?: string | null;
    icon?: string | null;
    category?: string | null;
    reminders: Array<number>;
  };

  export type InboxConversion = {
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

  export type InboxToTaskRequest = {
    date: string;
    description?: string | null;
    category?: string | null;
    tags?: Array<string>;
    priority?: "LOW" | "NORMAL" | "HIGH" | "URGENT";
    dueDate?: string | null;
    dueTime?: string | null;
    reminderEnabled?: boolean;
    reminderMinutesBefore?: number | null;
    subtasks?: Array<DutyLogApiSchemas.TaskSubtaskInput>;
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

  export type LedgerIntegrity = {
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

  export type LegacyOvertimeMigrationPreview = {
    sourceTimezone: string;
    requestedCount: number;
    migratableCount: number;
    blockedCount: number;
    credits: Array<DutyLogApiSchemas.LegacyOvertimeCredit>;
  };

  export type LegacyOvertimeMigrationRequest = {
    creditIds?: Array<number>;
    sourceTimezone?: string;
  };

  export type LegacyOvertimeMigrationResult = {
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

  export type LegacyOvertimeUsageMigrationPreview = {
    totalCount: number;
    fullDayCount: number;
    hoursOnlyCount: number;
    blockedCount: number;
    usages: Array<DutyLogApiSchemas.LegacyOvertimeUsageMigrationItem>;
  };

  export type LegacyOvertimeUsageMigrationRequest = {
    usageIds?: Array<number>;
  };

  export type LegacyOvertimeUsageMigrationResult = {
    migratedCount: number;
    skippedCount: number;
    absenceIds: Array<number>;
  };

  export type LegacyShiftMigrationPreview = {
    sourceTimezone: string;
    legacyCount: number;
    occurrences: Array<DutyLogApiSchemas.LegacyShiftOccurrence>;
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

  export type LegacyTaskDeadlineMigrationPreview = {
    sourceTimezone: string;
    targetTimezone: string;
    legacyCount: number;
    tasks: Array<DutyLogApiSchemas.LegacyTaskDeadline>;
  };

  export type LegacyTaskDeadlineMigrationRequest = {
    sourceTimezone: string;
    taskIds: Array<number>;
  };

  export type MobileBootstrap = {
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

  export type MobileSyncItemResult = {
    operationId: string;
    status: "APPLIED" | "ALREADY_APPLIED" | "CONFLICT" | "REJECTED";
    entityType: "day";
    entityId: string;
    serverVersion?: number | null;
    entity?: DutyLogApiSchemas.Day;
    errorCode?: "VERSION_CONFLICT" | "NO_CHANGES" | "MODULE_DISABLED" | "NOT_FOUND" | "VALIDATION_FAILED" | "REJECTED" | null;
    message?: string | null;
  };

  export type MobileSyncOperation = {
    operationId: string;
    baseVersion: number;
    day: DutyLogApiSchemas.MobileDayPatch;
  };

  export type MobileSyncRequest = {
    operations: Array<DutyLogApiSchemas.MobileSyncOperation>;
  };

  export type MobileSyncResponse = {
    apiVersion: "v1";
    serverTime: string;
    items: Array<DutyLogApiSchemas.MobileSyncItemResult>;
  };

  export type MobileTokenResponse = {
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

  export type Module = {
    key: string;
    titleRu: string;
    titleEn: string;
    descriptionRu: string;
    descriptionEn: string;
    enabled: boolean;
    locked: boolean;
    defaultEnabled: boolean;
    dependencies: Array<string>;
    hidden: boolean;
    category: string;
    order: number;
    uiSlots: Array<string>;
    apiPrefixes: Array<string>;
    offlineQueueTypes: Array<string>;
  };

  export type ModuleSettingsUpdateRequest = {
    enabled: Record<string, boolean>;
  };

  export type NotificationReminder = {
    id: string;
    type: string;
    sourceDate: string;
    remindAt: string;
    title: string;
    details?: string | null;
    priority: number;
    remindAtInstant?: string | null;
    workTimezone?: string | null;
    displayAt?: string | null;
    displayTimezone?: string | null;
  };

  export type NotificationSettings = {
    browserNotificationsEnabled: boolean;
    shiftRemindersEnabled: boolean;
    shiftReminderMinutesBefore: number;
    tomorrowDigestEnabled: boolean;
    tomorrowDigestTime: string;
    taskRemindersEnabled: boolean;
    taskReminderTime: string;
    importantDayRemindersEnabled: boolean;
    importantDayDaysBefore: number;
    importantDayReminderTime: string;
    updatedAt: string;
  };

  export type NotificationSettingsUpdateRequest = {
    browserNotificationsEnabled?: boolean;
    shiftRemindersEnabled?: boolean;
    shiftReminderMinutesBefore?: number;
    tomorrowDigestEnabled?: boolean;
    tomorrowDigestTime?: string;
    taskRemindersEnabled?: boolean;
    taskReminderTime?: string;
    importantDayRemindersEnabled?: boolean;
    importantDayDaysBefore?: number;
    importantDayReminderTime?: string;
  };

  export type OvertimeAccount = {
    totalEarnedHours: number;
    totalUsedHours: number;
    balanceHours: number;
    credits: Array<DutyLogApiSchemas.OvertimeCredit>;
    usages: Array<DutyLogApiSchemas.OvertimeUsage>;
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

  export type OvertimeCredit = {
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
    usages: Array<DutyLogApiSchemas.OvertimeUsageRef>;
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
    sourceKind: "MANUAL" | "SYSTEM_ACTUAL_WORK";
    editable: boolean;
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

  export type OvertimeCreditUpdateRequest = {
    date?: string | null;
    timeRange?: string | null;
    hours?: number | null;
    reason?: string | null;
    startDateTime?: string | null;
    endDateTime?: string | null;
    breakMinutes?: number | null;
    plannedHours?: number | null;
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

  export type OvertimeSettlement = {
    id: number;
    settlementDate: string;
    minutes: number;
    hours: number;
    reason?: string | null;
    createdAt: string;
    updatedAt: string;
  };

  export type OvertimeSettlementUpsertRequest = {
    settlementDate: string;
    minutes: number;
    reason?: string | null;
  };

  export type OvertimeUsage = {
    id: number;
    usageDate: string;
    hours: number;
    minutes: number;
    reason?: string | null;
    sourceKind: "MANUAL" | "ABSENCE" | "SETTLEMENT";
    sourceAbsenceId?: number | null;
    sourceSettlementId?: number | null;
    editable: boolean;
    postingState: "RESERVED" | "POSTED";
    reserved: boolean;
    allocations: Array<DutyLogApiSchemas.OvertimeAllocation>;
  };

  export type OvertimeUsageRef = {
    usageId: number;
    usageDate: string;
    hours: number;
    minutes: number;
    reason?: string | null;
    sourceKind: "MANUAL" | "ABSENCE" | "SETTLEMENT";
    sourceAbsenceId?: number | null;
    sourceSettlementId?: number | null;
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

  export type PasswordChangeRequest = {
    currentPassword: string;
    newPassword: string;
  };

  export type PayPricingRule = {
    code: string;
    dimension: "NIGHT" | "HOLIDAY" | "OVERTIME";
    premiumBps: number;
    fromMinute: number;
    toMinuteExclusive?: number | null;
    exclusiveGroup?: string | null;
  };

  export type PayPricingTerm = DutyLogApiSchemas.PayPricingTermInput & {
    id: number;
    effectiveFrom: string;
    updatedAt: string;
  };

  export type PayPricingTermInput = {
    rules: Array<DutyLogApiSchemas.PayPricingRule>;
  };

  export type PayrollAdjustment = DutyLogApiSchemas.PayrollAdjustmentInput & {
    id: number;
    createdAt: string;
  };

  export type PayrollAdjustmentInput = {
    month: string;
    adjustmentType: "ADDITION" | "DEDUCTION";
    amountMinor: number;
    title: string;
    note?: string | null;
  };

  export type PayrollCompensationComponentCreateInput = {
    effectiveMonth: string;
    version: DutyLogApiSchemas.PayrollCompensationComponentVersionInput;
  };

  export type PayrollCompensationComponentLine = {
    componentId: number;
    versionId: number;
    effectiveMonth: string;
    displayName: string;
    earningKind: "HARMFUL_CONDITIONS" | "COMBINATION" | "MONTHLY_BONUS" | "ONE_TIME_BONUS" | "REGIONAL_COEFFICIENT" | null;
    calculationType: "FIXED_AMOUNT" | "PERCENT_OF_BASE";
    calculationBase?: "NOMINAL_SALARY" | "EARNED_BASE_PAY" | null;
    rateBps?: number | null;
    configuredAmountMinor?: number | null;
    configuredCurrencyCode?: string | null;
    referenceBaseMinor: number;
    amountMinor: number;
  };

  export type PayrollCompensationComponentVersion = {
    componentId?: number;
    versionId?: number;
    effectiveMonth?: string;
    displayName?: string;
    earningKind?: "HARMFUL_CONDITIONS" | "COMBINATION" | "MONTHLY_BONUS" | "ONE_TIME_BONUS" | "REGIONAL_COEFFICIENT" | null;
    calculationType?: "FIXED_AMOUNT" | "PERCENT_OF_BASE";
    calculationBase?: "NOMINAL_SALARY" | "EARNED_BASE_PAY";
    rateBps?: number;
    amountMinor?: number;
    currencyCode?: string;
    enabled?: boolean;
    createdAt?: string;
    updatedAt?: string;
  };

  export type PayrollCompensationComponentVersionInput = {
    displayName: string;
    earningKind?: "UNCLASSIFIED" | "HARMFUL_CONDITIONS" | "COMBINATION" | "MONTHLY_BONUS" | "ONE_TIME_BONUS" | "REGIONAL_COEFFICIENT";
    calculationType: "FIXED_AMOUNT" | "PERCENT_OF_BASE";
    calculationBase?: "NOMINAL_SALARY" | "EARNED_BASE_PAY";
    rateBps?: number;
    amountMinor?: number;
    currencyCode?: string;
    enabled: boolean;
  };

  export type PayrollCompensationTerm = DutyLogApiSchemas.PayrollCompensationTermInput & {
    id: number;
    effectiveMonth: string;
    updatedAt: string;
  };

  export type PayrollCompensationTermInput = {
    payMode: "HOURLY" | "SALARY";
    currencyCode: string;
    hourlyRateMinor?: number | null;
    monthlySalaryMinor?: number | null;
  };

  export type PayrollMoneyProjection = {
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
    hourlyBasePayableMinutes: number;
    basePayMinor: number;
    compensationComponentCount: number;
    compensationComponentEarningsMinor: number;
    compensationComponentFingerprint: string | null;
    compensationComponentLines: Array<DutyLogApiSchemas.PayrollCompensationComponentLine>;
    settlementCount: number;
    settlementMinutes: number;
    settlementBasePayMinor: number;
    settlementPremiumPayMinor: number;
    settlementPayMinor: number;
    settlementPricingFingerprint: string | null;
    additionsMinor: number;
    deductionsMinor: number;
    totalPayMinor: number;
    payMode?: "HOURLY" | "SALARY" | null;
    compensationEffectiveMonth?: string | null;
    configuredHourlyRateMinor?: number | null;
    monthlySalaryMinor?: number | null;
    effectiveHourlyRateMinor: number;
    productionNormMinutes: number;
    salaryCoveredMinutes: number;
  };

  export type PayrollPeriod = {
    month: string;
    periodClosed: boolean;
    integrityHealthy: boolean;
    canCalculate: boolean;
    blockingReason?: "PERIOD_OPEN" | "LEDGER_INTEGRITY_FAILED" | "PAYROLL_COMPENSATION_REQUIRED" | "PAYROLL_PRODUCTION_NORM_INCOMPLETE" | "PAYROLL_PRODUCTION_NORM_REQUIRED" | "PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH" | "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE" | "PAYROLL_COMP_COMPONENT_INVALID" | "PAY_PRICING_PROVENANCE_REQUIRED" | "PAY_PRICING_RULES_REQUIRED" | "PAY_PRICING_CURRENCY_MISMATCH" | "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH" | "ORDINARY_PREMIUM_SOURCE_NOT_READY" | "PAYROLL_ORDINARY_PREMIUM_CURRENCY_MISMATCH" | null;
    settings: DutyLogApiSchemas.PayrollSettings;
    productionCalendar: DutyLogApiSchemas.ProductionCalendarMonth;
    preview: DutyLogApiSchemas.PayrollPreview;
    adjustments: Array<DutyLogApiSchemas.PayrollAdjustment>;
    latestSnapshot?: DutyLogApiSchemas.PayrollSnapshot | null;
    snapshots: Array<DutyLogApiSchemas.PayrollSnapshot>;
    effectiveCompensation?: DutyLogApiSchemas.PayrollCompensationTerm | null;
    compensationHistory: Array<DutyLogApiSchemas.PayrollCompensationTerm>;
  };

  export type PayrollPreview = DutyLogApiSchemas.PayrollMoneyProjection & {
    compensationComponentCalculationReady: boolean;
    compensationComponentCalculationBlockingReason: "PAYROLL_COMPENSATION_REQUIRED" | "PAYROLL_PRODUCTION_NORM_INCOMPLETE" | "PAYROLL_PRODUCTION_NORM_REQUIRED" | "PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH" | "PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE" | "PAYROLL_COMP_COMPONENT_INVALID" | null;
    ordinaryPremiumPricingReady: boolean;
    ordinaryPremiumPricingBlockingReason: "ORDINARY_PREMIUM_SOURCE_NOT_READY" | "PAY_PRICING_RULES_REQUIRED" | "PAYROLL_COMPENSATION_REQUIRED" | "PAYROLL_PRODUCTION_NORM_INCOMPLETE" | "PAYROLL_PRODUCTION_NORM_REQUIRED" | "PAY_PRICING_CURRENCY_MISMATCH" | "PAYROLL_ORDINARY_PREMIUM_CURRENCY_MISMATCH" | null;
    ordinaryPremiumPricingIdentityRequired: boolean;
    ordinaryPremiumMinutes: number;
    ordinaryPremiumReferenceBasePayMinor: number;
    ordinaryPremiumPayMinor: number;
    settlementPricingReady: boolean;
    settlementPricingBlockingReason: "PAY_PRICING_PROVENANCE_REQUIRED" | "PAY_PRICING_RULES_REQUIRED" | "PAYROLL_COMPENSATION_REQUIRED" | "PAYROLL_PRODUCTION_NORM_INCOMPLETE" | "PAYROLL_PRODUCTION_NORM_REQUIRED" | "PAY_PRICING_CURRENCY_MISMATCH" | "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH" | null;
  };

  export type PayrollSettings = DutyLogApiSchemas.PayrollSettingsInput & {
    updatedAt?: string | null;
  };

  export type PayrollSettingsInput = {
    currencyCode: string;
    hourlyRateMinor: number;
  };

  export type PayrollSnapshot = DutyLogApiSchemas.PayrollMoneyProjection & {
    ordinaryPremiumMinutes: number;
    ordinaryPremiumReferenceBasePayMinor: number;
    ordinaryPremiumPayMinor: number;
    ordinaryPremiumPricingFingerprint: string | null;
    id: number;
    revision: number;
    sourcePeriodClosedAt: string;
    sourceIntegrityCheckedAt: string;
    calculationHash: string;
    createdAt: string;
    supersededById?: number | null;
  };

  export type ProductionCalendarDay = DutyLogApiSchemas.ProductionCalendarDayInput & {
    date: string;
    sourceType: "NONE" | "CUSTOM" | "OFFICIAL" | "IMPORTED";
    sourceRef?: string | null;
    localOverride: boolean;
    baseNormMinutes: number;
    productionNormMinutes: number;
    adjustmentMinutes: number;
  };

  export type ProductionCalendarDayInput = {
    dayKind: "NORMAL" | "HOLIDAY" | "TRANSFERRED_DAY_OFF" | "TRANSFERRED_WORKDAY" | "SHORTENED_DAY";
    scheduleEffect: "NONE" | "NORM_OVERRIDE";
    normMinutesOverride?: number | null;
    payrollEffect: "NONE" | "HOLIDAY";
    label?: string | null;
  };

  export type ProductionCalendarMonth = {
    month: string;
    baseNormMinutes: number;
    productionNormMinutes: number;
    adjustmentMinutes: number;
    holidayReductionMinutes: number;
    shortenedReductionMinutes: number;
    transferredAdjustmentMinutes: number;
    affectedDays: number;
    scheduleCoverageDays: number;
    scheduleCoverageComplete: boolean;
    days: Array<DutyLogApiSchemas.ProductionCalendarDay>;
  };

  export type Profile = {
    username: string;
    displayName?: string | null;
    birthday?: string | null;
    admin: boolean;
    role: string;
    accountTier: string;
    themePreference: "system" | "light" | "dark";
    accentColor: string;
    themePreset: string;
    themeConfig: Record<string, unknown>;
    languagePreference: "ru" | "en";
    workTimezone: string;
    displayTimezone: string;
    onboardingCompleted: boolean;
  };

  export type ProfileUpdateRequest = {
    displayName?: string | null;
    birthday?: string | null;
    themePreference?: "system" | "light" | "dark";
    accentColor?: string;
    themePreset?: string;
    themeConfig?: Record<string, unknown>;
    languagePreference?: "ru" | "en";
    workTimezone?: string;
    displayTimezone?: string;
    onboardingCompleted?: boolean;
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

  export type QuickScenarioCreateRequest = {
    name: string;
    groupLabel?: string | null;
    description?: string | null;
    startMode?: "SHIFT_START" | "SHIFT_END";
    endMode?: "SHIFT_END" | "ADD_MINUTES" | "FIXED_TIME";
    endOffsetMinutes?: number;
    endFixedTime?: string | null;
    endNextDay?: boolean;
    endDayOffset?: number;
    breakMode?: "ZERO" | "SHIFT" | "CUSTOM";
    customBreakMinutes?: number;
    plannedMode?: "ZERO" | "SHIFT" | "CUSTOM";
    customPlannedHours?: number;
    reasonTemplate?: string | null;
    sortOrder?: number;
  };

  export type QuickScenarioUpdateRequest = {
    name?: string;
    groupLabel?: string | null;
    description?: string | null;
    startMode?: "SHIFT_START" | "SHIFT_END";
    endMode?: "SHIFT_END" | "ADD_MINUTES" | "FIXED_TIME";
    endOffsetMinutes?: number;
    endFixedTime?: string | null;
    endNextDay?: boolean;
    endDayOffset?: number;
    breakMode?: "ZERO" | "SHIFT" | "CUSTOM";
    customBreakMinutes?: number;
    plannedMode?: "ZERO" | "SHIFT" | "CUSTOM";
    customPlannedHours?: number;
    reasonTemplate?: string | null;
    sortOrder?: number;
  };

  export type ScheduleTemplate = {
    id: number;
    name: string;
    description?: string | null;
    alignmentMode: "CYCLE_START" | "WEEKDAY";
    systemPreset: boolean;
    sortOrder: number;
    steps: Array<DutyLogApiSchemas.ScheduleTemplateStep>;
    createdAt?: string;
    updatedAt?: string;
  };

  export type ScheduleTemplateApplyRequest = {
    startDate: string;
    endDate: string;
    anchorDate?: string | null;
    overwriteExistingShift?: boolean;
  };

  export type ScheduleTemplateApplyResult = {
    templateId?: number;
    from?: string;
    to?: string;
    appliedCount?: number;
    unchangedCount?: number;
    skippedCount?: number;
    conflictCount?: number;
    days?: Array<DutyLogApiSchemas.Day>;
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

  export type ScheduleTemplatePreview = {
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
    items?: Array<DutyLogApiSchemas.ScheduleTemplatePreviewItem>;
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

  export type ShiftType = {
    id: number;
    name: string;
    hours: number;
    color: string;
    builtin: boolean;
    startTime?: string | null;
    endTime?: string | null;
    breakMinutes: number;
    plannedHours: number;
    notificationsEnabled: boolean;
    notificationMinutesBefore?: number | null;
  };

  export type ShiftTypeCreateRequest = {
    name: string;
    hours?: number;
    color?: string;
    startTime?: string | null;
    endTime?: string | null;
    breakMinutes?: number;
    plannedHours?: number;
    notificationsEnabled?: boolean;
    notificationMinutesBefore?: number | null;
  };

  export type ShiftTypeUpdateRequest = {
    name?: string;
    hours?: number;
    color?: string;
    startTime?: string | null;
    endTime?: string | null;
    breakMinutes?: number;
    plannedHours?: number;
    notificationsEnabled?: boolean;
    notificationMinutesBefore?: number | null;
  };

  export type Task = {
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
    subtasks: Array<DutyLogApiSchemas.TaskSubtask>;
    project?: string | null;
    allDay: boolean;
    scheduledStartDate?: string | null;
    scheduledStartTime?: string | null;
    scheduledEndDate?: string | null;
    scheduledEndTime?: string | null;
    scheduledDurationMinutes?: number | null;
    scheduleAbsolute: boolean;
    scheduledSourceTimezone?: string | null;
    scheduledSourceStartDate?: string | null;
    scheduledSourceStartTime?: string | null;
    scheduledSourceEndDate?: string | null;
    scheduledSourceEndTime?: string | null;
  };

  export type TaskCreateRequest = {
    date: string;
    text: string;
    description?: string | null;
    project?: string | null;
    category?: string | null;
    tags?: Array<string>;
    priority?: "LOW" | "NORMAL" | "HIGH" | "URGENT";
    dueDate?: string | null;
    dueTime?: string | null;
    reminderEnabled?: boolean;
    reminderMinutesBefore?: number | null;
    subtasks?: Array<DutyLogApiSchemas.TaskSubtaskInput>;
    allDay?: boolean;
    scheduledStartDate?: string | null;
    scheduledStartTime?: string | null;
    scheduledEndDate?: string | null;
    scheduledEndTime?: string | null;
    scheduledDurationMinutes?: number | null;
  };

  export type TaskMetadata = {
    categories: Array<string>;
    tags: Array<string>;
    projects: Array<string>;
  };

  export type TaskPage = {
    items: Array<DutyLogApiSchemas.Task>;
    page: number;
    size: number;
    total: number;
    totalPages: number;
    hasPrevious: boolean;
    hasNext: boolean;
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

  export type TaskUpdateRequest = {
    text?: string;
    done?: boolean;
    date?: string;
    description?: string | null;
    project?: string | null;
    category?: string | null;
    tags?: Array<string>;
    priority?: "LOW" | "NORMAL" | "HIGH" | "URGENT";
    dueDate?: string | null;
    dueTime?: string | null;
    reminderEnabled?: boolean;
    reminderMinutesBefore?: number | null;
    subtasks?: Array<DutyLogApiSchemas.TaskSubtaskInput>;
    completeSubtasks?: boolean;
    allDay?: boolean;
    scheduledStartDate?: string | null;
    scheduledStartTime?: string | null;
    scheduledEndDate?: string | null;
    scheduledEndTime?: string | null;
    scheduledDurationMinutes?: number | null;
  };

  export type TelegramCode = {
    code: string;
    expiresAt: string;
    startCommand: string;
    deepLink?: string | null;
  };

  export type TelegramSettingsRequest = {
    notificationsEnabled?: boolean;
  };

  export type TelegramStatus = {
    configured: boolean;
    pollingEnabled: boolean;
    linked: boolean;
    enabled: boolean;
    notificationsEnabled: boolean;
    botUsername?: string | null;
    chatId?: string | null;
    username?: string | null;
    linkedAt?: string | null;
    pendingCode?: string | null;
    pendingCodeExpiresAt?: string | null;
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

  export type TimeCompensationSummary = {
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
    days: Array<DutyLogApiSchemas.TimeCompensationDay>;
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

  export type VacationPlanner = {
    settings: DutyLogApiSchemas.VacationSettings;
    summary: DutyLogApiSchemas.VacationSummary;
    durationPresets: Array<14 | 28 | 35>;
    types: Array<DutyLogApiSchemas.AbsenceType>;
    absences: Array<DutyLogApiSchemas.AbsencePeriod>;
    occurrences: Array<DutyLogApiSchemas.AbsenceOccurrence>;
    typeSummaries: Array<DutyLogApiSchemas.AbsenceTypeSummary>;
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

  export type WorkdayTruth = {
    date: string;
    shiftName?: string | null;
    scheduledStartTime?: string | null;
    scheduledEndTime?: string | null;
    scheduledBreakMinutes: number;
    baseNormMinutes: number;
    requiredNormMinutes: number;
    productionCalendar: DutyLogApiSchemas.ProductionCalendarDay;
    explicitActual: boolean;
    actualMinutes: number;
    absenceMinutes: number;
    overtimeEarnedMinutes: number;
    overtimeUsedMinutes: number;
    factLabel: string;
    actualWork: Array<DutyLogApiSchemas.ActualWorkInterval>;
  };

  export type WorkTimezoneChangeRequest = {
    effectiveFrom: string;
    timezone: string;
  };

  export type WorkTimezoneHistory = {
    currentTimezone: string;
    currentDate: string;
    terms: Array<DutyLogApiSchemas.WorkTimezoneTerm>;
  };

  export type WorkTimezoneTerm = {
    effectiveFrom: string;
    timezone: string;
    baseline: boolean;
  };
}

export const dutyLogOperations = {
  "addClosedPeriodAdjustment": { method: "POST", path: "/api/v1/ledger-integrity/adjustments" },
  "addPayrollAdjustment": { method: "POST", path: "/api/v1/payroll/adjustments" },
  "adminSystemStatus": { method: "GET", path: "/api/v1/admin/status" },
  "applyScheduleTemplate": { method: "POST", path: "/api/v1/schedule-templates/{id}/apply" },
  "calculatePayrollRevision": { method: "POST", path: "/api/v1/payroll/periods/{month}/calculate" },
  "calendarRange": { method: "GET", path: "/api/v1/calendar" },
  "captureInboxItem": { method: "POST", path: "/api/v1/inbox" },
  "changeProfilePassword": { method: "POST", path: "/api/v1/profile/password" },
  "closeAccountingPeriod": { method: "POST", path: "/api/v1/ledger-integrity/periods/{month}/close" },
  "convertInboxItemToTask": { method: "POST", path: "/api/v1/inbox/{id}/task" },
  "createAbsencePeriod": { method: "POST", path: "/api/v1/vacation-planner/absences" },
  "createAbsenceType": { method: "POST", path: "/api/v1/vacation-planner/types" },
  "createActualWorkInterval": { method: "POST", path: "/api/v1/actual-work" },
  "createCalendarLayer": { method: "POST", path: "/api/v1/calendar-layers" },
  "createDayNote": { method: "POST", path: "/api/v1/notes" },
  "createImportantDay": { method: "POST", path: "/api/v1/important-days" },
  "createOvertimeCredit": { method: "POST", path: "/api/v1/overtime/credits" },
  "createOvertimeSettlement": { method: "POST", path: "/api/v1/overtime/settlements" },
  "createPayrollCompensationComponent": { method: "POST", path: "/api/v1/payroll/compensation-components" },
  "createQuickScenario": { method: "POST", path: "/api/v1/quick-scenarios" },
  "createScheduleTemplate": { method: "POST", path: "/api/v1/schedule-templates" },
  "createShiftType": { method: "POST", path: "/api/v1/shift-types" },
  "createTask": { method: "POST", path: "/api/v1/tasks" },
  "createTelegramLinkCode": { method: "POST", path: "/api/v1/telegram/link-code" },
  "deleteAbsencePeriod": { method: "DELETE", path: "/api/v1/vacation-planner/absences/{id}" },
  "deleteAbsenceType": { method: "DELETE", path: "/api/v1/vacation-planner/types/{id}" },
  "deleteActualWorkInterval": { method: "DELETE", path: "/api/v1/actual-work/{id}" },
  "deleteCalendarLayer": { method: "DELETE", path: "/api/v1/calendar-layers/{id}" },
  "deleteCalendarLayerOverride": { method: "DELETE", path: "/api/v1/calendar-layers/{id}/overrides/{date}" },
  "deleteDayNote": { method: "DELETE", path: "/api/v1/notes/{id}" },
  "deleteImportantDay": { method: "DELETE", path: "/api/v1/important-days/{id}" },
  "deleteInboxItem": { method: "DELETE", path: "/api/v1/inbox/{id}" },
  "deleteLegacyManualOvertimeUsage": { method: "DELETE", path: "/api/v1/overtime/usages/{id}" },
  "deleteOvertimeCredit": { method: "DELETE", path: "/api/v1/overtime/credits/{id}" },
  "deleteOvertimeSettlement": { method: "DELETE", path: "/api/v1/overtime/settlements/{id}" },
  "deletePayrollCompensationTerm": { method: "DELETE", path: "/api/v1/payroll/compensation-terms/{month}" },
  "deletePayrollPricingTerm": { method: "DELETE", path: "/api/v1/payroll/pricing/terms/{effectiveFrom}" },
  "deleteProductionCalendarDayOverride": { method: "DELETE", path: "/api/v1/production-calendar/days/{date}" },
  "deleteQuickScenario": { method: "DELETE", path: "/api/v1/quick-scenarios/{id}" },
  "deleteScheduleTemplate": { method: "DELETE", path: "/api/v1/schedule-templates/{id}" },
  "deleteShiftType": { method: "DELETE", path: "/api/v1/shift-types/{id}" },
  "deleteTask": { method: "DELETE", path: "/api/v1/tasks/{taskId}" },
  "exportCalendarRange": { method: "GET", path: "/api/v1/calendar-sync/export" },
  "exportImportantEventIcs": { method: "GET", path: "/api/v1/calendar-sync/events/{id}.ics" },
  "exportNotes": { method: "GET", path: "/api/v1/export/notes" },
  "getAdminRegistrationSettings": { method: "GET", path: "/api/v1/admin/settings/registration" },
  "getCalendarSyncStatus": { method: "GET", path: "/api/v1/calendar-sync/status" },
  "getNotificationSettings": { method: "GET", path: "/api/v1/notifications/settings" },
  "getProfile": { method: "GET", path: "/api/v1/profile" },
  "getTaskDetails": { method: "GET", path: "/api/v1/tasks/{taskId}" },
  "getTelegramStatus": { method: "GET", path: "/api/v1/telegram/status" },
  "getTimeContext": { method: "GET", path: "/api/v1/time/context" },
  "getVacationPlanner": { method: "GET", path: "/api/v1/vacation-planner" },
  "getWorkTimezoneHistory": { method: "GET", path: "/api/v1/time/work-context" },
  "inspectLedgerIntegrity": { method: "GET", path: "/api/v1/ledger-integrity" },
  "listAbsenceTypes": { method: "GET", path: "/api/v1/vacation-planner/types" },
  "listActualWorkIntervals": { method: "GET", path: "/api/v1/actual-work" },
  "listAdminUsers": { method: "GET", path: "/api/v1/admin/users" },
  "listCalendarLayers": { method: "GET", path: "/api/v1/calendar-layers" },
  "listDayNotes": { method: "GET", path: "/api/v1/notes" },
  "listEffectivePayrollCompensationComponents": { method: "GET", path: "/api/v1/payroll/compensation-components/effective/{month}" },
  "listImportantDayOccurrences": { method: "GET", path: "/api/v1/important-days/occurrences" },
  "listImportantDays": { method: "GET", path: "/api/v1/important-days" },
  "listInbox": { method: "GET", path: "/api/v1/inbox" },
  "listMobileSessions": { method: "GET", path: "/api/v1/mobile/auth/sessions" },
  "listModules": { method: "GET", path: "/api/v1/modules" },
  "listOvertimeSettlements": { method: "GET", path: "/api/v1/overtime/settlements" },
  "listPayrollCompensationComponentHistory": { method: "GET", path: "/api/v1/payroll/compensation-components" },
  "listPayrollPricingTerms": { method: "GET", path: "/api/v1/payroll/pricing/terms" },
  "listProfileSessions": { method: "GET", path: "/api/v1/profile/sessions" },
  "listScheduleTemplates": { method: "GET", path: "/api/v1/schedule-templates" },
  "listShiftTypes": { method: "GET", path: "/api/v1/shift-types" },
  "listTasks": { method: "GET", path: "/api/v1/tasks" },
  "listTomorrowNotifications": { method: "GET", path: "/api/v1/notifications/tomorrow" },
  "listUpcomingNotifications": { method: "GET", path: "/api/v1/notifications/upcoming" },
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
  "productionCalendarMonth": { method: "GET", path: "/api/v1/production-calendar/months/{month}" },
  "promoteLegacyOvertimeUsages": { method: "POST", path: "/api/v1/overtime/legacy-usages/migrate" },
  "quickScenarios": { method: "GET", path: "/api/v1/quick-scenarios" },
  "readPrivateCalendarFeed": { method: "GET", path: "/api/v1/calendar-sync/events/{id}.ics" },
  "refreshMobileToken": { method: "POST", path: "/api/v1/mobile/auth/refresh" },
  "registerMobileUser": { method: "POST", path: "/api/v1/mobile/auth/register" },
  "registrationStatus": { method: "GET", path: "/api/v1/mobile/auth/registration-status" },
  "reopenAccountingPeriod": { method: "POST", path: "/api/v1/ledger-integrity/periods/{month}/reopen" },
  "resetAdminUserPassword": { method: "POST", path: "/api/v1/admin/users/{id}/password" },
  "retiredCreateManualOvertimeUsage": { method: "POST", path: "/api/v1/overtime/usages" },
  "retiredUpdateManualOvertimeUsage": { method: "PATCH", path: "/api/v1/overtime/usages/{id}" },
  "revokeCalendarSubscription": { method: "DELETE", path: "/api/v1/calendar-sync/subscription" },
  "revokeMobileSession": { method: "DELETE", path: "/api/v1/mobile/auth/sessions/{id}" },
  "revokeProfileSession": { method: "DELETE", path: "/api/v1/profile/sessions/{id}" },
  "rotateCalendarSubscription": { method: "POST", path: "/api/v1/calendar-sync/subscription" },
  "searchDayNotes": { method: "GET", path: "/api/v1/notes/search" },
  "syncMobileQueue": { method: "POST", path: "/api/v1/mobile/sync" },
  "taskBoard": { method: "GET", path: "/api/v1/tasks/board" },
  "taskMetadata": { method: "GET", path: "/api/v1/tasks/metadata" },
  "timeCompensationSummary": { method: "GET", path: "/api/v1/time-compensation" },
  "unlinkTelegram": { method: "DELETE", path: "/api/v1/telegram/link" },
  "updateAbsencePeriod": { method: "PATCH", path: "/api/v1/vacation-planner/absences/{id}" },
  "updateAbsenceType": { method: "PATCH", path: "/api/v1/vacation-planner/types/{id}" },
  "updateActualWorkInterval": { method: "PUT", path: "/api/v1/actual-work/{id}" },
  "updateAdminRegistrationSettings": { method: "PATCH", path: "/api/v1/admin/settings/registration" },
  "updateAdminUserRole": { method: "PATCH", path: "/api/v1/admin/users/{id}/role" },
  "updateCalendarLayer": { method: "PATCH", path: "/api/v1/calendar-layers/{id}" },
  "updateDayNote": { method: "PATCH", path: "/api/v1/notes/{id}" },
  "updateImportantDay": { method: "PATCH", path: "/api/v1/important-days/{id}" },
  "updateInboxItem": { method: "PATCH", path: "/api/v1/inbox/{id}" },
  "updateModules": { method: "PATCH", path: "/api/v1/modules" },
  "updateNotificationSettings": { method: "PATCH", path: "/api/v1/notifications/settings" },
  "updateOvertimeCredit": { method: "PATCH", path: "/api/v1/overtime/credits/{id}" },
  "updateOvertimeSettlement": { method: "PATCH", path: "/api/v1/overtime/settlements/{id}" },
  "updatePayrollSettings": { method: "PATCH", path: "/api/v1/payroll/settings" },
  "updateProfile": { method: "PUT", path: "/api/v1/profile" },
  "updateQuickScenario": { method: "PATCH", path: "/api/v1/quick-scenarios/{id}" },
  "updateScheduleTemplate": { method: "PATCH", path: "/api/v1/schedule-templates/{id}" },
  "updateShiftType": { method: "PATCH", path: "/api/v1/shift-types/{id}" },
  "updateSubtask": { method: "PATCH", path: "/api/v1/tasks/{taskId}/subtasks/{subtaskId}" },
  "updateTask": { method: "PATCH", path: "/api/v1/tasks/{taskId}" },
  "updateTelegramSettings": { method: "PATCH", path: "/api/v1/telegram/settings" },
  "updateVacationSettings": { method: "PATCH", path: "/api/v1/vacation-planner/settings" },
  "updateWorkTimezoneContext": { method: "PUT", path: "/api/v1/time/work-context" },
  "upsertCalendarLayerOverride": { method: "PUT", path: "/api/v1/calendar-layers/{id}/overrides/{date}" },
  "upsertDay": { method: "PUT", path: "/api/v1/days/{date}" },
  "upsertPayrollCompensationComponentVersion": { method: "PUT", path: "/api/v1/payroll/compensation-components/{componentId}/versions/{month}" },
  "upsertPayrollCompensationTerm": { method: "PUT", path: "/api/v1/payroll/compensation-terms/{month}" },
  "upsertPayrollPricingTerm": { method: "PUT", path: "/api/v1/payroll/pricing/terms/{effectiveFrom}" },
  "upsertProductionCalendarDay": { method: "PUT", path: "/api/v1/production-calendar/days/{date}" },
  "workdayTruth": { method: "GET", path: "/api/v1/workdays/{date}" },
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
  "adminSystemStatus": {
    requestBody: undefined;
    response: DutyLogApiSchemas.AdminSystemStatus;
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
  "changeProfilePassword": {
    requestBody: DutyLogApiSchemas.PasswordChangeRequest;
    response: undefined;
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
    response: DutyLogApiSchemas.ActualWorkInterval;
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
  "createOvertimeCredit": {
    requestBody: DutyLogApiSchemas.OvertimeCreditCreateRequest;
    response: unknown;
  };
  "createOvertimeSettlement": {
    requestBody: DutyLogApiSchemas.OvertimeSettlementUpsertRequest;
    response: DutyLogApiSchemas.OvertimeSettlement;
  };
  "createPayrollCompensationComponent": {
    requestBody: DutyLogApiSchemas.PayrollCompensationComponentCreateInput;
    response: DutyLogApiSchemas.PayrollCompensationComponentVersion;
  };
  "createQuickScenario": {
    requestBody: DutyLogApiSchemas.QuickScenarioCreateRequest;
    response: DutyLogApiSchemas.QuickScenario;
  };
  "createScheduleTemplate": {
    requestBody: DutyLogApiSchemas.ScheduleTemplateInput;
    response: DutyLogApiSchemas.ScheduleTemplate;
  };
  "createShiftType": {
    requestBody: DutyLogApiSchemas.ShiftTypeCreateRequest;
    response: DutyLogApiSchemas.ShiftType;
  };
  "createTask": {
    requestBody: DutyLogApiSchemas.TaskCreateRequest;
    response: DutyLogApiSchemas.Task;
  };
  "createTelegramLinkCode": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TelegramCode;
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
  "deleteCalendarLayerOverride": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteDayNote": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteImportantDay": {
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
  "deleteOvertimeCredit": {
    requestBody: undefined;
    response: unknown;
  };
  "deleteOvertimeSettlement": {
    requestBody: undefined;
    response: undefined;
  };
  "deletePayrollCompensationTerm": {
    requestBody: undefined;
    response: undefined;
  };
  "deletePayrollPricingTerm": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteProductionCalendarDayOverride": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteQuickScenario": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteScheduleTemplate": {
    requestBody: undefined;
    response: undefined;
  };
  "deleteShiftType": {
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
  "getAdminRegistrationSettings": {
    requestBody: undefined;
    response: DutyLogApiSchemas.AdminRegistrationSettings;
  };
  "getCalendarSyncStatus": {
    requestBody: undefined;
    response: DutyLogApiSchemas.CalendarSyncStatus;
  };
  "getNotificationSettings": {
    requestBody: undefined;
    response: DutyLogApiSchemas.NotificationSettings;
  };
  "getProfile": {
    requestBody: undefined;
    response: DutyLogApiSchemas.Profile;
  };
  "getTaskDetails": {
    requestBody: undefined;
    response: DutyLogApiSchemas.Task;
  };
  "getTelegramStatus": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TelegramStatus;
  };
  "getTimeContext": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TimeContext;
  };
  "getVacationPlanner": {
    requestBody: undefined;
    response: DutyLogApiSchemas.VacationPlanner;
  };
  "getWorkTimezoneHistory": {
    requestBody: undefined;
    response: DutyLogApiSchemas.WorkTimezoneHistory;
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
  "listAdminUsers": {
    requestBody: undefined;
    response: DutyLogApiSchemas.AdminUserPage;
  };
  "listCalendarLayers": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.CalendarLayer>;
  };
  "listDayNotes": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.DayNote>;
  };
  "listEffectivePayrollCompensationComponents": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.PayrollCompensationComponentVersion>;
  };
  "listImportantDayOccurrences": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.ImportantEventOccurrence>;
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
    response: Array<DutyLogApiSchemas.Module>;
  };
  "listOvertimeSettlements": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.OvertimeSettlement>;
  };
  "listPayrollCompensationComponentHistory": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.PayrollCompensationComponentVersion>;
  };
  "listPayrollPricingTerms": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.PayPricingTerm>;
  };
  "listProfileSessions": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.MobileSession>;
  };
  "listScheduleTemplates": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.ScheduleTemplate>;
  };
  "listShiftTypes": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.ShiftType>;
  };
  "listTasks": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.Task>;
  };
  "listTomorrowNotifications": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.NotificationReminder>;
  };
  "listUpcomingNotifications": {
    requestBody: undefined;
    response: Array<DutyLogApiSchemas.NotificationReminder>;
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
  "productionCalendarMonth": {
    requestBody: undefined;
    response: DutyLogApiSchemas.ProductionCalendarMonth;
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
  "resetAdminUserPassword": {
    requestBody: DutyLogApiSchemas.AdminUserPasswordResetRequest;
    response: DutyLogApiSchemas.AdminUser;
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
  "revokeProfileSession": {
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
  "taskBoard": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TaskPage;
  };
  "taskMetadata": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TaskMetadata;
  };
  "timeCompensationSummary": {
    requestBody: undefined;
    response: DutyLogApiSchemas.TimeCompensationSummary;
  };
  "unlinkTelegram": {
    requestBody: undefined;
    response: undefined;
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
    response: DutyLogApiSchemas.ActualWorkInterval;
  };
  "updateAdminRegistrationSettings": {
    requestBody: DutyLogApiSchemas.AdminRegistrationSettingsRequest;
    response: DutyLogApiSchemas.AdminRegistrationSettings;
  };
  "updateAdminUserRole": {
    requestBody: DutyLogApiSchemas.AdminUserRoleRequest;
    response: DutyLogApiSchemas.AdminUser;
  };
  "updateCalendarLayer": {
    requestBody: DutyLogApiSchemas.CalendarLayerPatch;
    response: unknown;
  };
  "updateDayNote": {
    requestBody: DutyLogApiSchemas.DayNoteUpdateRequest;
    response: DutyLogApiSchemas.DayNote;
  };
  "updateImportantDay": {
    requestBody: DutyLogApiSchemas.ImportantEventInput;
    response: DutyLogApiSchemas.ImportantEvent;
  };
  "updateInboxItem": {
    requestBody: DutyLogApiSchemas.InboxUpdateRequest;
    response: unknown;
  };
  "updateModules": {
    requestBody: DutyLogApiSchemas.ModuleSettingsUpdateRequest;
    response: Array<DutyLogApiSchemas.Module>;
  };
  "updateNotificationSettings": {
    requestBody: DutyLogApiSchemas.NotificationSettingsUpdateRequest;
    response: DutyLogApiSchemas.NotificationSettings;
  };
  "updateOvertimeCredit": {
    requestBody: DutyLogApiSchemas.OvertimeCreditUpdateRequest;
    response: unknown;
  };
  "updateOvertimeSettlement": {
    requestBody: DutyLogApiSchemas.OvertimeSettlementUpsertRequest;
    response: DutyLogApiSchemas.OvertimeSettlement;
  };
  "updatePayrollSettings": {
    requestBody: DutyLogApiSchemas.PayrollSettingsInput;
    response: unknown;
  };
  "updateProfile": {
    requestBody: DutyLogApiSchemas.ProfileUpdateRequest;
    response: DutyLogApiSchemas.Profile;
  };
  "updateQuickScenario": {
    requestBody: DutyLogApiSchemas.QuickScenarioUpdateRequest;
    response: DutyLogApiSchemas.QuickScenario;
  };
  "updateScheduleTemplate": {
    requestBody: DutyLogApiSchemas.ScheduleTemplatePatch;
    response: unknown;
  };
  "updateShiftType": {
    requestBody: DutyLogApiSchemas.ShiftTypeUpdateRequest;
    response: DutyLogApiSchemas.ShiftType;
  };
  "updateSubtask": {
    requestBody: {
      done: boolean;
    };
    response: DutyLogApiSchemas.Task;
  };
  "updateTask": {
    requestBody: DutyLogApiSchemas.TaskUpdateRequest;
    response: DutyLogApiSchemas.Task;
  };
  "updateTelegramSettings": {
    requestBody: DutyLogApiSchemas.TelegramSettingsRequest;
    response: DutyLogApiSchemas.TelegramStatus;
  };
  "updateVacationSettings": {
    requestBody: DutyLogApiSchemas.VacationSettingsInput;
    response: unknown;
  };
  "updateWorkTimezoneContext": {
    requestBody: DutyLogApiSchemas.WorkTimezoneChangeRequest;
    response: DutyLogApiSchemas.WorkTimezoneHistory;
  };
  "upsertCalendarLayerOverride": {
    requestBody: DutyLogApiSchemas.CalendarLayerOverrideInput;
    response: DutyLogApiSchemas.CalendarLayerOverride;
  };
  "upsertDay": {
    requestBody: undefined;
    response: unknown;
  };
  "upsertPayrollCompensationComponentVersion": {
    requestBody: DutyLogApiSchemas.PayrollCompensationComponentVersionInput;
    response: DutyLogApiSchemas.PayrollCompensationComponentVersion;
  };
  "upsertPayrollCompensationTerm": {
    requestBody: DutyLogApiSchemas.PayrollCompensationTermInput;
    response: unknown;
  };
  "upsertPayrollPricingTerm": {
    requestBody: DutyLogApiSchemas.PayPricingTermInput;
    response: DutyLogApiSchemas.PayPricingTerm;
  };
  "upsertProductionCalendarDay": {
    requestBody: DutyLogApiSchemas.ProductionCalendarDayInput;
    response: DutyLogApiSchemas.ProductionCalendarDay;
  };
  "workdayTruth": {
    requestBody: undefined;
    response: DutyLogApiSchemas.WorkdayTruth;
  };
}

export type DutyLogOperationRequest<T extends DutyLogOperationId> = DutyLogOperationTypes[T]["requestBody"];
export type DutyLogOperationResponse<T extends DutyLogOperationId> = DutyLogOperationTypes[T]["response"];
