import { createGeneratedDutyLogApiClient, type DutyLogGeneratedApiClient } from "@/platform/api/generatedClient";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";

export function createSettingsWorkspaceApi(client: DutyLogGeneratedApiClient = createGeneratedDutyLogApiClient()) {
  return Object.freeze({
    async profile() { return client.request("getProfile"); },
    async updateProfile(body: DutyLogApiSchemas.ProfileUpdateRequest) { return client.request("updateProfile", { body }); },
    async changePassword(body: DutyLogApiSchemas.PasswordChangeRequest) { await client.request("changeProfilePassword", { body }); },
    async sessions() { return (await client.request("listProfileSessions")) ?? []; },
    async revokeSession(id: number) { await client.request("revokeProfileSession", { path: { id } }); },
    async modules() { return (await client.request("listModules")) ?? []; },
    async updateModules(body: DutyLogApiSchemas.ModuleSettingsUpdateRequest) { return (await client.request("updateModules", { body })) ?? []; },
    async calendarSyncStatus() { return client.request("getCalendarSyncStatus"); },
    async rotateCalendarSubscription() { return client.request("rotateCalendarSubscription"); },
    async revokeCalendarSubscription() { await client.request("revokeCalendarSubscription"); },
    async telegramStatus() { return client.request("getTelegramStatus"); },
    async telegramCode() { return client.request("createTelegramLinkCode"); },
    async updateTelegram(body: DutyLogApiSchemas.TelegramSettingsRequest) { return client.request("updateTelegramSettings", { body }); },
    async unlinkTelegram() { await client.request("unlinkTelegram"); },
  });
}

export type SettingsWorkspaceApi = ReturnType<typeof createSettingsWorkspaceApi>;
