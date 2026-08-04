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

  interface Window {
    DutyLogLegacyPlatform?: DutyLogLegacyPlatform;
    DutyLogVuePlatform?: DutyLogVuePlatform;
    __dutylogVueReady?: Promise<DutyLogVuePlatform>;
  }
}
