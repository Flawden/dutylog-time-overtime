/*
 * 20-data.js — Data: API wrappers, CSRF, IndexedDB snapshot and offline queue
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

Object.assign(I18N_EN, {
  "Синхронизация…":"Syncing…",
  "Синхронизация завершена":"Sync completed",
  "Нет изменений":"No changes",
  "Нет подключения к сети":"No network connection",
  "Не все изменения отправлены":"Some changes were not uploaded",
  "Синхронизация уже выполняется":"Sync is already running",
  "Синхронизация выполняется в другой вкладке":"Sync is running in another tab"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

/* ─── API ───────────────────────────────────────────────────── */
const api = {
  async shiftTypes()        { return jfetch("/api/shift-types"); },
  async createShiftType(b)  { return jfetch("/api/shift-types", { method:"POST", body:b }); },
  async updateShiftType(id, b) { return jfetch(`/api/shift-types/${id}`, { method:"PATCH", body:b }); },
  async deleteShiftType(id) { return jfetch(`/api/shift-types/${id}`, { method:"DELETE" }); },
  async previewLegacyShifts(sourceTimezone) { return jfetch(`/api/shifts/legacy-migration/preview?sourceTimezone=${encodeURIComponent(sourceTimezone)}`); },
  async migrateLegacyShifts(b) { return jfetch("/api/shifts/legacy-migration", { method:"POST", body:b }); },
  async month(y, m, opts = {}) {
    const r = monthFromTo(y, m);
    const fresh = !!opts.fresh;
    const suffix = fresh ? `&_=${Date.now()}` : "";
    return jfetch(`/api/calendar?from=${r.from}&to=${r.to}${suffix}`, { cache:fresh ? "no-store" : undefined });
  },
  async upsertDay(k, b)     { return jfetch(`/api/days/${k}`, { method:"PUT", body:b }); },
  async fillDays(b)        { return jfetch("/api/days/fill", { method:"POST", body:b }); },
  async modules()          { return jfetch("/api/modules"); },
  async moduleContracts()  { return jfetch("/api/modules/contracts"); },
  async updateModules(enabled) { return jfetch("/api/modules", { method:"PATCH", body:{ enabled } }); },
  async task(id)            { return jfetch(`/api/tasks/${id}`); },
  async createTask(b)      { return jfetch("/api/tasks", { method:"POST", body:b }); },
  async updateTask(id, b)  { return jfetch(`/api/tasks/${id}`, { method:"PATCH", body:b }); },
  async updateSubtask(taskId, subtaskId, b) { return jfetch(`/api/tasks/${taskId}/subtasks/${subtaskId}`, { method:"PATCH", body:b }); },
  async deleteTask(id)     { return jfetch(`/api/tasks/${id}`, { method:"DELETE" }); },
  async taskBoard(filters = {}) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(filters)) if (v !== undefined && v !== null && String(v).trim() !== "") qs.set(k, v);
    return jfetch(`/api/tasks/board?${qs.toString()}`);
  },
  async taskMetadata() { return jfetch("/api/tasks/metadata"); },
  async previewLegacyTaskDeadlines(sourceTimezone) { return jfetch(`/api/tasks/legacy-deadline-migration/preview?sourceTimezone=${encodeURIComponent(sourceTimezone)}`); },
  async migrateLegacyTaskDeadlines(b) { return jfetch("/api/tasks/legacy-deadline-migration", { method:"POST", body:b }); },
  async inbox(status = "open") { return jfetch(`/api/inbox?status=${encodeURIComponent(status)}`); },
  async createInbox(b) { return jfetch("/api/inbox", { method:"POST", body:b }); },
  async updateInbox(id, b) { return jfetch(`/api/inbox/${id}`, { method:"PATCH", body:b }); },
  async deleteInbox(id) { return jfetch(`/api/inbox/${id}`, { method:"DELETE" }); },
  async convertInboxToTask(id, b) { return jfetch(`/api/inbox/${id}/task`, { method:"POST", body:b }); },
  async importantDays() { return jfetch("/api/important-days"); },
  async createImportantDay(b) { return jfetch("/api/important-days", { method:"POST", body:b }); },
  async updateImportantDay(id, b) { return jfetch(`/api/important-days/${id}`, { method:"PATCH", body:b }); },
  async deleteImportantDay(id) { return jfetch(`/api/important-days/${id}`, { method:"DELETE" }); },
  async overtimeAccount() { return jfetch("/api/overtime/account"); },
  async overtimeAccountPage(filters = {}) { const qs = new URLSearchParams(); for (const [k, v] of Object.entries(filters)) if (v !== undefined && v !== null && String(v).trim() !== "") qs.set(k, v); return jfetch(`/api/overtime/account-page?${qs.toString()}`); },
  async createOvertimeCredit(b) { return jfetch("/api/overtime/credits", { method:"POST", body:b }); },
  async updateOvertimeCredit(id, b) { return jfetch(`/api/overtime/credits/${id}`, { method:"PATCH", body:b }); },
  async deleteOvertimeCredit(id) { return jfetch(`/api/overtime/credits/${id}`, { method:"DELETE" }); },
  async createOvertimeUsage(b) { return jfetch("/api/overtime/usages", { method:"POST", body:b }); },
  async updateOvertimeUsage(id, b) { return jfetch(`/api/overtime/usages/${id}`, { method:"PATCH", body:b }); },
  async deleteOvertimeUsage(id) { return jfetch(`/api/overtime/usages/${id}`, { method:"DELETE" }); },
  async previewLegacyOvertime(b) { return jfetch("/api/overtime/legacy-credits/preview", { method:"POST", body:b }); },
  async migrateLegacyOvertime(b) { return jfetch("/api/overtime/legacy-credits/migrate", { method:"POST", body:b }); },
  async updateNotificationSettings(b) { return jfetch("/api/notifications/settings", { method:"PATCH", body:b }); },
  async notificationUpcoming(from, to, includePast = true) { return jfetch(`/api/notifications/upcoming?from=${from}&to=${to}&includePast=${includePast ? "true" : "false"}`); },
  async notificationTomorrow() { return jfetch("/api/notifications/tomorrow"); },
  async quickScenarios() { return jfetch("/api/quick-scenarios"); },
  async createQuickScenario(b) { return jfetch("/api/quick-scenarios", { method:"POST", body:b }); },
  async updateQuickScenario(id, b) { return jfetch(`/api/quick-scenarios/${id}`, { method:"PATCH", body:b }); },
  async deleteQuickScenario(id) { return jfetch(`/api/quick-scenarios/${id}`, { method:"DELETE" }); },
  async telegramStatus() { return jfetch("/api/telegram/status"); },
  async telegramCode() { return jfetch("/api/telegram/link-code", { method:"POST" }); },
  async telegramSettings(b) { return jfetch("/api/telegram/settings", { method:"PATCH", body:b }); },
  async telegramUnlink() { return jfetch("/api/telegram/link", { method:"DELETE" }); },
  async systemStatus() { return jfetch("/api/admin/status"); },
  async adminUsers(params = {}) { const qs = new URLSearchParams(); for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && String(v).trim() !== "") qs.set(k, v); return jfetch(`/api/admin/users?${qs.toString()}`); },
  async updateAdminUserRole(id, role) { return jfetch(`/api/admin/users/${id}/role`, { method:"PATCH", body:{ role } }); },
  async resetAdminUserPassword(id, newPassword) { return jfetch(`/api/admin/users/${id}/password`, { method:"POST", body:{ newPassword } }); },
  async registrationSettings() { return jfetch("/api/admin/settings/registration"); },
  async updateRegistrationSettings(enabled) { return jfetch("/api/admin/settings/registration", { method:"PATCH", body:{ enabled } }); },
};

