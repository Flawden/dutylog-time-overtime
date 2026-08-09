import { describe, expect, it } from "vitest";
import type { ImportantDraft, Task } from "./domain";
import {
  addMinutes,
  addMinutesToDateTime,
  importantInput,
  normalizeTags,
  sortDayTasks,
  taskDraftSubtasks,
  taskToDraft,
} from "./model";

function task(overrides: Partial<Task> = {}): Task {
  return {
    id: 1,
    date: "2026-08-08",
    text: "Task",
    done: false,
    tags: [],
    priority: "NORMAL",
    deadlineAbsolute: false,
    reminderEnabled: false,
    overdue: false,
    subtasks: [],
    allDay: true,
    scheduleAbsolute: false,
    ...overrides,
  };
}

describe("productivity domain model", () => {
  it("normalizes persistent task tags case-insensitively without duplicates", () => {
    expect(normalizeTags(" Browser, regression;browser, Release ")).toEqual(["browser", "regression", "release"]);
  });

  it("derives an end time from an exact task duration", () => {
    expect(addMinutes("17:41", 45)).toBe("18:26");
    expect(addMinutes("23:45", 30)).toBe("00:15");
    expect(addMinutesToDateTime("2026-08-09", "23:45", 30)).toEqual({ date: "2026-08-10", time: "00:15" });
  });

  it("preserves project, schedule, deadline and subtask dates when a task becomes an editor draft", () => {
    const draft = taskToDraft(task({
      project: "DutyLog",
      allDay: false,
      scheduledStartDate: "2026-08-08",
      scheduledStartTime: "18:33",
      scheduledEndDate: "2026-08-08",
      scheduledEndTime: "19:18",
      scheduledDurationMinutes: 45,
      dueDate: "2026-08-08",
      dueTime: "20:00",
      subtasks: [{ id: 9, text: "Check staging", done: false, sortOrder: 0, dueDate: "2026-08-08" }],
    }));
    expect(draft).toMatchObject({ project: "DutyLog", allDay: false, scheduledStartTime: "18:33", scheduledEndTime: "19:18", scheduledDurationMinutes: "45", dueTime: "20:00" });
    expect(draft.subtasks[0]).toMatchObject({ id: 9, dueDate: "2026-08-08" });
  });

  it("drops empty subtask drafts and publishes stable sort order", () => {
    const subtasks = taskDraftSubtasks({
      ...taskToDraft(task()),
      subtasks: [
        { text: " First ", done: false, sortOrder: 9, dueDate: "" },
        { text: "   ", done: false, sortOrder: 8, dueDate: "" },
        { id: 7, text: "Second", done: true, sortOrder: 3, dueDate: "2026-08-10" },
      ],
    });
    expect(subtasks).toEqual([
      { text: "First", done: false, sortOrder: 0 },
      { id: 7, text: "Second", done: true, sortOrder: 2, dueDate: "2026-08-10" },
    ]);
  });

  it("keeps open tasks above completed tasks while promoting overdue work", () => {
    const rows = sortDayTasks([
      task({ id: 1, text: "done", done: true }),
      task({ id: 2, text: "later", scheduledStartTime: "12:00" }),
      task({ id: 3, text: "overdue", overdue: true, scheduledStartTime: "14:00" }),
    ]);
    expect(rows.map(row => row.id)).toEqual([3, 2, 1]);
  });

  it("serializes important dates as floating all-day dates and timed events with source timezone", () => {
    const base: ImportantDraft = {
      id: null, title: "Release", eventType: "IMPORTANT_DATE", repeatMode: "YEARLY",
      date: "2026-08-08", endDate: "2026-08-09", allDay: false, startTime: "23:00", endTime: "23:30",
      sourceTimezone: "Europe/Moscow", place: "", category: "", icon: "★", color: "#F5B841", description: "", reminders: [60],
    };
    expect(importantInput(base)).toMatchObject({ eventType: "IMPORTANT_DATE", allDay: true, endDate: null, startTime: null, sourceTimezone: null });
    expect(importantInput({ ...base, eventType: "EVENT", repeatMode: "NONE", allDay: false })).toMatchObject({ eventType: "EVENT", allDay: false, endDate: "2026-08-09", startTime: "23:00", sourceTimezone: "Europe/Moscow" });
  });
});
