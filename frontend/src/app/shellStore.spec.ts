import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useShellStore } from "./shellStore";

beforeEach(() => setActivePinia(createPinia()));

function snapshot(overrides: Partial<DutyLogLegacySnapshot> = {}): DutyLogLegacySnapshot {
  return {
    version: "27.37.1",
    language: "ru",
    route: "calendar",
    online: true,
    modulesLoaded: true,
    navigation: ["today", "calendar", "settings"],
    availableViews: ["today", "calendar", "tasks", "settings"],
    profile: { displayName: "Даниил Т.", initials: "ДТ", admin: false },
    ...overrides,
  };
}

describe("shell store", () => {
  it("synchronizes route, workspace navigation and profile from the legacy read model", () => {
    const store = useShellStore();
    store.synchronize(snapshot({ route: "settings-profile", online: false }));

    expect(store.activeRoute).toBe("settings");
    expect(store.online).toBe(false);
    expect(store.primaryNavigation).toEqual(["today", "calendar", "settings"]);
    expect(store.secondaryNavigation).toEqual(["tasks"]);
    expect(store.initials).toBe("ДТ");
  });

  it("adds the admin route only for an administrator", () => {
    const store = useShellStore();
    store.synchronize(snapshot({ profile: { displayName: "Admin", initials: "AD", admin: true } }));
    expect(store.availableNavigation).toContain("admin");
  });

  it("expires shell toasts without retaining global product state", () => {
    vi.useFakeTimers();
    const store = useShellStore();
    store.announce("Сохранено", "success");
    expect(store.toasts).toHaveLength(1);
    vi.advanceTimersByTime(3600);
    expect(store.toasts).toHaveLength(0);
    vi.useRealTimers();
  });
});
