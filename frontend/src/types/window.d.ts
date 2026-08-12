import type { DutyLogAbsenceTimeBankDomain } from "@/features/absence-time-bank/types/domain";
import type { DutyLogCalendarTimelineDomain } from "@/features/calendar-timeline/types/domain";
import type { DutyLogProductivityDomain } from "@/features/productivity/types/domain";
import type { DutyLogSettingsWorkspaceDomain } from "@/features/settings-workspace/types/domain";

export {};

declare global {
  interface DutyLogLegacyProfileSnapshot {
    displayName: string;
    initials: string;
    admin: boolean;
    onboardingCompleted: boolean;
  }

  interface DutyLogLegacySnapshot {
    version: string;
    language: "ru" | "en";
    online: boolean;
    modulesLoaded: boolean;
    modules?: Readonly<Record<string, boolean>>;
    navigation: readonly string[];
    availableViews: readonly string[];
    profile: DutyLogLegacyProfileSnapshot | null;
  }

  interface DutyLogLegacyPlatform {
    readonly version: string;
    snapshot(): DutyLogLegacySnapshot;
    logout(): void;
    retireDomainOwners?(domain: "absence-time-bank" | "calendar-timeline" | "productivity" | "settings-workspace"): void;
    settingsAppearanceSnapshot?(): Record<string, unknown> | null;
    previewAppearance?(appearance: Record<string, unknown>): Record<string, unknown> | null;
    synchronizeProfile?(profile: Record<string, unknown>): void;
    previewLanguage?(language: "ru" | "en"): void;
    previewModuleEnabled?(key: string, enabled: boolean): void;
    commitModuleList?(modules: readonly Record<string, unknown>[]): Promise<void>;
    restoreModuleList?(modules: readonly Record<string, unknown>[]): void;
    writeCalendarDay?(date: string, patch: Record<string, unknown>): Promise<{ queued: boolean; day: unknown | null }>;
    offlineUpdateNote?(id: number, patch: Record<string, unknown>, date: string): Promise<{ queued: boolean; note: unknown | null }>;
    offlineSetTaskDone?(id: number, done: boolean): Promise<{ queued: boolean; task?: unknown }>;
    offlineCaptureInbox?(text: string): Promise<{ queued: boolean; item: unknown }>;
    offlineSync?(): Promise<void>;
    offlinePending?(): number;
    offlineSelectedDay?(date: string): Promise<{ tasks: unknown[]; notes: unknown[]; important: unknown[] }>;
    offlineCalendarSnapshot?(focusDate: string): Promise<{ bundle: unknown; savedAt: string | null } | null>;
    subscribe(listener: (snapshot: DutyLogLegacySnapshot) => void): () => void;
  }

  interface DutyLogVuePlatform {
    readonly version: string;
    readonly architecture: string;
    readonly mountedAt: string;
    snapshot(): Readonly<{
      releaseVersion: string;
      architecture: string;
      phase: string;
      legacyConnected: boolean;
      shellReady: boolean;
    }>;
    diagnostics(): Readonly<{
      releaseVersion: string;
      route: string;
      requestId: string | null;
      lastRequest: Readonly<{ method: string; url: string; status: number; requestId: string }> | null;
      fatal: Readonly<{
        source: "vue" | "promise" | "boot" | "network";
        message: string;
        route: string;
        releaseVersion: string;
        requestId: string | null;
        occurredAt: string;
      }> | null;
    }>;
    navigate(view: string): void;
  }

  interface DutyLogVueDomains {
    readonly absenceTimeBank?: DutyLogAbsenceTimeBankDomain;
    readonly calendarTimeline?: DutyLogCalendarTimelineDomain;
    readonly productivity?: DutyLogProductivityDomain;
    readonly settingsWorkspace?: DutyLogSettingsWorkspaceDomain;
  }

  interface Window {
    DutyLogLegacyPlatform?: DutyLogLegacyPlatform;
    DutyLogVuePlatform?: DutyLogVuePlatform;
    DutyLogVueDomains?: DutyLogVueDomains;
    __dutylogVueReady?: Promise<DutyLogVuePlatform>;
  }
}