const MODULE_KEYS = ["core","calendar","shifts","notes","tasks","overtime","important_dates","notifications","telegram","scenarios","admin"];
function setModuleList(list){
  state.modulesList = Array.isArray(list) ? list.filter(m => !m.hidden) : [];
  const map = { ...state.modules };
  for (const m of state.modulesList) map[m.key] = !!m.enabled;
  state.modules = map;
  state.modulesLoaded = true;
  applyModuleVisibility();
  // A module toggle is also a runtime boundary. In particular, an already
  // running notification interval must stop immediately when the module is
  // disabled instead of continuing to hit a guarded endpoint every 10 seconds.
  if (typeof syncBrowserNotificationSchedulerForModules === "function") {
    syncBrowserNotificationSchedulerForModules();
  }
}
function moduleEnabled(key){
  if (!key || key === "core") return true;
  if (!state.modulesLoaded) return true;
  return state.modules[key] !== false;
}
function moduleTitle(m){ return state.language === "en" ? (m.titleEn || m.titleRu || m.key) : (m.titleRu || m.titleEn || m.key); }
function moduleDescription(m){ return state.language === "en" ? (m.descriptionEn || m.descriptionRu || "") : (m.descriptionRu || m.descriptionEn || ""); }
function moduleDisplayName(key){
  const mod = (state.modulesList || []).find(m => m.key === key);
  return mod ? moduleTitle(mod) : key;
}
function moduleCategoryLabel(category){
  const key = String(category || "").toLowerCase();
  const ru = { core:"ядро", calendar:"календарь", productivity:"продуктивность", time_accounting:"учёт времени", integration:"интеграции", admin:"администрирование" }[key] || key;
  return t(ru);
}
function moduleContractCounts(m){
  const ui = Array.isArray(m?.uiSlots) ? m.uiSlots.length : 0;
  const api = Array.isArray(m?.apiPrefixes) ? m.apiPrefixes.length : 0;
  const offline = Array.isArray(m?.offlineQueueTypes) ? m.offlineQueueTypes.length : 0;
  return `${ui} UI · ${api} ${t("API")} · ${offline} ${t("offline")}`;
}
function requireModuleEnabled(key){
  if (!moduleEnabled(key)) {
    const err = new Error(`${t("модуль выключен")}: ${moduleDisplayName(key)}`);
    err.status = 403;
    throw err;
  }
}
function applyModulesFromBundle(bundle){
  if (Array.isArray(bundle?.modules)) setModuleList(bundle.modules);
}
function sanitizeDayForModules(day = {}){
  // Preserve identity and sync metadata. Earlier versions stripped `date`, so every
  // server day was written to state.days[undefined] and the month appeared empty
  // after an authoritative reload even though the database contained the schedule.
  return {
    date: day.date ?? null,
    shiftTypeId: day.shiftTypeId ?? null,
    note: moduleEnabled("notes") ? (day.note ?? null) : null,
    dayEmoji: day.dayEmoji ?? null,
    overtimeHours: moduleEnabled("overtime") ? numOr0(day.overtimeHours) : 0,
    timeOffHours: moduleEnabled("overtime") ? numOr0(day.timeOffHours) : 0,
    overtimeBalanceHours: moduleEnabled("overtime") ? numOr0(day.overtimeBalanceHours) : 0,
    version: Number.isFinite(Number(day.version)) ? Number(day.version) : 0,
    updatedAt: day.updatedAt ?? null,
    shiftInterval: day.shiftInterval ?? null,
  };
}
function dayUpsertPayload(day = {}){
  const clean = sanitizeDayForModules(day);
  const payload = {
    shiftTypeId: clean.shiftTypeId ?? null,
    dayEmoji: clean.dayEmoji ?? null,
  };
  // Optional module fields are omitted completely when their module is disabled.
  // The server then preserves the hidden values instead of treating 0/null as a write.
  if (moduleEnabled("notes")) payload.note = clean.note ?? null;
  if (moduleEnabled("overtime")) {
    payload.overtimeHours = numOr0(clean.overtimeHours);
    payload.timeOffHours = numOr0(clean.timeOffHours);
  }
  return payload;
}
function sanitizeCalendarBundleForModules(bundle){
  if (!bundle || Array.isArray(bundle)) return bundle;
  applyModulesFromBundle(bundle);
  const clean = { ...bundle };
  clean.modules = Array.isArray(bundle.modules) ? bundle.modules : (state.modulesList || []);
  clean.days = (bundle.days || []).map(sanitizeDayForModules);
  clean.shiftOccurrences = Array.isArray(bundle.shiftOccurrences) ? bundle.shiftOccurrences : [];
  if (!moduleEnabled("tasks")) clean.tasks = [];
  if (!moduleEnabled("important_dates")) clean.importantDays = [];
  if (!moduleEnabled("overtime")) {
    clean.overtime = { from:bundle.from, to:bundle.to, overtimeHours:0, timeOffHours:0, balanceHours:0 };
    clean.overtimeAccount = { totalEarnedHours:0, totalUsedHours:0, balanceHours:0, credits:[], usages:[] };
  }
  if (!moduleEnabled("notifications")) {
    clean.notificationSettings = null;
    clean.reminders = [];
  }
  if (!moduleEnabled("scenarios") || !moduleEnabled("overtime")) clean.quickScenarios = [];
  return clean;
}
function offlineOperationRequiredModules(item){
  if (!item) return [];
  if (item.type === "toggleTask" || item.type === "captureInbox") return ["tasks"];
  if (item.type === "putDay") {
    const day = item.payload?.day || {};
    const modules = [];
    if ((day.note || "").trim()) modules.push("notes");
    if (Math.abs(numOr0(day.overtimeHours)) > 0.0001 || Math.abs(numOr0(day.timeOffHours)) > 0.0001) modules.push("overtime");
    return modules;
  }
  return [];
}
function offlineOperationDisabledReason(item){
  const disabled = offlineOperationRequiredModules(item).filter(key => !moduleEnabled(key));
  return disabled.length ? disabled.map(moduleDisplayName).join(", ") : "";
}
function offlineModulesSummary(){
  if (!state.modulesLoaded) return t("модули не загружены");
  const visible = (state.modulesList || []).filter(m => !m.locked && !m.hidden);
  const enabled = visible.filter(m => m.enabled).length;
  return `${enabled}/${visible.length} ${t("включено")}`;
}
const DAY_PANEL_SECTIONS = [
  { id:"accShift", module:"shifts", titleRu:"Смена", titleEn:"Shift" },
  { id:"accEmoji", module:"core", titleRu:"Маркер", titleEn:"Marker" },
  { id:"accSched", module:"shifts", titleRu:"График", titleEn:"Schedule" },
  { id:"accOt", module:"overtime", titleRu:"Переработка", titleEn:"Overtime" },
  { id:"accImp", module:"important_dates", titleRu:"Важные дни", titleEn:"Important days" },
  { id:"accTasks", module:"tasks", titleRu:"Задачи", titleEn:"Tasks" },
  { id:"accNote", module:"notes", titleRu:"Заметка", titleEn:"Note" },
];
function dayPanelSectionEnabled(section){
  return !section.module || section.module === "core" || moduleEnabled(section.module);
}
const DAY_MODULES_HINT_DISMISSED_KEY = "dutylog.dayModulesHint.dismissed.v1";
function isDayModulesHintDismissed(){
  try { return localStorage.getItem(DAY_MODULES_HINT_DISMISSED_KEY) === "1"; }
  catch (_) { return false; }
}
function dismissDayModulesHint(){
  try { localStorage.setItem(DAY_MODULES_HINT_DISMISSED_KEY, "1"); } catch (_) {}
  const hint = $("dayModulesHint");
  if (hint) hint.hidden = true;
}
function setDayPanelSectionVisibility(){
  const hidden = [];
  for (const section of DAY_PANEL_SECTIONS) {
    const el = $(section.id);
    if (!el) continue;
    const enabled = dayPanelSectionEnabled(section);
    el.classList.toggle("moduleHidden", !enabled);
    el.hidden = !enabled;
    if (!enabled) {
      el.open = false;
      hidden.push(state.language === "en" ? section.titleEn : section.titleRu);
    }
  }
  const hint = $("dayModulesHint");
  if (hint) {
    const optionalEnabled = DAY_PANEL_SECTIONS.some(s => !["core","shifts"].includes(s.module) && dayPanelSectionEnabled(s));
    hint.hidden = !state.selected || hidden.length === 0 || isDayModulesHintDismissed();
    if (!hint.hidden) {
      hint.innerHTML = `
        <div class="dayModulesHintText">
          <b>${esc(t("Скрытые блоки"))}:</b> ${esc(hidden.join(", "))}.
          ${esc(t("Отключённый модуль не удаляет данные — его можно включить обратно в настройках."))}
          ${!optionalEnabled ? `<span class="dayModulesHintExtra">${esc(t("Сейчас включены только базовые блоки дня."))}</span>` : ""}
        </div>
        <div class="dayModulesHintActions">
          <button type="button" id="dayModulesSettingsBtn">${esc(t("Настроить модули"))}</button>
          <button type="button" id="dayModulesHintCloseBtn" class="dayModulesHintClose" aria-label="${esc(t("Скрыть подсказку"))}" title="${esc(t("Скрыть подсказку"))}">×</button>
        </div>`;
      $("dayModulesSettingsBtn")?.addEventListener("click", () => openSettingsSection("modules", true));
      $("dayModulesHintCloseBtn")?.addEventListener("click", dismissDayModulesHint);
    }
  }
}
function renderSelectedDayModules(){
  setDayPanelSectionVisibility();
  if (!state.selected) return;
  renderChips();
  renderDayEmojiControls();
  renderScheduleControls();
  if (moduleEnabled("overtime")) renderOvertimeControls();
  if (moduleEnabled("important_dates")) renderImportantDays();
  if (moduleEnabled("tasks")) renderTasks();
  if (moduleEnabled("notes")) setTab(state.tab || "edit");
  updateAccSummaries();
}
function applyModuleVisibility(){
  const toggle = (el, enabled) => { if (el) el.classList.toggle("moduleHidden", !enabled); };
  toggle(document.querySelector('#tabbar a[data-view="overtime"]'), moduleEnabled("overtime"));
  toggle($("view-overtime"), moduleEnabled("overtime"));
  toggle(document.querySelector('#tabbar a[data-view="tasks"]'), moduleEnabled("tasks"));
  toggle($("view-tasks"), moduleEnabled("tasks"));
  toggle($("globalQuickAdd"), moduleEnabled("tasks") || moduleEnabled("notes") || moduleEnabled("important_dates") || moduleEnabled("overtime"));
  const hasDraftAction = moduleEnabled("tasks") || moduleEnabled("notes") || moduleEnabled("important_dates");
  toggle($("quickActionCapture"), hasDraftAction);
  toggle($("quickActionInbox"), moduleEnabled("tasks"));
  toggle($("quickActionDivider"), hasDraftAction);
  toggle($("quickActionTask"), moduleEnabled("tasks"));
  toggle($("quickActionNote"), moduleEnabled("notes"));
  toggle($("quickActionImportant"), moduleEnabled("important_dates"));
  toggle($("quickActionCredit"), moduleEnabled("overtime"));
  toggle($("quickActionUsage"), moduleEnabled("overtime"));
  toggle(document.querySelector('#tabbar a[data-view="important"]'), moduleEnabled("important_dates"));
  toggle($("view-important"), moduleEnabled("important_dates"));
  setDayPanelSectionVisibility();
  document.querySelectorAll('.scenarioFeature').forEach(el => toggle(el, moduleEnabled("scenarios") && moduleEnabled("overtime")));
  toggle($("notifyCard"), moduleEnabled("notifications"));
  toggle(document.querySelector('[data-settings-jump="notifications"]'), moduleEnabled("notifications"));
  toggle($("telegramProfileTitle"), moduleEnabled("telegram"));
  toggle($("telegramBox"), moduleEnabled("telegram"));
  if (location.hash === "#tasks" && !moduleEnabled("tasks")) location.hash = "#calendar";
  if (location.hash === "#overtime" && !moduleEnabled("overtime")) location.hash = "#calendar";
  if (location.hash === "#settings-notifications" && !moduleEnabled("notifications")) location.hash = "#settings-modules";
  if (location.hash === "#important" && !moduleEnabled("important_dates")) location.hash = "#calendar";
  renderModuleSettings();
}
async function loadModules(){
  try {
    const list = await api.modules();
    setModuleList(list);
    maybeShowOnboarding();
  } catch (err) {
    console.warn("modules unavailable", err);
    state.modulesLoaded = false;
  }
}
async function saveModuleEnabled(key, enabled){
  const msg = $("modulesMsg");
  if (msg) setProfileMsg("modulesMsg", t("сохраняю…"), true);
  try {
    const list = await api.updateModules({ [key]: !!enabled });
    setModuleList(list);
    await loadMonth();
    await refreshModuleAwareData();
    if (msg) { setProfileMsg("modulesMsg", t("модули сохранены"), true); setTimeout(() => setProfileMsg("modulesMsg", ""), 1800); }
  } catch (err) {
    console.error(err);
    if (msg) setProfileMsg("modulesMsg", err.message || t("ошибка"));
  }
}
async function refreshModuleAwareData(){
  if (moduleEnabled("important_dates")) await refreshImportantSettings(); else { state.importantDays = []; state.importantByDate = {}; }
  if (moduleEnabled("tasks")) {
    await Promise.all([loadTaskBoard(true), loadTaskMetadata(true), loadInbox(true)]);
  } else {
    state.tasksByDate = {}; state.taskBoard.items = []; state.taskMetadata = { categories:[], tags:[] }; state.inbox.items = [];
  }
  if (moduleEnabled("overtime")) await loadLedgerPage(true); else { state.ledgerPage.items = []; state.overtimeAccount = { totalEarnedHours:0,totalUsedHours:0,balanceHours:0,credits:[],usages:[] }; }
  if (moduleEnabled("notifications")) await showMonthNotifications(); else { state.reminders=[]; state.remindersByDate={}; state.notificationSettings=null; }
  if (moduleEnabled("scenarios")) { try { state.quickScenarios = await api.quickScenarios(); } catch (_) {} } else state.quickScenarios = [];
  if (moduleEnabled("telegram")) loadTelegramStatus(); else state.telegramStatus = null;
  renderCalendar();
  if (state.selected) renderSelectedDayModules();
}
function renderModuleSettings(){
  const grid = $("moduleSettingsGrid");
  if (!grid) return;
  const list = (state.modulesList || [])
    .filter(m => !["core","calendar","shifts","admin"].includes(m.key) || m.key === "admin")
    .sort((a,b) => (Number(a.order || 0) - Number(b.order || 0)) || moduleTitle(a).localeCompare(moduleTitle(b)));
  if (!list.length) { grid.innerHTML = `<div class="settingsHint">${esc(t("модули загружаются…"))}</div>`; return; }
  const enabledCount = list.filter(m => m.enabled).length;
  const disabledCount = list.filter(m => !m.enabled && !m.locked).length;
  const baseCount = list.filter(m => m.locked).length;
  const status = $("modulesStatus");
  if (status) {
    status.className = "status statusMetrics";
    status.innerHTML = `
      <span class="statusChip statusChipOk"><b>${enabledCount}</b> ${esc(t("Включены"))}</span>
      <span class="statusChip"><b>${disabledCount}</b> ${esc(t("Выключены"))}</span>
      <span class="statusChip"><b>${baseCount}</b> ${esc(t("Базовые"))}</span>`;
  }
  grid.innerHTML = "";
  for (const m of list) {
    const card = document.createElement("div");
    card.className = "moduleCard" + (m.enabled ? " on" : "") + (m.locked ? " locked" : "");
    const deps = (m.dependencies || []).filter(d => !["core","calendar","shifts"].includes(d)).map(moduleDisplayName);
    const showDeveloperDetails = !!state.profile?.admin;
    const details = [];
    if (showDeveloperDetails && Array.isArray(m.uiSlots) && m.uiSlots.length) details.push(`${t("слоты")}: ${m.uiSlots.join(", ")}`);
    if (showDeveloperDetails && Array.isArray(m.apiPrefixes) && m.apiPrefixes.length) details.push(`${t("API")}: ${m.apiPrefixes.join(", ")}`);
    if (showDeveloperDetails && Array.isArray(m.offlineQueueTypes) && m.offlineQueueTypes.length) details.push(`${t("offline")}: ${m.offlineQueueTypes.join(", ")}`);
    const meta = [moduleCategoryLabel(m.category)];
    if (showDeveloperDetails) meta.push(`${t("контракт")}: ${moduleContractCounts(m)}`);
    const badge = esc(m.locked ? t("всегда включён") : (m.enabled ? t("включено") : t("выключено")));
    card.innerHTML = `
      <input type="checkbox" ${m.enabled ? "checked" : ""} ${m.locked ? "disabled" : ""} data-module-toggle="${esc(m.key)}"/>
      <span class="moduleMain">
        <span class="moduleTop"><b>${esc(moduleTitle(m))}</b><span class="moduleBadge">${badge}</span></span>
        <span class="moduleDescription">${esc(moduleDescription(m))}</span>
        <span class="moduleMeta">${meta.map(item => `<span>${esc(item)}</span>`).join("")}</span>
        ${deps.length ? `<small>${esc(t("зависит от"))}: ${esc(deps.join(", "))}</small>` : ""}
        ${!m.enabled && !m.locked ? `<small class="moduleDisabledHint">${esc(t("Отключено. Данные сохранены."))} ${esc(t("Можно включить обратно."))}</small>` : ""}
        ${details.length ? `<details class="moduleDevDetails"><summary>${esc(t("Технические детали"))}</summary><span>${esc(details.join(" · "))}</span></details>` : ""}
      </span>`;
    grid.appendChild(card);
  }
  grid.querySelectorAll('[data-module-toggle]').forEach(input => input.addEventListener('change', e => saveModuleEnabled(e.target.dataset.moduleToggle, e.target.checked)));
  grid.querySelectorAll('.moduleCard').forEach(card => card.addEventListener('click', e => {
    if (e.target.closest('input, details, summary, button, a')) return;
    const input = card.querySelector('[data-module-toggle]');
    if (!input || input.disabled) return;
    input.checked = !input.checked;
    input.dispatchEvent(new Event('change', { bubbles:true }));
  }));
}

