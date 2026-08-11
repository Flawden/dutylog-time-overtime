export const VUE_READY_EVENT = "dutylog:vue-ready";
export const LEGACY_COMMAND_EVENT = "dutylog:legacy-command";
export const LEGACY_STATE_EVENT = "dutylog:legacy-state";
export const ABSENCE_TIME_BANK_PROJECTION_EVENT = "dutylog:absence-time-bank-projection";
export const CALENDAR_TIMELINE_PROJECTION_EVENT = "dutylog:calendar-timeline-projection";
export const OFFLINE_SYNC_COMPLETE_EVENT = "dutylog:offline-sync-complete";

export interface AbsenceTimeBankProjectionSnapshot {
  planner: unknown;
  account: unknown;
  referenceDate: string;
}

export interface CalendarTimelineProjectionSnapshot {
  bundle: unknown;
  focusDate: string;
  mode: "month" | "week" | "day";
}

export type LegacyCommand =
  | { type: "navigate"; view: string }
  | { type: "open-modal"; id: string; focusId?: string | null }
  | { type: "logout" };

export interface LegacyBridge {
  connected(): boolean;
  snapshot(): DutyLogLegacySnapshot | null;
  navigate(view: string): void;
  openModal(id: string, focusId?: string | null): void;
  logout(): void;
  retireDomainOwners(domain: "absence-time-bank" | "calendar-timeline" | "productivity" | "settings-workspace"): void;
  settingsAppearanceSnapshot(): Record<string, unknown> | null;
  previewAppearance(appearance: Record<string, unknown>): Record<string, unknown> | null;
  synchronizeProfile(profile: Record<string, unknown>): void;
  previewLanguage(language: "ru" | "en"): void;
  previewModuleEnabled(key: string, enabled: boolean): void;
  commitModuleList(modules: readonly Record<string, unknown>[]): Promise<void>;
  restoreModuleList(modules: readonly Record<string, unknown>[]): void;
  writeCalendarDay(date: string, patch: Record<string, unknown>): Promise<{ queued: boolean; day: unknown | null }>;
  openTaskCreate(date: string): void;
  openTaskDetails(id: number): void;
  openImportantDetails(id: number): void;
  offlineUpdateNote(id: number, patch: Record<string, unknown>, date: string): Promise<{ queued: boolean; note: unknown | null }>;
  offlineSetTaskDone(id: number, done: boolean): Promise<{ queued: boolean; task?: unknown }>;
  offlineCaptureInbox(text: string): Promise<{ queued: boolean; item: unknown }>;
  offlineSync(): Promise<void>;
  offlinePending(): number;
  offlineSelectedDay(date: string): Promise<{ tasks: unknown[]; notes: unknown[]; important: unknown[] }>;
  offlineCalendarSnapshot(focusDate: string): Promise<{ bundle: unknown; savedAt: string | null } | null>;
  subscribe(listener: (snapshot: DutyLogLegacySnapshot) => void): () => void;
}

function normalizeView(view: string): string {
  return view.trim().replace(/^#/, "");
}

export function createLegacyBridge(target: Window = window): LegacyBridge {
  const adapter = () => target.DutyLogLegacyPlatform;
  const emitFallback = (command: LegacyCommand) => {
    target.dispatchEvent(new CustomEvent<LegacyCommand>(LEGACY_COMMAND_EVENT, { detail: command }));
  };

  return {
    connected: () => Boolean(adapter()),
    snapshot: () => adapter()?.snapshot() ?? null,
    navigate(view: string) {
      const normalized = normalizeView(view);
      if (!normalized) return;
      if (adapter()) adapter()?.navigate(normalized);
      else emitFallback({ type: "navigate", view: normalized });
    },
    openModal(id: string, focusId: string | null = null) {
      if (!id.trim()) return;
      if (adapter()) adapter()?.openModal(id, focusId);
      else emitFallback({ type: "open-modal", id, focusId });
    },
    logout() {
      if (adapter()) adapter()?.logout();
      else emitFallback({ type: "logout" });
    },
    retireDomainOwners(domain) {
      adapter()?.retireDomainOwners?.(domain);
    },
    settingsAppearanceSnapshot() { return adapter()?.settingsAppearanceSnapshot?.() ?? null; },
    previewAppearance(appearance) { return adapter()?.previewAppearance?.(appearance) ?? null; },
    synchronizeProfile(profile) { adapter()?.synchronizeProfile?.(profile); },
    previewLanguage(language) { adapter()?.previewLanguage?.(language); },
    previewModuleEnabled(key, enabled) { adapter()?.previewModuleEnabled?.(key, enabled); },
    async commitModuleList(modules) { await adapter()?.commitModuleList?.(modules); },
    restoreModuleList(modules) { adapter()?.restoreModuleList?.(modules); },
    async writeCalendarDay(date: string, patch: Record<string, unknown>) {
      return (await adapter()?.writeCalendarDay?.(date, patch)) ?? { queued: false, day: null };
    },
    openTaskCreate(date: string) { adapter()?.openTaskCreate?.(date); },
    openTaskDetails(id: number) { adapter()?.openTaskDetails?.(id); },
    openImportantDetails(id: number) { adapter()?.openImportantDetails?.(id); },
    async offlineUpdateNote(id: number, patch: Record<string, unknown>, date: string) {
      return (await adapter()?.offlineUpdateNote?.(id, patch, date)) ?? { queued: false, note: null };
    },
    async offlineSetTaskDone(id: number, done: boolean) {
      return (await adapter()?.offlineSetTaskDone?.(id, done)) ?? { queued: false };
    },
    async offlineCaptureInbox(text: string) {
      return (await adapter()?.offlineCaptureInbox?.(text)) ?? { queued: false, item: null };
    },
    async offlineSync() { await adapter()?.offlineSync?.(); },
    offlinePending() { return Number(adapter()?.offlinePending?.() ?? 0); },
    async offlineSelectedDay(date: string) {
      return (await adapter()?.offlineSelectedDay?.(date)) ?? { tasks: [], notes: [], important: [] };
    },
    async offlineCalendarSnapshot(focusDate: string) {
      return (await adapter()?.offlineCalendarSnapshot?.(focusDate)) ?? null;
    },
    subscribe(listener) {
      const direct = adapter()?.subscribe(listener);
      if (direct) return direct;
      const handler = (event: Event) => listener((event as CustomEvent<DutyLogLegacySnapshot>).detail);
      target.addEventListener(LEGACY_STATE_EVENT, handler);
      return () => target.removeEventListener(LEGACY_STATE_EVENT, handler);
    },
  };
}

export function announceVueReady(target: Window, platform: DutyLogVuePlatform): void {
  target.dispatchEvent(new CustomEvent<DutyLogVuePlatform>(VUE_READY_EVENT, { detail: platform }));
}

export function publishAbsenceTimeBankProjection(
  target: Window,
  snapshot: AbsenceTimeBankProjectionSnapshot,
): void {
  target.dispatchEvent(new CustomEvent<AbsenceTimeBankProjectionSnapshot>(
    ABSENCE_TIME_BANK_PROJECTION_EVENT,
    { detail: snapshot },
  ));
}

export function publishCalendarTimelineProjection(
  target: Window,
  snapshot: CalendarTimelineProjectionSnapshot,
): void {
  target.dispatchEvent(new CustomEvent<CalendarTimelineProjectionSnapshot>(
    CALENDAR_TIMELINE_PROJECTION_EVENT,
    { detail: snapshot },
  ));
}
