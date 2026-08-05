import { defineStore } from "pinia";
import { DutyLogApiError } from "@/platform/api/httpClient";
import { useShellStore } from "@/app/shellStore";
import { publishAbsenceTimeBankProjection } from "@/platform/bridge/legacyBridge";
import { createAbsenceTimeBankApi, type AbsenceTimeBankApi } from "../api/absenceTimeBankApi";
import type {
  AbsenceComposerOpenOptions,
  AbsenceDraft,
  AbsencePreview,
  ActualWorkInterval,
  CreditDraft,
  LedgerIntegrityReadModel,
  LedgerRangeMode,
  OvertimeAccountReadModel,
  OvertimeCreditPreview,
  QuickScenario,
  ScenarioDraft,
  TimeBankTab,
  TimeCompensationSummary,
  VacationPlannerReadModel,
} from "../types/domain";
import {
  absenceCreateBody,
  absencePatchBody,
  applyScenarioToCreditDraft,
  creditDraftFromRow,
  creditHoursFromInterval,
  defaultCompensation,
  draftFromPeriod,
  newAbsenceDraft,
  newCreditDraft,
  newScenarioDraft,
  scenarioCreateBody,
  scenarioDraftFromRow,
  scenarioUpdateBody,
  todayIso,
  toLocalDateTimeInput,
} from "../types/model";

let api: AbsenceTimeBankApi = createAbsenceTimeBankApi();
let previewController: AbortController | null = null;
let creditPreviewController: AbortController | null = null;
let refreshSequence = 0;

