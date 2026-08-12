<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { navigateHashRoute } from "@/platform/router/hashRoute";
import { RELEASE_VERSION } from "@/platform/version";
import { useAdminStore } from "../stores/adminStore";
import type { AdminRole, AdminSystemStatus, AdminUser } from "../types/domain";

const shell = useShellStore();
const store = useAdminStore();
const { activeRoute, language, admin, modulesLoaded, modules } = storeToRefs(shell);
const { usersPage, registration, diagnostics, usersLoading, registrationLoading, diagnosticsLoading, error } = storeToRefs(store);

const root = ref<HTMLElement | null>(null);
const query = ref("");
const roleFilter = ref<"all" | AdminRole>("all");
const activeSection = ref<"users" | "registration" | "diagnostics">("users");
const message = ref("");
const messageOk = ref(true);
const browserSummary = navigator.userAgent.replace(/\s+/g, " ").slice(0, 90);
let searchTimer: ReturnType<typeof setTimeout> | null = null;
let observer: IntersectionObserver | null = null;

const visible = computed(() => activeRoute.value === "admin" && admin.value && (!modulesLoaded.value || modules.value.admin !== false));
const text = computed(() => language.value === "en" ? {
  title: "Administration", profile: "Service workspace", intro: "Users, access, registration and system diagnostics. This workspace is available only to application administrators.",
  users: "Users", usersHint: "roles and passwords", registration: "Registration", registrationHint: "new account access", diagnostics: "Diagnostics", diagnosticsHint: "server, database and client",
  back: "Back to settings", usersTitle: "Users and roles", usersDescription: "Public registration creates USER accounts only. Existing administrators can grant ADMIN access.",
  search: "search: login, name, role…", allRoles: "all roles", refreshUsers: "Refresh users", shown: "Shown", adminsOnPage: "Admins on page", noUsers: "No users found.",
  created: "created", updated: "updated", you: "you", password: "Reset password", passwordPrompt: "New password (minimum 12 characters)", passwordShort: "Password must contain at least 12 characters", roleSaved: "Role updated", passwordSaved: "Password updated",
  registrationTitle: "Public registration", registrationDescription: "Allow or block creation of new ordinary accounts. The bootstrap administrator is still created only from environment variables.", allowRegistration: "Allow public user registration", refreshRegistration: "Refresh registration status", open: "open", closed: "closed", defaultSource: "default value", databaseSource: "admin setting", changed: "changed", by: "by",
  diagnosticsTitle: "System status", diagnosticsDescription: "Application version, server, database, session protection and Telegram integration.", frontend: "Frontend", uiCore: "UI Core", cache: "App cache", browser: "Browser", csrf: "Session protection", refreshDiagnostics: "Refresh diagnostics", copyDiagnostics: "Copy report", copyOk: "Diagnostics report copied", copyFail: "Failed to copy diagnostics", clickDiagnostics: "Refresh diagnostics to load the report.",
  serverVersion: "Server version", profiles: "Spring profiles", serverTime: "Server time", serverTimezone: "Server timezone", database: "Database", totalUsers: "Users", admins: "Administrators", roles: "Access roles", tiers: "Future tiers", publicRegistration: "Public registration", registrationSource: "Registration setting source", telegramBot: "Telegram bot", telegramToken: "Telegram token", telegramPolling: "Telegram polling", telegramNotifications: "Telegram notifications", telegramLinked: "Telegram linked", enabled: "enabled", disabled: "disabled", configured: "configured", notConfigured: "not configured", yes: "yes", no: "no", active: "active", notRegistered: "not registered", unsupported: "unsupported", cookiePresent: "cookie present", cookieMissing: "cookie missing", loading: "Loading…", failed: "Operation failed", previous: "Previous", next: "Next",
} : {
  title: "Администрирование", profile: "Служебный профиль", intro: "Пользователи, доступ, регистрация и диагностика системы. Этот раздел доступен только администратору приложения.",
  users: "Пользователи", usersHint: "роли и пароли", registration: "Регистрация", registrationHint: "доступ новых аккаунтов", diagnostics: "Диагностика", diagnosticsHint: "сервер, база и клиент",
  back: "Назад к настройкам", usersTitle: "Пользователи и роли", usersDescription: "Публичная регистрация создаёт только USER. Действующий администратор может назначить дополнительный ADMIN-доступ.",
  search: "поиск: логин, имя, роль…", allRoles: "все роли", refreshUsers: "Обновить пользователей", shown: "Показано", adminsOnPage: "Админов на странице", noUsers: "Пользователи не найдены.",
  created: "создан", updated: "обновлён", you: "это вы", password: "Сменить пароль", passwordPrompt: "Новый пароль (минимум 12 символов)", passwordShort: "Пароль должен быть минимум 12 символов", roleSaved: "Роль обновлена", passwordSaved: "Пароль обновлён",
  registrationTitle: "Публичная регистрация", registrationDescription: "Можно открыть или закрыть создание новых обычных аккаунтов. Стартовый админ по-прежнему создаётся только через переменные окружения.", allowRegistration: "Разрешить публичную регистрацию пользователей", refreshRegistration: "Обновить статус регистрации", open: "открыта", closed: "закрыта", defaultSource: "значение по умолчанию", databaseSource: "из админки", changed: "изменено", by: "пользователем",
  diagnosticsTitle: "Состояние системы", diagnosticsDescription: "Версия приложения, сервер, база данных, защита сессии и Telegram-интеграция.", frontend: "Интерфейс", uiCore: "UI Core", cache: "Кэш приложения", browser: "Браузер", csrf: "Защита сессии", refreshDiagnostics: "Обновить диагностику", copyDiagnostics: "Скопировать отчёт", copyOk: "Отчёт диагностики скопирован", copyFail: "Не удалось скопировать отчёт", clickDiagnostics: "Нажмите «Обновить диагностику», чтобы получить отчёт.",
  serverVersion: "Версия сервера", profiles: "Профили Spring", serverTime: "Серверное время", serverTimezone: "Часовой пояс сервера", database: "База данных", totalUsers: "Пользователи", admins: "Администраторы", roles: "Роли доступа", tiers: "Будущие тарифы", publicRegistration: "Публичная регистрация", registrationSource: "Источник настройки регистрации", telegramBot: "Telegram bot", telegramToken: "Telegram token", telegramPolling: "Telegram polling", telegramNotifications: "Telegram уведомления", telegramLinked: "Аккаунт подключен к Telegram", enabled: "включён", disabled: "выключен", configured: "задан", notConfigured: "не задан", yes: "да", no: "нет", active: "активен", notRegistered: "не зарегистрирован", unsupported: "не поддерживается", cookiePresent: "cookie есть", cookieMissing: "cookie не найден", loading: "Загрузка…", failed: "Операция не выполнена", previous: "Назад", next: "Дальше",
});

