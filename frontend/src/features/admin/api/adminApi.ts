import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type { AdminRole } from "../types/domain";

export function createAdminApi(client: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient()) {
  return Object.freeze({
    async status() {
      return client.request("adminSystemStatus");
    },
    async users(page: number, size: number, query: string, role: "all" | AdminRole) {
      return client.request("listAdminUsers", { query: { page, size, q: query, role } });
    },
    async updateRole(id: number, role: AdminRole) {
      return client.request("updateAdminUserRole", { path: { id }, body: { role } });
    },
    async resetPassword(id: number, newPassword: string) {
      return client.request("resetAdminUserPassword", { path: { id }, body: { newPassword } });
    },
    async registration() {
      return client.request("getAdminRegistrationSettings");
    },
    async updateRegistration(enabled: boolean) {
      return client.request("updateAdminRegistrationSettings", { body: { enabled } });
    },
  });
}

export type AdminApi = ReturnType<typeof createAdminApi>;
