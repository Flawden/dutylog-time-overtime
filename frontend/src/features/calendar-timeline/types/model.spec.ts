import { describe, expect, it } from "vitest";
import {
  addDays,
  calendarImportantGlyph,
  calendarLoadRange,
  calendarOpenTaskCount,
  calendarScheduleFree,
  dayFacts,
  dayFactsForProfile,
  profileLayer,
  sharedAvailabilityForDate,
  durationCountdown,
  importantRelativeLabel,
  monthGridDates,
  navigateDate,
  normalizeCalendarBundle,
  todayShiftProjection,
  weekDates,
} from "./model";

describe("calendar and timeline model", () => {
  it("loads a complete Monday-to-Sunday grid around the focused month", () => {
    expect(calendarLoadRange("2026-08-15")).toEqual({ from: "2026-07-27", to: "2026-09-06" });
    expect(monthGridDates("2026-08-15")).toHaveLength(42);
  });

  it("navigates month, week and day without losing a valid date", () => {
    expect(navigateDate("2026-01-31", "month", 1)).toBe("2026-02-28");
    expect(navigateDate("2026-08-05", "week", 1)).toBe("2026-08-12");
    expect(navigateDate("2026-08-05", "day", -1)).toBe("2026-08-04");
  });

  it("builds a stable Monday week", () => {
    expect(weekDates("2026-08-05")).toEqual([
      "2026-08-03", "2026-08-04", "2026-08-05", "2026-08-06", "2026-08-07", "2026-08-08", "2026-08-09",
    ]);
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  });

  it("normalizes optional calendar collections and canonical overtime balance", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      overtimeAccount: { totalEarnedHours: 8, totalUsedHours: 3, balanceHours: 5 },
    }, "2026-08-01", "2026-08-31");
    expect(bundle.days).toEqual([]);
    expect(bundle.calendarLayers).toEqual([]);
    expect(bundle.overtimeAccount).toMatchObject({ totalEarnedHours: 8, totalUsedHours: 3, balanceHours: 5, credits: [], usages: [] });
  });

  it("composes one selected-day read model from shifts, tasks, events, absences and layers", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-07-27", to: "2026-09-06",
      shiftTypes: [{ id: 1, name: "День", color: "#ffaa00" }],
      days: [{ date: "2026-08-05", shiftTypeId: 1, overtimeHours: 0, timeOffHours: 0, overtimeBalanceHours: 0, version: 1 }],
      tasks: [{ id: 2, date: "2026-08-05", text: "Task", done: false, tags: [], priority: "NORMAL", deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [] }],
      importantDays: [{ id: 3, date: "2026-08-05", title: "Event" }],
      absences: [{ periodId: 4, typeId: 1, typeName: "Отгул", typeColor: "#00aaee", date: "2026-08-05", startDate: "2026-08-05", endDate: "2026-08-05", status: "PLANNED", countedDay: true, shiftConflict: false }],
      reminders: [{ id: "r1", type: "TASK", sourceDate: "2026-08-05", title: "Reminder" }],
      calendarLayers: [{ id: 9, name: "Напарник", color: "#445566", visible: true, entries: [{ date: "2026-08-05", shiftTypeName: "Ночь" }] }],
    }, "2026-07-27", "2026-09-06");
    const facts = dayFacts(bundle, "2026-08-05");
    expect(facts.shift?.name).toBe("День");
    expect(facts.tasks[0]?.text).toBe("Task");
    expect(facts.important[0]?.title).toBe("Event");
    expect(facts.absences[0]?.typeName).toBe("Отгул");
    expect(facts.reminders[0]?.title).toBe("Reminder");
    expect(facts.layers[0]?.layer.name).toBe("Напарник");

    const projected = normalizeCalendarBundle({
      from: "2026-07-27", to: "2026-09-06",
      shiftTypes: [{ id: 7, name: "Поздняя", color: "#7b8ce0", startTime: "04:00", endTime: "12:00" }],
      days: [{ date: "2026-08-03", shiftTypeId: 7, overtimeHours: 0, timeOffHours: 0, overtimeBalanceHours: 0, version: 1 }],
      shiftOccurrences: [{
        dayEntryId: 17, sourceDate: "2026-08-03", shiftTypeId: 7,
        startInstant: "2026-08-03T23:00:00Z", endInstant: "2026-08-04T07:00:00Z",
        sourceStart: "2026-08-03T23:00", sourceEnd: "2026-08-04T07:00",
        displayStart: "2026-08-04T04:00", displayEnd: "2026-08-04T12:00",
        sourceTimezone: "UTC", displayTimezone: "Asia/Yekaterinburg",
        breakMinutes: 0, elapsedMinutes: 480, netMinutes: 480, legacyLocal: false,
      }],
    }, "2026-07-27", "2026-09-06");
    expect(dayFacts(projected, "2026-08-04").shift?.name).toBe("Поздняя");
    expect(dayFacts(projected, "2026-08-04").occurrences).toHaveLength(1);
    expect(dayFacts(projected, "2026-08-03").shift).toBeNull();

    const crossMidnight = normalizeCalendarBundle({
      from: "2026-07-27", to: "2026-09-06",
      shiftTypes: [{ id: 8, name: "До полуночи", color: "#445566" }],
      shiftOccurrences: [{
        dayEntryId: 18, sourceDate: "2026-08-04", shiftTypeId: 8,
        startInstant: "2026-08-04T16:00:00Z", endInstant: "2026-08-05T00:00:00Z",
        sourceStart: "2026-08-04T16:00", sourceEnd: "2026-08-05T00:00",
        displayStart: "2026-08-04T16:00", displayEnd: "2026-08-05T00:00",
        sourceTimezone: "UTC", displayTimezone: "UTC",
        breakMinutes: 0, elapsedMinutes: 480, netMinutes: 480, legacyLocal: false,
      }],
      tasks: [{
        id: 19, date: "2026-08-04", text: "Ночная задача", done: false, tags: [], priority: "NORMAL",
        deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [], allDay: false,
        scheduledStartDate: "2026-08-04", scheduledStartTime: "23:45",
        scheduledEndDate: "2026-08-05", scheduledEndTime: "00:15", scheduleAbsolute: false,
      }],
    }, "2026-07-27", "2026-09-06");
    expect(dayFacts(crossMidnight, "2026-08-04").occurrences).toHaveLength(1);
    expect(dayFacts(crossMidnight, "2026-08-05").occurrences).toHaveLength(0);
    expect(dayFacts(crossMidnight, "2026-08-04").tasks[0]?.text).toBe("Ночная задача");
    expect(dayFacts(crossMidnight, "2026-08-05").tasks[0]?.text).toBe("Ночная задача");
  });

  it("switches to a people profile without leaking owner-only calendar facts", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      shiftTypes: [{ id: 1, name: "Моя смена", color: "#112233" }],
      days: [{ date: "2026-08-05", shiftTypeId: 1, overtimeHours: 0, timeOffHours: 0, overtimeBalanceHours: 0, version: 1 }],
      tasks: [{ id: 2, date: "2026-08-05", text: "Личная задача", done: false, tags: [], priority: "NORMAL", deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [] }],
      calendarLayers: [{ id: 9, name: "Сашка", color: "#445566", timezone: "Europe/Moscow", visible: true, entries: [{ date: "2026-08-05", sourceDate: "2026-08-05", shiftTypeId: 7, shiftTypeName: "Ночь", shiftColor: "#778899", displayStart: "2026-08-05T20:00", displayEnd: "2026-08-06T08:00", timed: true, dayOff: false }] }],
    }, "2026-08-01", "2026-08-31");
    expect(profileLayer(bundle, "9")?.name).toBe("Сашка");
    const profileFacts = dayFactsForProfile(bundle, "2026-08-05", "9");
    expect(profileFacts.shift?.name).toBe("Ночь");
    expect(profileFacts.tasks).toEqual([]);
    expect(profileFacts.absences).toEqual([]);
    expect(profileFacts.important).toEqual([]);
    expect(dayFactsForProfile(bundle, "2026-08-05", "self").tasks[0]?.text).toBe("Личная задача");
  });

  it("calculates shared free windows from both effective work schedules", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      shiftTypes: [{ id: 1, name: "Моя смена", color: "#112233" }],
      shiftOccurrences: [{
        dayEntryId: 101, sourceDate: "2026-08-15", shiftTypeId: 1,
        startInstant: "2026-08-15T08:00:00Z", endInstant: "2026-08-15T17:00:00Z",
        sourceStart: "2026-08-15T08:00", sourceEnd: "2026-08-15T17:00",
        displayStart: "2026-08-15T08:00", displayEnd: "2026-08-15T17:00",
        sourceTimezone: "UTC", displayTimezone: "UTC",
        breakMinutes: 0, elapsedMinutes: 540, netMinutes: 540, legacyLocal: false,
      }],
      calendarLayers: [{
        id: 9, name: "Сашка", color: "#445566", visible: true,
        entries: [{ date: "2026-08-15", sourceDate: "2026-08-15", shiftTypeId: 7, shiftTypeName: "Поздняя", timed: true, dayOff: false, displayStart: "2026-08-15T12:00", displayEnd: "2026-08-15T20:00" }],
      }],
    }, "2026-08-01", "2026-08-31");

    const result = sharedAvailabilityForDate(bundle, "2026-08-15", "9");
    expect(result?.precise).toBe(true);
    expect(result?.freeWindows.map(item => `${item.startTime}–${item.endTime}`)).toEqual(["00:00–08:00", "20:00–24:00"]);
    expect(result?.sharedBusyWindows.map(item => `${item.startTime}–${item.endTime}`)).toEqual(["12:00–17:00"]);
  });

  it("counts even one minute of simultaneous work but not adjacent shifts", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      shiftTypes: [{ id: 1, name: "Моя смена", color: "#112233" }],
      shiftOccurrences: [
        {
          dayEntryId: 111, sourceDate: "2026-08-15", shiftTypeId: 1,
          startInstant: "2026-08-15T08:00:00Z", endInstant: "2026-08-15T14:00:00Z",
          sourceStart: "2026-08-15T08:00", sourceEnd: "2026-08-15T14:00",
          displayStart: "2026-08-15T08:00", displayEnd: "2026-08-15T14:00",
          sourceTimezone: "UTC", displayTimezone: "UTC",
          breakMinutes: 0, elapsedMinutes: 360, netMinutes: 360, legacyLocal: false,
        },
        {
          dayEntryId: 112, sourceDate: "2026-08-16", shiftTypeId: 1,
          startInstant: "2026-08-16T08:00:00Z", endInstant: "2026-08-16T14:00:00Z",
          sourceStart: "2026-08-16T08:00", sourceEnd: "2026-08-16T14:00",
          displayStart: "2026-08-16T08:00", displayEnd: "2026-08-16T14:00",
          sourceTimezone: "UTC", displayTimezone: "UTC",
          breakMinutes: 0, elapsedMinutes: 360, netMinutes: 360, legacyLocal: false,
        },
      ],
      calendarLayers: [{
        id: 9, name: "Сашка", color: "#445566", visible: true,
        entries: [
          { date: "2026-08-15", sourceDate: "2026-08-15", shiftTypeId: 7, shiftTypeName: "Поздняя", timed: true, dayOff: false, displayStart: "2026-08-15T13:59", displayEnd: "2026-08-15T22:00" },
          { date: "2026-08-16", sourceDate: "2026-08-16", shiftTypeId: 7, shiftTypeName: "Поздняя", timed: true, dayOff: false, displayStart: "2026-08-16T14:00", displayEnd: "2026-08-16T22:00" },
        ],
      }],
    }, "2026-08-01", "2026-08-31");

    expect(sharedAvailabilityForDate(bundle, "2026-08-15", "9")?.sharedBusyWindows.map(item => `${item.startTime}–${item.endTime}`)).toEqual(["13:59–14:00"]);
    expect(sharedAvailabilityForDate(bundle, "2026-08-16", "9")?.sharedBusyWindows).toEqual([]);
  });

  it("clips overnight work to the selected display date before finding shared free time", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      shiftTypes: [{ id: 1, name: "Ночь", color: "#112233" }],
      shiftOccurrences: [{
        dayEntryId: 102, sourceDate: "2026-08-15", shiftTypeId: 1,
        startInstant: "2026-08-15T20:00:00Z", endInstant: "2026-08-16T04:00:00Z",
        sourceStart: "2026-08-15T20:00", sourceEnd: "2026-08-16T04:00",
        displayStart: "2026-08-15T20:00", displayEnd: "2026-08-16T04:00",
        sourceTimezone: "UTC", displayTimezone: "UTC",
        breakMinutes: 0, elapsedMinutes: 480, netMinutes: 480, legacyLocal: false,
      }],
      calendarLayers: [{
        id: 9, name: "Сашка", color: "#445566", visible: true,
        entries: [{ date: "2026-08-16", sourceDate: "2026-08-16", shiftTypeId: 7, shiftTypeName: "День", timed: true, dayOff: false, displayStart: "2026-08-16T06:00", displayEnd: "2026-08-16T14:00" }],
      }],
    }, "2026-08-01", "2026-08-31");

    const result = sharedAvailabilityForDate(bundle, "2026-08-16", "9");
    expect(result?.freeWindows.map(item => `${item.startTime}–${item.endTime}`)).toEqual(["04:00–06:00", "14:00–24:00"]);
  });

  it("treats a full-day work absence as free time without leaking personal calendar items", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      shiftTypes: [{ id: 1, name: "Моя смена", color: "#112233" }],
      shiftOccurrences: [{
        dayEntryId: 103, sourceDate: "2026-08-15", shiftTypeId: 1,
        startInstant: "2026-08-15T08:00:00Z", endInstant: "2026-08-15T17:00:00Z",
        sourceStart: "2026-08-15T08:00", sourceEnd: "2026-08-15T17:00",
        displayStart: "2026-08-15T08:00", displayEnd: "2026-08-15T17:00",
        sourceTimezone: "UTC", displayTimezone: "UTC",
        breakMinutes: 0, elapsedMinutes: 540, netMinutes: 540, legacyLocal: false,
      }],
      absences: [{
        periodId: 77, typeId: 1, typeName: "Отгул", typeColor: "#4A90E2", date: "2026-08-15",
        startDate: "2026-08-15", endDate: "2026-08-15", coverage: "FULL_DAY", status: "PLANNED", countedDay: true, shiftConflict: true, replacesShift: true,
      }],
      tasks: [{ id: 2, date: "2026-08-15", text: "Личное", done: false, tags: [], priority: "NORMAL", deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [] }],
      calendarLayers: [{
        id: 9, name: "Сашка", color: "#445566", visible: true,
        entries: [{ date: "2026-08-15", sourceDate: "2026-08-15", shiftTypeId: 7, shiftTypeName: "День", timed: true, dayOff: false, displayStart: "2026-08-15T09:00", displayEnd: "2026-08-15T18:00" }],
      }],
    }, "2026-08-01", "2026-08-31");

    const result = sharedAvailabilityForDate(bundle, "2026-08-15", "9");
    expect(result?.selfBusy).toEqual([]);
    expect(result?.freeWindows.map(item => `${item.startTime}–${item.endTime}`)).toEqual(["00:00–09:00", "18:00–24:00"]);
  });

  it("fails closed when an effective work shift has no exact time", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      calendarLayers: [{
        id: 9, name: "Сашка", color: "#445566", visible: true,
        entries: [{ date: "2026-08-15", sourceDate: "2026-08-15", shiftTypeId: 7, shiftTypeName: "Смена", timed: false, dayOff: false }],
      }],
    }, "2026-08-01", "2026-08-31");

    const result = sharedAvailabilityForDate(bundle, "2026-08-15", "9");
    expect(result?.precise).toBe(false);
    expect(result?.unknownReason).toBe("PROFILE_UNTIMED_WORK");
    expect(result?.freeWindows).toEqual([]);
  });

  it("defines the calendar visual language without conflating schedule, markers and tasks", () => {
    const free = dayFacts(normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      days: [{ date: "2026-08-09", dayEmoji: "🧪", overtimeHours: 0, timeOffHours: 0, overtimeBalanceHours: 0, version: 1 }],
      tasks: [
        { id: 51, date: "2026-08-09", text: "Open", done: false, tags: [], priority: "NORMAL", deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [] },
        { id: 52, date: "2026-08-09", text: "Done", done: true, tags: [], priority: "NORMAL", deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [] },
      ],
    }, "2026-08-01", "2026-08-31"), "2026-08-09");
    expect(calendarScheduleFree(free)).toBe(true);
    expect(calendarOpenTaskCount(free)).toBe(1);
    expect(free.day?.dayEmoji).toBe("🧪");

    expect(calendarImportantGlyph({ id: 1, date: "2026-08-09", title: "Birthday", icon: "🎂" })).toBe("🎂");
    expect(calendarImportantGlyph({ id: 2, date: "2026-08-09", title: "Period", eventType: "PERIOD" })).toBe("◇");
    expect(calendarImportantGlyph({ id: 3, date: "2026-08-09", title: "Generic" })).toBe("★");

    const absent = dayFacts(normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      absences: [{ periodId: 9, typeId: 1, typeName: "Отгул", typeColor: "#4A90E2", date: "2026-08-09", startDate: "2026-08-09", endDate: "2026-08-09", status: "PLANNED", countedDay: true, shiftConflict: false }],
    }, "2026-08-01", "2026-08-31"), "2026-08-09");
    expect(calendarScheduleFree(absent)).toBe(false);
  });

  it("restores Today live shift countdown and progress from immutable occurrence instants", () => {
    const bundle = normalizeCalendarBundle({
      from: "2026-08-01", to: "2026-08-31",
      shiftTypes: [{ id: 11, name: "Ночь", color: "#445566" }],
      shiftOccurrences: [{
        dayEntryId: 31, sourceDate: "2026-08-12", shiftTypeId: 11,
        startInstant: "2026-08-12T20:00:00Z", endInstant: "2026-08-13T04:00:00Z",
        sourceStart: "2026-08-12T20:00", sourceEnd: "2026-08-13T04:00",
        displayStart: "2026-08-12T20:00", displayEnd: "2026-08-13T04:00",
        sourceTimezone: "UTC", displayTimezone: "UTC",
        breakMinutes: 30, elapsedMinutes: 480, netMinutes: 450, legacyLocal: false,
      }],
    }, "2026-08-01", "2026-08-31");

    const active = todayShiftProjection(bundle, "2026-08-13", Date.parse("2026-08-13T02:30:00Z"));
    expect(active?.phase).toBe("active");
    expect(active?.shift?.name).toBe("Ночь");
    expect(active?.progress).toBe(81);
    expect(durationCountdown(active?.remainingMs ?? 0, "ru")).toBe("1 ч 30 мин");

    const future = todayShiftProjection(bundle, "2026-08-12", Date.parse("2026-08-12T18:45:00Z"));
    expect(future?.phase).toBe("future");
    expect(future?.progress).toBe(0);
    expect(durationCountdown(future?.remainingMs ?? 0, "en")).toBe("1 h 15 min");
  });

  it("restores relative Important Days copy on Today", () => {
    expect(importantRelativeLabel("2026-08-13", "2026-08-13", "ru")).toBe("сегодня");
    expect(importantRelativeLabel("2026-08-13", "2026-08-14", "ru")).toBe("завтра");
    expect(importantRelativeLabel("2026-08-13", "2026-08-15", "ru")).toBe("через 2 дня");
    expect(importantRelativeLabel("2026-08-13", "2026-08-18", "ru")).toBe("через 5 дней");
    expect(importantRelativeLabel("2026-08-13", "2026-09-03", "ru")).toBe("через 21 день");
    expect(importantRelativeLabel("2026-08-13", "2026-08-15", "en")).toBe("in 2 days");
  });

});
