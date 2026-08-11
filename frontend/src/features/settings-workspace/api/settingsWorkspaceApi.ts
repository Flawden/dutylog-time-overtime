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
    async timeContext() { return client.request("getTimeContext"); },
    async shiftTypes() { return (await client.request("listShiftTypes")) ?? []; },
    async createShiftType(body: DutyLogApiSchemas.ShiftTypeCreateRequest) { return client.request("createShiftType", { body }); },
    async updateShiftType(id: number, body: DutyLogApiSchemas.ShiftTypeUpdateRequest) { return client.request("updateShiftType", { path: { id }, body }); },
    async deleteShiftType(id: number) { await client.request("deleteShiftType", { path: { id } }); },
    async legacyShiftPreview(sourceTimezone: string) { return client.request("previewLegacyShiftMigration", { query: { sourceTimezone } }); },
    async migrateLegacyShifts(body: DutyLogApiSchemas.LegacyShiftMigrationRequest) { return client.request("migrateLegacyShifts", { body }); },
    async legacyTaskDeadlinePreview(sourceTimezone: string) { return client.request("previewLegacyTaskDeadlineMigration", { query: { sourceTimezone } }); },
    async migrateLegacyTaskDeadlines(body: DutyLogApiSchemas.LegacyTaskDeadlineMigrationRequest) { return client.request("migrateLegacyTaskDeadlines", { body }); },
    async notificationSettings() { return client.request("getNotificationSettings"); },
    async updateNotificationSettings(body: DutyLogApiSchemas.NotificationSettingsUpdateRequest) { return client.request("updateNotificationSettings", { body }); },
    async upcomingNotifications(from: string, to: string) { return (await client.request("listUpcomingNotifications", { query: { from, to } })) ?? []; },
    async tomorrowNotifications() { return (await client.request("listTomorrowNotifications")) ?? []; },
    async scheduleTemplates() { return (await client.request("listScheduleTemplates")) ?? []; },
    async createScheduleTemplate(body: DutyLogApiSchemas.ScheduleTemplateInput) { return client.request("createScheduleTemplate", { body }); },
    async updateScheduleTemplate(id: number, body: DutyLogApiSchemas.ScheduleTemplatePatch) { await client.request("updateScheduleTemplate", { path: { id }, body }); },
    async deleteScheduleTemplate(id: number) { await client.request("deleteScheduleTemplate", { path: { id } }); },
    async calendarLayers() { return (await client.request("listCalendarLayers")) ?? []; },
    async createCalendarLayer(body: DutyLogApiSchemas.CalendarLayerInput) { return client.request("createCalendarLayer", { body }); },
    async updateCalendarLayer(id: number, body: DutyLogApiSchemas.CalendarLayerPatch) { return client.request("updateCalendarLayer", { path: { id }, body }); },
    async deleteCalendarLayer(id: number) { await client.request("deleteCalendarLayer", { path: { id } }); },
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
