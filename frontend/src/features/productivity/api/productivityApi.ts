import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import type {
  DayNoteUpdateRequest,
  ImportantEventInput,
  TaskCreateRequest,
  TaskUpdateRequest,
} from "../types/domain";

export function createProductivityApi(client: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient()) {
  return Object.freeze({
    async timeContext() { return client.request("getTimeContext"); },
    async taskBoard(query: Record<string, string | number | undefined>) {
      return client.request("taskBoard", { query });
    },
    async tasksForDate(date: string) { return (await client.request("listTasks", { query: { date } })) ?? []; },
    async taskMetadata() { return client.request("taskMetadata"); },
    async task(id: number) { return client.request("getTaskDetails", { path: { taskId: id } }); },
    async createTask(body: TaskCreateRequest) { return client.request("createTask", { body }); },
    async updateTask(id: number, body: TaskUpdateRequest) { return client.request("updateTask", { path: { taskId: id }, body }); },
    async deleteTask(id: number) { await client.request("deleteTask", { path: { taskId: id } }); },
    async updateSubtask(taskId: number, subtaskId: number, done: boolean) {
      return client.request("updateSubtask", { path: { taskId, subtaskId }, body: { done } });
    },
    async notesForDate(date: string) { return (await client.request("listDayNotes", { query: { date } })) ?? []; },
    async createNote(date: string) { return client.request("createDayNote", { body: { date, title: null, content: "", pinned: false } }); },
    async updateNote(id: number, body: DayNoteUpdateRequest) { return client.request("updateDayNote", { path: { id }, body }); },
    async deleteNote(id: number) { await client.request("deleteDayNote", { path: { id } }); },
    async moveNote(id: number, direction: "UP" | "DOWN") { return client.request("moveDayNote", { path: { id }, body: { direction } }); },
    async searchNotes(q: string) { return (await client.request("searchDayNotes", { query: { q, limit: 40 } })) ?? []; },
    async importantDays() { return (await client.request("listImportantDays")) ?? []; },
    async importantOccurrences(from: string, to: string) {
      return (await client.request("listImportantDayOccurrences", { query: { from, to } })) ?? [];
    },
    async createImportant(body: ImportantEventInput) { return client.request("createImportantDay", { body }); },
    async updateImportant(id: number, body: ImportantEventInput) { return client.request("updateImportantDay", { path: { id }, body }); },
    async deleteImportant(id: number) { await client.request("deleteImportantDay", { path: { id } }); },
    async inbox(status: "open" | "archived" | "all" = "open") { return (await client.request("listInbox", { query: { status } })) ?? []; },
    async captureInbox(text: string, clientOperationId?: string) {
      return client.request("captureInboxItem", { body: { text, ...(clientOperationId ? { clientOperationId } : {}) } });
    },
    async deleteInbox(id: number) { await client.request("deleteInboxItem", { path: { id } }); },
    async convertInbox(id: number, body: DutyLogApiSchemas.InboxToTaskRequest) {
      return client.request("convertInboxItemToTask", { path: { id }, body });
    },
  });
}

export type ProductivityApi = ReturnType<typeof createProductivityApi>;
