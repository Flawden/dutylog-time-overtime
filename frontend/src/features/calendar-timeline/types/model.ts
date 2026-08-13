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

function dateSpanContains(date: string, startDate: string, endDate: string, endTime?: string | null): boolean {
  if (!startDate || date < startDate || date > endDate) return false;
  if (date === endDate && endDate > startDate && String(endTime ?? "").slice(0, 5) === "00:00") return false;
  return true;
}

export function occurrencesOf(bundle: CalendarRangeBundle | null, date: string): DutyLogApiSchemas.ShiftOccurrence[] {
  return (bundle?.shiftOccurrences ?? []).filter(item => {
    const startDate = String(item.displayStart ?? "").slice(0, 10);
    const endDate = String(item.displayEnd ?? item.displayStart ?? "").slice(0, 10);
    return dateSpanContains(date, startDate, endDate, timePart(item.displayEnd));
  });
}

export function tasksOf(bundle: CalendarRangeBundle | null, date: string): CalendarTask[] {
  return (bundle?.tasks ?? []).filter(item => {
    const startDate = item.scheduledStartDate || item.date;
    if (item.allDay === false && item.scheduledStartTime) {
      const endDate = item.scheduledEndDate || startDate;
      return dateSpanContains(date, startDate, endDate, item.scheduledEndTime);
    }
    return (item.scheduledStartDate || item.dueDate || item.date) === date;
  });
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
  const occurrences = occurrencesOf(bundle, date);
  const projectedShift = occurrences.map(item => shiftType(bundle, item.shiftTypeId)).find(Boolean) ?? null;
  const shift = projectedShift ?? ((bundle?.shiftOccurrences.length ?? 0) === 0 ? shiftType(bundle, day?.shiftTypeId) : null);
  return {
    date,
    day,
    shift,
    occurrences,
    tasks: tasksOf(bundle, date),
    important: importantOf(bundle, date),
    absences: absencesOf(bundle, date),
    reminders: remindersOf(bundle, date),
    layers: layersOf(bundle, date),
  };
}


export type TodayShiftPhase = "active" | "future" | "finished" | "next";

export interface TodayShiftProjection {
  phase: TodayShiftPhase;
  occurrence: DutyLogApiSchemas.ShiftOccurrence;
  shift: CalendarShiftType | null;
  progress: number;
  remainingMs: number;
  date: string;
}

function validInstant(value: string | null | undefined): number | null {
  const parsed = Date.parse(String(value ?? ""));
  return Number.isFinite(parsed) ? parsed : null;
}

/** Canonical Today shift projection, migrated from the retired legacy dashboard. */
export function todayShiftProjection(bundle: CalendarRangeBundle | null, date: string, nowMs: number): TodayShiftProjection | null {
  const timed = (bundle?.shiftOccurrences ?? [])
    .map(occurrence => ({ occurrence, startMs: validInstant(occurrence.startInstant), endMs: validInstant(occurrence.endInstant) }))
    .filter((item): item is { occurrence: DutyLogApiSchemas.ShiftOccurrence; startMs: number; endMs: number } => item.startMs !== null && item.endMs !== null)
    .sort((a, b) => a.startMs - b.startMs);
  const active = timed.find(item => item.startMs <= nowMs && nowMs < item.endMs) ?? null;
  const today = occurrencesOf(bundle, date)
    .map(occurrence => ({ occurrence, startMs: validInstant(occurrence.startInstant), endMs: validInstant(occurrence.endInstant) }))
    .filter((item): item is { occurrence: DutyLogApiSchemas.ShiftOccurrence; startMs: number; endMs: number } => item.startMs !== null && item.endMs !== null)
    .sort((a, b) => a.startMs - b.startMs);
  const next = timed.find(item => item.startMs > nowMs) ?? null;
  const relevant = active
    ?? today.find(item => item.endMs > nowMs)
    ?? today.at(-1)
    ?? (next && next.startMs - nowMs <= 36 * 60 * 60 * 1000 ? next : null);
  if (!relevant) return null;

  const isActive = relevant.startMs <= nowMs && nowMs < relevant.endMs;
  const isFuture = nowMs < relevant.startMs;
  const duration = Math.max(1, relevant.endMs - relevant.startMs);
  const progress = isFuture ? 0 : Math.max(0, Math.min(100, Math.round((nowMs - relevant.startMs) * 100 / duration)));
  const phase: TodayShiftPhase = isActive ? "active" : (isFuture ? (today.includes(relevant) ? "future" : "next") : "finished");
  return {
    phase,
    occurrence: relevant.occurrence,
    shift: shiftType(bundle, relevant.occurrence.shiftTypeId),
    progress,
    remainingMs: isActive ? relevant.endMs - nowMs : (isFuture ? relevant.startMs - nowMs : 0),
    date: String(relevant.occurrence.displayStart ?? relevant.occurrence.sourceDate).slice(0, 10) || date,
  };
}

export function durationCountdown(ms: number, language: "ru" | "en"): string {
  const totalMinutes = Math.max(0, Math.ceil(ms / 60_000));
  const days = Math.floor(totalMinutes / 1_440);
  const hours = Math.floor((totalMinutes % 1_440) / 60);
  const minutes = totalMinutes % 60;
  if (language === "en") {
    if (days > 0) return `${days} d ${hours} h`;
    if (hours > 0) return `${hours} h ${String(minutes).padStart(2, "0")} min`;
    return `${minutes} min`;
  }
  if (days > 0) return `${days} дн. ${hours} ч`;
  if (hours > 0) return `${hours} ч ${String(minutes).padStart(2, "0")} мин`;
  return `${minutes} мин`;
}

export function daysBetween(from: string, to: string): number {
  return Math.round((dateValue(to).getTime() - dateValue(from).getTime()) / 86_400_000);
}

function russianDayWord(value: number): string {
  const mod100 = Math.abs(value) % 100;
  const mod10 = Math.abs(value) % 10;
  if (mod100 >= 11 && mod100 <= 14) return "дней";
  if (mod10 === 1) return "день";
  if (mod10 >= 2 && mod10 <= 4) return "дня";
  return "дней";
}

export function importantRelativeLabel(from: string, to: string, language: "ru" | "en"): string {
  const diff = daysBetween(from, to);
  if (language === "en") {
    if (diff === 0) return "today";
    if (diff === 1) return "tomorrow";
    return diff > 1 ? `in ${diff} days` : `${Math.abs(diff)} days ago`;
  }
  if (diff === 0) return "сегодня";
  if (diff === 1) return "завтра";
  if (diff > 1) return `через ${diff} ${russianDayWord(diff)}`;
  const ago = Math.abs(diff);
  return `${ago} ${russianDayWord(ago)} назад`;
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