const adminsOnPage = computed(() => usersPage.value.items.filter(user => user.role === "ADMIN").length);
const shownRange = computed(() => {
  if (!usersPage.value.total || !usersPage.value.items.length) return "0";
  const start = usersPage.value.page * usersPage.value.size + 1;
  const end = start + usersPage.value.items.length - 1;
  return `${start}–${end} / ${usersPage.value.total}`;
});

function fmt(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(language.value === "en" ? "en-US" : "ru-RU");
}

function canChangeRole(user: AdminUser): boolean {
  return !(user.bootstrapAdmin && user.role === "ADMIN") && !user.currentUser;
}

function flash(ok: boolean, value: string): void {
  messageOk.value = ok;
  message.value = value;
  window.setTimeout(() => { if (message.value === value) message.value = ""; }, 3500);
}

async function refreshUsers(): Promise<void> {
  store.query = query.value;
  try { await store.loadUsers(); } catch { flash(false, error.value || text.value.failed); }
}

async function changePageSize(event: Event): Promise<void> {
  const target = event.target as HTMLSelectElement;
  try { await store.setSize(Number(target.value)); }
  catch { flash(false, error.value || text.value.failed); }
}

async function changeRole(user: AdminUser, event: Event): Promise<void> {
  const target = event.target as HTMLSelectElement;
  const next = target.value as AdminRole;
  if (next === user.role) return;
  try { await store.updateRole(user.id, next); flash(true, `${text.value.roleSaved}: @${user.username} → ${next}`); }
  catch { target.value = user.role; flash(false, error.value || text.value.failed); }
}

async function resetPassword(user: AdminUser): Promise<void> {
  const password = window.prompt(`${text.value.passwordPrompt}: @${user.username}`);
  if (password == null) return;
  if (password.length < 12) { flash(false, text.value.passwordShort); return; }
  try { await store.resetPassword(user.id, password); flash(true, `${text.value.passwordSaved}: @${user.username}`); }
  catch { flash(false, error.value || text.value.failed); }
}

async function changeRegistration(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement;
  try { await store.updateRegistration(target.checked); }
  catch { target.checked = registration.value?.enabled === true; flash(false, error.value || text.value.failed); }
}

