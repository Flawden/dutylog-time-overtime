import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { DutyLogApiError } from "@/platform/api/httpClient";
import type { AbsenceTimeBankApi } from "../api/absenceTimeBankApi";
import { installAbsenceTimeBankApiForTests, useAbsenceTimeBankStore } from "./absenceTimeBankStore";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

function loaded(label: string, balanceHours = 8) {
  return {
    planner: {
      settings: {}, summary: { timeOffRemainingMinutes: 480 }, durationPresets: [7, 14],
      types: [{ id: 1, name: label, systemCode: "TIME_OFF" }], absences: [], occurrences: [], typeSummaries: [],
    },
    account: { totalEarnedHours: balanceHours, totalUsedHours: 0, balanceHours, credits: [], usages: [] },
    compensation: null,
    integrity: { from: "2026-01-01", to: "2026-12-31", healthy: true, reservedMinutes: 0, postedMinutes: 0, reversedMinutes: 0, orphanUsageCount: 0, allocationMismatchCount: 0, issues: [], entries: [], periods: [] },
    actualWork: [], scenarios: [], range: { from: "2026-01-01", to: "2026-12-31" },
  } as unknown as Awaited<ReturnType<AbsenceTimeBankApi["load"]>>;
}

function mockApi(overrides: Partial<AbsenceTimeBankApi> = {}): AbsenceTimeBankApi {
  return {
    load: vi.fn().mockResolvedValue(loaded("Time off")),
    previewAbsence: vi.fn().mockResolvedValue({ durationMinutes: 240 }),
    createAbsence: vi.fn().mockResolvedValue({ id: 1 }),
    updateAbsence: vi.fn().mockResolvedValue({ id: 1 }),
    deleteAbsence: vi.fn().mockResolvedValue(null),
    previewCredit: vi.fn().mockResolvedValue({ creditedMinutes: 60, creditedHours: 1 }),
    createCredit: vi.fn().mockResolvedValue({}),
    updateCredit: vi.fn().mockResolvedValue({}),
    deleteCredit: vi.fn().mockResolvedValue(null),
    shiftForDate: vi.fn().mockResolvedValue(null),
    listScenarios: vi.fn().mockResolvedValue([]),
    createScenario: vi.fn().mockResolvedValue({}),
    updateScenario: vi.fn().mockResolvedValue({}),
    deleteScenario: vi.fn().mockResolvedValue(null),
    creditBody: vi.fn().mockReturnValue({ date: "2026-08-01", hours: 1 }),
    ...overrides,
  } as unknown as AbsenceTimeBankApi;
}

beforeEach(() => setActivePinia(createPinia()));

