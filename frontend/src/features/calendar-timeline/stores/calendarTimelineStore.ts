import { defineStore } from "pinia";
import { calendarTimelineApi } from "../api/calendarTimelineApi";
import type { CalendarMode, CalendarRangeBundle, CalendarTimelineProjectionSnapshot } from "../types/domain";
import { calendarLoadRange, navigateDate, normalizeCalendarBundle, todayIso, validDate } from "../types/model";
import { publishCalendarTimelineProjection } from "@/platform/bridge/legacyBridge";

const MODE_KEY = "dutylog.calendar.mode.v2";
const FOCUS_KEY = "dutylog.calendar.focus.v2";
let readSequence = 0;
let activeApi = calendarTimelineApi;

type CalendarTimelineOfflineSnapshot = Readonly<{ bundle: unknown; savedAt: string | null }>;
type CalendarTimelineOfflineSource = (focusDate: string) => Promise<CalendarTimelineOfflineSnapshot | null>;
let activeOfflineSource: CalendarTimelineOfflineSource | null = null;

function storageGet(key: string): string | null {
  try { return typeof localStorage === "undefined" ? null : localStorage.getItem(key); } catch { return null; }
}
function storageSet(key: string, value: string): void {
  try { if (typeof localStorage !== "undefined") localStorage.setItem(key, value); } catch { /* private mode */ }
}

export function installCalendarTimelineApiForTests(api: typeof calendarTimelineApi): () => void {
  const previous = activeApi;
  activeApi = api;
  return () => { activeApi = previous; };
}

export function installCalendarTimelineOfflineSource(source: CalendarTimelineOfflineSource): () => void {
  const previous = activeOfflineSource;
  activeOfflineSource = source;
  return () => { if (activeOfflineSource === source) activeOfflineSource = previous; };
}

function canUseOfflineFallback(error: unknown): boolean {
  if (typeof navigator !== "undefined" && navigator.onLine === false) return true;
  if (error instanceof TypeError) return true;
  const message = error instanceof Error ? error.message.toLowerCase() : String(error ?? "").toLowerCase();
  return message.includes("network") || message.includes("failed to fetch") || message.includes("load failed") || message.includes("offline") || message.includes("нет связи");
}

async function loadOfflineCalendar(focusDate: string): Promise<CalendarRangeBundle | null> {
  if (!activeOfflineSource) return null;
  try {
    const snapshot = await activeOfflineSource(focusDate);
    if (!snapshot?.bundle) return null;
    const range = calendarLoadRange(focusDate);
    return normalizeCalendarBundle(snapshot.bundle, range.from, range.to);
  } catch {
    return null;
  }
}

function initialMode(): CalendarMode {
  const value = storageGet(MODE_KEY);
  return value === "week" || value === "day" ? value : "month";
}

export const useCalendarTimelineStore = defineStore("dutylog-calendar-timeline", {
  state: () => ({
    mode: initialMode() as CalendarMode,
    focusDate: validDate(storageGet(FOCUS_KEY), todayIso()),
    workDate: todayIso(),
    bundle: null as CalendarRangeBundle | null,
    loading: false,
    loaded: false,
    error: "" as string,
  }),
  getters: {
    range(state): { from: string; to: string } { return state.bundle ? { from: state.bundle.from, to: state.bundle.to } : calendarLoadRange(state.focusDate); },
    atToday(state): boolean { return state.focusDate === state.workDate; },
  },
  actions: {
    persist(): void {
      storageSet(MODE_KEY, this.mode);
      storageSet(FOCUS_KEY, this.focusDate);
    },
    async ensureLoaded(): Promise<void> { if (!this.loaded) await this.refresh(); },
    async ensureTodayLoaded(): Promise<void> {
      if (!this.loaded || this.workDate < this.range.from || this.workDate > this.range.to) await this.refresh(true);
    },
    async refresh(preferWorkDate = false): Promise<void> {
      const sequence = ++readSequence;
      this.loading = true;
      this.error = "";
      try {
        const result = await activeApi.load(this.focusDate, preferWorkDate);
        if (sequence !== readSequence) return;
        this.bundle = result.bundle;
        this.workDate = result.workDate;
        if (preferWorkDate) {
          this.focusDate = result.focusDate;
          this.persist();
        }
        this.loaded = true;
        const snapshot: CalendarTimelineProjectionSnapshot = { bundle: result.bundle, focusDate: this.focusDate, mode: this.mode };
        if (typeof window !== "undefined") publishCalendarTimelineProjection(window, snapshot);
      } catch (error) {
        if (sequence !== readSequence) return;
        const fallbackFocus = preferWorkDate ? this.workDate : this.focusDate;
        const offlineBundle = canUseOfflineFallback(error) ? await loadOfflineCalendar(fallbackFocus) : null;
        if (sequence !== readSequence) return;
        if (offlineBundle) {
          this.bundle = offlineBundle;
          if (preferWorkDate) {
            this.focusDate = fallbackFocus;
            this.persist();
          }
          this.loaded = true;
          const snapshot: CalendarTimelineProjectionSnapshot = { bundle: offlineBundle, focusDate: this.focusDate, mode: this.mode };
          if (typeof window !== "undefined") publishCalendarTimelineProjection(window, snapshot);
          return;
        }
        this.error = error instanceof Error ? error.message : "Не удалось загрузить календарь";
      } finally {
        if (sequence === readSequence) this.loading = false;
      }
    },
    async openDate(date: string, mode?: CalendarMode): Promise<void> {
      const resolvedMode = mode ?? this.mode;
      const next = validDate(date, this.workDate);
      const currentRange = this.range;
      this.focusDate = next;
      this.mode = resolvedMode;
      this.persist();
      if (!this.loaded || next < currentRange.from || next > currentRange.to) await this.refresh();
    },
    async setMode(mode: CalendarMode): Promise<void> {
      this.mode = mode;
      this.persist();
    },
    async navigate(delta: number): Promise<void> {
      const next = navigateDate(this.focusDate, this.mode, delta);
      await this.openDate(next, this.mode);
    },
    async goToday(mode?: CalendarMode): Promise<void> {
      await this.openDate(this.workDate || todayIso(), mode ?? this.mode);
    },
    async toggleLayer(id: number, visible: boolean): Promise<void> {
      const layer = this.bundle?.calendarLayers.find(item => Number(item.id) === Number(id));
      if (layer) layer.visible = visible;
      try { await activeApi.setLayerVisibility(id, visible); }
      catch (error) {
        if (layer) layer.visible = !visible;
        this.error = error instanceof Error ? error.message : "Не удалось изменить слой";
      }
    },
  },
});