function registrationDetails(): string {
  const value = registration.value;
  if (!value) return text.value.loading;
  const state = value.enabled ? text.value.open : text.value.closed;
  const source = value.source === "database" ? text.value.databaseSource : text.value.defaultSource;
  const changed = value.updatedAt ? ` · ${text.value.changed} ${fmt(value.updatedAt)}${value.updatedBy ? ` ${text.value.by} ${value.updatedBy}` : ""}` : "";
  return `${text.value.registrationTitle}: ${state} · ${source}${changed}`;
}

function clientCacheStatus(): string {
  if (!("serviceWorker" in navigator)) return text.value.unsupported;
  return navigator.serviceWorker.controller ? text.value.active : text.value.notRegistered;
}

function csrfStatus(): string {
  return document.cookie.split(";").some(value => value.trim().startsWith("XSRF-TOKEN=")) ? text.value.cookiePresent : text.value.cookieMissing;
}

function rows(data: AdminSystemStatus | null): Array<{ label: string; value: string; tone?: "ok" | "warn" | undefined }> {
  if (!data) return [];
  return [
    { label: text.value.serverVersion, value: data.version },
    { label: text.value.profiles, value: data.profiles.join(", ") || "default/dev" },
    { label: text.value.serverTime, value: data.serverTime },
    { label: text.value.serverTimezone, value: data.serverTimezone },
    { label: text.value.database, value: data.database.ok ? "ok" : (data.database.error || "error"), tone: data.database.ok ? "ok" : "warn" },
    { label: text.value.totalUsers, value: String(data.users.total) },
    { label: text.value.admins, value: String(data.users.admins), tone: data.users.admins > 0 ? "ok" : "warn" },
    { label: text.value.roles, value: data.users.rolesAllowed.join(", ") || "USER, ADMIN" },
    { label: text.value.tiers, value: data.users.accountTiersReserved.join(", ") || "FREE, PAID, VIP" },
    { label: text.value.publicRegistration, value: data.registration.enabled ? text.value.open : text.value.closed, tone: data.registration.enabled ? "warn" : "ok" },
    { label: text.value.registrationSource, value: data.registration.source === "database" ? text.value.databaseSource : text.value.defaultSource },
    { label: text.value.telegramBot, value: data.telegram.enabled ? text.value.enabled : text.value.disabled, tone: data.telegram.enabled ? "ok" : undefined },
    { label: text.value.telegramToken, value: data.telegram.tokenConfigured ? text.value.configured : text.value.notConfigured, tone: data.telegram.tokenConfigured ? "ok" : undefined },
    { label: text.value.telegramPolling, value: data.telegram.pollingEnabled ? text.value.enabled : text.value.disabled, tone: data.telegram.pollingEnabled ? "ok" : undefined },
    { label: text.value.telegramNotifications, value: data.telegram.notificationsEnabled ? text.value.enabled : text.value.disabled, tone: data.telegram.notificationsEnabled ? "ok" : undefined },
    { label: text.value.telegramLinked, value: data.telegram.linked ? text.value.yes : text.value.no, tone: data.telegram.linked ? "ok" : undefined },
  ];
}

function diagnosticsReport(): string {
  const data = diagnostics.value;
  return [
    `DutyLog UI: v${RELEASE_VERSION}`,
    "Client: Vue web/PWA inside Spring Boot monolith",
    `Server: ${data?.version ?? "—"}`,
    `Profiles: ${data?.profiles.join(", ") || "default/dev"}`,
    `Server time: ${data?.serverTime ?? "—"}`,
    `Server timezone: ${data?.serverTimezone ?? "—"}`,
    `Database: ${data?.database.ok ? "ok" : (data?.database.error ?? "unknown")}`,
    `Users total: ${data?.users.total ?? "unknown"}`,
    `Admins total: ${data?.users.admins ?? "unknown"}`,
    `Registration enabled: ${Boolean(data?.registration.enabled)}`,
    `Telegram enabled: ${Boolean(data?.telegram.enabled)}`,
    `Telegram linked: ${Boolean(data?.telegram.linked)}`,
    `Browser: ${navigator.userAgent}`,
  ].join("\n");
}

async function copyDiagnostics(): Promise<void> {
  try { await navigator.clipboard.writeText(diagnosticsReport()); flash(true, text.value.copyOk); }
  catch { flash(false, text.value.copyFail); }
}