const ONBOARDING_OPTIONAL_MODULES = ["notes","tasks","overtime","important_dates","notifications","telegram","scenarios"];
const ONBOARDING_PRESETS = {
  basic: { notes:false, tasks:false, overtime:false, important_dates:false, notifications:false, telegram:false, scenarios:false },
  work: { notes:true, tasks:false, overtime:true, important_dates:true, notifications:false, telegram:false, scenarios:true },
  full: { notes:true, tasks:true, overtime:true, important_dates:true, notifications:true, telegram:true, scenarios:true },
};
function onboardingModules(){
  return (state.modulesList || [])
    .filter(m => ONBOARDING_OPTIONAL_MODULES.includes(m.key) && !m.hidden)
    .sort((a,b) => (Number(a.order || 0) - Number(b.order || 0)) || moduleTitle(a).localeCompare(moduleTitle(b)));
}
function applyOnboardingDependencies(draft){
  const out = { ...draft };
  let changed;
  do {
    changed = false;
    for (const m of onboardingModules()) {
      if (!out[m.key]) continue;
      for (const dep of (m.dependencies || [])) {
        if (ONBOARDING_OPTIONAL_MODULES.includes(dep) && out[dep] !== true) {
          out[dep] = true;
          changed = true;
        }
      }
    }
  } while (changed);
  return out;
}
function disableOnboardingDependents(draft, disabledKey){
  const out = { ...draft };
  const disabled = new Set([disabledKey]);
  let changed;
  do {
    changed = false;
    for (const m of onboardingModules()) {
      if (out[m.key] === false) continue;
      if ((m.dependencies || []).some(dep => disabled.has(dep))) {
        out[m.key] = false;
        disabled.add(m.key);
        changed = true;
      }
    }
  } while (changed);
  return out;
}
function setOnboardingDraft(next, changedKey = null, checked = true){
  let draft = { ...(next || {}) };
  if (changedKey && checked === false) draft = disableOnboardingDependents(draft, changedKey);
  state.onboardingDraft = applyOnboardingDependencies(draft);
  renderOnboardingModules();
}
function ensureOnboardingDraft(){
  if (state.onboardingDraft) return state.onboardingDraft;
  const draft = { ...ONBOARDING_PRESETS.work };
  for (const m of onboardingModules()) {
    if (!(m.key in draft)) draft[m.key] = !!m.enabled;
  }
  state.onboardingDraft = applyOnboardingDependencies(draft);
  return state.onboardingDraft;
}
function onboardingDraftMatchesPreset(draft, preset){
  const normalized = applyOnboardingDependencies({ ...preset });
  return ONBOARDING_OPTIONAL_MODULES.every(key => (draft[key] !== false) === (normalized[key] !== false));
}
function renderOnboardingPresetState(draft){
  document.querySelectorAll('[data-onboarding-preset]').forEach(btn => {
    const preset = ONBOARDING_PRESETS[btn.dataset.onboardingPreset];
    const active = !!preset && onboardingDraftMatchesPreset(draft, preset);
    btn.classList.toggle('primarySoft', active);
    btn.setAttribute('aria-pressed', active ? 'true' : 'false');
  });
}
function renderOnboardingModules(){
  const grid = $("onboardingModuleGrid");
  if (!grid) return;
  const draft = ensureOnboardingDraft();
  renderOnboardingPresetState(draft);
  const modules = onboardingModules();
  if (!modules.length) {
    grid.innerHTML = `<div class="settingsHint">${esc(t("модули загружаются…"))}</div>`;
    return;
  }
  grid.innerHTML = "";
  for (const m of modules) {
    const checked = draft[m.key] !== false;
    const deps = (m.dependencies || []).filter(d => ONBOARDING_OPTIONAL_MODULES.includes(d)).map(moduleDisplayName);
    const card = document.createElement("div");
    card.className = "onboardingModuleCard" + (checked ? " on" : "");
    card.innerHTML = `
      <input type="checkbox" data-onboarding-module="${esc(m.key)}" ${checked ? "checked" : ""}/>
      <span>
        <b>${esc(moduleTitle(m))}</b>
        <span>${esc(moduleDescription(m))}</span>
        ${deps.length ? `<small>${esc(t("зависит от"))}: ${esc(deps.join(", "))}</small>` : ""}
      </span>`;
    grid.appendChild(card);
  }
  grid.querySelectorAll('[data-onboarding-module]').forEach(input => input.addEventListener('change', e => {
    const key = e.target.dataset.onboardingModule;
    setOnboardingDraft({ ...ensureOnboardingDraft(), [key]: e.target.checked }, key, e.target.checked);
  }));
}
function maybeShowOnboarding(){
  const overlay = $("firstRunOnboarding");
  if (!overlay || !state.profile || !state.modulesLoaded) return;
  if (state.profile.onboardingCompleted === false && overlay.hidden) {
    state.onboardingDraft = null;
    renderOnboardingModules();
    overlay.hidden = false;
    document.body.style.overflow = "hidden";
  }
}
function hideOnboarding(){
  const overlay = $("firstRunOnboarding");
  if (overlay) overlay.hidden = true;
  document.body.style.overflow = "";
}
async function finishOnboarding({ skip = false } = {}){
  const msgId = "onboardingMsg";
  setProfileMsg(msgId, t("сохраняю…"), true);
  try {
    if (!skip) {
      const list = await api.updateModules(ensureOnboardingDraft());
      setModuleList(list);
      await loadMonth();
      await refreshModuleAwareData();
    }
    const p = await jfetch('/api/profile', { method:'PUT', body: currentProfilePayload({ onboardingCompleted:true }) });
    state.profile = p;
    hideOnboarding();
    applyModuleVisibility();
    renderCalendar();
    setProfileMsg(msgId, "");
  } catch (err) {
    setProfileMsg(msgId, err.message || t("ошибка"));
  }
}

