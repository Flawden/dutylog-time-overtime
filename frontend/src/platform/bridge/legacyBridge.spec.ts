import { describe, expect, it, vi } from "vitest";
import {
  ABSENCE_TIME_BANK_PROJECTION_EVENT,
  createLegacyBridge,
  LEGACY_COMMAND_EVENT,
  LEGACY_STATE_EVENT,
  publishAbsenceTimeBankProjection,
} from "./legacyBridge";

function fakeWindow(): Window {
  return new EventTarget() as unknown as Window;
}

function snapshot(route = "calendar"): DutyLogLegacySnapshot {
  return {
    version: "27.37.2",
    language: "ru",
    route,
    online: true,
    modulesLoaded: true,
    navigation: ["today", "calendar", "settings"],
    availableViews: ["today", "calendar", "tasks", "settings"],
    profile: { displayName: "Даниил", initials: "Д", admin: false },
  };
}

describe("legacy bridge", () => {
  it("uses the explicit legacy adapter when it is available", () => {
    const target = fakeWindow();
    const navigate = vi.fn();
    const openModal = vi.fn();
    const logout = vi.fn();
    const retireDomainOwners = vi.fn();
    const subscribe = vi.fn(() => vi.fn());
    target.DutyLogLegacyPlatform = {
      version: "27.37.2",
      snapshot: () => snapshot(),
      navigate,
      openModal,
      logout,
      retireDomainOwners,
      subscribe,
    };

    const bridge = createLegacyBridge(target);
    bridge.navigate("#overtime");
    bridge.openModal("absenceComposerModal", "vacationStart");
    bridge.logout();
    bridge.retireDomainOwners("absence-time-bank");
    const listener = vi.fn();
    bridge.subscribe(listener);

    expect(bridge.connected()).toBe(true);
    expect(bridge.snapshot()?.route).toBe("calendar");
    expect(navigate).toHaveBeenCalledWith("overtime");
    expect(openModal).toHaveBeenCalledWith("absenceComposerModal", "vacationStart");
    expect(logout).toHaveBeenCalledOnce();
    expect(retireDomainOwners).toHaveBeenCalledWith("absence-time-bank");
    expect(subscribe).toHaveBeenCalledWith(listener);
  });

  it("falls back to typed DOM events before the legacy adapter is ready", () => {
    const target = fakeWindow();
    const commands: unknown[] = [];
    target.addEventListener(LEGACY_COMMAND_EVENT, event => commands.push((event as CustomEvent).detail));

    const bridge = createLegacyBridge(target);
    bridge.navigate("tasks");
    bridge.openModal("taskEditModal");
    bridge.logout();

    expect(bridge.connected()).toBe(false);
    expect(commands).toEqual([
      { type: "navigate", view: "tasks" },
      { type: "open-modal", id: "taskEditModal", focusId: null },
      { type: "logout" },
    ]);
  });


  it("publishes immutable Absence and Time Bank projections to the remaining legacy surfaces", () => {
    const target = fakeWindow();
    const listener = vi.fn();
    const planner = Object.freeze({ occurrences: Object.freeze([{ id: 14 }]) });
    const account = Object.freeze({ availableMinutes: 420 });

    target.addEventListener(ABSENCE_TIME_BANK_PROJECTION_EVENT, listener);
    publishAbsenceTimeBankProjection(target, {
      planner,
      account,
      referenceDate: "2026-08-05",
    });

    expect(listener).toHaveBeenCalledOnce();
    expect((listener.mock.calls[0]?.[0] as CustomEvent).detail).toEqual({
      planner,
      account,
      referenceDate: "2026-08-05",
    });
  });

  it("subscribes to immutable legacy state events before the adapter is ready", () => {
    const target = fakeWindow();
    const listener = vi.fn();
    const bridge = createLegacyBridge(target);
    const unsubscribe = bridge.subscribe(listener);

    target.dispatchEvent(new CustomEvent(LEGACY_STATE_EVENT, { detail: snapshot("settings-profile") }));
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ route: "settings-profile" }));

    unsubscribe();
    target.dispatchEvent(new CustomEvent(LEGACY_STATE_EVENT, { detail: snapshot("today") }));
    expect(listener).toHaveBeenCalledTimes(1);
  });
});