function go(section: "users" | "registration" | "diagnostics"): void {
  activeSection.value = section;
  root.value?.querySelector<HTMLElement>(`[data-admin-section="${section}"]`)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function installObserver(): void {
  observer?.disconnect();
  if (!("IntersectionObserver" in window) || !root.value) return;
  observer = new IntersectionObserver(entries => {
    const visibleEntry = entries.filter(entry => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
    const section = (visibleEntry?.target as HTMLElement | undefined)?.dataset.adminSection;
    if (section === "users" || section === "registration" || section === "diagnostics") activeSection.value = section;
  }, { threshold: [0.22, 0.55], rootMargin: "-15% 0px -55% 0px" });
  root.value.querySelectorAll<HTMLElement>("[data-admin-section]").forEach(card => observer?.observe(card));
}

watch(query, () => {
  if (!visible.value) return;
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => { store.usersPage.page = 0; void refreshUsers(); }, 350);
});

watch(roleFilter, async value => {
  if (!visible.value) return;
  try { await store.setRoleFilter(value); } catch { flash(false, error.value || text.value.failed); }
});

watch(visible, async isVisible => {
  if (!isVisible) return;
  query.value = store.query;
  roleFilter.value = store.role;
  await nextTick();
  installObserver();
  await store.refreshAll();
}, { immediate: true });

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer);
  observer?.disconnect();
});
</script>

