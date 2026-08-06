import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { CalendarTimelineApi } from "../api/calendarTimelineApi";
import { normalizeCalendarBundle } from "../types/model";
import { installCalendarTimelineApiForTests, useCalendarTimelineStore } from "./calendarTimelineStore";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(res => { resolve = res; });
  return { promise, resolve };
}

function loaded(label: string, date = "2026-08-05") {
  return {
    workDate: date,
    focusDate: date,
    bundle: normalizeCalendarBundle({
      from: "2026-07-27", to: "2026-09-06",
      tasks: [{ id: 1, date, text: label, done: false, tags: [], priority: "NORMAL", deadlineAbsolute: false, reminderEnabled: false, overdue: false, subtasks: [] }],
    }, "2026-07-27", "2026-09-06"),
  };
}

function mockApi(overrides: Partial<CalendarTimelineApi> = {}): CalendarTimelineApi {
  return {
    load: vi.fn().mockResolvedValue(loaded("Initial")),
    setLayerVisibility: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as CalendarTimelineApi;
}

beforeEach(() => setActivePinia(createPinia()));

describe("calendar and timeline store", () => {
  it("does not let a stale month response replace a newer range", async () => {
    const first = deferred<ReturnType<typeof loaded>>();
    const second = deferred<ReturnType<typeof loaded>>();
    const restore = installCalendarTimelineApiForTests(mockApi({ load: vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise) }));
    const store = useCalendarTimelineStore();
    store.focusDate = "2026-07-15";
    const stale = store.refresh();
    store.focusDate = "2026-08-15";
    const current = store.refresh();
    second.resolve(loaded("Newest")); await current;
    first.resolve(loaded("Stale")); await stale;
    expect(store.bundle?.tasks[0]?.text).toBe("Newest");
    restore();
  });

  it("switches month week and day without reloading the canonical range", async () => {
    const load = vi.fn().mockResolvedValue(loaded("Range"));
    const restore = installCalendarTimelineApiForTests(mockApi({ load }));
    const store = useCalendarTimelineStore();
    store.focusDate = "2026-08-05";
    await store.refresh();
    await store.setMode("week");
    await store.setMode("day");
    expect(store.mode).toBe("day");
    expect(load).toHaveBeenCalledTimes(1);
    restore();
  });

  it("reloads only when the requested date leaves the loaded grid", async () => {
    const load = vi.fn().mockResolvedValue(loaded("Range"));
    const restore = installCalendarTimelineApiForTests(mockApi({ load }));
    const store = useCalendarTimelineStore();
    store.focusDate = "2026-08-05";
    await store.refresh();
    await store.openDate("2026-08-20", "day");
    expect(load).toHaveBeenCalledTimes(1);
    await store.openDate("2026-10-01", "day");
    expect(load).toHaveBeenCalledTimes(2);
    restore();
  });


  it("loads the work-date range for Today instead of a persisted historical focus", async () => {
    const load = vi.fn().mockResolvedValue(loaded("Today", "2026-08-05"));
    const restore = installCalendarTimelineApiForTests(mockApi({ load }));
    const store = useCalendarTimelineStore();
    store.focusDate = "2025-01-15";
    store.workDate = "2026-08-05";
    await store.ensureTodayLoaded();
    expect(load).toHaveBeenCalledWith("2025-01-15", true);
    expect(store.focusDate).toBe("2026-08-05");
    restore();
  });

  it("optimistically toggles a calendar layer and rolls back a failed mutation", async () => {
    const setLayerVisibility = vi.fn().mockRejectedValue(new Error("network"));
    const api = mockApi({
      load: vi.fn().mockResolvedValue({ ...loaded("Range"), bundle: normalizeCalendarBundle({ from: "2026-07-27", to: "2026-09-06", calendarLayers: [{ id: 7, name: "Layer", color: "#123456", visible: true, entries: [] }] }, "2026-07-27", "2026-09-06") }),
      setLayerVisibility,
    });
    const restore = installCalendarTimelineApiForTests(api);
    const store = useCalendarTimelineStore();
    await store.refresh();
    await store.toggleLayer(7, false);
    expect(store.bundle?.calendarLayers[0]?.visible).toBe(true);
    expect(store.error).toContain("network");
    restore();
  });
});
