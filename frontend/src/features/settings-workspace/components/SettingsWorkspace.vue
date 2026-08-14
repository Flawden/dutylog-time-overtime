<script setup lang="ts">
import { computed, onBeforeMount, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";
import SettingsCard from "./SettingsCard.vue";
import AppearanceSettingsCard from "./AppearanceSettingsCard.vue";
import TimeSettingsCard from "./TimeSettingsCard.vue";
import ScheduleSettingsCard from "./ScheduleSettingsCard.vue";
import NotificationSettingsCard from "./NotificationSettingsCard.vue";
import ShiftTypeManagerModal from "./ShiftTypeManagerModal.vue";
import type { DutyLogSettingsWorkspaceDomain } from "../types/domain";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const settings = useSettingsWorkspaceStore();
const { activeRoute, rawRoute, modules: shellModules } = storeToRefs(shell);
const { profile, sessions, modules, calendarSync, telegram } = storeToRefs(settings);

type Section = "profile" | "language" | "modules" | "calendar-sync" | "appearance" | "time" | "schedule" | "notifications";
const SECTIONS: readonly Section[] = ["profile", "language", "modules", "calendar-sync", "appearance", "time", "schedule", "notifications"];
const activeSection = ref<Section>("profile");
const expandMode = ref<"single" | "all" | "none">("single");
const displayName = ref("");
const birthday = ref("");
const pwCurrent = ref("");
const pwNew = ref("");
const pwRepeat = ref("");
const telegramCode = ref<{ code: string; expiresAt: string; startCommand: string; deepLink?: string | null } | null>(null);
const exportFrom = ref("");
const exportTo = ref("");
let previousDomain: DutyLogSettingsWorkspaceDomain | undefined;

const language = computed(() => settings.language);
const text = computed(() => language.value === "en" ? {
  title: "Settings", profile: "Profile", profileHint: "name, password, Telegram", language: "Language", languageHint: "Russian / English", modules: "Modules", modulesHint: "enable needed features", calendar: "External calendar", calendarHint: ".ics and read-only subscription", appearance: "Appearance", appearanceHint: "theme, workspace and palette", time: "Time", timeHint: "timezone and shift defaults", schedule: "Schedules", scheduleHint: "cycles and people profiles", notifications: "Notifications", notificationsHint: "browser and schedules", expand: "expand all", collapse: "collapse all",
  profileEyebrow: "Profile", profileTitle: "User profile", profileDescription: "The name is shown in the app shell. Birthday is used for the calendar greeting.", name: "Display name", birthday: "Birthday", save: "Save", password: "Change password", currentPassword: "Current password", newPassword: "New password", repeatPassword: "Repeat", changePassword: "Change password", passwordMismatch: "New passwords do not match", data: "My data", exportNotes: "Download all notes (.zip)", exportHint: "Markdown files by date — opens in Obsidian.", sessions: "Active devices", noSessions: "No mobile sessions — only this browser.", active: "active", revoked: "revoked", revoke: "revoke", telegram: "Telegram bot", tgNotConfigured: "Bot is not configured on the server.", tgConnected: "Connected", tgNotConnected: "Not connected. Create a code and send it to the bot.", tgNotify: "Receive Telegram reminders", tgCode: "Create link code", tgUnlink: "Disconnect Telegram", sendBot: "Send to bot:", languageTitle: "App language", languageDescription: "Language is stored in your profile and applies to web/PWA.", ru: "Russian", en: "English", modulesTitle: "App modules", modulesDescription: "Disable features you do not need. Data is never deleted and modules can be enabled again.", enabled: "Enabled", disabled: "Disabled", base: "Base", always: "always on", depends: "depends on", savedData: "Disabled. Data is preserved and can be enabled again.", calendarTitle: "External calendar", calendarDescription: "Export a range to .ics or connect a private read-only subscription.", notConfigured: "not configured", subscriptionActive: "active", export: "One-time export", from: "From", to: "To", download: "Download .ics", private: "Private subscription", noLink: "No link has been created yet.", activeHint: "Private feed is active. Rotate it if the secret was exposed.", copyNow: "Copy the link now", copy: "Copy", issue: "Create subscription", rotate: "Rotate subscription", revokeSubscription: "Revoke link", secretWarning: "The link is a secret and grants read-only access. DutyLog stores only the SHA-256 token hash; the raw URL is shown only after issue/rotation.",
} : {
  title: "Настройки", profile: "Профиль", profileHint: "имя, пароль, Telegram", language: "Язык", languageHint: "русский / English", modules: "Модули", modulesHint: "включить нужные функции", calendar: "Внешний календарь", calendarHint: ".ics и read-only подписка", appearance: "Внешний вид", appearanceHint: "тема, рабочая область и палитра", time: "Время", timeHint: "часовой пояс и параметры смен", schedule: "Графики", scheduleHint: "циклы и календарные слои", notifications: "Уведомления", notificationsHint: "браузер и расписания", expand: "развернуть всё", collapse: "свернуть всё",
  profileEyebrow: "Профиль", profileTitle: "Профиль пользователя", profileDescription: "Имя отображается в шапке приложения. День рождения используется для поздравительного баннера в календаре.", name: "Отображаемое имя", birthday: "День рождения", save: "Сохранить", password: "Смена пароля", currentPassword: "Текущий пароль", newPassword: "Новый пароль", repeatPassword: "Ещё раз", changePassword: "Сменить пароль", passwordMismatch: "Новые пароли не совпадают", data: "Мои данные", exportNotes: "Скачать все заметки (.zip)", exportHint: "Markdown-файлы по датам — открываются в Obsidian.", sessions: "Активные устройства", noSessions: "Мобильных сессий нет — только этот браузер.", active: "активна", revoked: "отозвана", revoke: "отозвать", telegram: "Telegram-бот", tgNotConfigured: "Бот не настроен на сервере.", tgConnected: "Подключено", tgNotConnected: "Не подключено. Создайте код и отправьте его боту.", tgNotify: "Получать напоминания в Telegram", tgCode: "Создать код привязки", tgUnlink: "Отключить Telegram", sendBot: "Отправьте боту:", languageTitle: "Язык приложения", languageDescription: "Выбор языка хранится в профиле и применяется к web/PWA интерфейсу.", ru: "Русский", en: "English", modulesTitle: "Модули приложения", modulesDescription: "Отключайте функции, которые сейчас не нужны. Данные не удаляются: модуль можно включить обратно в любой момент.", enabled: "Включены", disabled: "Выключены", base: "Базовые", always: "всегда включён", depends: "зависит от", savedData: "Отключено. Данные сохранены. Можно включить обратно.", calendarTitle: "Внешний календарь", calendarDescription: "Экспортируйте диапазон в .ics или подключите приватную read-only подписку.", notConfigured: "не настроено", subscriptionActive: "активна", export: "Разовый экспорт", from: "С даты", to: "По дату", download: "Скачать .ics", private: "Приватная подписка", noLink: "Ссылка ещё не создана.", activeHint: "Приватная лента активна. Если секрет раскрыт — выпустите новую ссылку.", copyNow: "Скопируйте ссылку сейчас", copy: "Копировать", issue: "Создать подписку", rotate: "Перевыпустить подписку", revokeSubscription: "Отозвать ссылку", secretWarning: "Ссылка является секретом и даёт доступ только на чтение. DutyLog хранит SHA-256 токена; исходный URL показывается только после выпуска/ротации.",
});

const moduleRows = computed(() => [...modules.value].filter(module => !module.hidden && (!["core", "calendar", "shifts", "admin"].includes(module.key) || module.key === "admin")).sort((a,b) => a.order - b.order));
const enabledCount = computed(() => moduleRows.value.filter(item => item.enabled).length);
const disabledCount = computed(() => moduleRows.value.filter(item => !item.enabled && !item.locked).length);
const baseCount = computed(() => moduleRows.value.filter(item => item.locked).length);
const calendarEnabled = computed(() => settings.moduleEnabled("calendar_sync"));
const notificationsEnabled = computed(() => settings.moduleEnabled("notifications"));
const telegramEnabled = computed(() => settings.moduleEnabled("telegram"));
const telegramStatusText = computed(() => {
  if (!telegram.value) return language.value === "en" ? "loading…" : "загрузка…";
  if (!telegram.value.configured) return text.value.tgNotConfigured;
  if (telegram.value.linked) return `${text.value.tgConnected}: ${telegram.value.username ? `@${telegram.value.username}` : `chat ${telegram.value.chatId ?? ""}`}`;
  return text.value.tgNotConnected;
});

function sectionFromRoute(route: string): Section | null {
  const normalized = route.startsWith("settings-") ? route.slice("settings-".length) : "";
  return SECTIONS.includes(normalized as Section) ? normalized as Section : null;
}
function isOpen(section: Section): boolean { return expandMode.value === "all" || (expandMode.value === "single" && activeSection.value === section); }
function isSectionVisible(section: Section): boolean {
  if (section === "calendar-sync") return calendarEnabled.value;
  if (section === "notifications") return notificationsEnabled.value;
  return true;
}
function open(section: Section, navigate = true): void {
  if (!isSectionVisible(section)) section = "modules";
  expandMode.value = "single";
  activeSection.value = section;
  try { localStorage.setItem("dutylog.settings.openSection", section); } catch (_) {}
  if (navigate && rawRoute.value !== `settings-${section}`) navigateHashRoute(`settings-${section}`);
}
function expandAll(): void { expandMode.value = "all"; try { localStorage.setItem("dutylog.settings.openSection", "all"); } catch (_) {} }
function collapseAll(): void { expandMode.value = "none"; try { localStorage.setItem("dutylog.settings.openSection", "none"); } catch (_) {} }
function moduleTitle(module: (typeof moduleRows.value)[number]): string { return language.value === "en" ? module.titleEn || module.titleRu || module.key : module.titleRu || module.titleEn || module.key; }
function moduleDescription(module: (typeof moduleRows.value)[number]): string { return language.value === "en" ? module.descriptionEn || module.descriptionRu || "" : module.descriptionRu || module.descriptionEn || ""; }
function dependencyNames(keys: string[]): string {
  return keys
    .filter(key => !["core", "calendar", "shifts"].includes(key))
    .map(key => {
      const module = modules.value.find(item => item.key === key);
      if (!module) return key;
      return language.value === "en" ? module.titleEn || module.titleRu || key : module.titleRu || module.titleEn || key;
    })
    .join(", ");
}

async function saveProfile(): Promise<void> { await settings.saveProfile(displayName.value, birthday.value, props.bridge); }
async function changePassword(): Promise<void> {
  if (pwNew.value !== pwRepeat.value) { settings.passwordMessage = text.value.passwordMismatch; settings.passwordMessageOk = false; return; }
  await settings.changePassword(pwCurrent.value, pwNew.value);
  pwCurrent.value = ""; pwNew.value = ""; pwRepeat.value = "";
}
async function issueTelegramCode(): Promise<void> { telegramCode.value = await settings.createTelegramCode(); }
async function copyCalendarUrl(): Promise<void> {
  if (!settings.calendarSyncIssuedUrl) return;
  try { await navigator.clipboard.writeText(settings.calendarSyncIssuedUrl); settings.calendarSyncMessage = language.value === "en" ? "Link copied" : "Ссылка скопирована"; settings.calendarSyncMessageOk = true; } catch (_) {}
}
function downloadCalendar(): void {
  if (!exportFrom.value || !exportTo.value || exportTo.value < exportFrom.value) { settings.calendarSyncMessage = language.value === "en" ? "Choose a valid date range" : "Укажите корректный диапазон дат."; settings.calendarSyncMessageOk = false; return; }
  const href = `/api/v1/calendar-sync/export?from=${encodeURIComponent(exportFrom.value)}&to=${encodeURIComponent(exportTo.value)}`;
  const anchor = document.createElement("a"); anchor.href = href; anchor.download = `dutylog-calendar-${exportFrom.value}-${exportTo.value}.ics`; document.body.appendChild(anchor); anchor.click(); anchor.remove();
}
function isoDay(date: Date): string { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`; }

const domain: DutyLogSettingsWorkspaceDomain = Object.freeze({
  ready: () => settings.loaded,
  openShiftTypeManager: (editId?: number | null) => { settings.openShiftTypeManager(editId ?? null); if (!settings.shiftTypes.length) void settings.refreshShiftTypes(); },
  closeShiftTypeManager: () => settings.closeShiftTypeManager(),
  refreshShiftTypes: () => settings.refreshShiftTypes(),
  snapshot: () => Object.freeze({ shiftTypes: settings.shiftTypes.length, editingId: settings.shiftTypeEditingId, managerOpen: settings.shiftTypeManagerOpen }),
});

onBeforeMount(() => {
  previousDomain = window.DutyLogVueDomains?.settingsWorkspace;
  window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), settingsWorkspace: domain });
  props.bridge.retireDomainOwners("settings-workspace");
});
onMounted(async () => {
  const now = new Date(); const from = new Date(now); from.setDate(from.getDate() - 30); const to = new Date(now); to.setDate(to.getDate() + 335); exportFrom.value = isoDay(from); exportTo.value = isoDay(to);
  try { await settings.bootstrap(props.bridge); } catch (_) {}
  // Settings metadata may bootstrap before first-run onboarding commits its
  // module preset. The shell snapshot is the current runtime authority, so
  // merge its enabled flags after bootstrap and on every later module event.
  settings.synchronizeModuleEnabledMap(shellModules.value);
  displayName.value = profile.value?.displayName ?? ""; birthday.value = profile.value?.birthday ?? "";
  const route = sectionFromRoute(rawRoute.value);
  const saved = (() => { try { return localStorage.getItem("dutylog.settings.openSection"); } catch (_) { return null; } })();
  if (route) open(route, false); else if (saved === "all") expandAll(); else if (saved === "none") collapseAll(); else if (saved && SECTIONS.includes(saved as Section)) open(saved as Section, false); else open("profile", false);
});
watch(profile, value => { if (!value) return; displayName.value = value.displayName ?? ""; birthday.value = value.birthday ?? ""; });
watch(shellModules, value => { settings.synchronizeModuleEnabledMap(value); }, { deep: true });
watch(rawRoute, route => { const section = sectionFromRoute(route); if (section) open(section, false); });
watch([calendarEnabled, notificationsEnabled], () => { if (!isSectionVisible(activeSection.value)) open("modules"); });
onBeforeUnmount(() => {
  settings.closeShiftTypeManager();
  if (previousDomain) window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), settingsWorkspace: previousDomain });
  else if (window.DutyLogVueDomains) { const { settingsWorkspace: _removed, ...rest } = window.DutyLogVueDomains; window.DutyLogVueDomains = Object.freeze(rest); }
});
</script>

<template>
  <section v-show="activeRoute === 'settings'" class="view settingsView vueSettingsWorkspace" data-vue-domain-owner="settings-workspace" data-vue-settings-workspace-view>
    <div class="settingsShell">
      <aside class="settingsIndex" aria-label="Settings sections">
        <div class="settingsIndexTitle">{{ text.title }}</div>
        <a data-settings-jump="profile" href="#settings-profile" :class="{ on: activeSection === 'profile' && expandMode === 'single' }" @click.prevent="open('profile')"><b>{{ text.profile }}</b><span>{{ text.profileHint }}</span></a>
        <a data-settings-jump="language" href="#settings-language" :class="{ on: activeSection === 'language' && expandMode === 'single' }" @click.prevent="open('language')"><b>{{ text.language }}</b><span>{{ text.languageHint }}</span></a>
        <a data-settings-jump="modules" href="#settings-modules" :class="{ on: activeSection === 'modules' && expandMode === 'single' }" @click.prevent="open('modules')"><b>{{ text.modules }}</b><span>{{ text.modulesHint }}</span></a>
        <a v-if="calendarEnabled" data-settings-jump="calendar-sync" href="#settings-calendar-sync" :class="{ on: activeSection === 'calendar-sync' && expandMode === 'single' }" @click.prevent="open('calendar-sync')"><b>{{ text.calendar }}</b><span>{{ text.calendarHint }}</span></a>
        <a data-settings-jump="appearance" href="#settings-appearance" :class="{ on: activeSection === 'appearance' && expandMode === 'single' }" @click.prevent="open('appearance')"><b>{{ text.appearance }}</b><span>{{ text.appearanceHint }}</span></a>
        <a data-settings-jump="time" href="#settings-time" :class="{ on: activeSection === 'time' && expandMode === 'single' }" @click.prevent="open('time')"><b>{{ text.time }}</b><span>{{ text.timeHint }}</span></a>
        <a data-settings-jump="schedule" href="#settings-schedule" :class="{ on: activeSection === 'schedule' && expandMode === 'single' }" @click.prevent="open('schedule')"><b>{{ text.schedule }}</b><span>{{ text.scheduleHint }}</span></a>
        <a v-if="notificationsEnabled" data-settings-jump="notifications" href="#settings-notifications" :class="{ on: activeSection === 'notifications' && expandMode === 'single' }" @click.prevent="open('notifications')"><b>{{ text.notifications }}</b><span>{{ text.notificationsHint }}</span></a>
        <div class="settingsIndexActions"><button id="settingsExpandAll" type="button" @click="expandAll">{{ text.expand }}</button><button id="settingsCollapseAll" type="button" @click="collapseAll">{{ text.collapse }}</button></div>
      </aside>

      <div class="settingsContent">
        <SettingsCard id="profileCard" section="profile" :active="isOpen('profile')" :eyebrow="text.profileEyebrow" :title="text.profileTitle" :hint="text.profileDescription" @open="open('profile')">
          <template #status><div class="avatar avatarBig" id="profileAvatar">{{ (profile?.displayName || profile?.username || '?').slice(0,1).toUpperCase() }}</div></template>
          <div class="profileGrid"><label>{{ text.name }}<input id="profileName" v-model="displayName" maxlength="60" type="text"/></label><label>{{ text.birthday }}<input id="profileBirthday" v-model="birthday" type="date"/></label><button class="primary" id="profileSave" type="button" @click="saveProfile">{{ text.save }}</button></div>
          <div class="profileMsg" id="profileMsg" :class="{ ok: settings.profileMessageOk }">{{ settings.profileMessage }}</div>
          <div class="profileSub">{{ text.password }}</div>
          <div class="profileGrid"><label>{{ text.currentPassword }}<input id="pwCurrent" v-model="pwCurrent" autocomplete="current-password" type="password"/></label><label>{{ text.newPassword }}<input id="pwNew" v-model="pwNew" autocomplete="new-password" type="password"/></label><label>{{ text.repeatPassword }}<input id="pwRepeat" v-model="pwRepeat" autocomplete="new-password" type="password"/></label><button class="primary" id="pwChange" type="button" @click="changePassword">{{ text.changePassword }}</button></div>
          <div class="profileMsg" id="pwMsg" :class="{ ok: settings.passwordMessageOk }">{{ settings.passwordMessage }}</div>
          <div class="profileSub">{{ text.data }}</div><div class="row" style="align-items:center"><a class="dashedBtn" id="exportNotesBtn" href="/api/v1/export/notes" download>{{ text.exportNotes }}</a><span class="settingsHint" style="margin:0">{{ text.exportHint }}</span></div>
          <div class="profileSub">{{ text.sessions }}</div><div class="sessionsList" id="sessionsList"><div v-if="!sessions.length" class="sessionRow"><span class="meta">{{ text.noSessions }}</span></div><div v-for="(session, sessionIndex) in sessions" :key="session.id ?? sessionIndex" class="sessionRow"><span class="dev" :class="{ dead: !session.active }">{{ session.deviceName || 'device' }}</span><span class="meta">{{ session.active ? text.active : text.revoked }} · {{ session.lastUsedAt || session.createdAt || '' }}</span><button v-if="session.active" type="button" @click="settings.revokeSession(session.id ?? 0)">{{ text.revoke }}</button></div></div>
          <template v-if="telegramEnabled"><div class="profileSub" id="telegramProfileTitle">{{ text.telegram }}</div><div class="telegramBox" id="telegramBox"><div class="telegramStatus" id="telegramStatus" :class="{ ok: telegram?.linked, warn: telegram && !telegram.configured }">{{ telegramStatusText }}</div><label class="telegramNotifyToggle"><input id="telegramNotificationsEnabled" type="checkbox" :checked="telegram?.notificationsEnabled === true" :disabled="!telegram?.configured || !telegram?.linked" @change="settings.setTelegramNotifications(($event.target as HTMLInputElement).checked)"/> {{ text.tgNotify }}</label><div class="telegramActions"><button class="primary" id="telegramCodeBtn" type="button" @click="issueTelegramCode">{{ text.tgCode }}</button><button id="telegramUnlinkBtn" type="button" :disabled="!telegram?.linked" @click="settings.unlinkTelegram()">{{ text.tgUnlink }}</button></div><div class="telegramCode" id="telegramCodeBox" :hidden="!telegramCode"><template v-if="telegramCode"><div class="code">{{ telegramCode.code }}</div><div>{{ text.sendBot }} <b>{{ telegramCode.startCommand }}</b></div><div class="meta"><a v-if="telegramCode.deepLink" :href="telegramCode.deepLink" target="_blank" rel="noreferrer">{{ telegramCode.deepLink }}</a></div></template></div><div class="telegramCommands"><span>/today</span><span>/tasks</span><span>/task</span><span>/done</span><span>/ppr</span><span>/timeoff</span><span>/balance</span></div></div></template>
        </SettingsCard>

        <SettingsCard id="languageCard" section="language" :active="isOpen('language')" eyebrow="Интерфейс" :title="text.languageTitle" :hint="text.languageDescription" @open="open('language')">
          <template #status><div class="status statusLanguageSummary" id="languageStatus">{{ language === 'en' ? 'English' : 'Русский' }}</div></template>
          <div class="languageChoiceGrid"><button class="languageChoice" :class="{ on: language === 'ru' }" data-language-choice="ru" type="button" @click="settings.setLanguage('ru', bridge)"><b>Русский</b><span>Основной язык</span></button><button class="languageChoice" :class="{ on: language === 'en' }" data-language-choice="en" type="button" @click="settings.setLanguage('en', bridge)"><b>English</b><span>Additional language</span></button></div><div class="profileMsg" id="languageMsg">{{ settings.languageMessage }}</div>
        </SettingsCard>

        <SettingsCard id="modulesCard" section="modules" :active="isOpen('modules')" eyebrow="Модульность" :title="text.modulesTitle" :hint="text.modulesDescription" @open="open('modules')">
          <template #status><div class="status statusMetrics" id="modulesStatus"><span class="statusChip statusChipOk"><b>{{ enabledCount }}</b> {{ text.enabled }}</span><span class="statusChip"><b>{{ disabledCount }}</b> {{ text.disabled }}</span><span class="statusChip"><b>{{ baseCount }}</b> {{ text.base }}</span></div></template>
          <div class="moduleGrid" id="moduleSettingsGrid"><label v-for="module in moduleRows" :key="module.key" class="moduleCard" :class="{ on: module.enabled, locked: module.locked }"><input type="checkbox" :checked="module.enabled" :disabled="module.locked" :data-module-toggle="module.key" @change="settings.toggleModule(module.key, ($event.target as HTMLInputElement).checked, bridge)"/><span class="moduleMain"><span class="moduleTop"><b>{{ moduleTitle(module) }}</b><span class="moduleBadge">{{ module.locked ? text.always : module.enabled ? text.enabled : text.disabled }}</span></span><span class="moduleDescription">{{ moduleDescription(module) }}</span><small v-if="dependencyNames(module.dependencies)">{{ text.depends }}: {{ dependencyNames(module.dependencies) }}</small><small v-if="!module.enabled && !module.locked" class="moduleDisabledHint">{{ text.savedData }}</small></span></label></div><div class="profileMsg" id="modulesMsg" :class="{ ok: settings.modulesMessageOk }">{{ settings.modulesMessage }}</div>
        </SettingsCard>

        <SettingsCard v-if="calendarEnabled" id="calendarSyncCard" section="calendar-sync" :active="isOpen('calendar-sync')" eyebrow="Интеграции" :title="text.calendarTitle" :hint="text.calendarDescription" @open="open('calendar-sync')">
          <template #status><div class="status" id="calendarSyncStatus">{{ calendarSync?.active ? text.subscriptionActive : text.notConfigured }}</div></template>
          <div class="calendarSyncGrid"><div class="calendarSyncBlock"><div class="profileSub">{{ text.export }}</div><div class="calendarSyncRange"><label>{{ text.from }}<input id="calendarExportFrom" v-model="exportFrom" type="date"/></label><label>{{ text.to }}<input id="calendarExportTo" v-model="exportTo" type="date"/></label><button class="primary" id="calendarExportRange" type="button" @click="downloadCalendar">{{ text.download }}</button></div></div><div class="calendarSyncBlock"><div class="profileSub">{{ text.private }}</div><div class="calendarSyncSummary" id="calendarSyncSummary">{{ calendarSync?.active ? text.activeHint : text.noLink }}</div><div class="calendarSyncSecret" id="calendarSyncSecret" :hidden="!settings.calendarSyncIssuedUrl"><label>{{ text.copyNow }}<input id="calendarSyncUrl" :value="settings.calendarSyncIssuedUrl" readonly/></label><button id="calendarSyncCopy" type="button" @click="copyCalendarUrl">{{ text.copy }}</button></div><div class="calendarSyncActions"><button class="primary" id="calendarSyncIssue" type="button" @click="settings.rotateCalendarSync()">{{ calendarSync?.active ? text.rotate : text.issue }}</button><button class="dangerGhost" id="calendarSyncRevoke" type="button" :hidden="!calendarSync?.active" @click="settings.revokeCalendarSync()">{{ text.revokeSubscription }}</button></div><div class="settingsHint calendarSyncWarning">{{ text.secretWarning }}</div></div></div><div class="profileMsg" id="calendarSyncMsg" :class="{ ok: settings.calendarSyncMessageOk }">{{ settings.calendarSyncMessage }}</div>
        </SettingsCard>

        <AppearanceSettingsCard :bridge="bridge" :active="isOpen('appearance')" @open="open('appearance')" />

        <TimeSettingsCard :bridge="bridge" :active="isOpen('time')" @open="open('time')" />
        <ScheduleSettingsCard :active="isOpen('schedule')" @open="open('schedule')" />
        <NotificationSettingsCard v-if="notificationsEnabled" :active="isOpen('notifications')" @open="open('notifications')" />
      </div>
    </div>
  </section>
  <ShiftTypeManagerModal />
</template>
