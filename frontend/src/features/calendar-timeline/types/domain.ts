import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type CalendarMode = "month" | "week" | "day";
export type CalendarDaySection = "shift" | "emoji" | "schedule" | "overtime" | "vacation" | "important" | "tasks" | "notes";

export interface CalendarShiftType {
  id: number;
  name: string;
  color: string;
  startTime?: string | null;
  endTime?: string | null;
  plannedHours?: number;
}

export type CalendarTask = DutyLogApiSchemas.Task;

export interface CalendarImportantOccurrence {
  id: number;
  date: string;
  title: string;
  color?: string | null;
  eventType?: "IMPORTANT_DATE" | "EVENT" | "PERIOD" | string;
  startDate?: string | null;
  endDate?: string | null;
  allDay?: boolean;
  startTime?: string | null;
  endTime?: string | null;
  displayTimezone?: string | null;
  place?: string | null;
  icon?: string | null;
}

export interface CalendarReminder {
  id: string;
  type: string;
  sourceDate: string;
  remindAt?: string | null;
  title: string;
  details?: string | null;
  displayAt?: string | null;
}

export interface CalendarLayerEntry {
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
}

export interface CalendarLayer {
  id: number;
  name: string;
  color: string;
  visible: boolean;
  timezone?: string;
  scheduleEditable?: boolean;
  entries: CalendarLayerEntry[];
}

export interface SharedAvailabilityWindow {
  startMinute: number;
  endMinute: number;
  startTime: string;
  endTime: string;
  durationMinutes: number;
}

export type SharedAvailabilityUnknownReason = "SELF_UNTIMED_WORK" | "PROFILE_UNTIMED_WORK";

export interface SharedAvailabilityDay {
  date: string;
  profileId: number;
  profileName: string;
  precise: boolean;
  unknownReason: SharedAvailabilityUnknownReason | null;
  allDayFree: boolean;
  noSharedFreeTime: boolean;
  freeWindows: SharedAvailabilityWindow[];
  selfBusy: SharedAvailabilityWindow[];
  profileBusy: SharedAvailabilityWindow[];
}

export interface CalendarOvertimeSummary {
  overtimeHours: number;
  timeOffHours: number;
  balanceHours: number;
}

export interface CalendarRangeBundle {
  from: string;
  to: string;
  shiftTypes: CalendarShiftType[];
  days: DutyLogApiSchemas.Day[];
  shiftOccurrences: DutyLogApiSchemas.ShiftOccurrence[];
  tasks: CalendarTask[];
  importantDays: CalendarImportantOccurrence[];
  absences: DutyLogApiSchemas.AbsenceOccurrence[];
  overtime: CalendarOvertimeSummary;
  overtimeAccount: DutyLogApiSchemas.OvertimeAccount;
  reminders: CalendarReminder[];
  calendarLayers: CalendarLayer[];
  modules: Array<{ key: string; enabled: boolean }>;
}

export interface CalendarTimelineProjectionSnapshot {
  bundle: CalendarRangeBundle;
  focusDate: string;
  mode: CalendarMode;
}

export interface DutyLogCalendarTimelineDomain {
  ready(): boolean;
  refresh(): Promise<void>;
  openDate(date: string, mode?: CalendarMode): Promise<void>;
  openDay(date: string, section?: CalendarDaySection | null): Promise<void>;
  closeDay(): void;
  snapshot(): Readonly<{ focusDate: string; mode: CalendarMode; from: string; to: string }> | null;
}
