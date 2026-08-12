import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { AdminApi } from "../api/adminApi";
import { installAdminApiForTests, useAdminStore } from "./adminStore";

function page(label: string, pageNumber = 0) {
  return { items: [{ id: 1, username: label, displayName: label, role: "USER" as const, accountTier: "FREE", bootstrapAdmin: false, currentUser: false }], page: pageNumber, size: 50, total: 1, totalPages: 1, hasPrevious: false, hasNext: false };
}

function mockApi(overrides: Partial<AdminApi> = {}): AdminApi {
  return {
    status: vi.fn().mockResolvedValue(null),
    users: vi.fn().mockResolvedValue(page("admin")),
    updateRole: vi.fn().mockResolvedValue(null),
    resetPassword: vi.fn().mockResolvedValue(null),
    registration: vi.fn().mockResolvedValue({ enabled: false, mode: "closed", source: "default" }),
    updateRegistration: vi.fn().mockResolvedValue({ enabled: true, mode: "open", source: "database" }),
    ...overrides,
  } as AdminApi;
}

beforeEach(() => setActivePinia(createPinia()));

describe("admin store", () => {
  it("keeps the newest users page when filtered reads race", async () => {
    let firstResolve!: (value: ReturnType<typeof page>) => void;
    let secondResolve!: (value: ReturnType<typeof page>) => void;
    const first = new Promise<ReturnType<typeof page>>(resolve => { firstResolve = resolve; });
    const second = new Promise<ReturnType<typeof page>>(resolve => { secondResolve = resolve; });
    const api = mockApi({ users: vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(second) });
    const restore = installAdminApiForTests(api);
    const store = useAdminStore();
    store.query = "old";
    const stale = store.loadUsers();
    store.query = "new";
    const current = store.loadUsers();
    secondResolve(page("new"));
    await current;
    firstResolve(page("old"));
    await stale;
    expect(store.usersPage.items[0]?.username).toBe("new");
    restore();
  });

  it("uses the generated admin API for mutations and updates the local read model", async () => {
    const updateRole = vi.fn().mockResolvedValue({ ...page("worker").items[0], role: "ADMIN" as const });
    const updateRegistration = vi.fn().mockResolvedValue({ enabled: true, mode: "open" as const, source: "database" as const });
    const api = mockApi({ users: vi.fn().mockResolvedValue(page("worker")), updateRole, updateRegistration });
    const restore = installAdminApiForTests(api);
    const store = useAdminStore();
    await store.loadUsers();
    await store.updateRole(1, "ADMIN");
    await store.updateRegistration(true);
    expect(updateRole).toHaveBeenCalledWith(1, "ADMIN");
    expect(store.usersPage.items[0]?.role).toBe("ADMIN");
    expect(store.registration?.enabled).toBe(true);
    restore();
  });
  it("passes password reset through the API without reloading the entire users page", async () => {
    const resetPassword = vi.fn().mockResolvedValue({ ...page("worker").items[0], updatedAt: "2026-08-12T10:00:00Z" });
    const users = vi.fn().mockResolvedValue(page("worker"));
    const api = mockApi({ users, resetPassword });
    const restore = installAdminApiForTests(api);
    const store = useAdminStore();
    await store.loadUsers();
    await store.resetPassword(1, "correct-horse-battery");
    expect(resetPassword).toHaveBeenCalledWith(1, "correct-horse-battery");
    expect(users).toHaveBeenCalledTimes(1);
    expect(store.usersPage.items[0]?.updatedAt).toBe("2026-08-12T10:00:00Z");
    restore();
  });

});
