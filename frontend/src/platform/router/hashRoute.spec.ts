import { describe, expect, it, vi } from "vitest";
import { VUE_ROUTE_COMMITTED_EVENT, guardHashRoute, navigateHashRoute, publishCommittedHashRoute, readHashRoute, subscribeHashRoute } from "./hashRoute";

function fakeWindow(hash = "#today"): Window {
  const target = new EventTarget() as unknown as Window;
  Object.defineProperty(target, "location", { value: { hash }, configurable: true });
  return target;
}

describe("Vue hash route authority", () => {
  it("reads raw nested settings routes while normalizing the active Vue section", () => {
    const target = fakeWindow("#settings-notifications");
    expect(readHashRoute(target)).toEqual({ rawRoute: "settings-notifications", activeRoute: "settings" });
  });

  it("writes the canonical hash directly without routing through the legacy platform", () => {
    const target = fakeWindow("#today");
    navigateHashRoute("#overtime", target);
    expect(target.location.hash).toBe("#overtime");
  });

  it("publishes the guarded canonical route for the narrow legacy side-effect adapter", () => {
    const target = fakeWindow("#payroll");
    const listener = vi.fn();
    target.addEventListener(VUE_ROUTE_COMMITTED_EVENT, event => listener((event as CustomEvent).detail));
    const snapshot = { rawRoute: "payroll", activeRoute: "payroll" } as const;
    publishCommittedHashRoute(snapshot, target);
    expect(listener).toHaveBeenCalledWith(snapshot);
  });

  it("subscribes directly to hashchange and can unsubscribe", () => {
    const target = fakeWindow("#calendar");
    const listener = vi.fn();
    const unsubscribe = subscribeHashRoute(listener, target);
    target.dispatchEvent(new Event("hashchange"));
    expect(listener).toHaveBeenCalledWith({ rawRoute: "calendar", activeRoute: "calendar" });
    unsubscribe();
    target.dispatchEvent(new Event("hashchange"));
    expect(listener).toHaveBeenCalledTimes(1);
  });
  it("redirects non-admin and disabled-module routes to calendar after access state is known", () => {
    const base = { profileLoaded: true, admin: false, modulesLoaded: true, modules: { tasks: false, payroll: true } };
    expect(guardHashRoute({ rawRoute: "admin", activeRoute: "admin" }, base)).toEqual({ rawRoute: "calendar", activeRoute: "calendar" });
    expect(guardHashRoute({ rawRoute: "tasks", activeRoute: "tasks" }, base)).toEqual({ rawRoute: "calendar", activeRoute: "calendar" });
    expect(guardHashRoute({ rawRoute: "payroll", activeRoute: "payroll" }, base)).toEqual({ rawRoute: "payroll", activeRoute: "payroll" });
  });

  it("does not reject module/admin routes before authoritative access state is loaded", () => {
    expect(guardHashRoute(
      { rawRoute: "admin", activeRoute: "admin" },
      { profileLoaded: false, admin: false, modulesLoaded: false, modules: { tasks: false } },
    )).toEqual({ rawRoute: "admin", activeRoute: "admin" });
    expect(guardHashRoute(
      { rawRoute: "tasks", activeRoute: "tasks" },
      { profileLoaded: true, admin: false, modulesLoaded: false, modules: { tasks: false } },
    )).toEqual({ rawRoute: "tasks", activeRoute: "tasks" });
  });

});
