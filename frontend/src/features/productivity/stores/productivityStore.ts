import { defineStore } from "pinia";
import { DutyLogApiError } from "@/platform/api/httpClient";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { createProductivityApi, type ProductivityApi } from "../api/productivityApi";
import type {
  DayNote,
  ImportantDraft,
  ImportantEvent,
  ImportantEventOccurrence,
  InboxItem,
  Task,
  TaskDraft,
  TaskMetadata,
  TaskPage,
} from "../types/domain";
import {
  addMinutes,
  addMinutesToDateTime,
  emptyImportantDraft,
  emptyTaskDraft,
  importantInput,
  importantToDraft,
  normalizeTags,
  sortDayTasks,
  taskDraftSubtasks,
  taskToDraft,
  todayIso,
  validDate,
} from "../types/model";

let api: ProductivityApi = createProductivityApi();
let bridge: LegacyBridge | null = null;
let boardReadSequence = 0;
let selectedReadSequence = 0;
let importantReadSequence = 0;
let inboxReadSequence = 0;
let searchReadSequence = 0;
const taskReadYourWrite = new Map<number, Task>();

const emptyMetadata = (): TaskMetadata => ({ categories: [], tags: [], projects: [] });
const emptyPage = (): TaskPage => ({ items: [], page: 0, size: 50, total: 0, totalPages: 0, hasPrevious: false, hasNext: false });

export function installProductivityRuntime(nextApi: ProductivityApi, nextBridge: LegacyBridge): () => void {
  const previousApi = api;
  const previousBridge = bridge;
  api = nextApi;
  bridge = nextBridge;
  return () => { api = previousApi; bridge = previousBridge; };
}

export function installProductivityBridge(nextBridge: LegacyBridge): () => void {
  const previous = bridge;
  bridge = nextBridge;
  return () => { bridge = previous; };
}


function runtimeModuleEnabled(key: string): boolean {
  const snapshot = bridge?.snapshot();
  if (snapshot?.modulesLoaded && snapshot.modules && Object.prototype.hasOwnProperty.call(snapshot.modules, key)) {
    return snapshot.modules[key] !== false;
  }
  const shell = useShellStore();
  if (!shell.modulesLoaded) return true;
  return shell.modules[key] !== false;
}

function errorMessage(error: unknown): string {
  if (error instanceof DutyLogApiError) return error.code ? `${error.message} (${error.code})` : error.message;
  return error instanceof Error ? error.message : "Не удалось выполнить запрос";
}

function taskDraftSnapshot(draft: TaskDraft): TaskDraft {
  return {
    ...draft,
    subtasks: draft.subtasks.map(item => ({ ...item })),
  };
}

function importantDraftSnapshot(draft: ImportantDraft): ImportantDraft {
  return {
    ...draft,
    reminders: [...draft.reminders],
  };
}

function taskPayload(draft: TaskDraft) {
  const timed = !draft.allDay;
  const reminderMinutes = Number(draft.reminderMinutesBefore);
  const duration = Number(draft.scheduledDurationMinutes);
  return {
    date: validDate(draft.date),
    text: draft.text.trim(),
    description: draft.description.trim() || null,
    project: draft.project.trim() || null,
    category: draft.category.trim() || null,
    tags: normalizeTags(draft.tags),
    priority: draft.priority,
    dueDate: draft.dueDate || null,
    dueTime: draft.dueDate && draft.dueTime ? draft.dueTime : null,
    reminderEnabled: draft.reminderEnabled,
    reminderMinutesBefore: draft.reminderEnabled && Number.isFinite(reminderMinutes) ? Math.max(0, Math.round(reminderMinutes)) : null,
    subtasks: taskDraftSubtasks(draft),
    allDay: draft.allDay,
    scheduledStartDate: validDate(draft.date),
    scheduledStartTime: timed ? draft.scheduledStartTime || null : null,
    scheduledEndDate: timed ? validDate(draft.scheduledEndDate, draft.date) : null,
    scheduledEndTime: timed ? draft.scheduledEndTime || null : null,
    scheduledDurationMinutes: timed && Number.isFinite(duration) && duration > 0 ? Math.round(duration) : null,
  } as const;
}