document.querySelectorAll('[data-onboarding-preset]').forEach(btn => btn.addEventListener('click', () => setOnboardingDraft(ONBOARDING_PRESETS[btn.dataset.onboardingPreset] || ONBOARDING_PRESETS.work)));
$("onboardingStart")?.addEventListener("click", () => finishOnboarding({ skip:false }));
$("onboardingSkip")?.addEventListener("click", () => finishOnboarding({ skip:true }));

function normalizePageResponse(res, fallbackSize = 50) {
  if (Array.isArray(res)) {
    return { items: res, page:0, size:res.length || fallbackSize, total:res.length, totalPages:res.length ? 1 : 0, hasPrevious:false, hasNext:false };
  }
  const page = Number(res?.page || 0);
  const size = Number(res?.size || fallbackSize);
  const total = Number(res?.total || 0);
  return {
    items: Array.isArray(res?.items) ? res.items : [],
    page: Number.isFinite(page) ? page : 0,
    size: Number.isFinite(size) && size > 0 ? size : fallbackSize,
    total: Number.isFinite(total) ? total : 0,
    totalPages: Number(res?.totalPages || 0),
    hasPrevious: !!res?.hasPrevious,
    hasNext: !!res?.hasNext,
  };
}
function pageRangeText(p) {
  const total = Number(p?.total || 0);
  const count = Number((p?.items || []).length || 0);
  if (!total || !count) return state.language === "en" ? "0 of 0" : "0 из 0";
  const start = Number(p.page || 0) * Number(p.size || 50) + 1;
  const end = Math.min(total, start + count - 1);
  return state.language === "en" ? `${start}–${end} of ${total}` : `${start}–${end} из ${total}`;
}
function renderPager(id, pageInfo, onPage, onSize) {
  const box = $(id);
  if (!box) return;
  const p = pageInfo || { page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false, items:[] };
  // A pager for a single page wastes a large amount of mobile space and offers
  // no action. Keep it completely out of the accessibility tree until needed.
  if (Number(p.totalPages || 0) <= 1) {
    box.hidden = true;
    box.innerHTML = "";
    return;
  }
  box.hidden = false;
  box.innerHTML = `
    <button type="button" data-pager-prev ${p.hasPrevious ? "" : "disabled"}>${t("Назад")}</button>
    <span class="pagerText">${pageRangeText(p)} · ${t("стр.")} ${Number(p.totalPages || 0) ? Number(p.page || 0) + 1 : 0}/${Number(p.totalPages || 0)}</span>
    <button type="button" data-pager-next ${p.hasNext ? "" : "disabled"}>${t("Вперёд")}</button>
    <label>${t("на странице")}
      <select data-pager-size>
        <option value="25" ${Number(p.size) === 25 ? "selected" : ""}>25</option>
        <option value="50" ${Number(p.size) === 50 ? "selected" : ""}>50</option>
        <option value="100" ${Number(p.size) === 100 ? "selected" : ""}>100</option>
      </select>
    </label>`;
  box.querySelector("[data-pager-prev]")?.addEventListener("click", () => onPage(Math.max(0, Number(p.page || 0) - 1)));
  box.querySelector("[data-pager-next]")?.addEventListener("click", () => onPage(Number(p.page || 0) + 1));
  box.querySelector("[data-pager-size]")?.addEventListener("change", e => onSize(Number(e.target.value || 50)));
}

