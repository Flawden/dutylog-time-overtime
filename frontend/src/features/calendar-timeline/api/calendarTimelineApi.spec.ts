import { beforeEach, describe, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/platform/api/generatedClient", () => ({ createGeneratedDutyLogApiClient: () => ({ request: mocks.request }) }));
import { calendarTimelineApi } from "./calendarTimelineApi";
import { calendarLoadRange } from "../types/model";
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(res => { resolve = res; });
  return { promise, resolve };
}
beforeEach(() => mocks.request.mockReset());
describe("calendar read dependency ordering", () => {
  it("starts an explicit month read before the work-date context finishes", async () => {
    const context = deferred<{ workDate: string }>();
    mocks.request.mockImplementation(operation => operation === "getTimeContext" ? context.promise : Promise.resolve({}));
    const pending = calendarTimelineApi.load("2026-10-15");
    expect(mocks.request.mock.calls.map(call => call[0])).toEqual(["getTimeContext", "calendarRange"]);
    expect(mocks.request.mock.calls[1]?.[1].query).toEqual(calendarLoadRange("2026-10-15"));
    context.resolve({ workDate: "2026-10-16" });
    const result = await pending;
    expect(result.focusDate).toBe("2026-10-15");
    expect(result.workDate).toBe("2026-10-16");
  });
  it("waits for authoritative Today across a month boundary", async () => {
    const context = deferred<{ workDate: string }>();
    mocks.request.mockImplementation(operation => operation === "getTimeContext" ? context.promise : Promise.resolve({}));
    const pending = calendarTimelineApi.load("2026-10-31", true);
    expect(mocks.request.mock.calls.map(call => call[0])).toEqual(["getTimeContext"]);
    context.resolve({ workDate: "2026-11-01" });
    const result = await pending;
    expect(mocks.request.mock.calls[1]?.[1].query).toEqual(calendarLoadRange("2026-11-01"));
    expect(result.focusDate).toBe("2026-11-01");
  });
  it("does not publish a calendar result after a context authentication failure", async () => {
    const unauthorized = Object.assign(new Error("unauthenticated"), { status: 401 });
    mocks.request.mockImplementation(operation => operation === "getTimeContext" ? Promise.reject(unauthorized) : Promise.resolve({}));
    await expect(calendarTimelineApi.load("2026-10-15")).rejects.toBe(unauthorized);
  });
  it("keeps the explicit month usable after an optional context network failure", async () => {
    mocks.request.mockImplementation(operation => operation === "getTimeContext" ? Promise.reject(new TypeError("network")) : Promise.resolve({}));
    const result = await calendarTimelineApi.load("2026-10-15");
    expect(result.focusDate).toBe("2026-10-15");
    expect(result.bundle.from).toBe(calendarLoadRange("2026-10-15").from);
  });
  it("uses authoritative work date before requesting a range for missing focus", async () => {
    const context = deferred<{ workDate: string }>();
    mocks.request.mockImplementation(operation => operation === "getTimeContext" ? context.promise : Promise.resolve({}));
    const pending = calendarTimelineApi.load("");
    expect(mocks.request).toHaveBeenCalledTimes(1);
    context.resolve({ workDate: "2026-12-01" });
    const result = await pending;
    expect(result.focusDate).toBe("2026-12-01");
    expect(mocks.request.mock.calls[1]?.[1].query).toEqual(calendarLoadRange("2026-12-01"));
  });
});