function taskEndKey(draft: TaskDraft): string | null {
  if (draft.allDay || !draft.scheduledStartTime) return null;
  const endDate = validDate(draft.scheduledEndDate, draft.date);
  const endTime = draft.scheduledEndTime || (draft.scheduledDurationMinutes ? addMinutes(draft.scheduledStartTime, Number(draft.scheduledDurationMinutes)) : "");
  if (!endTime) return null;
  return `${endDate}T${endTime}`;
}

function deadlineKey(draft: TaskDraft): string | null {
  if (!draft.dueDate) return null;
  return `${draft.dueDate}T${draft.dueTime || "23:59"}`;
}

function taskDisplayDate(task: Task): string {
  return validDate(task.scheduledStartDate ?? task.date);
}

function sortDayNotes(notes: DayNote[]): DayNote[] {
  return [...notes].sort((a, b) => Number(b.pinned) - Number(a.pinned) || a.sortOrder - b.sortOrder || a.id - b.id);
}

function defaultBoardAccepts(task: Task, state: {
  boardStatus: string; boardCategory: string; boardProject: string; boardPriority: string;
  boardSearch: string; boardFrom: string; boardTo: string;
}): boolean {
  return state.boardStatus === "open"
    && !state.boardCategory && !state.boardProject && !state.boardPriority
    && !state.boardSearch.trim() && !state.boardFrom && !state.boardTo
    && !task.done;
}

async function refreshCalendarIfMounted(): Promise<void> {
  try { await window.DutyLogVueDomains?.calendarTimeline?.refresh(); } catch { /* page keeps its authoritative domain state */ }
}