/* CSRF: Spring кладёт токен в cookie XSRF-TOKEN, мы возвращаем его заголовком */
function csrfToken(){
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

function offlineRequiredMessage(url){
  if (url.startsWith("/api/overtime")) return t("Переработки и отгулы можно изменять только при подключении к серверу. Смены, заметки и галочки задач сохраняются оффлайн.");
  if (url.startsWith("/api/days/fill")) return t("Автозаполнение графика требует связи с сервером. Отдельную смену выбранного дня можно изменить оффлайн.");
  if (url.startsWith("/api/shift-types")) return t("Типы смен и их расписание меняются только при подключении к серверу.");
  if (url.startsWith("/api/quick-scenarios")) return t("Шаблоны переработок меняются только при подключении к серверу.");
  if (url.startsWith("/api/notifications")) return t("Настройки уведомлений требуют связи с сервером.");
  if (url.startsWith("/api/telegram")) return t("Telegram-интеграция настраивается только при подключении к серверу.");
  if (url.startsWith("/api/profile")) return t("Профиль и сессии меняются только при подключении к серверу.");
  if (url.startsWith("/api/modules")) return t("Настройки модулей меняются только при подключении к серверу.");
  if (url.startsWith("/api/inbox")) return t("Новые мысли можно сохранить оффлайн. Разбор, редактирование и удаление входящих требуют связи с сервером.");
  if (url.startsWith("/api/important-days")) return t("Важные даты меняются только при подключении к серверу.");
  if (url.startsWith("/api/admin")) return t("Админские настройки меняются только при подключении к серверу.");
  return t("Эта операция требует связи с сервером. Смена дня, заметки и галочки задач сохраняются оффлайн.");
}

async function jfetch(url, opts = {}) {
  const method = opts.method || "GET";
  if (!navigator.onLine && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    const err = new Error(offlineRequiredMessage(url));
    err.status = 0;
    throw err;
  }
  const headers = opts.body ? { "Content-Type": "application/json" } : {};
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = csrfToken();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  const res = await fetch(url, {
    method,
    headers,
    body: opts.body ? JSON.stringify(opts.body) : undefined,
    cache: opts.cache,
  });
  if (res.status === 401) {
    // Сессия истекла или не залогинен — на страницу входа
    window.location.href = "/login.html";
    throw new Error(t("401: не авторизован"));
  }
  if (!res.ok) {
    let msg = `${opts.method || "GET"} ${url} → ${res.status}`;
    let code = null;
    let moduleKey = null;
    try {
      const body = await res.json();
      code = body?.code || null;
      if (body?.error) msg = body.error;
      moduleKey = body?.moduleKey || null;
      const moduleMarker = [body?.error, body?.message, body?.code]
        .map(value => String(value || ""))
        .find(value => value.startsWith("MODULE_DISABLED:"));
      if (!moduleKey && moduleMarker) moduleKey = moduleMarker.split(":")[1] || "";
      if (moduleKey) {
        const mod = (state.modulesList || []).find(m => m.key === moduleKey);
        msg = `${t("модуль выключен")}: ${mod ? moduleTitle(mod) : moduleKey}`;
      }
    } catch (_) { /* ответ был не JSON */ }
    const err = new Error(msg);
    err.status = res.status;
    err.url = url;
    err.method = method;
    err.code = code;
    err.moduleKey = moduleKey;
    throw err;
  }
  if (res.status === 204) return null;
  const responseText = await res.text();
  if (!responseText.trim()) return null;
  return JSON.parse(responseText);
}

function setSave(s, msg = "") {
  const el = $("saveState");
  el.classList.toggle("err", s === "err");
  el.textContent = s === "saving" ? t("сохраняю…") : s === "saved" ? "✓" : s === "err" ? (msg || t("ошибка сети")) : "";
  if (s === "saved") setTimeout(() => { if (el.textContent === "✓") el.textContent = ""; }, 1500);
}


/* ─── Offline Mode / local-first lite ─────────────────────────
 * Сервер остаётся главным источником истины. IndexedDB хранит последний
 * снимок календаря и очередь безопасных мутаций: день/заметка и done-задачи.
 */
const OFFLINE_DB_NAME = "dutylog-offline";
const OFFLINE_DB_VERSION = 1;
const OFFLINE_SNAPSHOT_KEY = "bootstrap";
const OFFLINE_META_FAILED_KEY = "failedMutations";
const OFFLINE_SYNC_LOCK_KEY = "dutylog.offline.syncLock.v1";
const OFFLINE_SYNC_LOCK_TTL_MS = 2 * 60 * 1000;
const OFFLINE_STALE_MS = 24 * 60 * 60 * 1000;
const OFFLINE_CLIENT_ID = (() => {
  try {
    const key = "dutylog.offline.clientId.v1";
    let id = sessionStorage.getItem(key);
    if (!id) { id = uuid(); sessionStorage.setItem(key, id); }
    return id;
  } catch (_) { return uuid(); }
})();

function isNetworkError(err){
  return !err || err.name === "TypeError" || err.message === "Failed to fetch" || err.message === "NetworkError" || err.status === 0;
}
function uuid(){
  if (crypto?.randomUUID) return crypto.randomUUID();
  return "op-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
}
function fmtSyncTime(iso){
  if (!iso) return t("нет синхронизации");
  return formatAbsoluteInstant(iso);
}
function syncAgeMs(iso){
  if (!iso) return null;
  const t = new Date(iso).getTime();
  return Number.isFinite(t) ? Date.now() - t : null;
}
function fmtSyncAge(iso){
  const age = syncAgeMs(iso);
  if (age == null || age < 0) return "";
  const min = Math.floor(age / 60000);
  if (min < 1) return t("только что");
  if (min < 60) return `${min} мин назад`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h} ч назад`;
  const d = Math.floor(h / 24);
  return `${d} дн назад`;
}
function escapeHtml(v){
  return String(v ?? "").replace(/[&<>"]/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;"}[ch] || ch));
}
function describeOfflineOperation(item){
  if (!item) return t("Операция");
  if (item.type === "putDay") {
    const d = item.payload?.day || {};
    const parts = [];
    if (d.shiftTypeId) parts.push(t("смена"));
    if ((d.note || "").trim()) parts.push(t("заметка"));
    if ((d.dayEmoji || "").trim()) parts.push("emoji " + d.dayEmoji);
    if (!parts.length) parts.push(t("день"));
    return `${item.payload?.date || t("дата")}: ${parts.join(" + ")}`;
  }
  if (item.type === "toggleTask") return `${t("Задача")} #${item.payload?.taskId}: ${item.payload?.done ? t("выполнена") : t("открыта")}`;
  if (item.type === "captureInbox") return `${t("Входящие")}: ${String(item.payload?.text || "").slice(0, 80)}`;
  return item.type || "Операция";
}
function acquireOfflineSyncLock(){
  try {
    const now = Date.now();
    const raw = localStorage.getItem(OFFLINE_SYNC_LOCK_KEY);
    const current = raw ? JSON.parse(raw) : null;
    if (current?.owner && current.owner !== OFFLINE_CLIENT_ID && Number(current.expiresAt || 0) > now) {
      return null;
    }
    const lock = { owner:OFFLINE_CLIENT_ID, token:uuid(), startedAt:new Date().toISOString(), expiresAt:now + OFFLINE_SYNC_LOCK_TTL_MS };
    localStorage.setItem(OFFLINE_SYNC_LOCK_KEY, JSON.stringify(lock));
    const saved = JSON.parse(localStorage.getItem(OFFLINE_SYNC_LOCK_KEY) || "{}");
    return saved.token === lock.token ? lock : null;
  } catch (_) { return { owner:OFFLINE_CLIENT_ID, token:"memory", expiresAt:Date.now() + OFFLINE_SYNC_LOCK_TTL_MS }; }
}
function refreshOfflineSyncLock(lock){
  if (!lock || lock.token === "memory") return;
  try {
    const raw = localStorage.getItem(OFFLINE_SYNC_LOCK_KEY);
    const current = raw ? JSON.parse(raw) : null;
    if (current?.token === lock.token) {
      current.expiresAt = Date.now() + OFFLINE_SYNC_LOCK_TTL_MS;
      localStorage.setItem(OFFLINE_SYNC_LOCK_KEY, JSON.stringify(current));
    }
  } catch (_) {}
}
function releaseOfflineSyncLock(lock){
  if (!lock || lock.token === "memory") return;
  try {
    const raw = localStorage.getItem(OFFLINE_SYNC_LOCK_KEY);
    const current = raw ? JSON.parse(raw) : null;
    if (current?.token === lock.token) localStorage.removeItem(OFFLINE_SYNC_LOCK_KEY);
  } catch (_) {}
}
function offlineSyncLockInfo(){
  try {
    const raw = localStorage.getItem(OFFLINE_SYNC_LOCK_KEY);
    if (!raw) return { active:false, label:t("нет"), raw:null };
    const lock = JSON.parse(raw);
    const expiresAt = Number(lock?.expiresAt || 0);
    const expired = expiresAt > 0 && expiresAt <= Date.now();
    const mine = lock?.owner === OFFLINE_CLIENT_ID;
    return {
      active:!expired,
      expired,
      mine,
      owner:lock?.owner || "—",
      startedAt:lock?.startedAt || null,
      expiresAt:expiresAt ? new Date(expiresAt).toISOString() : null,
      label: expired ? t("протух") : (mine ? t("активен в этой вкладке") : t("активен в другой вкладке")),
      raw:lock,
    };
  } catch (err) {
    return { active:false, label:t("не читается"), error:err.message || String(err) };
  }
}

