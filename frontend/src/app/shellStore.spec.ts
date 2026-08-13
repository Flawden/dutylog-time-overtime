import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useShellStore } from "./shellStore";

beforeEach(() => setActivePinia(createPinia()));

function snapshot(overrides: Partial<DutyLogLegacySnapshot> = {}): DutyLogLegacySnapshot {
  return {
    version: "27.40.27",
    language: "ru",
    online: true,
    offline: { online:true, cacheReady:true, lastSyncAt:null, stale:false, pending:0, failed:0, syncing:false, syncLockedByOther:false },
    modulesLoaded: true,
    navigation: ["today", "calendar", "settings"],
    availableViews: ["today", "calendar", "tasks", "settings"],
    profile: { displayName: "Даниил Т.", initials: "ДТ", admin: false, onboardingCompleted: true },
    ...overrides,
  };
}

describe("shell store", () => {
  it("synchronizes route from Vue hash authority and workspace/profile from the legacy read model", () => {
    const store = useShellStore();
    store.synchronizeRoute("settings-profile");
    store.synchronize(snapshot({ online: false }));

    expect(store.activeRoute).toBe("settings");
    expect(store.online).toBe(false);
    expect(store.offline.pending).toBe(0);
    expect(store.primaryNavigation).toEqual(["today", "calendar", "settings"]);
    expect(store.secondaryNavigation).toEqual(["tasks"]);
    expect(store.initials).toBe("ДТ");
    expect(store.profileLoaded).toBe(true);
    expect(store.onboardingCompleted).toBe(true);

    store.synchronize(snapshot({ modules: { tasks: false, notes: true } }));
    const stableModules = store.modules;
    store.synchronize(snapshot({ modules: { tasks: false, notes: true } }));
    expect(store.modules).toBe(stableModules);
    store.synchronize(snapshot({ modules: { tasks: true, notes: true } }));
    expect(store.modules).not.toBe(stableModules);
  });

  it("adds the admin route only for an administrator with the Admin module enabled", () => {
    const store = useShellStore();
    const profile = { displayName: "Admin", initials: "AD", admin: true, onboardingCompleted: true };
    store.synchronize(snapshot({ profile }));
    expect(store.availableNavigation).toContain("admin");

    store.synchronize(snapshot({
      profile,
      modules: { admin: false },
      availableViews: ["today", "calendar", "settings", "admin"],
    }));
    expect(store.availableNavigation).not.toContain("admin");
  });

  it("expires shell toasts without retaining global product state", () => {
    vi.useFakeTimers();
    const store = useShellStore();
    store.announce("Сохранено", "success");
    store.synchronizeSaveFeedback({ state:"saved", message:"✓" });
    expect(store.toasts).toHaveLength(1);
    expect(store.saveFeedback?.message).toBe("✓");
    vi.advanceTimersByTime(1500);
    expect(store.saveFeedback).toBeNull();
    vi.advanceTimersByTime(2100);
    expect(store.toasts).toHaveLength(0);
    vi.useRealTimers();
  });
});