export const useProductivityStore = defineStore("dutylog-productivity", {
  state: () => ({
    selectedDate: todayIso(),
    selectedTasks: [] as Task[],
    selectedNotes: [] as DayNote[],
    selectedImportant: [] as ImportantEventOccurrence[],
    board: emptyPage() as TaskPage,
    metadata: emptyMetadata() as TaskMetadata,
    boardStatus: "open" as "open" | "overdue" | "done" | "all",
    boardCategory: "",
    boardProject: "",
    boardPriority: "" as "" | "LOW" | "NORMAL" | "HIGH" | "URGENT",
    boardSearch: "",
    boardFrom: "",
    boardTo: "",
    boardPage: 0,
    boardSize: 50,
    importantDays: [] as ImportantEvent[],
    importantScope: "all" as "all" | "upcoming" | "past" | "recurring",
    importantSearch: "",
    inbox: [] as InboxItem[],
    inboxShowArchived: false,
    inboxSearch: "",
    noteSearch: "",
    noteSearchResults: [] as DayNote[],
    selectedNoteId: null as number | null,
    workTimezone: "UTC",
    workDate: todayIso(),
    loading: false,
    boardLoading: false,
    selectedLoading: false,
    mutationPending: false,
    loaded: false,
    error: "",
    conflict: "",
    queuedMutations: 0,
    taskEditorOpen: false,
    taskDetailsOpen: false,
    taskDraft: emptyTaskDraft() as TaskDraft,
    taskDetails: null as Task | null,
    importantEditorOpen: false,
    importantDetailsOpen: false,
    importantDraft: emptyImportantDraft() as ImportantDraft,
    importantDetails: null as ImportantEvent | null,
    quickActionsOpen: false,
    quickActionText: "",
    quickActionDate: todayIso(),
  }),
  getters: {
    currentNote(state): DayNote | null {
      return state.selectedNotes.find(note => Number(note.id) === Number(state.selectedNoteId)) ?? state.selectedNotes[0] ?? null;
    },
    filteredImportantDays(state): ImportantEvent[] {
      const query = state.importantSearch.trim().toLocaleLowerCase("ru-RU");
      const today = validDate(state.workDate);
      return state.importantDays.filter(item => {
        if (state.importantScope === "recurring" && item.repeatMode === "NONE") return false;
        if (state.importantScope === "upcoming" && item.date < today && item.repeatMode === "NONE") return false;
        if (state.importantScope === "past" && (item.date >= today || item.repeatMode !== "NONE")) return false;
        if (!query) return true;
        return `${item.title} ${item.date} ${item.place ?? ""} ${item.category ?? ""}`.toLocaleLowerCase("ru-RU").includes(query);
      });
    },
    visibleInbox(state): InboxItem[] {
      const q = state.inboxSearch.trim().toLocaleLowerCase("ru-RU");
      return state.inbox.filter(item => !q || item.text.toLocaleLowerCase("ru-RU").includes(q));
    },
  },
  actions: {
    synchronizeQueuedCount(): void { this.queuedMutations = bridge?.offlinePending() ?? 0; },
    async ensureLoaded(date?: string): Promise<void> {
      const targetDate = date ?? this.selectedDate;
      if (!this.loaded) await this.refreshAll(targetDate);
    },
    async refreshAll(date?: string): Promise<void> {
      const targetDate = date ?? this.selectedDate;
      this.loading = true;
      this.error = "";
      this.loaded = false;
      try {
        const offline = typeof navigator !== "undefined" && !navigator.onLine && bridge !== null;
        if (!offline) {
          const context = await api.timeContext();
          this.workTimezone = context?.workTimezone || this.workTimezone;
          this.workDate = validDate(context?.workDate, this.workDate);
        }
        const tasksEnabled = runtimeModuleEnabled("tasks");
        const importantEnabled = runtimeModuleEnabled("important_dates");
        const [selectedOk, boardOk, importantOk, inboxOk] = await Promise.all([
          this.loadSelectedDate(targetDate),
          tasksEnabled && !offline ? this.loadBoard() : Promise.resolve(true),
          importantEnabled && !offline ? this.loadImportantDays() : Promise.resolve(true),
          tasksEnabled && !offline ? this.loadInbox() : Promise.resolve(true),
        ]);
        // The selected-day IndexedDB snapshot is the offline authority. Board,
        // Inbox and full Important lists are server-owned and deliberately stay
        // untouched until connectivity returns.
        this.loaded = selectedOk && boardOk && importantOk && inboxOk;
        this.synchronizeQueuedCount();
      } catch (error) {
        this.loaded = false;
        this.error = errorMessage(error);
      } finally { this.loading = false; }
    },
    async loadSelectedDate(date: string): Promise<boolean> {
      const safeDate = validDate(date, this.selectedDate || this.workDate || todayIso());
      const sequence = ++selectedReadSequence;
      this.selectedDate = safeDate;
      this.selectedLoading = true;
      try {
        const tasksEnabled = runtimeModuleEnabled("tasks");
        const notesEnabled = runtimeModuleEnabled("notes");
        const importantEnabled = runtimeModuleEnabled("important_dates");
        let tasks: Task[] = [];
        let notes: DayNote[] = [];
        let occurrences: ImportantEventOccurrence[] = [];
        if (typeof navigator !== "undefined" && !navigator.onLine && bridge) {
          const cached = await bridge.offlineSelectedDay(safeDate);
          tasks = tasksEnabled ? cached.tasks as Task[] : [];
          notes = notesEnabled ? cached.notes as DayNote[] : [];
          occurrences = importantEnabled ? cached.important as ImportantEventOccurrence[] : [];
        } else {
          [tasks, notes, occurrences] = await Promise.all([
            tasksEnabled ? api.tasksForDate(safeDate) : Promise.resolve([]),
            notesEnabled ? api.notesForDate(safeDate) : Promise.resolve([]),
            importantEnabled ? api.importantOccurrences(safeDate, safeDate) : Promise.resolve([]),
          ]);
        }
        if (sequence !== selectedReadSequence) return false;
        this.selectedTasks = sortDayTasks(tasks);
        // A committed mutation DTO stays authoritative while follow-up projections
        // settle. Merge staged writes into each accepted day read so a slower or
        // older projection cannot make a just-saved task disappear from the UI.
        for (const saved of taskReadYourWrite.values()) {
          if (taskDisplayDate(saved) === safeDate) {
            this.selectedTasks = sortDayTasks([
              ...this.selectedTasks.filter(task => task.id !== saved.id),
              saved,
            ]);
          }
        }
        this.selectedNotes = [...notes].sort((a, b) => Number(b.pinned) - Number(a.pinned) || a.sortOrder - b.sortOrder || a.id - b.id);
        this.selectedImportant = occurrences;
        if (!this.selectedNotes.some(note => note.id === this.selectedNoteId)) this.selectedNoteId = this.selectedNotes[0]?.id ?? null;
        return true;
      } catch (error) {
        if (sequence === selectedReadSequence) this.error = errorMessage(error);
        return false;
      } finally { if (sequence === selectedReadSequence) this.selectedLoading = false; }
    },
    async loadBoard(): Promise<boolean> {
      const sequence = ++boardReadSequence;
      if (!runtimeModuleEnabled("tasks")) {
        this.boardLoading = false;
        return true;
      }
      this.boardLoading = true;
      try {
        const query: Record<string, string | number | undefined> = {
          status: this.boardStatus,
          category: this.boardCategory || undefined,
          project: this.boardProject || undefined,
          priority: this.boardPriority || undefined,
          q: this.boardSearch.trim() || undefined,
          from: this.boardFrom || undefined,
          to: this.boardTo || undefined,
          page: this.boardPage,
          size: this.boardSize,
        };
        const [page, metadata] = await Promise.all([api.taskBoard(query), api.taskMetadata()]);
        if (sequence !== boardReadSequence) return false;
        this.board = page ?? emptyPage();
        this.metadata = metadata ?? emptyMetadata();
        // Preserve read-your-write semantics throughout the projection refresh,
        // not only before/after Promise.all in saveTask().
        for (const saved of taskReadYourWrite.values()) this.publishSavedTask(saved);
        return true;
      } catch (error) {
        if (sequence === boardReadSequence) this.error = errorMessage(error);
        return false;
      } finally { if (sequence === boardReadSequence) this.boardLoading = false; }
    },
    async loadImportantDays(): Promise<boolean> {
      const sequence = ++importantReadSequence;
      if (!runtimeModuleEnabled("important_dates")) return true;
      try {
        const rows = await api.importantDays();
        if (sequence !== importantReadSequence) return false;
        this.importantDays = rows;
        return true;
      } catch (error) { if (sequence === importantReadSequence) this.error = errorMessage(error); return false; }
    },
    async loadInbox(): Promise<boolean> {
      const sequence = ++inboxReadSequence;
      if (!runtimeModuleEnabled("tasks")) return true;
      try {
        const rows = await api.inbox(this.inboxShowArchived ? "all" : "open");
        if (sequence !== inboxReadSequence) return false;
        this.inbox = rows;
        return true;
      } catch (error) { if (sequence === inboxReadSequence) this.error = errorMessage(error); return false; }
    },
    async setBoardFilters(): Promise<void> { this.boardPage = 0; await this.loadBoard(); },
    async searchNotesNow(): Promise<void> {
      if (!runtimeModuleEnabled("notes")) { this.noteSearchResults = []; return; }
      const q = this.noteSearch.trim();
      const sequence = ++searchReadSequence;
      if (!q) { this.noteSearchResults = []; return; }
      try {
        const rows = await api.searchNotes(q);
        if (sequence === searchReadSequence) this.noteSearchResults = rows;
      } catch (error) { if (sequence === searchReadSequence) this.error = errorMessage(error); }
    },
    openQuickActions(date?: string): void {
      this.quickActionDate = validDate(date, this.selectedDate || this.workDate || todayIso());
      this.quickActionText = "";
      this.error = "";
      this.quickActionsOpen = true;
    },
    closeQuickActions(): void {
      this.quickActionsOpen = false;
      this.quickActionText = "";
      this.error = "";
    },
    async openTaskCreate(date?: string, text = "", sourceInboxId: number | null = null): Promise<void> {
      const targetDate = date ?? this.selectedDate;
      await this.ensureLoaded(targetDate);
      this.taskDraft = emptyTaskDraft(targetDate);
      this.taskDraft.text = text;
      this.taskDraft.sourceInboxId = sourceInboxId;
      this.taskEditorOpen = true;
      this.taskDetailsOpen = false;
      this.error = "";
      this.conflict = "";
    },
    async openTaskDetails(id: number): Promise<void> {
      this.error = "";
      const row = await api.task(id);
      if (!row) { this.error = "Задача не найдена"; return; }
      this.taskDetails = row;
      this.taskDetailsOpen = true;
      this.taskEditorOpen = false;
    },
    editTaskDetails(): void {
      if (!this.taskDetails) return;
      this.taskDraft = taskToDraft(this.taskDetails);
      this.taskDetailsOpen = false;
      this.taskEditorOpen = true;
    },
    updateTaskDuration(minutes: number): void {
      if (!Number.isFinite(minutes) || minutes <= 0) return;
      this.taskDraft.scheduledDurationMinutes = String(Math.round(minutes));
      const end = addMinutesToDateTime(this.taskDraft.date, this.taskDraft.scheduledStartTime, minutes);
      this.taskDraft.scheduledEndDate = end.date;
      this.taskDraft.scheduledEndTime = end.time;
    },
    validateTaskDraft(): string {
      if (!this.taskDraft.text.trim()) return "Текст задачи не должен быть пустым.";
      if (!this.taskDraft.allDay && (!this.taskDraft.scheduledStartTime || !this.taskDraft.scheduledEndTime)) return "Укажите начало и окончание запланированного интервала.";
      const end = taskEndKey(this.taskDraft);
      const deadline = deadlineKey(this.taskDraft);
      if (this.taskDraft.dueDate && this.taskDraft.dueDate < this.taskDraft.date) return "Дедлайн не может быть раньше окончания запланированного интервала.";
      if (end && deadline && deadline < end) return "Дедлайн не может быть раньше окончания запланированного интервала.";
      return "";
    },
    publishSavedTask(saved: Task): void {
      if (taskDisplayDate(saved) === this.selectedDate) {
        this.selectedTasks = sortDayTasks([
          ...this.selectedTasks.filter(task => task.id !== saved.id),
          saved,
        ]);
      } else {
        this.selectedTasks = this.selectedTasks.filter(task => task.id !== saved.id);
      }

      const boardIndex = this.board.items.findIndex(task => task.id === saved.id);
      if (boardIndex >= 0) {
        const items = [...this.board.items];
        items[boardIndex] = saved;
        this.board = { ...this.board, items };
      } else if (defaultBoardAccepts(saved, this)) {
        const items = [...this.board.items, saved];
        this.board = { ...this.board, items, total: Math.max(this.board.total, items.length) };
      }

      const categories = saved.category
        ? [...new Set([...this.metadata.categories, saved.category])].sort((a, b) => a.localeCompare(b, "ru"))
        : this.metadata.categories;
      const projects = saved.project
        ? [...new Set([...this.metadata.projects, saved.project])].sort((a, b) => a.localeCompare(b, "ru"))
        : this.metadata.projects;
      const tags = saved.tags?.length
        ? [...new Set([...this.metadata.tags, ...saved.tags])].sort((a, b) => a.localeCompare(b, "ru"))
        : this.metadata.tags;
      this.metadata = { ...this.metadata, categories, projects, tags };
    },
    async saveTask(): Promise<void> {
      if (this.mutationPending) return;
      const validation = this.validateTaskDraft();
      if (validation) { this.error = validation; return; }
      this.mutationPending = true;
      this.error = "";
      this.conflict = "";
      const draft = taskDraftSnapshot(this.taskDraft);
      try {
        const payload = taskPayload(draft);
        let saved: Task | null;
        if (draft.id) {
          saved = await api.updateTask(draft.id, payload);
        } else if (draft.sourceInboxId) {
          const conversion = await api.convertInbox(draft.sourceInboxId, {
            date: draft.date,
            description: payload.description,
            category: payload.category,
            tags: payload.tags,
            priority: payload.priority,
            dueDate: payload.dueDate,
            dueTime: payload.dueTime,
            reminderEnabled: payload.reminderEnabled,
            reminderMinutesBefore: payload.reminderMinutesBefore,
            subtasks: payload.subtasks,
          });
          saved = conversion?.task ?? null;
        } else {
          saved = await api.createTask(payload);
        }
        this.taskEditorOpen = false;
        if (saved) {
          this.taskDetails = saved;
          // Stage the committed backend DTO across every follow-up projection read.
          // loadSelectedDate/loadBoard merge this overlay before assigning UI state,
          // closing the read-your-write gap that could blank a freshly saved task.
          taskReadYourWrite.set(saved.id, saved);
          this.publishSavedTask(saved);
        }
        await Promise.all([this.loadSelectedDate(draft.date), this.loadBoard(), this.loadInbox()]);
        if (saved) {
          this.publishSavedTask(saved);
          taskReadYourWrite.delete(saved.id);
        }
        await refreshCalendarIfMounted();
        useShellStore().announce("Задача сохранена", "success");
      } catch (error) { await this.handleMutationError(error); }
      finally { this.mutationPending = false; }
    },
    async toggleTask(task: Task, done: boolean): Promise<void> {
      const pendingChildren = done && (task.subtasks ?? []).some(item => !item.done);
      if (pendingChildren && !globalThis.confirm("Завершить задачу вместе с незавершёнными подзадачами?")) return;
      this.error = "";
      try {
        if (pendingChildren) {
          await api.updateTask(task.id, { done: true, completeSubtasks: true });
          await Promise.all([this.loadSelectedDate(this.selectedDate), this.loadBoard()]);
        } else {
          if (!bridge) throw new Error("Offline bridge is not ready");
          const result = await bridge.offlineSetTaskDone(task.id, done);
          if (result.queued) {
            const apply = (row: Task) => row.id === task.id ? { ...row, done } : row;
            this.selectedTasks = this.selectedTasks.map(apply);
            this.board.items = this.board.items.map(apply);
            if (this.taskDetails?.id === task.id) this.taskDetails = { ...this.taskDetails, done };
            useShellStore().announce("Изменение сохранено оффлайн", "warning");
          } else {
            await Promise.all([this.loadSelectedDate(this.selectedDate), this.loadBoard()]);
          }
        }
        this.synchronizeQueuedCount();
        await refreshCalendarIfMounted();
      } catch (error) { this.error = errorMessage(error); }
    },
    async toggleSubtask(taskId: number, subtaskId: number, done: boolean): Promise<void> {
      if (this.mutationPending) return;
      this.mutationPending = true;
      try {
        const updated = await api.updateSubtask(taskId, subtaskId, done);
        if (updated) this.taskDetails = updated;
        await Promise.all([this.loadSelectedDate(this.selectedDate), this.loadBoard()]);
      } catch (error) { this.error = errorMessage(error); }
      finally { this.mutationPending = false; }
    },
    async deleteTask(id: number): Promise<void> {
      if (this.mutationPending || !globalThis.confirm("Удалить задачу?")) return;
      this.mutationPending = true;
      try {
        await api.deleteTask(id);
        this.taskDetailsOpen = false;
        this.taskEditorOpen = false;
        await Promise.all([this.loadSelectedDate(this.selectedDate), this.loadBoard()]);
        await refreshCalendarIfMounted();
      } catch (error) { await this.handleMutationError(error); }
      finally { this.mutationPending = false; }
    },
    addSubtaskDraft(): void {
      this.taskDraft.subtasks.push({ text: "", done: false, sortOrder: this.taskDraft.subtasks.length, dueDate: "" });
    },
    moveSubtaskDraft(index: number, delta: number): void {
      const next = index + delta;
      if (index < 0 || next < 0 || index >= this.taskDraft.subtasks.length || next >= this.taskDraft.subtasks.length) return;
      const rows = [...this.taskDraft.subtasks];
      [rows[index], rows[next]] = [rows[next]!, rows[index]!];
      this.taskDraft.subtasks = rows;
    },
    removeSubtaskDraft(index: number): void { this.taskDraft.subtasks.splice(index, 1); },
    async createNote(date?: string, content = ""): Promise<void> {
      if (this.mutationPending) return;
      const targetDate = validDate(date, this.selectedDate || this.workDate);
      this.mutationPending = true;
      try {
        if (targetDate !== this.selectedDate) await this.loadSelectedDate(targetDate);
        const note = await api.createNote(targetDate, content);
        if (note) {
          // The create response is already the authoritative DTO. Publish it
          // immediately and do not issue a follow-up selected-day reload that
          // can race the user's first keystrokes in the newly opened editor.
          this.selectedNotes = sortDayNotes([
            ...this.selectedNotes.filter(item => item.id !== note.id),
            note,
          ]);
          this.selectedNoteId = note.id;
        }
        await refreshCalendarIfMounted();
      } catch (error) { this.error = errorMessage(error); }
      finally { this.mutationPending = false; }
    },
    selectNote(id: number): void { this.selectedNoteId = id; },
    async updateNote(id: number, patch: { title?: string | null; content?: string | null; pinned?: boolean }): Promise<void> {
      const row = this.selectedNotes.find(note => note.id === id);
      if (!row || !bridge) return;
      const optimistic: DayNote = {
        ...row,
        title: patch.title !== undefined ? patch.title : (row.title ?? null),
        content: patch.content ?? row.content,
        pinned: patch.pinned ?? row.pinned,
      };
      this.selectedNotes = sortDayNotes(this.selectedNotes.map(note => note.id === id ? optimistic : note));
      try {
        const result = await bridge.offlineUpdateNote(id, patch, row.date);
        if (result.queued) useShellStore().announce("Заметка сохранена оффлайн", "warning");
        else if (result.note) this.selectedNotes = sortDayNotes(this.selectedNotes.map(note => note.id === id ? result.note as DayNote : note));
        this.synchronizeQueuedCount();
        await refreshCalendarIfMounted();
      } catch (error) { this.error = errorMessage(error); await this.loadSelectedDate(row.date); }
    },
    async moveNote(id: number, direction: "UP" | "DOWN"): Promise<void> {
      try { this.selectedNotes = (await api.moveNote(id, direction)) ?? this.selectedNotes; await refreshCalendarIfMounted(); }
      catch (error) { this.error = errorMessage(error); }
    },
    async deleteNote(id: number): Promise<void> {
      if (!globalThis.confirm("Удалить заметку?")) return;
      try { await api.deleteNote(id); await this.loadSelectedDate(this.selectedDate); await refreshCalendarIfMounted(); }
      catch (error) { this.error = errorMessage(error); }
    },
    async openImportantCreate(date?: string, title = ""): Promise<void> {
      const targetDate = date ?? this.selectedDate;
      await this.ensureLoaded(targetDate);
      this.importantDraft = emptyImportantDraft(targetDate, this.workTimezone);
      this.importantDraft.title = title;
      this.importantEditorOpen = true;
      this.importantDetailsOpen = false;
      this.error = "";
    },
    async openImportantDetails(id: number): Promise<void> {
      await this.ensureLoaded();
      const item = this.importantDays.find(row => row.id === id) ?? null;
      if (!item) { await this.loadImportantDays(); }
      this.importantDetails = this.importantDays.find(row => row.id === id) ?? null;
      if (!this.importantDetails) { this.error = "Событие не найдено"; return; }
      this.importantDetailsOpen = true;
      this.importantEditorOpen = false;
    },
    async openImportantEdit(id: number): Promise<void> {
      await this.openImportantDetails(id);
      if (this.importantDetails) this.editImportantDetails();
    },
    editImportantDetails(): void {
      if (!this.importantDetails) return;
      this.importantDraft = importantToDraft(this.importantDetails, this.workTimezone);
      this.importantDetailsOpen = false;
      this.importantEditorOpen = true;
    },
    async saveImportant(): Promise<void> {
      if (this.mutationPending) return;
      if (!this.importantDraft.title.trim()) { this.error = "Название обязательно"; return; }
      this.mutationPending = true;
      this.error = "";
      const draft = importantDraftSnapshot(this.importantDraft);
      try {
        const body = importantInput(draft);
        if (draft.id) await api.updateImportant(draft.id, body); else await api.createImportant(body);
        this.importantEditorOpen = false;
        await Promise.all([this.loadImportantDays(), this.loadSelectedDate(draft.date)]);
        await refreshCalendarIfMounted();
        useShellStore().announce("Важное событие сохранено", "success");
      } catch (error) { await this.handleMutationError(error); }
      finally { this.mutationPending = false; }
    },
    async deleteImportant(id: number): Promise<void> {
      if (!globalThis.confirm("Удалить событие?")) return;
      try { await api.deleteImportant(id); this.importantDetailsOpen = false; await Promise.all([this.loadImportantDays(), this.loadSelectedDate(this.selectedDate)]); await refreshCalendarIfMounted(); }
      catch (error) { this.error = errorMessage(error); }
    },
    async captureInbox(text: string): Promise<boolean> {
      if (!bridge || !text.trim()) return false;
      try {
        const result = await bridge.offlineCaptureInbox(text.trim());
        if (!result.queued) await this.loadInbox();
        else useShellStore().announce("Запись добавлена в офлайн-очередь", "warning");
        this.synchronizeQueuedCount();
        return true;
      } catch (error) { this.error = errorMessage(error); return false; }
    },
    async convertInboxToTask(item: InboxItem): Promise<void> { await this.openTaskCreate(this.selectedDate, item.text, item.id); },
    async deleteInbox(id: number): Promise<void> { try { await api.deleteInbox(id); await this.loadInbox(); } catch (error) { this.error = errorMessage(error); } },
    async flushOfflineQueue(): Promise<void> {
      if (!bridge) return;
      await bridge.offlineSync();
      this.synchronizeQueuedCount();
      await this.refreshAll(this.selectedDate);
    },
    async handleMutationError(error: unknown): Promise<void> {
      if (error instanceof DutyLogApiError && error.status === 409) {
        this.conflict = "Данные изменились на сервере. DutyLog обновил экран — проверьте изменения и повторите действие.";
        await this.refreshAll(this.selectedDate);
        return;
      }
      this.error = errorMessage(error);
    },
  },
});
