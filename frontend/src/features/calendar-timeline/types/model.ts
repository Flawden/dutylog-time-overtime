import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import type {
  CalendarImportantOccurrence,
  CalendarLayer,
  CalendarMode,
  CalendarRangeBundle,
  CalendarReminder,
  CalendarShiftType,
  CalendarTask,
} from "./domain";

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export function todayIso(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function validDate(value: unknown, fallback = todayIso()): string {
  const text = String(value ?? "");
  return DATE_PATTERN.test(text) ? text : fallback;
}

function dateParts(key: string): { year: number; month: number; day: number } {
  const parts = validDate(key).split("-");
  return { year: Number(parts[0]), month: Number(parts[1]), day: Number(parts[2]) };
}

function dateValue(key: string): Date {
  const { year, month, day } = dateParts(key);
  return new Date(Date.UTC(year, month - 1, day));
}

export function addDays(key: string, amount: number): string {
  const value = dateValue(key);
  value.setUTCDate(value.getUTCDate() + amount);
  return value.toISOString().slice(0, 10);
}

export function monthStart(key: string): string {
  const { year, month } = dateParts(key);
  return `${year}-${String(month).padStart(2, "0")}-01`;
}

export function monthEnd(key: string): string {
  const { year, month } = dateParts(key);
  return new Date(Date.UTC(year, month, 0)).toISOString().slice(0, 10);
}

export function weekStart(key: string): string {
  const value = dateValue(key);
  const offset = (value.getUTCDay() + 6) % 7;
  return addDays(key, -offset);
}

export function calendarLoadRange(key: string): { from: string; to: string } {
  const first = monthStart(key);
  const last = monthEnd(key);
  return { from: weekStart(first), to: addDays(weekStart(last), 6) };
}

export function monthGridDates(key: string): string[] {
  const range = calendarLoadRange(key);
  const result: string[] = [];
  for (let cursor = range.from; cursor <= range.to; cursor = addDays(cursor, 1)) result.push(cursor);
  return result;
}

export function weekDates(key: string): string[] {
  const start = weekStart(key);
  return Array.from({ length: 7 }, (_, index) => addDays(start, index));
}

export function navigateDate(key: string, mode: CalendarMode, delta: number): string {
  if (mode === "day") return addDays(key, delta);
  if (mode === "week") return addDays(key, delta * 7);
  const value = dateValue(key);
  const targetDay = value.getUTCDate();
  value.setUTCDate(1);
  value.setUTCMonth(value.getUTCMonth() + delta);
  const last = new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + 1, 0)).getUTCDate();
  value.setUTCDate(Math.min(targetDay, last));
  return value.toISOString().slice(0, 10);
}