<template>
  <section v-if="visible" id="view-admin" ref="root" class="view adminView" data-vue-domain-route="admin">
    <div class="adminShell settingsShell">
      <aside class="settingsIndex adminIndex" :aria-label="text.title">
        <div class="settingsIndexTitle">{{ text.title }}</div>
        <a href="#admin-users" :class="{ on: activeSection === 'users' }" data-admin-jump="users" @click.prevent="go('users')"><b>{{ text.users }}</b><span>{{ text.usersHint }}</span></a>
        <a href="#admin-registration" :class="{ on: activeSection === 'registration' }" data-admin-jump="registration" @click.prevent="go('registration')"><b>{{ text.registration }}</b><span>{{ text.registrationHint }}</span></a>
        <a href="#admin-diagnostics" :class="{ on: activeSection === 'diagnostics' }" data-admin-jump="diagnostics" @click.prevent="go('diagnostics')"><b>{{ text.diagnostics }}</b><span>{{ text.diagnosticsHint }}</span></a>
        <div class="settingsIndexActions"><button id="adminBackNav" type="button" @click="navigateHashRoute('settings')">{{ text.back }}</button></div>
      </aside>

      <div class="adminContent settingsContent">
        <div class="adminIntro settingsCard">
          <div class="settingsHead"><div><div class="eyebrow">{{ text.title }}</div><div class="settingsTitle">{{ text.profile }}</div><div class="settingsHint">{{ text.intro }}</div></div><button id="adminBack" type="button" @click="navigateHashRoute('settings')">{{ text.back }}</button></div>
        </div>

        <div id="adminUsersCard" class="settingsCard" data-admin-section="users">
          <a id="admin-users" class="settingsAnchor"></a>
          <div class="settingsHead"><div><div class="eyebrow">{{ text.users }}</div><div class="settingsTitle">{{ text.usersTitle }}</div><div class="settingsHint">{{ text.usersDescription }}</div></div><div id="adminUsersStatus" class="status statusMetrics"><span class="statusChip"><b>{{ text.shown }}:</b> {{ shownRange }}</span><span class="statusChip" :class="adminsOnPage > 0 ? 'statusChipOk' : 'statusChipWarn'"><b>{{ text.adminsOnPage }}:</b> {{ adminsOnPage }}</span></div></div>
          <div class="adminUserFilters"><input id="adminUsersSearch" v-model="query" :placeholder="text.search" type="text"/><select id="adminUsersRoleFilter" v-model="roleFilter"><option value="all">{{ text.allRoles }}</option><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select></div>
          <div id="adminUsersPager" class="pagerRow">
            <button type="button" :disabled="!usersPage.hasPrevious || usersLoading" @click="store.setPage(usersPage.page - 1)">{{ text.previous }}</button>
            <span>{{ usersPage.page + 1 }} / {{ Math.max(1, usersPage.totalPages) }}</span>
            <button type="button" :disabled="!usersPage.hasNext || usersLoading" @click="store.setPage(usersPage.page + 1)">{{ text.next }}</button>
            <select :value="usersPage.size" :disabled="usersLoading" @change="changePageSize"><option :value="10">10</option><option :value="25">25</option><option :value="50">50</option><option :value="100">100</option></select>
          </div>
          <div id="adminUsersList" class="adminUsersList">
            <span v-if="usersLoading && !usersPage.items.length" class="emptyLine">{{ text.loading }}</span>
            <article v-for="user in usersPage.items" :key="user.id" class="adminUserRow" :data-user-id="user.id">
              <div class="adminUserMain"><b>{{ user.displayName || user.username }}</b><span>@{{ user.username }} · {{ text.created }} {{ fmt(user.createdAt) }} · {{ text.updated }} {{ fmt(user.updatedAt) }}</span><div class="adminUserBadges"><span v-if="user.bootstrapAdmin" class="miniBadge warn">env admin</span><span v-if="user.currentUser" class="miniBadge">{{ text.you }}</span><span class="miniBadge">{{ user.accountTier || 'FREE' }}</span></div></div>
              <div class="adminUserActions"><select :value="user.role" :disabled="!canChangeRole(user)" :data-admin-role="user.id" @change="changeRole(user, $event)"><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select><button type="button" :data-admin-password="user.id" :data-username="user.username" @click="resetPassword(user)">{{ text.password }}</button></div>
            </article>
            <span v-if="!usersLoading && !usersPage.items.length" class="emptyLine">{{ text.noUsers }}</span>
          </div>
          <div class="diagnosticsActions"><button id="adminUsersRefresh" class="primary" type="button" :disabled="usersLoading" @click="refreshUsers">{{ text.refreshUsers }}</button></div>
        </div>

        <div id="registrationAdminCard" class="settingsCard" data-admin-section="registration">
          <a id="admin-registration" class="settingsAnchor"></a>
          <div class="settingsHead"><div><div class="eyebrow">{{ text.registration }}</div><div class="settingsTitle">{{ text.registrationTitle }}</div><div class="settingsHint">{{ text.registrationDescription }}</div></div><div id="registrationAdminStatus" class="status" :class="registration?.enabled ? 'warn' : 'ok'">{{ registration?.enabled ? text.open : text.closed }}</div></div>
          <div class="settingsGrid one"><label class="checkline"><input id="registrationEnabledToggle" type="checkbox" :checked="registration?.enabled === true" :disabled="registrationLoading" @change="changeRegistration"><span>{{ text.allowRegistration }}</span></label></div>
          <div id="registrationAdminDetails" class="settingsHint">{{ registrationDetails() }}</div>
          <div class="diagnosticsActions"><button id="registrationRefresh" type="button" :disabled="registrationLoading" @click="store.loadRegistration">{{ text.refreshRegistration }}</button></div>
        </div>

        <div id="diagnosticsCard" class="settingsCard" data-admin-section="diagnostics">
          <a id="admin-diagnostics" class="settingsAnchor"></a>
          <div class="settingsHead"><div><div class="eyebrow">{{ text.diagnostics }}</div><div class="settingsTitle">{{ text.diagnosticsTitle }}</div><div class="settingsHint">{{ text.diagnosticsDescription }}</div></div><div id="diagnosticsStatus" class="status" :class="diagnostics?.database.ok ? 'ok' : diagnostics ? 'warn' : ''">{{ diagnosticsLoading ? text.loading : diagnostics?.database.ok ? 'ok' : diagnostics ? 'check' : '—' }}</div></div>
          <div id="diagnosticsGrid" class="diagnosticsGrid"><div class="diagItem"><span>{{ text.frontend }}</span><b id="diagFrontend">v{{ RELEASE_VERSION }}</b></div><div class="diagItem"><span>{{ text.uiCore }}</span><b id="diagUiCore">v1</b></div><div class="diagItem"><span>{{ text.cache }}</span><b id="diagSw">{{ clientCacheStatus() }}</b></div><div class="diagItem"><span>{{ text.browser }}</span><b id="diagBrowser">{{ browserSummary }}</b></div><div class="diagItem"><span>{{ text.csrf }}</span><b id="diagCsrf">{{ csrfStatus() }}</b></div></div>
          <div id="diagnosticsList" class="diagnosticsList"><div v-for="row in rows(diagnostics)" :key="row.label" class="diagRow" :class="row.tone"><span>{{ row.label }}</span><b>{{ row.value }}</b></div><span v-if="!diagnostics" class="emptyLine">{{ text.clickDiagnostics }}</span></div>
          <div class="diagnosticsActions"><button id="diagnosticsRefresh" class="primary" type="button" :disabled="diagnosticsLoading" @click="store.loadDiagnostics">{{ text.refreshDiagnostics }}</button><button id="diagnosticsCopy" type="button" @click="copyDiagnostics">{{ text.copyDiagnostics }}</button></div>
        </div>

        <div v-if="message || error" id="adminMessage" class="appModalMessage" :class="(message ? messageOk : false) ? 'ok' : 'err'" role="status" aria-live="polite">{{ message || error }}</div>
      </div>
    </div>
  </section>
</template>