export function installAbsenceTimeBankApiForTests(next: AbsenceTimeBankApi): () => void {
  const previous = api;
  api = next;
  return () => { api = previous; };
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

function plusDays(date: string, days: number): string {
  const value = new Date(`${date}T12:00:00`);
  value.setDate(value.getDate() + days);
  return todayIso(value);
}

export const useAbsenceTimeBankStore = defineStore("absence-time-bank", {
  state: () => ({
    planner: null as VacationPlannerReadModel | null,
    account: null as OvertimeAccountReadModel | null,
    compensation: null as TimeCompensationSummary | null,
    integrity: null as LedgerIntegrityReadModel | null,
    actualWork: [] as ActualWorkInterval[],
    scenarios: [] as QuickScenario[],
    range: { from: "", to: "" },
    rangeMode: "year" as LedgerRangeMode,
    loading: false,
    loaded: false,
    error: "",
    conflict: "",
    mutationPending: false,
    absenceModalOpen: false,
    creditModalOpen: false,
    guideOpen: false,
    absenceDraft: newAbsenceDraft() as AbsenceDraft,
    creditDraft: newCreditDraft() as CreditDraft,
    scenarioDraft: newScenarioDraft() as ScenarioDraft,
    absencePreview: null as AbsencePreview | null,
    creditPreview: null as OvertimeCreditPreview | null,
    previewLoading: false,
    creditPreviewLoading: false,
    timeBankTab: "overview" as TimeBankTab,
    periodFilter: "all" as "all" | "active" | "history",
    search: "",
    fifoForecastHours: 8,
    focusAbsenceUsageId: null as number | null,
    scenarioManagerMode: "closed" as "closed" | "list" | "form",
    scenarioReturnAfterSave: "credit" as "credit" | "list",
  }),
  getters: {
    selectedAbsenceType(state) {
      return state.planner?.types.find(type => Number(type.id) === Number(state.absenceDraft.typeId)) ?? null;
    },
    filteredAbsences(state) {
      const query = state.search.trim().toLocaleLowerCase("ru-RU");
      return (state.planner?.absences ?? []).filter(period => {
        const status = String(period.status ?? "");
        if (state.periodFilter === "active" && ["REJECTED", "CANCELLED", "COMPLETED"].includes(status)) return false;
        if (state.periodFilter === "history" && !["REJECTED", "CANCELLED", "COMPLETED"].includes(status)) return false;
        if (!query) return true;
        return `${period.title ?? ""} ${period.typeName ?? ""} ${period.startDate ?? ""} ${period.endDate ?? ""}`.toLocaleLowerCase("ru-RU").includes(query);
      });
    },
  },
  actions: {
    async refresh(referenceDate: string = todayIso(), rangeMode?: LedgerRangeMode): Promise<void> {
      const resolvedRangeMode = rangeMode ?? this.rangeMode;
      const sequence = ++refreshSequence;
      this.loading = true;
      this.error = "";
      try {
        const result = await api.load(referenceDate, resolvedRangeMode);
        if (sequence !== refreshSequence) return;
        this.planner = result.planner;
        this.account = result.account;
        this.compensation = result.compensation;
        this.integrity = result.integrity;
        this.actualWork = result.actualWork;
        this.scenarios = result.scenarios;
        this.range = result.range;
        this.rangeMode = resolvedRangeMode;
        this.loaded = true;
        if (typeof window !== "undefined") {
          publishAbsenceTimeBankProjection(window, {
            planner: this.planner,
            account: this.account,
            referenceDate,
          });
        }
      } catch (error) {
        if (sequence === refreshSequence) this.error = this.errorMessage(error);
      } finally {
        if (sequence === refreshSequence) this.loading = false;
      }
    },
    async ensureLoaded(): Promise<void> {
      if (!this.loaded) await this.refresh();
    },
    async setRangeMode(mode: LedgerRangeMode): Promise<void> {
      if (this.rangeMode === mode && this.loaded) return;
      await this.refresh(todayIso(), mode);
    },
    openGuide(): void { this.guideOpen = true; },
    closeGuide(): void { this.guideOpen = false; },
    async openTimeBankUsage(absenceId: number | null = null): Promise<void> {
      this.timeBankTab = "usage";
      this.focusAbsenceUsageId = absenceId;
      await this.ensureLoaded();
    },
    clearUsageFocus(): void { this.focusAbsenceUsageId = null; },
    async openAbsenceComposer(options: AbsenceComposerOpenOptions = {}): Promise<void> {
      await this.ensureLoaded();
      const date = options.date || todayIso();
      const draft = newAbsenceDraft(date);
      const types = this.planner?.types ?? [];
      const requested = String(options.systemCode ?? "").toUpperCase();
      const type = types.find(item => String(item.systemCode ?? "").toUpperCase() === requested) ?? types[0] ?? null;
      draft.typeId = Number(type?.id ?? 0) || null;
      draft.compensationPolicy = defaultCompensation(type);
      draft.title = String(options.reason ?? "");
      this.absenceDraft = draft;
      this.absencePreview = null;
      this.conflict = "";
      this.error = "";
      this.absenceModalOpen = true;
      await this.previewAbsence();
    },
    async openAbsenceEditor(id: number): Promise<void> {
      await this.ensureLoaded();
      let period = this.planner?.absences.find(item => Number(item.id) === Number(id));
      if (!period) {
        await this.refresh();
        period = this.planner?.absences.find(item => Number(item.id) === Number(id));
      }
      if (!period) throw new Error("Отсутствие не найдено");
      this.absenceDraft = draftFromPeriod(period);
      this.absencePreview = null;
      this.conflict = "";
      this.error = "";
      this.absenceModalOpen = true;
      await this.previewAbsence();
    },
    closeAbsenceComposer(): void {
      previewController?.abort();
      previewController = null;
      this.previewLoading = false;
      this.absenceModalOpen = false;
      this.absencePreview = null;
      this.error = "";
    },
    syncCompensationFromType(): void {
      this.absenceDraft.compensationPolicy = defaultCompensation(this.selectedAbsenceType);
    },
    syncAbsenceCoverage(): void {
      if (this.absenceDraft.coverage === "PARTIAL") this.absenceDraft.endDate = this.absenceDraft.startDate;
    },
    setAbsenceDuration(days: number): void {
      const safeDays = Math.max(1, Math.round(days));
      this.absenceDraft.endDate = plusDays(this.absenceDraft.startDate, safeDays - 1);
    },
    async previewAbsence(): Promise<void> {
      const draft = this.absenceDraft;
      if (!draft.typeId || !draft.startDate || !draft.endDate) return;
      previewController?.abort();
      const controller = new AbortController();
      previewController = controller;
      this.previewLoading = true;
      try {
        const result = await api.previewAbsence({
          typeId: draft.typeId,
          startDate: draft.startDate,
          endDate: draft.coverage === "PARTIAL" ? draft.startDate : draft.endDate,
          excludePeriodId: draft.id,
          coverage: draft.coverage,
          startTime: draft.coverage === "PARTIAL" ? draft.startTime : null,
          endTime: draft.coverage === "PARTIAL" ? draft.endTime : null,
          compensationPolicy: draft.compensationPolicy,
        }, controller.signal);
        if (previewController === controller) this.absencePreview = result;
      } catch (error) {
        if (previewController === controller && !isAbortError(error)) this.error = this.errorMessage(error);
      } finally {
        if (previewController === controller) {
          previewController = null;
          this.previewLoading = false;
        }
      }
    },
    async saveAbsence(): Promise<void> {
      if (this.mutationPending) return;
      if (!this.absenceDraft.typeId) { this.error = "Выберите тип отсутствия"; return; }
      this.mutationPending = true;
      this.error = "";
      this.conflict = "";
      try {
        if (this.absenceDraft.id) {
          await api.updateAbsence(this.absenceDraft.id, absencePatchBody(this.absenceDraft));
        } else {
          await api.createAbsence(absenceCreateBody(this.absenceDraft));
        }
        const referenceDate = this.absenceDraft.startDate;
        this.absenceModalOpen = false;
        await this.refresh(referenceDate);
        useShellStore().announce("Отсутствие сохранено", "success");
      } catch (error) {
        await this.handleMutationError(error);
      } finally {
        this.mutationPending = false;
      }
    },
    async deleteAbsence(id: number): Promise<void> {
      if (this.mutationPending || !globalThis.confirm("Удалить это отсутствие?")) return;
      this.mutationPending = true;
      this.error = "";
      try {
        await api.deleteAbsence(id);
        this.absenceModalOpen = false;
        await this.refresh();
        useShellStore().announce("Отсутствие удалено", "success");
      } catch (error) {
        await this.handleMutationError(error);
      } finally {
        this.mutationPending = false;
      }
    },
    async openCreditEditor(date: string | null = null): Promise<void> {
      await this.ensureLoaded();
      this.creditDraft = newCreditDraft(date || todayIso());
      this.creditPreview = null;
      this.creditModalOpen = true;
      this.scenarioManagerMode = "closed";
      this.conflict = "";
      this.error = "";
    },
    editCredit(id: number): void {
      const row = this.account?.credits.find(item => Number(item.id) === Number(id));
      if (!row) return;
      this.creditDraft = creditDraftFromRow(row);
      this.creditPreview = null;
      this.creditModalOpen = true;
      this.scenarioManagerMode = "closed";
      this.error = "";
    },
    closeCreditEditor(): void {
      creditPreviewController?.abort();
      creditPreviewController = null;
      this.creditPreviewLoading = false;
      this.creditModalOpen = false;
      this.scenarioManagerMode = "closed";
      this.creditPreview = null;
      this.error = "";
    },
    updateCreditHoursFromDraft(): void {
      this.creditDraft.hours = creditHoursFromInterval(this.creditDraft);
    },
    async previewCredit(): Promise<void> {
      creditPreviewController?.abort();
      const controller = new AbortController();
      creditPreviewController = controller;
      this.creditPreviewLoading = true;
      try {
        const result = await api.previewCredit(api.creditBody(this.creditDraft), controller.signal);
        if (creditPreviewController === controller) {
          this.creditPreview = result;
          if (result) this.creditDraft.hours = Number(result.creditedHours ?? 0);
        }
      } catch (error) {
        if (creditPreviewController === controller && !isAbortError(error)) this.error = this.errorMessage(error);
      } finally {
        if (creditPreviewController === controller) {
          creditPreviewController = null;
          this.creditPreviewLoading = false;
        }
      }
    },
    async fillCreditFromShift(): Promise<void> {
      this.error = "";
      const shift = await api.shiftForDate(this.creditDraft.date);
      if (!shift) { this.error = "На выбранном дне нет смены"; return; }
      this.creditDraft.startDateTime = toLocalDateTimeInput(shift.workStart);
      this.creditDraft.endDateTime = toLocalDateTimeInput(shift.workEnd);
      this.creditDraft.breakMinutes = Number(shift.breakMinutes ?? 0);
      this.creditDraft.plannedHours = Math.round((Number(shift.netMinutes ?? 0) / 60) * 100) / 100;
      await this.previewCredit();
    },
    async saveCredit(): Promise<void> {
      if (this.mutationPending) return;
      this.mutationPending = true;
      this.error = "";
      this.conflict = "";
      try {
        const body = api.creditBody(this.creditDraft);
        if (this.creditDraft.id) await api.updateCredit(this.creditDraft.id, body);
        else await api.createCredit(body);
        const referenceDate = this.creditDraft.date;
        this.creditModalOpen = false;
        await this.refresh(referenceDate);
        useShellStore().announce("Начисление сохранено", "success");
      } catch (error) {
        await this.handleMutationError(error);
      } finally {
        this.mutationPending = false;
      }
    },
    async deleteCredit(id: number): Promise<void> {
      if (this.mutationPending || !globalThis.confirm("Удалить начисление переработки?")) return;
      this.mutationPending = true;
      this.error = "";
      try {
        await api.deleteCredit(id);
        await this.refresh();
        useShellStore().announce("Начисление удалено", "success");
      } catch (error) {
        await this.handleMutationError(error);
      } finally {
        this.mutationPending = false;
      }
    },
    async applyScenario(id: number): Promise<void> {
      const scenario = this.scenarios.find(item => Number(item.id) === id);
      if (!scenario) return;
      this.error = "";
      const shift = await api.shiftForDate(this.creditDraft.date);
      if (!shift) { this.error = "Для сценария нужна смена на выбранной дате"; return; }
      this.creditDraft = applyScenarioToCreditDraft(this.creditDraft, scenario, shift);
      await this.previewCredit();
    },
    openScenarioManager(): void {
      this.scenarioManagerMode = "list";
      this.scenarioReturnAfterSave = "list";
    },
    openScenarioDraftFromCredit(): void {
      const next = newScenarioDraft();
      next.endOffsetMinutes = Math.max(0, Math.round(this.creditDraft.hours * 60));
      next.customBreakMinutes = this.creditDraft.breakMinutes;
      next.breakMode = this.creditDraft.breakMinutes > 0 ? "CUSTOM" : "ZERO";
      next.customPlannedHours = this.creditDraft.plannedHours;
      next.plannedMode = this.creditDraft.plannedHours > 0 ? "CUSTOM" : "ZERO";
      next.reasonTemplate = this.creditDraft.reason;
      this.scenarioDraft = next;
      this.scenarioManagerMode = "form";
      this.scenarioReturnAfterSave = "credit";
    },
    openNewScenarioFromManager(): void {
      this.scenarioDraft = newScenarioDraft();
      this.scenarioManagerMode = "form";
      this.scenarioReturnAfterSave = "list";
    },
    editScenario(id: number): void {
      const row = this.scenarios.find(item => Number(item.id) === id);
      if (!row) return;
      this.scenarioDraft = scenarioDraftFromRow(row);
      this.scenarioManagerMode = "form";
      this.scenarioReturnAfterSave = "list";
    },
    cancelScenarioForm(): void {
      this.scenarioManagerMode = this.scenarioReturnAfterSave === "credit" ? "closed" : "list";
    },
    closeScenarioManager(): void { this.scenarioManagerMode = "closed"; },
    async reloadScenarios(): Promise<void> {
      try {
        const result = await api.listScenarios();
        this.scenarios = Array.isArray(result) ? result : [];
      } catch {
        this.scenarios = [];
      }
    },
    async saveScenario(): Promise<void> {
      if (this.mutationPending) return;
      if (!this.scenarioDraft.name.trim()) { this.error = "Название сценария обязательно"; return; }
      this.mutationPending = true;
      this.error = "";
      try {
        if (this.scenarioDraft.id) {
          await api.updateScenario(this.scenarioDraft.id, scenarioUpdateBody(this.scenarioDraft));
        } else {
          await api.createScenario(scenarioCreateBody(this.scenarioDraft));
        }
        await this.reloadScenarios();
        this.scenarioManagerMode = this.scenarioReturnAfterSave === "credit" ? "closed" : "list";
        useShellStore().announce("Сценарий сохранён", "success");
      } catch (error) {
        await this.handleMutationError(error);
      } finally {
        this.mutationPending = false;
      }
    },
    async deleteScenario(id: number): Promise<void> {
      if (this.mutationPending || !globalThis.confirm("Удалить сценарий переработки?")) return;
      this.mutationPending = true;
      try {
        await api.deleteScenario(id);
        await this.reloadScenarios();
        useShellStore().announce("Сценарий удалён", "success");
      } catch (error) {
        await this.handleMutationError(error);
      } finally {
        this.mutationPending = false;
      }
    },
    async handleMutationError(error: unknown): Promise<void> {
      if (error instanceof DutyLogApiError && error.status === 409) {
        this.conflict = "Данные изменились на сервере. DutyLog обновил экран — проверьте изменения и повторите действие.";
        await this.refresh();
        return;
      }
      this.error = this.errorMessage(error);
    },
    errorMessage(error: unknown): string {
      if (error instanceof DutyLogApiError) return error.code ? `${error.message} (${error.code})` : error.message;
      return error instanceof Error ? error.message : "Не удалось выполнить запрос";
    },
  },
});