describe("absence and time-bank store concurrency", () => {
  it("does not let a stale refresh overwrite a newer read model", async () => {
    const first = deferred<ReturnType<typeof loaded>>();
    const second = deferred<ReturnType<typeof loaded>>();
    const api = mockApi({ load: vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise) });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();

    const oldRequest = store.refresh("2026-01-01", "year");
    const newRequest = store.refresh("2026-08-01", "month");
    second.resolve(loaded("Newest"));
    await newRequest;
    first.resolve(loaded("Stale"));
    await oldRequest;

    expect(store.planner?.types[0]?.name).toBe("Newest");
    expect(store.rangeMode).toBe("month");
    restore();
  });

  it("blocks a second submit while the first absence mutation is in flight", async () => {
    const pending = deferred<unknown>();
    const createAbsence = vi.fn().mockReturnValue(pending.promise);
    const api = mockApi({ createAbsence });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();
    await store.refresh();
    store.absenceDraft.typeId = 1;

    const first = store.saveAbsence();
    const second = store.saveAbsence();
    expect(createAbsence).toHaveBeenCalledTimes(1);
    pending.resolve({ id: 1 });
    await Promise.all([first, second]);
    restore();
  });

  it("refreshes the server model and surfaces a durable message after HTTP 409", async () => {
    const load = vi.fn().mockResolvedValue(loaded("Fresh server type"));
    const api = mockApi({
      load,
      createAbsence: vi.fn().mockRejectedValue(new DutyLogApiError("Conflict", {
        status: 409, code: "STALE_WRITE", url: "/api/v1/vacation-planner/absences", method: "POST", requestId: "conflict-1",
      })),
    });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();
    await store.refresh();
    store.absenceDraft.typeId = 1;
    await store.saveAbsence();

    expect(store.conflict).toContain("Данные изменились на сервере");
    expect(load).toHaveBeenCalledTimes(2);
    expect(store.mutationPending).toBe(false);
    restore();
  });


  it("keeps absence preview loading bound to the newest request", async () => {
    const first = deferred<{ durationMinutes: number }>();
    const second = deferred<{ durationMinutes: number }>();
    const previewAbsence = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const api = mockApi({ previewAbsence });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();
    await store.refresh();
    store.absenceDraft.typeId = 1;

    const staleRequest = store.previewAbsence();
    const newestRequest = store.previewAbsence();
    first.resolve({ durationMinutes: 60 });
    await staleRequest;

    expect(store.previewLoading).toBe(true);
    expect(store.absencePreview).toBeNull();

    second.resolve({ durationMinutes: 120 });
    await newestRequest;
    expect(store.previewLoading).toBe(false);
    expect(store.absencePreview?.durationMinutes).toBe(120);
    restore();
  });

  it("keeps credit preview loading and values bound to the newest request", async () => {
    const first = deferred<{ creditedMinutes: number; creditedHours: number }>();
    const second = deferred<{ creditedMinutes: number; creditedHours: number }>();
    const previewCredit = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const api = mockApi({ previewCredit });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();

    const staleRequest = store.previewCredit();
    const newestRequest = store.previewCredit();
    first.resolve({ creditedMinutes: 60, creditedHours: 1 });
    await staleRequest;

    expect(store.creditPreviewLoading).toBe(true);
    expect(store.creditPreview).toBeNull();

    second.resolve({ creditedMinutes: 150, creditedHours: 2.5 });
    await newestRequest;
    expect(store.creditPreviewLoading).toBe(false);
    expect(store.creditDraft.hours).toBe(2.5);
    restore();
  });

  it("switches the ledger period immediately while the refreshed model is loading", async () => {
    const pending = deferred<ReturnType<typeof loaded>>();
    const load = vi.fn().mockReturnValue(pending.promise);
    const api = mockApi({ load });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();
    store.loaded = true;
    store.rangeMode = "year";

    const request = store.setRangeMode("month");
    expect(store.rangeMode).toBe("month");

    pending.resolve(loaded("Monthly"));
    await request;
    expect(store.rangeMode).toBe("month");
    restore();
  });

  it("refreshes a previously loaded account before opening the absence composer", async () => {
    const load = vi.fn()
      .mockResolvedValueOnce(loaded("Initial", 0))
      .mockResolvedValueOnce(loaded("Fresh", 8));
    const api = mockApi({ load });
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();

    await store.refresh("2026-08-05", "year");
    expect(store.account?.balanceHours).toBe(0);

    await store.openAbsenceComposer({ date: "2026-08-06", systemCode: "TIME_OFF" });

    expect(load).toHaveBeenNthCalledWith(2, "2026-08-06", "year");
    expect(store.account?.balanceHours).toBe(8);
    expect(store.absenceModalOpen).toBe(true);
    expect(store.absenceDraft.typeId).toBe(1);
    restore();
  });

  it("keeps the requested usage tab and linked absence focus across loading", async () => {
    const api = mockApi();
    const restore = installAbsenceTimeBankApiForTests(api);
    const store = useAbsenceTimeBankStore();
    await store.openTimeBankUsage(44);
    expect(store.timeBankTab).toBe("usage");
    expect(store.focusAbsenceUsageId).toBe(44);
    expect(store.loaded).toBe(true);
    restore();
  });
});
