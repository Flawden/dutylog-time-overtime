import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export type Task = DutyLogApiSchemas.Task;
export type TaskCreateRequest = DutyLogApiSchemas.TaskCreateRequest;
export type TaskUpdateRequest = DutyLogApiSchemas.TaskUpdateRequest;
export type TaskSubtask = DutyLogApiSchemas.TaskSubtask;
export type TaskSubtaskInput = DutyLogApiSchemas.TaskSubtaskInput;
export type TaskMetadata = DutyLogApiSchemas.TaskMetadata;
export type TaskPage = DutyLogApiSchemas.TaskPage;
export type DayNote = DutyLogApiSchemas.DayNote;
export type DayNoteUpdateRequest = DutyLogApiSchemas.DayNoteUpdateRequest;
export type ImportantEvent = DutyLogApiSchemas.ImportantEvent;
export type ImportantEventInput = DutyLogApiSchemas.ImportantEventInput;
export type ImportantEventOccurrence = DutyLogApiSchemas.ImportantEventOccurrence;
export type InboxItem = DutyLogApiSchemas.InboxItem;

export type TaskBoardStatus = "open" | "overdue" | "done" | "all";

export interface TaskBoardFilters {
  status: TaskBoardStatus;
  category: string;
  project: string;
  priority: "" | "LOW" | "NORMAL" | "HIGH" | "URGENT";
  q: string;
}

export interface TaskDraftSubtask {
  id?: number;
  text: string;
  done: boolean;
  sortOrder: number;
  dueDate: string;
}

export interface TaskDraft {
  id: number | null;
  sourceInboxId: number | null;
  text: string;
  description: string;
  project: string;
  category: string;
  tags: string;
  priority: "LOW" | "NORMAL" | "HIGH" | "URGENT";
  date: string;
  allDay: boolean;
  scheduledStartTime: string;
  scheduledEndDate: string;
  scheduledEndTime: string;
  scheduledDurationMinutes: string;
  dueDate: string;
  dueTime: string;
  reminderEnabled: boolean;
  reminderMinutesBefore: string;
  subtasks: TaskDraftSubtask[];
}

export interface ImportantDraft {
  id: number | null;
  title: string;
  eventType: "IMPORTANT_DATE" | "EVENT" | "PERIOD";
  repeatMode: "NONE" | "MONTHLY" | "YEARLY";
  date: string;
  endDate: string;
  allDay: boolean;
  startTime: string;
  endTime: string;
  sourceTimezone: string;
  place: string;
  category: string;
  icon: string;
  color: string;
  description: string;
  reminders: number[];
}

export interface ProductivitySnapshot {
  selectedDate: string;
  boardTotal: number;
  selectedTasks: number;
  selectedNotes: number;
  selectedImportant: number;
  queuedMutations: number;
}

export interface DutyLogProductivityDomain {
  ready(): boolean;
  refresh(): Promise<void>;
  openTaskCreate(date?: string): Promise<void>;
  openTaskDetails(id: number): Promise<void>;
  openImportantCreate(date?: string): Promise<void>;
  openImportantEdit(id: number): Promise<void>;
  openImportantDetails(id: number): Promise<void>;
  snapshot(): Readonly<ProductivitySnapshot>;
}
