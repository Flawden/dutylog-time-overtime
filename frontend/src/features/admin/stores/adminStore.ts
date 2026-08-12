import { defineStore } from "pinia";
import { createAdminApi, type AdminApi } from "../api/adminApi";
import type { AdminRegistrationSettings, AdminRole, AdminSystemStatus, AdminUser, AdminUserPage } from "../types/domain";

let api: AdminApi = createAdminApi();
let usersSequence = 0;

function message(error: unknown): string {
  return error instanceof Error && error.message ? error.message : "Не удалось выполнить запрос";
}

function emptyPage(size = 50): AdminUserPage {
  return { items: [], page: 0, size, total: 0, totalPages: 0, hasPrevious: false, hasNext: false };
}

export function installAdminApiForTests(next: AdminApi): () => void {
  const previous = api;
  api = next;
  return () => { api = previous; };
}

export const useAdminStore = defineStore("dutylog-admin", {
  state: () => ({
    usersPage: emptyPage(),
    query: "",
    role: "all" as "all" | AdminRole,
    registration: null as AdminRegistrationSettings | null,
    diagnostics: null as AdminSystemStatus | null,
    usersLoading: false,
    registrationLoading: false,
    diagnosticsLoading: false,
    error: "",
  }),
  actions: {
    async loadUsers(): Promise<void> {
      const sequence = ++usersSequence;
      this.usersLoading = true;
      this.error = "";
      try {
        const page = await api.users(this.usersPage.page, this.usersPage.size, this.query.trim(), this.role);
        if (sequence !== usersSequence) return;
        this.usersPage = page ?? emptyPage(this.usersPage.size);
      } catch (error) {
        if (sequence === usersSequence) this.error = message(error);
        throw error;
      } finally {
        if (sequence === usersSequence) this.usersLoading = false;
      }
    },
    async setPage(page: number): Promise<void> {
      this.usersPage.page = Math.max(0, page);
      await this.loadUsers();
    },
    async setSize(size: number): Promise<void> {
      this.usersPage.size = size;
      this.usersPage.page = 0;
      await this.loadUsers();
    },
    async setRoleFilter(role: "all" | AdminRole): Promise<void> {
      this.role = role;
      this.usersPage.page = 0;
      await this.loadUsers();
    },
    async updateRole(id: number, role: AdminRole): Promise<AdminUser | null> {
      const updated = await api.updateRole(id, role);
      if (updated) this.usersPage.items = this.usersPage.items.map(user => user.id === id ? updated : user);
      return updated;
    },
    async resetPassword(id: number, newPassword: string): Promise<AdminUser | null> {
      const updated = await api.resetPassword(id, newPassword);
      if (updated) this.usersPage.items = this.usersPage.items.map(user => user.id === id ? updated : user);
      return updated;
    },
    async loadRegistration(): Promise<void> {
      this.registrationLoading = true;
      try { this.registration = await api.registration(); }
      catch (error) { this.error = message(error); throw error; }
      finally { this.registrationLoading = false; }
    },
    async updateRegistration(enabled: boolean): Promise<void> {
      this.registrationLoading = true;
      try { this.registration = await api.updateRegistration(enabled); }
      catch (error) { this.error = message(error); throw error; }
      finally { this.registrationLoading = false; }
    },
    async loadDiagnostics(): Promise<void> {
      this.diagnosticsLoading = true;
      try { this.diagnostics = await api.status(); }
      catch (error) { this.error = message(error); throw error; }
      finally { this.diagnosticsLoading = false; }
    },
    async refreshAll(): Promise<void> {
      const results = await Promise.allSettled([this.loadUsers(), this.loadRegistration(), this.loadDiagnostics()]);
      const rejected = results.find(result => result.status === "rejected");
      if (rejected?.status === "rejected") this.error = message(rejected.reason);
    },
  },
});