function offlineDiagnosticsRows(queue, failed){
  const online = navigator.onLine && state.offline.online !== false;
  const lock = offlineSyncLockInfo();
  const rows = [
    ["Подключение", online ? "онлайн" : "оффлайн", online],
    ["IndexedDB", state.offline.cacheReady ? "доступна" : "недоступна", !!state.offline.cacheReady],
    ["Последняя синхронизация", state.offline.lastSyncAt ? `${fmtSyncTime(state.offline.lastSyncAt)} · ${fmtSyncAge(state.offline.lastSyncAt)}` : "ещё нет", !!state.offline.lastSyncAt],
    ["Возраст snapshot", state.offline.lastSyncAt ? fmtSyncAge(state.offline.lastSyncAt) : "нет локальной копии", state.offline.lastSyncAt ? !state.offline.stale : false],
    ["snapshot модулей", offlineModulesSummary(), state.modulesLoaded],
    ["Очередь", `${queue.length} ожидает отправки`, queue.length === 0],
    ["Неудачные операции", `${failed.length} не применилось`, failed.length === 0],
    ["Sync lock", lock.label, !lock.active || lock.mine],
  ];
  if (lock.startedAt) rows.push(["Lock запущен", fmtSyncTime(lock.startedAt), !lock.expired]);
  if (lock.expiresAt) rows.push(["Lock истекает", fmtSyncTime(lock.expiresAt), !lock.expired]);
  return rows;
}

function renderOfflineDiagnostics(queue, failed){
  const box = $("offlineDiagnosticsList");
  if (!box) return;
  const rows = offlineDiagnosticsRows(queue || [], failed || []);
  box.innerHTML = rows.map(([label, value, ok]) => {
    const cls = ok === true ? " ok" : ok === false ? " warn" : "";
    return `<div class="diagRow${cls}"><span>${escapeHtml(t(label))}</span><b>${escapeHtml(t(value))}</b></div>`;
  }).join("");
}

function offlineDiagnosticsReportText(){
  const q = state.offline.pending || 0;
  const f = state.offline.failed?.length || 0;
  const lock = offlineSyncLockInfo();
  return [
    `DutyLog UI: v${DUTYLOG_VERSION}`,
    `Client: web/PWA inside Spring Boot monolith`,
    `Native mobile app: not present`,
    `Online: ${navigator.onLine && state.offline.online !== false}`,
    `IndexedDB ready: ${!!state.offline.cacheReady}`,
    `Last sync: ${state.offline.lastSyncAt || "—"}`,
    `Snapshot age: ${state.offline.lastSyncAt ? fmtSyncAge(state.offline.lastSyncAt) : "—"}`,
    `Snapshot stale: ${!!state.offline.stale}`,
    `Modules loaded: ${!!state.modulesLoaded}`,
    `Modules enabled: ${JSON.stringify(state.modules || {})}`,
    `Queue pending: ${q}`,
    `Failed mutations: ${f}`,
    `Syncing: ${!!state.offline.syncing}`,
    `Sync locked by other tab: ${!!state.offline.syncLockedByOther}`,
    `Sync lock: ${lock.label}`,
    `Sync lock owner: ${lock.owner || "—"}`,
    `Browser: ${navigator.userAgent}`,
  ].join("\n");
}

function requestToPromise(req){
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
function txDone(tx){
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error("IndexedDB transaction aborted"));
  });
}

