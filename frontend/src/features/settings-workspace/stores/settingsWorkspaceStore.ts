import { defineStore } from "pinia";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { createSettingsWorkspaceApi, type SettingsWorkspaceApi } from "../api/settingsWorkspaceApi";
import { DEFAULT_APPEARANCE, normalizeAppearance, type AppearancePreferences, type SettingsLanguage } from "../types/model";

let appearanceSaveTimer: ReturnType<typeof setTimeout> | null = null;
let appearanceRevision = 0;
let appearanceSaveQueue: Promise<void> = Promise.resolve();

function errorMessage(error: unknown): string {
  return error instanceof Error && error.message ? error.message : "Unknown error";
}

function plainRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? { ...(value as Record<string, unknown>) } : {};
}

export const useSettingsWorkspaceStore = defineStore("dutylog-settings-workspace", {
  state: () => ({
    loaded: false,
    loading: false,
    profile: null as DutyLogApiSchemas.Profile | null,
    sessions: [] as DutyLogApiSchemas.MobileSession[],
    modules: [] as DutyLogApiSchemas.Module[],
    calendarSync: null as DutyLogApiSchemas.CalendarSyncStatus | null,
    calendarSyncIssuedUrl: "",
    telegram: null as DutyLogApiSchemas.TelegramStatus | null,
    appearance: normalizeAppearance(DEFAULT_APPEARANCE) as AppearancePreferences,
    profileMessage: "",
    profileMessageOk: false,
    passwordMessage: "",
    passwordMessageOk: false,
    languageMessage: "",
    modulesMessage: "",
    modulesMessageOk: false,
    calendarSyncMessage: "",
    calendarSyncMessageOk: false,
    appearanceMessage: "",
    appearanceMessageOk: false,
    telegramMessage: "",
    error: "",
  }),
  getters: {
    language(state): SettingsLanguage { return state.profile?.languagePreference === "en" ? "en" : "ru"; },
    moduleMap(state): Record<string, boolean> { return Object.fromEntries(state.modules.map(item => [item.key, item.enabled])); },
  },
  actions: {
    moduleEnabled(key: string): boolean {
      const item = this.modules.find(module => module.key === key);
      return item ? item.enabled : false;
    },
    async bootstrap(bridge: LegacyBridge, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      if (this.loading) return;
      this.loading = true;
      this.error = "";
      try {
        const [profile, modules] = await Promise.all([api.profile(), api.modules()]);
        if (!profile) throw new Error("Profile unavailable");
        this.profile = profile;
        this.modules = modules;
        bridge.synchronizeProfile(plainRecord(profile));
        this.appearance = normalizeAppearance(bridge.settingsAppearanceSnapshot() ?? {
          themePreference: profile.themePreference,
          accentColor: profile.accentColor,
          themePreset: profile.themePreset,
          themeConfig: profile.themeConfig,
        });
        await Promise.all([this.loadSessions(api), this.refreshIntegrations(api)]);
        this.loaded = true;
      } catch (error) {
        this.error = errorMessage(error);
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async refreshIntegrations(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      const jobs: Promise<unknown>[] = [];
      if (this.moduleEnabled("calendar_sync")) jobs.push(this.loadCalendarSync(api));
      else { this.calendarSync = null; this.calendarSyncIssuedUrl = ""; }
      if (this.moduleEnabled("telegram")) jobs.push(this.loadTelegram(api));
      else this.telegram = null;
      await Promise.all(jobs);
    },
    async saveProfile(displayName: string, birthday: string, bridge: LegacyBridge, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      this.profileMessage = this.language === "en" ? "Saving…" : "сохраняю…";
      this.profileMessageOk = true;
      try {
        const updated = await api.updateProfile({ displayName: displayName.trim() || null, birthday: birthday || null });
        if (!updated) throw new Error("Profile update returned no data");
        this.profile = updated;
        bridge.synchronizeProfile(plainRecord(updated));
        this.profileMessage = this.language === "en" ? "Saved" : "Сохранено";
        this.profileMessageOk = true;
      } catch (error) {
        this.profileMessage = errorMessage(error); this.profileMessageOk = false; throw error;
      }
    },
    async changePassword(currentPassword: string, newPassword: string, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      this.passwordMessage = this.language === "en" ? "Saving…" : "сохраняю…";
      this.passwordMessageOk = true;
      try {
        await api.changePassword({ currentPassword, newPassword });
        this.passwordMessage = this.language === "en" ? "Password changed. Mobile sessions were revoked." : "Пароль изменён. Активные мобильные сессии завершены.";
        this.passwordMessageOk = true;
        await this.loadSessions(api);
      } catch (error) {
        this.passwordMessage = errorMessage(error); this.passwordMessageOk = false; throw error;
      }
    },
    async loadSessions(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> { this.sessions = await api.sessions(); },
    async revokeSession(id: number, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> { await api.revokeSession(id); await this.loadSessions(api); },
    async setLanguage(language: SettingsLanguage, bridge: LegacyBridge, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      const previous = this.profile?.languagePreference === "en" ? "en" : "ru";
      bridge.previewLanguage(language);
      this.languageMessage = language === "en" ? "Saving…" : "сохраняю…";
      try {
        const updated = await api.updateProfile({ languagePreference: language });
        if (!updated) throw new Error("Language update returned no data");
        this.profile = updated;
        bridge.synchronizeProfile(plainRecord(updated));
        this.languageMessage = language === "en" ? "Language saved" : "Язык сохранён";
      } catch (error) {
        bridge.previewLanguage(previous);
        this.languageMessage = errorMessage(error);
        throw error;
      }
    },
    async toggleModule(key: string, enabled: boolean, bridge: LegacyBridge, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      const previous = this.modules.map(item => ({ ...item }));
      this.modulesMessage = this.language === "en" ? "saving…" : "сохраняю…";
      this.modulesMessageOk = true;
      if (!enabled) {
        this.modules = this.modules.map(item => item.key === key ? { ...item, enabled: false } : item);
        bridge.previewModuleEnabled(key, false);
      }
      try {
        const updated = await api.updateModules({ enabled: { [key]: enabled } });
        this.modules = updated;
        await bridge.commitModuleList(updated as unknown as readonly Record<string, unknown>[]);
        this.modulesMessage = this.language === "en" ? "modules saved" : "модули сохранены";
        this.modulesMessageOk = true;
        await this.refreshIntegrations(api);
      } catch (error) {
        this.modules = previous;
        bridge.restoreModuleList(previous as unknown as readonly Record<string, unknown>[]);
        this.modulesMessage = errorMessage(error); this.modulesMessageOk = false; throw error;
      }
    },
    previewAppearance(next: AppearancePreferences, bridge: LegacyBridge): void {
      const normalized = normalizeAppearance(next);
      const previewed = bridge.previewAppearance(normalized as unknown as Record<string, unknown>);
      this.appearance = normalizeAppearance(previewed ?? normalized);
    },
    scheduleAppearanceSave(bridge: LegacyBridge, api: SettingsWorkspaceApi = createSettingsWorkspaceApi(), delay = 650): void {
      if (appearanceSaveTimer) clearTimeout(appearanceSaveTimer);
      const revision = ++appearanceRevision;
      const snapshot = normalizeAppearance(this.appearance);
      this.appearanceMessage = this.language === "en" ? "Saving…" : "Сохраняется…";
      this.appearanceMessageOk = true;
      appearanceSaveTimer = setTimeout(() => {
        const operation = async () => {
          if (revision < appearanceRevision) return;
          try {
            const updated = await api.updateProfile({
              themePreference: snapshot.themePreference,
              accentColor: snapshot.accentColor,
              themePreset: snapshot.themePreset,
              themeConfig: snapshot.themeConfig as unknown as Record<string, unknown>,
            });
            if (!updated || revision !== appearanceRevision) return;
            this.profile = updated;
            bridge.synchronizeProfile(plainRecord(updated));
            this.appearance = normalizeAppearance({ themePreference: updated.themePreference, accentColor: updated.accentColor, themePreset: updated.themePreset, themeConfig: updated.themeConfig });
            this.appearanceMessage = this.language === "en" ? "Saved automatically" : "Сохранено автоматически";
            this.appearanceMessageOk = true;
          } catch (error) {
            if (revision === appearanceRevision) { this.appearanceMessage = errorMessage(error); this.appearanceMessageOk = false; }
          }
        };
        const queued = appearanceSaveQueue.then(operation, operation);
        appearanceSaveQueue = queued.then(() => undefined, () => undefined);
      }, delay);
    },
    async persistAppearanceNow(bridge: LegacyBridge, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      if (appearanceSaveTimer) clearTimeout(appearanceSaveTimer);
      const revision = ++appearanceRevision;
      const snapshot = normalizeAppearance(this.appearance);
      this.appearanceMessage = this.language === "en" ? "Saving…" : "Сохраняется…";
      try {
        const updated = await api.updateProfile({ themePreference: snapshot.themePreference, accentColor: snapshot.accentColor, themePreset: snapshot.themePreset, themeConfig: snapshot.themeConfig as unknown as Record<string, unknown> });
        if (!updated || revision !== appearanceRevision) return;
        this.profile = updated; bridge.synchronizeProfile(plainRecord(updated));
        this.appearance = normalizeAppearance({ themePreference: updated.themePreference, accentColor: updated.accentColor, themePreset: updated.themePreset, themeConfig: updated.themeConfig });
        this.appearanceMessage = this.language === "en" ? "Saved automatically" : "Сохранено автоматически"; this.appearanceMessageOk = true;
      } catch (error) { this.appearanceMessage = errorMessage(error); this.appearanceMessageOk = false; throw error; }
    },
    async loadCalendarSync(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> { this.calendarSync = await api.calendarSyncStatus(); },
    async rotateCalendarSync(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      this.calendarSyncMessage = this.language === "en" ? "Saving…" : "сохраняю…";
      try {
        const issued = await api.rotateCalendarSubscription();
        if (!issued) throw new Error("Calendar subscription unavailable");
        this.calendarSync = issued;
        this.calendarSyncIssuedUrl = issued.subscriptionUrl;
        this.calendarSyncMessage = this.language === "en" ? "New link created. Copy it now." : "Новая ссылка создана. Скопируйте её сейчас.";
        this.calendarSyncMessageOk = true;
      } catch (error) { this.calendarSyncMessage = errorMessage(error); this.calendarSyncMessageOk = false; throw error; }
    },
    async revokeCalendarSync(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> {
      await api.revokeCalendarSubscription();
      this.calendarSyncIssuedUrl = "";
      await this.loadCalendarSync(api);
      this.calendarSyncMessage = this.language === "en" ? "Subscription revoked" : "Подписка отозвана";
      this.calendarSyncMessageOk = true;
    },
    async loadTelegram(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> { this.telegram = await api.telegramStatus(); },
    async createTelegramCode(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<DutyLogApiSchemas.TelegramCode | null> {
      const code = await api.telegramCode();
      await this.loadTelegram(api);
      return code;
    },
    async setTelegramNotifications(enabled: boolean, api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> { this.telegram = await api.updateTelegram({ notificationsEnabled: enabled }); },
    async unlinkTelegram(api: SettingsWorkspaceApi = createSettingsWorkspaceApi()): Promise<void> { await api.unlinkTelegram(); await this.loadTelegram(api); },
  },
});
