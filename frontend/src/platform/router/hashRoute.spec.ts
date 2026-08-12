import { describe, expect, it, vi } from "vitest";
import { navigateHashRoute, readHashRoute, subscribeHashRoute } from "./hashRoute";

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
});
