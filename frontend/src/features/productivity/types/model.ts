import type {
  DayNote,
  ImportantEvent,
  ImportantEventInput,
  Task,
  TaskDraft,
  TaskSubtaskInput,
} from "./domain";

export function todayIso(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function validDate(value: string | null | undefined, fallback = todayIso()): string {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(value ?? "")) ? String(value) : fallback;
}

export function normalizeTags(value: string): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const raw of value.split(/[;,]/)) {
    const tag = raw.trim().toLowerCase();
    if (!tag || seen.has(tag)) continue;
    seen.add(tag);
    result.push(tag);
    if (result.length >= 10) break;
  }
  return result;
}

export function emptyTaskDraft(date = todayIso()): TaskDraft {
  return {
    id: null,
    sourceInboxId: null,
    text: "",
    description: "",
    project: "",
    category: "",
    tags: "",
    priority: "NORMAL",
    date: validDate(date),
    allDay: true,
    scheduledStartTime: "09:00",
    scheduledEndDate: validDate(date),
    scheduledEndTime: "10:00",
    scheduledDurationMinutes: "60",
    dueDate: "",
    dueTime: "",
    reminderEnabled: false,
    reminderMinutesBefore: "30",
    subtasks: [],
  };
}

export function taskToDraft(task: Task): TaskDraft {
  return {
    id: task.id,
    sourceInboxId: null,
    text: task.text,
    description: task.description ?? "",
    project: task.project ?? "",
    category: task.category ?? "",
    tags: (task.tags ?? []).join(", "),
    priority: task.priority,
    date: validDate(task.scheduledStartDate ?? task.date),
    allDay: task.allDay !== false,
    scheduledStartTime: task.scheduledStartTime ?? "09:00",
    scheduledEndDate: validDate(task.scheduledEndDate ?? task.scheduledStartDate ?? task.date),
    scheduledEndTime: task.scheduledEndTime ?? "10:00",
    scheduledDurationMinutes: task.scheduledDurationMinutes == null ? "" : String(task.scheduledDurationMinutes),
    dueDate: task.dueDate ?? "",
    dueTime: task.dueTime ?? "",
    reminderEnabled: task.reminderEnabled,
    reminderMinutesBefore: task.reminderMinutesBefore == null ? "30" : String(task.reminderMinutesBefore),
    subtasks: (task.subtasks ?? []).map((item, index) => ({
      id: item.id,
      text: item.text,
      done: item.done,
      sortOrder: item.sortOrder ?? index,
      dueDate: item.dueDate ?? "",
    })),
  };
}

export function taskDraftSubtasks(draft: TaskDraft): TaskSubtaskInput[] {
  return draft.subtasks
    .map((item, index) => ({
      ...(item.id != null ? { id: item.id } : {}),
      text: item.text.trim(),
      done: item.done,
      sortOrder: index,
      ...(item.dueDate ? { dueDate: item.dueDate } : {}),
    }))
    .filter(item => item.text.length > 0);
}

export function addMinutes(time: string, minutes: number): string {
  const match = /^(\d{2}):(\d{2})$/.exec(time);
  if (!match) return time;
  const total = (Number(match[1]) * 60 + Number(match[2]) + Math.max(0, minutes)) % (24 * 60);
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

export function taskScheduleLabel(task: Task): string {
  if (task.allDay !== false || !task.scheduledStartTime) return task.scheduledStartDate ?? task.date;
  const date = task.scheduledStartDate ?? task.date;
  const endDate = task.scheduledEndDate ?? date;
  const end = task.scheduledEndTime ?? "";
  const range = end ? `${task.scheduledStartTime}–${end}` : task.scheduledStartTime;
  return endDate !== date ? `${date} ${task.scheduledStartTime} → ${endDate} ${end}` : `${date} · ${range}`;
}

export function sortDayTasks(tasks: readonly Task[]): Task[] {
  return [...tasks].sort((a, b) => {
    if (a.done !== b.done) return a.done ? 1 : -1;
    if (a.overdue !== b.overdue) return a.overdue ? -1 : 1;
    const aTime = `${a.scheduledStartTime ?? "99:99"}|${a.dueTime ?? "99:99"}|${a.text}`;
    const bTime = `${b.scheduledStartTime ?? "99:99"}|${b.dueTime ?? "99:99"}|${b.text}`;
    return aTime.localeCompare(bTime, "ru");
  });
}

export function noteLabel(note: DayNote): string {
  const title = String(note.title ?? "").trim();
  if (title) return title;
  const first = String(note.content ?? "").split(/\r?\n/).map(value => value.trim()).find(Boolean);
  return first?.slice(0, 60) || "Без названия";
}

export function emptyImportantDraft(date = todayIso(), timezone = "UTC"): ImportantDraftLike {
  const safeDate = validDate(date);
  return {
    id: null,
    title: "",
    eventType: "IMPORTANT_DATE",
    repeatMode: "NONE",
    date: safeDate,
    endDate: safeDate,
    allDay: true,
    startTime: "09:00",
    endTime: "10:00",
    sourceTimezone: timezone,
    place: "",
    category: "",
    icon: "★",
    color: "#F5B841",
    description: "",
    reminders: [],
  };
}

type ImportantDraftLike = import("./domain").ImportantDraft;

export function importantToDraft(item: ImportantEvent, timezone: string): ImportantDraftLike {
  return {
    id: item.id,
    title: item.title,
    eventType: item.eventType ?? "IMPORTANT_DATE",
    repeatMode: item.repeatMode ?? "NONE",
    date: validDate(item.date),
    endDate: validDate(item.endDate ?? item.date),
    allDay: item.eventType === "IMPORTANT_DATE" ? true : item.allDay !== false,
    startTime: item.startTime ?? "09:00",
    endTime: item.endTime ?? "10:00",
    sourceTimezone: item.sourceTimezone ?? timezone,
    place: item.place ?? "",
    category: item.category ?? "",
    icon: item.icon ?? "★",
    color: item.color ?? "#F5B841",
    description: item.description ?? "",
    reminders: [...(item.reminders ?? [])],
  };
}

export function importantInput(draft: ImportantDraftLike): ImportantEventInput {
  const importantDate = draft.eventType === "IMPORTANT_DATE";
  const allDay = importantDate || draft.allDay;
  return {
    title: draft.title.trim(),
    date: validDate(draft.date),
    endDate: importantDate ? null : validDate(draft.endDate || draft.date),
    eventType: draft.eventType,
    allDay,
    startTime: allDay ? null : draft.startTime || null,
    endTime: allDay ? null : draft.endTime || null,
    sourceTimezone: allDay ? null : draft.sourceTimezone || null,
    place: draft.place.trim() || null,
    description: draft.description.trim() || null,
    icon: draft.icon.trim() || null,
    category: draft.category.trim() || null,
    color: /^#[0-9a-fA-F]{6}$/.test(draft.color) ? draft.color : "#F5B841",
    repeatMode: draft.repeatMode,
    reminders: [...draft.reminders],
  };
}

export function importantScheduleLabel(item: ImportantEvent): string {
  if (item.eventType === "IMPORTANT_DATE") return item.date;
  const endDate = item.endDate ?? item.date;
  if (item.allDay !== false) return endDate === item.date ? item.date : `${item.date}–${endDate}`;
  const times = `${item.startTime ?? "—"}–${item.endTime ?? "—"}`;
  return endDate === item.date ? `${item.date} · ${times}` : `${item.date} ${item.startTime ?? "—"} → ${endDate} ${item.endTime ?? "—"}`;
}
