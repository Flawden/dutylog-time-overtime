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

function snapshot(): DutyLogLegacySnapshot {
  return {
    version: "27.40.32",
    language: "ru",
    online: true,
    offline: { online:true, cacheReady:true, lastSyncAt:null, stale:false, pending:0, failed:0, syncing:false, syncLockedByOther:false },
    modulesLoaded: true,
    navigation: ["today", "calendar", "settings"],
    availableViews: ["today", "calendar", "tasks", "settings"],
    profile: { displayName: "Даниил", initials: "Д", admin: false, onboardingCompleted: true },
  };
}

describe("legacy bridge", () => {
  it("uses the explicit legacy adapter when it is available", async () => {
    const target = fakeWindow();
    const logout = vi.fn();
    const retireDomainOwners = vi.fn();
    const writeCalendarDay = vi.fn(async () => ({ queued: true, day: { date: "2026-08-11", shiftTypeId: 2 } }));
    const offlineSyncDetails = vi.fn(async () => ({ queue: [], failed: [], lock: { active:false, expired:false, mine:false, startedAt:null, expiresAt:null }, diagnosticsReport:"ok" }));
    const offlineRetryAllFailed = vi.fn(async () => undefined);
    const subscribe = vi.fn(() => vi.fn());
    target.DutyLogLegacyPlatform = {
      version: "27.40.32",
      snapshot: () => snapshot(),
      logout,
      retireDomainOwners,
      writeCalendarDay,
      offlineSyncDetails,
      offlineRetryAllFailed,
      subscribe,
    };

    const bridge = createLegacyBridge(target);
    bridge.logout();
    bridge.retireDomainOwners("absence-time-bank");
    const written = await bridge.writeCalendarDay("2026-08-11", { shiftTypeId: 2 });
    const offlineDetails = await bridge.offlineSyncDetails();
    await bridge.offlineRetryAllFailed();
    const listener = vi.fn();
    bridge.subscribe(listener);

    expect(bridge.connected()).toBe(true);
    expect(bridge.snapshot()?.language).toBe("ru");
    expect(logout).toHaveBeenCalledOnce();
    expect(retireDomainOwners).toHaveBeenCalledWith("absence-time-bank");
    expect(writeCalendarDay).toHaveBeenCalledWith("2026-08-11", { shiftTypeId: 2 });
    expect(written).toEqual({ queued: true, day: { date: "2026-08-11", shiftTypeId: 2 } });
    expect(offlineDetails?.diagnosticsReport).toBe("ok");
    expect(offlineRetryAllFailed).toHaveBeenCalledOnce();
    expect(subscribe).toHaveBeenCalledWith(listener);
  });

  it("keeps only logout as a typed DOM fallback before the legacy adapter is ready", () => {
    const target = fakeWindow();
    const commands: unknown[] = [];
    target.addEventListener(LEGACY_COMMAND_EVENT, event => commands.push((event as CustomEvent).detail));

    const bridge = createLegacyBridge(target);
    bridge.logout();

    expect(bridge.connected()).toBe(false);
    expect(commands).toEqual([{ type: "logout" }]);
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

    target.dispatchEvent(new CustomEvent(LEGACY_STATE_EVENT, { detail: snapshot() }));
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ language: "ru" }));

    unsubscribe();
    target.dispatchEvent(new CustomEvent(LEGACY_STATE_EVENT, { detail: snapshot() }));
    expect(listener).toHaveBeenCalledTimes(1);
  });
});
