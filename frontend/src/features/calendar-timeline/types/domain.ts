import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type CalendarMode = "month" | "week" | "day";

export interface CalendarShiftType {
  id: number;
  name: string;
  color: string;
  startTime?: string | null;
  endTime?: string | null;
  plannedHours?: number;
}

export interface CalendarTask extends DutyLogApiSchemas.Task {
  scheduledStartDate?: string | null;
  scheduledStartTime?: string | null;
  scheduledEndDate?: string | null;
  scheduledEndTime?: string | null;
  allDay?: boolean;
}

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
  shiftTypeId?: number;
  shiftTypeName?: string;
  shiftColor?: string;
  displayStart?: string | null;
  displayEnd?: string | null;
  timed?: boolean;
  dayOff?: boolean;
}

export interface CalendarLayer {
  id: number;
  name: string;
  color: string;
  visible: boolean;
  timezone?: string;
  entries: CalendarLayerEntry[];
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
  snapshot(): Readonly<{ focusDate: string; mode: CalendarMode; from: string; to: string }> | null;
}