function object(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function array<T>(value: unknown): T[] { return Array.isArray(value) ? value as T[] : []; }
function number(value: unknown): number { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : 0; }

export function normalizeCalendarBundle(payload: unknown, from: string, to: string): CalendarRangeBundle {
  const raw = object(payload);
  const overtimeRaw = object(raw.overtime);
  const accountRaw = object(raw.overtimeAccount);
  return {
    from: validDate(raw.from, from),
    to: validDate(raw.to, to),
    shiftTypes: array<CalendarShiftType>(raw.shiftTypes),
    days: array<DutyLogApiSchemas.Day>(raw.days),
    shiftOccurrences: array<DutyLogApiSchemas.ShiftOccurrence>(raw.shiftOccurrences),
    tasks: array<CalendarTask>(raw.tasks),
    importantDays: array<CalendarImportantOccurrence>(raw.importantDays),
    absences: array<DutyLogApiSchemas.AbsenceOccurrence>(raw.absences),
    overtime: {
      overtimeHours: number(overtimeRaw.overtimeHours),
      timeOffHours: number(overtimeRaw.timeOffHours),
      balanceHours: number(overtimeRaw.balanceHours),
    },
    overtimeAccount: {
      totalEarnedHours: number(accountRaw.totalEarnedHours),
      totalUsedHours: number(accountRaw.totalUsedHours),
      balanceHours: number(accountRaw.balanceHours),
      credits: array<DutyLogApiSchemas.OvertimeCredit>(accountRaw.credits),
      usages: array<DutyLogApiSchemas.OvertimeUsage>(accountRaw.usages),
    },
    reminders: array<CalendarReminder>(raw.reminders),
    calendarLayers: array<CalendarLayer>(raw.calendarLayers),
    modules: array<{ key: string; enabled: boolean }>(raw.modules),
  };
}

export function shiftType(bundle: CalendarRangeBundle | null, id: number | null | undefined): CalendarShiftType | null {
  if (!bundle || id == null) return null;
  return bundle.shiftTypes.find(item => Number(item.id) === Number(id)) ?? null;
}

export function dayOf(bundle: CalendarRangeBundle | null, date: string): DutyLogApiSchemas.Day | null {
  return bundle?.days.find(item => item.date === date) ?? null;
}

export function occurrencesOf(bundle: CalendarRangeBundle | null, date: string): DutyLogApiSchemas.ShiftOccurrence[] {
  return (bundle?.shiftOccurrences ?? []).filter(item => item.displayStart?.slice(0, 10) === date || item.displayEnd?.slice(0, 10) === date || item.sourceDate === date);
}

export function tasksOf(bundle: CalendarRangeBundle | null, date: string): CalendarTask[] {
  return (bundle?.tasks ?? []).filter(item => (item.scheduledStartDate || item.dueDate || item.date) === date);
}

export function importantOf(bundle: CalendarRangeBundle | null, date: string): CalendarImportantOccurrence[] {
  return (bundle?.importantDays ?? []).filter(item => item.date === date || item.startDate === date || (item.startDate && item.endDate && item.startDate <= date && item.endDate >= date));
}

export function absencesOf(bundle: CalendarRangeBundle | null, date: string): DutyLogApiSchemas.AbsenceOccurrence[] {
  return (bundle?.absences ?? []).filter(item => item.date === date);
}

export function remindersOf(bundle: CalendarRangeBundle | null, date: string): CalendarReminder[] {
  return (bundle?.reminders ?? []).filter(item => item.sourceDate === date);
}

export function layersOf(bundle: CalendarRangeBundle | null, date: string): Array<{ layer: CalendarLayer; entry: CalendarLayer["entries"][number] }> {
  const result: Array<{ layer: CalendarLayer; entry: CalendarLayer["entries"][number] }> = [];
  for (const layer of bundle?.calendarLayers ?? []) {
    if (!layer.visible) continue;
    for (const entry of layer.entries ?? []) if ((entry.date || entry.sourceDate) === date) result.push({ layer, entry });
  }
  return result;
}

export interface CalendarDayFacts {
  date: string;
  day: DutyLogApiSchemas.Day | null;
  shift: CalendarShiftType | null;
  occurrences: DutyLogApiSchemas.ShiftOccurrence[];
  tasks: CalendarTask[];
  important: CalendarImportantOccurrence[];
  absences: DutyLogApiSchemas.AbsenceOccurrence[];
  reminders: CalendarReminder[];
  layers: ReturnType<typeof layersOf>;
}

export function dayFacts(bundle: CalendarRangeBundle | null, date: string): CalendarDayFacts {
  const day = dayOf(bundle, date);
  return {
    date,
    day,
    shift: shiftType(bundle, day?.shiftTypeId),
    occurrences: occurrencesOf(bundle, date),
    tasks: tasksOf(bundle, date),
    important: importantOf(bundle, date),
    absences: absencesOf(bundle, date),
    reminders: remindersOf(bundle, date),
    layers: layersOf(bundle, date),
  };
}

export function dateLabel(date: string, language: "ru" | "en", options: Intl.DateTimeFormatOptions = {}): string {
  return new Intl.DateTimeFormat(language === "en" ? "en-US" : "ru-RU", { timeZone: "UTC", ...options }).format(dateValue(date));
}

export function timePart(value: string | null | undefined): string {
  const match = String(value ?? "").match(/T(\d{2}:\d{2})/);
  return match?.[1] ?? String(value ?? "").slice(0, 5);
}

export function minutesOf(time: string | null | undefined, fallback: number): number {
  const match = String(time ?? "").match(/(\d{2}):(\d{2})/);
  return match ? Number(match[1]) * 60 + Number(match[2]) : fallback;
}