const offlineDb = {
  db: null,
  async open(){
    if (this.db) return this.db;
    if (!('indexedDB' in window)) throw new Error(t("Браузер не поддерживает локальное хранилище"));
    this.db = await new Promise((resolve, reject) => {
      const req = indexedDB.open(OFFLINE_DB_NAME, OFFLINE_DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains("snapshot")) db.createObjectStore("snapshot", { keyPath:"key" });
        if (!db.objectStoreNames.contains("queue")) db.createObjectStore("queue", { keyPath:"id" });
        if (!db.objectStoreNames.contains("meta")) db.createObjectStore("meta", { keyPath:"key" });
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    return this.db;
  },
  async get(store, key){
    const db = await this.open();
    return requestToPromise(db.transaction(store, "readonly").objectStore(store).get(key));
  },
  async put(store, value){
    const db = await this.open();
    const tx = db.transaction(store, "readwrite");
    tx.objectStore(store).put(value);
    await txDone(tx);
  },
  async delete(store, key){
    const db = await this.open();
    const tx = db.transaction(store, "readwrite");
    tx.objectStore(store).delete(key);
    await txDone(tx);
  },
  async all(store){
    const db = await this.open();
    return requestToPromise(db.transaction(store, "readonly").objectStore(store).getAll());
  },
};

const dataLayer = {
  async init(){
    try { await offlineDb.open(); state.offline.cacheReady = true; }
    catch (err) { console.warn("offline cache disabled", err); state.offline.cacheReady = false; }
    await this.refreshQueueState();
    updateOfflineStatus();
  },
  async readSnapshot(){
    if (!state.offline.cacheReady) return null;
    return (await offlineDb.get("snapshot", OFFLINE_SNAPSHOT_KEY)) || null;
  },
  async writeSnapshot(bundle, y = state.y, m = state.m){
    if (!state.offline.cacheReady || !bundle) return;
    const savedAt = new Date().toISOString();
    const cleanBundle = sanitizeCalendarBundleForModules(bundle);
    await offlineDb.put("snapshot", { key:OFFLINE_SNAPSHOT_KEY, y, m, savedAt, modules:cleanBundle?.modules || state.modulesList || [], bundle:cleanBundle });
    state.offline.lastSyncAt = savedAt;
    updateOfflineStatus();
  },
  async updateSnapshotDay(date, day){
    const snap = await this.readSnapshot();
    if (!snap?.bundle) return;
    const days = Array.isArray(snap.bundle.days) ? snap.bundle.days.slice() : [];
    const clean = normalizeDay(sanitizeDayForModules(day));
    const idx = days.findIndex(x => x.date === date);
    const empty = !clean.shiftTypeId && !(clean.note || "").trim() && !(clean.dayEmoji || "").trim() && Math.abs(clean.overtimeHours) < 0.0001 && Math.abs(clean.timeOffHours) < 0.0001;
    if (empty && idx >= 0) days.splice(idx, 1);
    else if (!empty && idx >= 0) days[idx] = { ...days[idx], date, ...clean };
    else if (!empty) days.push({ date, ...clean });
    snap.bundle.days = days;
    await offlineDb.put("snapshot", snap);
  },
  async updateSnapshotTaskDone(taskId, done){
    const snap = await this.readSnapshot();
    if (!snap?.bundle || !Array.isArray(snap.bundle.tasks)) return;
    snap.bundle.tasks = snap.bundle.tasks.map(t => Number(t.id) === Number(taskId) ? { ...t, done: !!done } : t);
    await offlineDb.put("snapshot", snap);
  },
  async updateSnapshotTask(task){
    if (!task?.id) return;
    const snap = await this.readSnapshot();
    if (!snap?.bundle || !Array.isArray(snap.bundle.tasks)) return;
    snap.bundle.tasks = snap.bundle.tasks.filter(item => Number(item.id) !== Number(task.id));
    const [year, month] = String(task.date || "").split("-").map(Number);
    if (year === Number(snap.y) && month - 1 === Number(snap.m)) snap.bundle.tasks.push(task);
    await offlineDb.put("snapshot", snap);
  },
  async enqueue(type, payload){
    if (!state.offline.cacheReady) throw new Error(t("Нет связи с сервером, а локальная очередь недоступна"));
    const disabledReason = offlineOperationDisabledReason({ type, payload });
    if (disabledReason) throw new Error(`${t("модуль выключен")}: ${disabledReason}`);
    await offlineDb.put("queue", { id:uuid(), type, payload, createdAt:new Date().toISOString(), attempts:0, lastError:null });
    await this.refreshQueueState();
    updateOfflineStatus();
  },
  async refreshQueueState(){
    if (!state.offline.cacheReady) { state.offline.pending = 0; return; }
    const q = await this.getQueueItems();
    const failed = await this.getFailedItems();
    state.offline.pending = q.length;
    state.offline.failed = failed;
  },
  async getQueueItems(){
    if (!state.offline.cacheReady) return [];
    return (await offlineDb.all("queue")).sort((a,b) => String(a.createdAt).localeCompare(String(b.createdAt)) || String(a.id).localeCompare(String(b.id)));
  },
  async getFailedItems(){
    if (!state.offline.cacheReady) return [];
    const failed = await offlineDb.get("meta", OFFLINE_META_FAILED_KEY);
    return failed?.items || [];
  },
  async setFailedItems(items){
    if (!state.offline.cacheReady) return;
    await offlineDb.put("meta", { key:OFFLINE_META_FAILED_KEY, items:items || [] });
    await this.refreshQueueState();
    updateOfflineStatus();
  },
  async removeFailed(index){
    const items = await this.getFailedItems();
    items.splice(index, 1);
    await this.setFailedItems(items);
  },
  async retryFailed(index){
    const items = await this.getFailedItems();
    const item = items.splice(index, 1)[0];
    if (!item) return;
    await offlineDb.put("queue", { ...item, id:uuid(), attempts:0, lastError:null, createdAt:new Date().toISOString() });
    await this.setFailedItems(items);
  },
  async retryAllFailed(){
    const items = await this.getFailedItems();
    if (!items.length) return;
    for (const item of items) {
      await offlineDb.put("queue", { ...item, id:uuid(), attempts:0, lastError:null, createdAt:new Date().toISOString() });
    }
    await this.setFailedItems([]);
  },
  async exportOfflineData(){
    if (!state.offline.cacheReady) throw new Error(t("Локальное хранилище недоступно"));
    const failed = await this.getFailedItems();
    const storageMeta = await offlineDb.all("meta");
    const data = {
      exportedAt:new Date().toISOString(),
      app:"DutyLog",
      version:DUTYLOG_VERSION,
      snapshot:await this.readSnapshot(),
      queue:await this.getQueueItems(),
      failed,
      meta:{
        lastSyncAt:state.offline.lastSyncAt,
        pending:state.offline.pending,
        failedCount:failed.length,
        online:navigator.onLine && state.offline.online !== false,
        stale:state.offline.stale,
        cacheReady:state.offline.cacheReady,
        storage:storageMeta,
        modules:state.modulesList || [],
        moduleMap:state.modules || {},
        browser:{
          userAgent:navigator.userAgent,
          language:navigator.language || null,
        },
      },
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type:"application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `dutylog-offline-${new Date().toISOString().replace(/[:.]/g,"-")}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  async loadCalendar(y, m, applyBundle, opts = {}){
    const fresh = !!opts.fresh;
    let hadCache = false;
    const snap = fresh ? null : await this.readSnapshot();
    // A snapshot belongs to one exact calendar month. Never paint July data into
    // August (or vice versa) while the network request is still in flight.
    const snapshotMatchesMonth = snap?.bundle && snap.y === y && snap.m === m;
    if (snapshotMatchesMonth) {
      hadCache = true;
      state.offline.lastSyncAt = snap.savedAt || null;
      if (Array.isArray(snap.modules) && !Array.isArray(snap.bundle.modules)) snap.bundle.modules = snap.modules;
      applyBundle(snap.bundle, true);
      updateOfflineStatus();
      // Быстрый локальный рендер, пока сеть отвечает.
      renderNotifications();
      renderCalendar();
    }
    if (!navigator.onLine) {
      state.offline.online = false;
      updateOfflineStatus();
      if (hadCache) return { fromCache:true };
      throw new Error(t("Нет связи и ещё нет локальной копии данных"));
    }
    try {
      const bundle = await api.month(y, m, { fresh });
      state.offline.online = true;
      await this.writeSnapshot(bundle, y, m);
      applyBundle(bundle, false);
      updateOfflineStatus();
      return { fromCache:false };
    } catch (err) {
      state.offline.online = false;
      updateOfflineStatus();
      if (hadCache && isNetworkError(err)) return { fromCache:true };
      throw err;
    }
  },
  async putDay(date, day){
    const cleanDay = sanitizeDayForModules(day);
    await this.updateSnapshotDay(date, cleanDay);
    if (navigator.onLine) {
      try {
        const savedDay = await api.upsertDay(date, dayUpsertPayload(cleanDay));
        // The backend returns an empty 200 response when the row was deleted. Mirror
        // that authoritative result into IndexedDB instead of keeping a ghost shift.
        await this.updateSnapshotDay(date, savedDay || {});
        state.offline.online = true;
        updateOfflineStatus();
        return { queued:false, day:savedDay || null };
      } catch (err) {
        if (!isNetworkError(err)) throw err;
      }
    }
    state.offline.online = false;
    await this.enqueue("putDay", { date, day: normalizeDay(cleanDay) });
    return { queued:true, day:normalizeDay(cleanDay) };
  },
  async setTaskDone(taskId, done){
    requireModuleEnabled("tasks");
    await this.updateSnapshotTaskDone(taskId, done);
    if (navigator.onLine) {
      try {
        const updated = await api.updateTask(taskId, { done: !!done });
        state.offline.online = true;
        updateOfflineStatus();
        return { queued:false, task:updated };
      } catch (err) {
        if (!isNetworkError(err)) throw err;
      }
    }
    state.offline.online = false;
    await this.enqueue("toggleTask", { taskId, done: !!done });
    return { queued:true };
  },
  async captureInbox(text){
    requireModuleEnabled("tasks");
    const clean = String(text || "").trim();
    if (!clean) throw new Error(t("Текст записи не должен быть пустым"));
    if (clean.length > 2000) throw new Error(t("Текст записи: максимум 2000 символов"));
    const clientOperationId = uuid();
    if (navigator.onLine) {
      try {
        const item = await api.createInbox({ text:clean, clientOperationId });
        state.offline.online = true;
        updateOfflineStatus();
        return { queued:false, item };
      } catch (err) {
        if (!isNetworkError(err)) throw err;
      }
    }
    state.offline.online = false;
    const payload = { text:clean, clientOperationId };
    await this.enqueue("captureInbox", payload);
    return { queued:true, item:{ id:`local-${clientOperationId}`, text:clean, status:"OPEN", createdAt:new Date().toISOString(), localOnly:true, clientOperationId } };
  },
  async discardQueuedInbox(clientOperationId){
    if (!state.offline.cacheReady || !clientOperationId) return false;
    const items = await this.getQueueItems();
    const target = items.find(item => item.type === "captureInbox" && item.payload?.clientOperationId === clientOperationId);
    if (!target) return false;
    await offlineDb.delete("queue", target.id);
    await this.refreshQueueState();
    updateOfflineStatus();
    return true;
  },
  async syncQueue(){
    if (!state.offline.cacheReady || state.offline.syncing) return;
    if (!navigator.onLine) { state.offline.online = false; updateOfflineStatus(); return; }
    const lock = acquireOfflineSyncLock();
    if (!lock) {
      state.offline.syncLockedByOther = true;
      updateOfflineStatus();
      setSave("err", t("Синхронизация уже запущена в другой вкладке"));
      return;
    }
    state.offline.syncLockedByOther = false;
    state.offline.syncing = true;
    updateOfflineStatus();
    try {
      const items = await this.getQueueItems();
      const failed = [];
      for (const item of items) {
        refreshOfflineSyncLock(lock);
        try {
          const disabledReason = offlineOperationDisabledReason(item);
          if (disabledReason) {
            throw Object.assign(new Error(`${t("операция относится к выключенному модулю")}: ${disabledReason}`), { status:403 });
          }
          if (item.type === "putDay") {
            await api.upsertDay(item.payload.date, dayUpsertPayload(item.payload.day));
          } else if (item.type === "toggleTask") {
            await api.updateTask(item.payload.taskId, { done: !!item.payload.done });
          } else if (item.type === "captureInbox") {
            await api.createInbox({
              text:item.payload.text,
              clientOperationId:item.payload.clientOperationId || item.id
            });
          } else {
            throw Object.assign(new Error("Неизвестный тип операции: " + item.type), { status:400 });
          }
          await offlineDb.delete("queue", item.id);
        } catch (err) {
          if (err.status === 401) throw err;
          if (err.status === 400 || err.status === 403 || err.status === 404 || err.status === 409) {
            failed.push({ ...item, failedAt:new Date().toISOString(), lastError:err.message || "операция не применена" });
            await offlineDb.delete("queue", item.id);
            continue;
          }
          item.attempts = Number(item.attempts || 0) + 1;
          item.lastError = err.message || "ошибка сети";
          await offlineDb.put("queue", item);
          break;
        }
      }
      if (failed.length) {
        const prev = await offlineDb.get("meta", OFFLINE_META_FAILED_KEY);
        await offlineDb.put("meta", { key:OFFLINE_META_FAILED_KEY, items:[...(prev?.items || []), ...failed].slice(-30) });
      }
      await this.refreshQueueState();
      if (state.offline.pending === 0) {
        const bundle = await api.month(state.y, state.m);
        await this.writeSnapshot(bundle, state.y, state.m);
        applyCalendarBundle(bundle);
        renderNotifications();
        renderCalendar();
        if (state.selected) { renderChips(); renderTasks(); renderImportantDays(); }
        if (moduleEnabled("tasks")) {
          await loadTaskBoard(true);
          if (typeof loadInbox === "function") await loadInbox(true);
        }
      }
      state.offline.online = true;
    } catch (err) {
      console.error(err);
      if (err.status !== 401) setSave("err", err.message || t("синхронизация не удалась"));
      state.offline.online = navigator.onLine;
    } finally {
      releaseOfflineSyncLock(lock);
      state.offline.syncing = false;
      await this.refreshQueueState();
      updateOfflineStatus();
      if (document.body.classList.contains("syncDialogOpen")) renderOfflineSyncDialog();
    }
  },};

function updateOfflineStatus(){
  const online = navigator.onLine && state.offline.online !== false;
  const stale = !!state.offline.lastSyncAt && (syncAgeMs(state.offline.lastSyncAt) || 0) > OFFLINE_STALE_MS;
  state.offline.stale = stale;
  document.body.classList.toggle("offline", !online);
  document.body.classList.toggle("offline-stale", stale);
  document.body.classList.toggle("has-pending-sync", state.offline.pending > 0);
  const el = $("offlineStatus");
  if (!el) return;
  const parts = [];
  const compactStatus = window.matchMedia?.("(max-width:700px)")?.matches === true;
  if (state.offline.syncing) parts.push(compactStatus ? "синхр…" : "синхронизация…");
  else if (state.offline.syncLockedByOther) parts.push(compactStatus ? "синхр. в другой вкладке" : "синхронизация в другой вкладке");
  else parts.push(online ? "онлайн" : "оффлайн");
  if (state.offline.pending) parts.push(`${state.offline.pending} не отправлено`);
  if (state.offline.failed?.length) parts.push(`${state.offline.failed.length} не применилось`);
  if (state.offline.lastSyncAt) {
    if (stale) parts.push("данные устарели");
    else if (!online) parts.push("данные от " + fmtSyncTime(state.offline.lastSyncAt));
  }
  el.textContent = parts.join(" · ");
  el.className = "offlineStatus" + (!online ? " off" : "") + (state.offline.pending ? " pending" : "") + (state.offline.failed?.length ? " failed" : "") + (stale ? " stale" : "");
  const age = state.offline.lastSyncAt ? `Последняя синхронизация: ${fmtSyncTime(state.offline.lastSyncAt)} (${fmtSyncAge(state.offline.lastSyncAt)})` : "Локальной копии пока нет";
  el.title = state.offline.failed?.length
    ? "Есть операции, которые сервер не принял. Нажмите, чтобы открыть синхронизацию. " + age
    : (state.offline.pending ? "Есть изменения, ожидающие отправки. Нажмите, чтобы открыть синхронизацию. " + age : "Состояние подключения. " + age);
}

function setOfflineSyncFeedback(message = "", tone = ""){
  const box = $("offlineSyncFeedback");
  if (!box) return;
  box.textContent = message;
  box.className = "offlineSyncFeedback" + (tone ? ` ${tone}` : "");
  box.hidden = !message;
}
function setOfflineSyncButtonBusy(busy){
  const button = $("offlineSyncNow");
  if (!button) return;
  button.disabled = !!busy;
  button.setAttribute("aria-busy", busy ? "true" : "false");
  button.textContent = t(busy ? "Синхронизация…" : "Синхронизировать");
}

async function renderOfflineSyncDialog(){
  const pendingList = $("offlinePendingList");
  const failedList = $("offlineFailedList");
  const meta = $("offlineSyncMeta");
  if (!pendingList || !failedList || !meta) return;
  const queue = await dataLayer.getQueueItems();
  const failed = await dataLayer.getFailedItems();
  const online = navigator.onLine && state.offline.online !== false;
  setOfflineSyncButtonBusy(state.offline.syncing);
  const syncAge = state.offline.lastSyncAt ? `${fmtSyncTime(state.offline.lastSyncAt)} · ${fmtSyncAge(state.offline.lastSyncAt)}` : "локальной копии пока нет";
  meta.innerHTML = `
    <div><b>${online ? "Онлайн" : "Оффлайн"}</b></div>
    <div>Последняя синхронизация: ${escapeHtml(syncAge)}</div>
    ${state.offline.stale ? '<div class="syncWarn">Локальные данные старше суток. Проверьте их после подключения к серверу.</div>' : ''}
  `;
  pendingList.innerHTML = queue.length ? queue.map(item => `
    <div class="syncItem">
      <div><b>${escapeHtml(describeOfflineOperation(item))}</b><span>${escapeHtml(fmtSyncTime(item.createdAt))}${item.attempts ? ` · попыток: ${item.attempts}` : ""}</span></div>
      ${item.lastError ? `<small>${escapeHtml(item.lastError)}</small>` : ""}
    </div>`).join("") : '<span class="emptyLine">Нет изменений, ожидающих отправки.</span>';
  failedList.innerHTML = failed.length ? failed.map((item, idx) => `
    <div class="syncItem failed">
      <div><b>${escapeHtml(describeOfflineOperation(item))}</b><span>${escapeHtml(fmtSyncTime(item.failedAt || item.createdAt))}</span></div>
      <small>${escapeHtml(item.lastError || "сервер не применил операцию")}</small>
      <div class="syncItemActions">
        <button type="button" data-failed-retry="${idx}">Повторить операцию</button>
        <button type="button" data-failed-remove="${idx}">Убрать из списка</button>
      </div>
    </div>`).join("") : '<span class="emptyLine">Неудачных операций синхронизации нет.</span>';
  renderOfflineDiagnostics(queue, failed);
}

async function openOfflineSyncDialog(){
  const dlg = $("offlineSyncDialog");
  if (!dlg) return;
  await dataLayer.refreshQueueState();
  setOfflineSyncFeedback();
  await renderOfflineSyncDialog();
  dlg.hidden = false;
  document.body.classList.add("syncDialogOpen");
}
function closeOfflineSyncDialog(){
  const dlg = $("offlineSyncDialog");
  if (dlg) dlg.hidden = true;
  document.body.classList.remove("syncDialogOpen");
}

window.addEventListener("online", () => { state.offline.online = true; updateOfflineStatus(); dataLayer.syncQueue(); });
window.addEventListener("offline", () => { state.offline.online = false; updateOfflineStatus(); });
window.addEventListener("storage", e => {
  if (e.key === OFFLINE_SYNC_LOCK_KEY) updateOfflineStatus();
});
document.addEventListener("keydown", e => {
  if (e.key === "Escape" && document.body.classList.contains("syncDialogOpen")) closeOfflineSyncDialog();
});
document.addEventListener("click", async e => {
  if (e.target?.id === "offlineStatus") { await openOfflineSyncDialog(); return; }
  if (e.target?.id === "offlineSyncClose" || e.target?.id === "offlineSyncBackdrop") { closeOfflineSyncDialog(); return; }
  if (e.target?.id === "offlineSyncNow") {
    const beforePending = Number(state.offline.pending || 0);
    const beforeFailed = Number(state.offline.failed?.length || 0);
    if (state.offline.syncing) {
      setOfflineSyncFeedback(t("Синхронизация уже выполняется"), "warn");
      return;
    }
    if (!navigator.onLine) {
      state.offline.online = false;
      updateOfflineStatus();
      setOfflineSyncFeedback(t("Нет подключения к сети"), "err");
      return;
    }
    setOfflineSyncButtonBusy(true);
    setOfflineSyncFeedback(t("Синхронизация…"), "busy");
    await dataLayer.syncQueue();
    await renderOfflineSyncDialog();
    setOfflineSyncButtonBusy(false);
    if (state.offline.syncLockedByOther) {
      setOfflineSyncFeedback(t("Синхронизация выполняется в другой вкладке"), "warn");
    } else if (Number(state.offline.pending || 0) > 0 || Number(state.offline.failed?.length || 0) > beforeFailed) {
      setOfflineSyncFeedback(t("Не все изменения отправлены"), "err");
    } else if (beforePending > 0) {
      setOfflineSyncFeedback(t("Синхронизация завершена"), "ok");
    } else {
      setOfflineSyncFeedback(t("Нет изменений"), "ok");
    }
    return;
  }
  if (e.target?.id === "offlineFailedRetryAll") { await dataLayer.retryAllFailed(); await renderOfflineSyncDialog(); return; }
  if (e.target?.id === "offlineExport") { await dataLayer.exportOfflineData(); return; }
  if (e.target?.id === "offlineDiagnosticsCopy") {
    try { await navigator.clipboard.writeText(offlineDiagnosticsReportText()); setSave("saved", t("диагностика оффлайна скопирована")); }
    catch (err) { setSave("err", t("не удалось скопировать диагностику")); }
    return;
  }
  if (e.target?.id === "offlineFailedClear") { await dataLayer.setFailedItems([]); await renderOfflineSyncDialog(); return; }
  const retry = e.target?.dataset?.failedRetry;
  if (retry != null) { await dataLayer.retryFailed(Number(retry)); await renderOfflineSyncDialog(); return; }
  const remove = e.target?.dataset?.failedRemove;
  if (remove != null) { await dataLayer.removeFailed(Number(remove)); await renderOfflineSyncDialog(); return; }
});
