import type { DutyLogAbsenceTimeBankDomain } from "@/features/absence-time-bank/types/domain";
import type { DutyLogCalendarTimelineDomain } from "@/features/calendar-timeline/types/domain";

export {};

declare global {
  interface DutyLogLegacyProfileSnapshot {
    displayName: string;
    initials: string;
    admin: boolean;
  }

  interface DutyLogLegacySnapshot {
    version: string;
    language: "ru" | "en";
    route: string;
    online: boolean;
    modulesLoaded: boolean;
    navigation: readonly string[];
    availableViews: readonly string[];
    profile: DutyLogLegacyProfileSnapshot | null;
  }

  interface DutyLogLegacyPlatform {
    readonly version: string;
    snapshot(): DutyLogLegacySnapshot;
    navigate(view: string): void;
    openModal(id: string, focusId?: string | null): void;
    logout(): void;
    retireDomainOwners?(domain: "absence-time-bank" | "calendar-timeline"): void;
    attachCalendarEditor?(hostId: string): void;
    openCalendarDay?(date: string): void;
    closeCalendarDay?(): void;
    openTaskCreate?(date: string): void;
    openTaskDetails?(id: number): void;
    openQuickActions?(date: string): void;
    openImportantDetails?(id: number): void;
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
    navigateLegacy(view: string): void;
  }

  interface DutyLogVueDomains {
    readonly absenceTimeBank?: DutyLogAbsenceTimeBankDomain;
    readonly calendarTimeline?: DutyLogCalendarTimelineDomain;
  }

  interface Window {
    DutyLogLegacyPlatform?: DutyLogLegacyPlatform;
    DutyLogVuePlatform?: DutyLogVuePlatform;
    DutyLogVueDomains?: DutyLogVueDomains;
    __dutylogVueReady?: Promise<DutyLogVuePlatform>;
  }
}
