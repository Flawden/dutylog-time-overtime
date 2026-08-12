/*
 * 70-user-boot.js — Boot: loading, routing, notes fullscreen, profile and Telegram UI
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 35-today → 37-calendar-experience → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

/* ─── Загрузка данных ───────────────────────────────────────── */
function applyCalendarBundle(bundle){
  applyModulesFromBundle(bundle);
  bundle = sanitizeCalendarBundleForModules(bundle);
  if (Array.isArray(bundle)) {
    // На всякий случай оставлен fallback под старый endpoint.
    state.days = {};
    state.shiftOccurrences = [];
    state.shiftSegmentsByDate = {};
    state.tasksByDate = {};
    state.importantByDate = {};
    state.absenceOccurrences = [];
    state.absencesByDate = {};
    state.remindersByDate = {};
    for (const e of bundle) state.days[e.date] = normalizeDay(e);
    return;
  }
  state.days = {};
  state.shiftOccurrences = [];
  state.shiftSegmentsByDate = {};
  state.tasksByDate = {};
  state.importantByDate = {};
  state.absenceOccurrences = [];
  state.absencesByDate = {};
  state.remindersByDate = {};
  state.calendarLayerEntriesByDate = {};
  if (bundle.shiftTypes) {
    state.shiftTypes = bundle.shiftTypes;
    if (typeof syncTimeSettingsFromBuiltins === "function") syncTimeSettingsFromBuiltins();
  }
  for (const e of bundle.days || []) state.days[e.date] = normalizeDay(e);
  state.shiftOccurrences = Array.isArray(bundle.shiftOccurrences) ? bundle.shiftOccurrences.map(normalizeShiftOccurrence) : [];
  if (!state.shiftOccurrences.length) {
    state.shiftOccurrences = (bundle.days || []).map(occurrenceFromLegacyDay).filter(Boolean);
  }
  rebuildShiftOccurrenceIndex();
  if (moduleEnabled("tasks")) for (const t of bundle.tasks || []) addTaskToDateMap(state.tasksByDate, t);
  if (moduleEnabled("important_dates")) for (const i of bundle.importantDays || []) addToDateMap(state.importantByDate, i);
  state.absenceOccurrences = moduleEnabled("vacation") && Array.isArray(bundle.absences) ? bundle.absences : [];
  if (moduleEnabled("vacation")) for (const absence of state.absenceOccurrences) addToDateMap(state.absencesByDate, absence);
  state.notificationSettings = moduleEnabled("notifications") ? (bundle.notificationSettings || state.notificationSettings) : null;
  state.quickScenarios = moduleEnabled("scenarios") ? (bundle.quickScenarios || state.quickScenarios || []) : [];
  state.calendarLayers = Array.isArray(bundle.calendarLayers) ? bundle.calendarLayers : (state.calendarLayers || []);
  for (const layer of state.calendarLayers) {
    for (const entry of layer.entries || []) addToDateMap(state.calendarLayerEntriesByDate, { ...entry, layer });
  }
  state.reminders = moduleEnabled("notifications") ? (bundle.reminders || []) : [];
  for (const r of state.reminders) addToDateMap(state.remindersByDate, { ...r, date:r.sourceDate });
  if (moduleEnabled("overtime") && bundle.overtimeAccount) state.overtimeAccount = bundle.overtimeAccount;
}

let dutyLogPageLifecycleEnding = false;
window.addEventListener("pagehide", () => { dutyLogPageLifecycleEnding = true; });
window.addEventListener("pageshow", () => { dutyLogPageLifecycleEnding = false; });
function expectedPageLifecycleFetchAbort(error){
  if (!dutyLogPageLifecycleEnding) return false;
  const name = String(error?.name || "");
  const message = String(error?.message || error || "");
  return name === "AbortError" || /Failed to fetch/i.test(message);
}

let calendarLoadGeneration = 0;
function recordCalendarLoadMetric({ year, month, startedAt, result = null, error = null }){
  const finishedAt = performance.now();
  const metric = {
    year,
    month:month + 1,
    durationMs:Math.max(0, Math.round(finishedAt - startedAt)),
    fromCache:!!result?.fromCache,
    ok:!error,
    recordedAt:new Date().toISOString(),
  };
  const metrics = Array.isArray(state.ui.calendarLoadMetrics) ? state.ui.calendarLoadMetrics : [];
  state.ui.calendarLoadMetrics = [...metrics.slice(-19), metric];
  try {
    performance.measure(`dutylog.calendar.${year}-${String(month + 1).padStart(2, "0")}`, {
      start:startedAt,
      end:finishedAt,
      detail:metric,
    });
  } catch (_) { /* older browsers still keep the in-memory metric */ }
  document.dispatchEvent(new CustomEvent("dutylog:calendar-load", { detail:metric }));
  if (metric.durationMs >= 1200) console.info("[DutyLog] slow calendar load", metric);
}

async function loadMonth(opts = {}){
  const generation = ++calendarLoadGeneration;
  const requestedYear = state.y;
  const requestedMonth = state.m;
  const startedAt = performance.now();
  let loadResult = null;
  let loadError = null;
  state.ui.loadingCalendar = true;
  renderCalendar();
  try {
    const res = await dataLayer.loadCalendar(requestedYear, requestedMonth, bundle => {
      // Ignore a late response from a month that is no longer active.
      if (generation !== calendarLoadGeneration || state.y !== requestedYear || state.m !== requestedMonth) return;
      applyCalendarBundle(bundle);
      // The data layer may first deliver a matching IndexedDB snapshot and then the
      // authoritative server response. Every accepted bundle must be rendered;
      // otherwise the server state is applied only in memory and the screen keeps
      // showing the stale pre-fill snapshot until another unrelated render occurs.
      state.ui.loadingCalendar = false;
      renderNotifications();
      renderCalendar();
    }, { fresh:!!opts.fresh });
    loadResult = res;
    if (generation !== calendarLoadGeneration) return;
    setSave(res?.fromCache ? "" : "");
  } catch (err) {
    if (expectedPageLifecycleFetchAbort(err)) return;
    loadError = err;
    console.error(err);
    setSave("err", err.message);
  } finally {
    if (!dutyLogPageLifecycleEnding && generation === calendarLoadGeneration) {
      const wasStillLoading = state.ui.loadingCalendar;
      state.ui.loadingCalendar = false;
      // Network errors without a usable snapshot must also remove the skeleton.
      if (wasStillLoading) renderCalendar();
      recordCalendarLoadMetric({ year:requestedYear, month:requestedMonth, startedAt, result:loadResult, error:loadError });
    }
  }
}

/* ─── Пользователь ──────────────────────────────────────────── */
function initMobileFilterToggles(){
  document.querySelectorAll(".mobileFilterToggle[aria-controls]").forEach(button => {
    const target = document.getElementById(button.getAttribute("aria-controls"));
    if (!target || button.dataset.bound === "true") return;
    button.dataset.bound = "true";
    button.addEventListener("click", () => {
      const expanded = button.getAttribute("aria-expanded") === "true";
      button.setAttribute("aria-expanded", String(!expanded));
    });
  });
}

initMobileFilterToggles();

$("logout").addEventListener("click", async () => {
  try { await flushPendingSave(); } catch (e) { /* не блокируем выход */ }
  try { await fetch("/logout", { method: "POST", headers: csrfToken() ? { "X-XSRF-TOKEN": csrfToken() } : {} }); } catch (e) { /* пофиг, всё равно уходим */ }
  window.location.href = "/login.html";
});

let dutyLogServiceWorkerRegistrationPromise = null;
async function registerDutyLogServiceWorker(){
  if (!("serviceWorker" in navigator)) return null;
  if (dutyLogServiceWorkerRegistrationPromise) return dutyLogServiceWorkerRegistrationPromise;
  dutyLogServiceWorkerRegistrationPromise = (async () => {
    let reloading = false;
    let hasController = Boolean(navigator.serviceWorker.controller);
    navigator.serviceWorker.addEventListener("controllerchange", () => {
      if (!hasController) { hasController = true; return; }
      if (reloading) return;
      const reloadKey = `dutylog-sw-reload-${DUTYLOG_VERSION}`;
      if (sessionStorage.getItem(reloadKey) === "1") return;
      sessionStorage.setItem(reloadKey, "1");
      reloading = true;
      window.location.reload();
    }, { once:false });
    try {
      const existingRegistration = await navigator.serviceWorker.getRegistration("/");
      const registration = await navigator.serviceWorker.register("/service-worker.js", { updateViaCache: "none" });
      registration.waiting?.postMessage({ type:"SKIP_WAITING" });
      registration.addEventListener("updatefound", () => {
        const worker = registration.installing;
        worker?.addEventListener("statechange", () => {
          if (worker.state === "installed" && navigator.serviceWorker.controller) {
            worker.postMessage({ type:"SKIP_WAITING" });
          }
        });
      });
      // register() already starts the install lifecycle for a first registration.
      // Force an update check only when a registration existed before this call;
      // doing both on first onboarding can create a duplicate install/claim race.
      if (existingRegistration) await registration.update();
      return registration;
    } catch (_) {
      dutyLogServiceWorkerRegistrationPromise = null;
      return null; /* offline startup keeps the currently controlled shell */
    }
  })();
  return dutyLogServiceWorkerRegistrationPromise;
}
window.DutyLogPwaRuntime = Object.freeze({ register:registerDutyLogServiceWorker });

let bootFailsafeTimer = null;
function armBootFailsafe(){
  clearTimeout(bootFailsafeTimer);
  bootFailsafeTimer = setTimeout(() => {
    if (state.ui?.booting) hideBootOnStartupError("загрузка заняла слишком много времени — интерфейс разблокирован");
  }, 15000);
}
function clearBootFailsafe(){
  clearTimeout(bootFailsafeTimer);
  bootFailsafeTimer = null;
}
window.addEventListener("error", () => {
  if (state.ui?.booting) hideBootOnStartupError("ошибка загрузки");
});
window.addEventListener("unhandledrejection", () => {
  if (state.ui?.booting) hideBootOnStartupError("ошибка загрузки");
});

async function init(){
  armBootFailsafe();
  setAppBooting(true, "Подготавливаем интерфейс…");
  state.timeSettings = loadTimeSettings();
  renderSwatches();
  initTimeSettingsEvents();
  initDiagnosticsEvents();
  initSettingsAccordion();
  await dataLayer.init();
  try {
    const me = await jfetch("/api/auth/me");
    $("whoami").textContent = me.username;
    setAppBooting(true, "Загружаю модули…");
    await loadModules();
    setAppBooting(true, "Загружаю календарь…");
    state.shiftTypes = await api.shiftTypes();
    state.quickScenarios = moduleEnabled("scenarios") ? await api.quickScenarios() : [];
    state.scheduleTemplates = await api.scheduleTemplates();
    state.calendarLayers = await api.calendarLayers();
    if (moduleEnabled("important_dates")) await refreshImportantSettings();
    // The calendar projection depends on the persisted work/display zones. Load the
    // authoritative profile before the first month request instead of racing both.
    await loadProfile();
    if (moduleEnabled("calendar_sync") && typeof loadCalendarSyncStatus === "function") await loadCalendarSyncStatus(true);
    // The first visible month follows DutyLog's persisted work timezone rather
    // than the browser clock. This matters near month boundaries and in UTC±14.
    const [profileTodayYear, profileTodayMonth] = todayKey().split("-").map(Number);
    if (profileTodayYear && profileTodayMonth) {
      state.y = profileTodayYear;
      state.m = profileTodayMonth - 1;
    }
  } catch (err) {
    console.error(err);
    if (err.status === 401) return; // при 401 нас уже уносит на login.html
    state.offline.online = false;
    setSave("err", t("нет связи — открыта локальная копия"));
  }
  await loadMonth();
  if (moduleEnabled("overtime")) await loadLedgerPage(true);
  if (moduleEnabled("tasks")) await Promise.all([loadTaskBoard(true), loadTaskMetadata(true), loadInbox(true)]);
  applyModuleVisibility();
  if (typeof calendarExperienceRestoreFocus === "function") await calendarExperienceRestoreFocus();
  renderCalendar();
  clearBootFailsafe();
  setAppBooting(false);
  startBrowserNotificationScheduler();
  dataLayer.syncQueue();
  // Existing users may register/update the worker after authenticated boot.
  // First-run users wait until finishOnboarding() has persisted their profile,
  // so an initial claim can never race the onboarding controls.
  if (state.profile?.onboardingCompleted === true) void registerDutyLogServiceWorker();
}
init().catch(err => {
  console.error(err);
  clearBootFailsafe();
  hideBootOnStartupError(err.message || "ошибка загрузки");
});

/* ─── Вкладки: hash-роутинг ─────────────────────────────────── */
const VIEWS = window.DutyLogUI?.views?.() || { today:"view-today", calendar:"view-calendar", vacation:"view-vacation", overtime:"view-overtime", payroll:"view-payroll", tasks:"view-tasks", important:"view-important", settings:"view-settings", admin:"view-admin" };
window.__dutylogLedgerRouteReady = Promise.resolve();
function applyRoute(){
  const defaultRoute = "#today";
  const rawRoute = (location.hash || defaultRoute).slice(1);
  const name = rawRoute.startsWith("settings-") ? "settings" : rawRoute;
  let active = VIEWS[name] ? name : "today";
  if (active === "admin" && state.profile && !state.profile.admin) active = "calendar";
  if (active === "tasks" && !moduleEnabled("tasks")) active = "calendar";
  if (active === "overtime" && !moduleEnabled("overtime")) active = "calendar";
  if (active === "payroll" && !moduleEnabled("payroll")) active = "calendar";
  if (active === "important" && !moduleEnabled("important_dates")) active = "calendar";
  if (active === "vacation" && !moduleEnabled("vacation")) active = "calendar";

  if (document.documentElement.dataset.vueShell === "ready") {
    // Vue owns route state, route guards, migrated route rendering and the
    // selected-day close-on-route-exit behavior. Legacy only owns the two
    // remaining legacy route-entry side effects until Payroll/Admin migrate.
    const payrollView = document.getElementById(VIEWS.payroll);
    const adminView = document.getElementById(VIEWS.admin);
    if (payrollView) payrollView.hidden = active !== "payroll";
    if (adminView) adminView.hidden = active !== "admin";
    if (active === "payroll" && typeof openPayrollView === "function") {
      window.__dutylogPayrollReady = Promise.resolve(openPayrollView(true));
    }
    if (active === "admin") {
      if (typeof initAdminNavigation === "function") initAdminNavigation();
      renderDiagnosticsClient();
      if (state.profile?.admin) refreshAdminPanel();
    }
    return;
  }

  // Pre-Vue recovery keeps the historical router intact.
  document.body.dataset.view = active;
  if (active !== "calendar") selectDay(null);
  for (const [key, id] of Object.entries(VIEWS)) {
    const el = document.getElementById(id);
    if (el) el.hidden = key !== active;
  }
  document.querySelectorAll("#tabbar a").forEach(a => {
    const selected = a.dataset.view === active;
    a.classList.toggle("on", selected);
    if (selected) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  });
  document.querySelectorAll(".nav #prev, .nav #todayBtn, .nav #next").forEach(control => {
    control.style.visibility = active === "calendar" ? "visible" : "hidden";
  });
  if (active === "settings") {
    renderSettingsPanels();
    if (rawRoute.startsWith("settings-")) {
      const section = rawRoute.replace("settings-", "");
      $("view-settings")?.__openSettingsSection?.(section, true);
    }
  }
  if (active === "today" && typeof renderTodayDashboard === "function") renderTodayDashboard();
  if (active === "calendar") renderCalendar();
  if (active === "important" && typeof renderImportantBoard === "function") renderImportantBoard();
  if (active === "vacation" && typeof openVacationPlannerView === "function") {
    window.__dutylogVacationReady = Promise.resolve(openVacationPlannerView(true));
  }
  if (active === "overtime" && typeof loadLedgerPage === "function") {
    window.__dutylogLedgerRouteReady = Promise.resolve(loadLedgerPage(true));
  }
  if (active === "payroll" && typeof openPayrollView === "function") {
    window.__dutylogPayrollReady = Promise.resolve(openPayrollView(true));
  }
  if (active === "admin") {
    if (typeof initAdminNavigation === "function") initAdminNavigation();
    renderDiagnosticsClient();
    if (state.profile?.admin) refreshAdminPanel();
  }
  publishLegacyPlatformState();
}
window.addEventListener("hashchange", applyRoute);
applyRoute();

/* ─── Полноэкранный редактор заметок ────────────────────────── */
function renderNoteFsPrev(){
  const v = $("noteFsEdit").value;
  $("noteFsPrev").innerHTML = v.trim() ? renderMd(v)
    : `<span class="noteFsEmpty">${esc(t("Пусто. Пиши слева — превью живое."))}</span>`;
}

function openNoteFullscreen(){
  if (!moduleEnabled("notes")) return;
  if (!state.selected) return;
  const active = activeDayNote(state.selected);
  if (!active) return setSave("err", t("Сначала создайте заметку"));
  $("noteFsEdit").value = $("noteEdit").value;
  if ($("noteFsName")) $("noteFsName").textContent = noteLabel(active);
  $("noteFsDate").textContent = ($("pWeekday")?.textContent || "") + " · " + ($("pDate")?.textContent || state.selected);
  renderNoteFsPrev();
  $("noteFullscreen").hidden = false;
  document.body.style.overflow = "hidden"; // страница под оверлеем не скроллится
  $("noteFsEdit").focus();
}

function closeNoteFullscreen(){
  flushPendingNoteSave().catch(err => console.error(err));
  $("noteFullscreen").hidden = true;
  document.body.style.overflow = "";
}

// Сквозная запись: переиспользуем весь существующий пайплайн сохранения —
// пишем в noteEdit и диспатчим его событие. Дебаунс, календарь, сводки — всё штатное.
$("noteFsEdit").addEventListener("input", () => {
  $("noteEdit").value = $("noteFsEdit").value;
  $("noteEdit").dispatchEvent(new Event("input"));
  renderNoteFsPrev();
});

// Tab в редакторе — отступ, а не прыжок фокуса (как в Obsidian)
$("noteFsEdit").addEventListener("keydown", e => {
  if (e.key === "Tab") {
    e.preventDefault();
    const el = e.target, s = el.selectionStart, end = el.selectionEnd;
    el.value = el.value.slice(0, s) + "  " + el.value.slice(end);
    el.selectionStart = el.selectionEnd = s + 2;
    el.dispatchEvent(new Event("input"));
  }
});

$("noteExpand").addEventListener("click", openNoteFullscreen);
$("noteFsClose").addEventListener("click", closeNoteFullscreen);
document.addEventListener("keydown", e => {
  const fullscreen = $("noteFullscreen");
  if (e.key === "Escape" && fullscreen && !fullscreen.hidden) closeNoteFullscreen();
});
$("noteFsTab").addEventListener("click", () => {
  const fs = $("noteFullscreen");
  fs.classList.toggle("showPrev");
  const prev = fs.classList.contains("showPrev");
  $("noteFsTab").textContent = prev ? t("редактор") : t("превью");
  if (prev) renderNoteFsPrev();
});

/* ─── Профиль: имя, аватар, ДР, пароль, сессии ──────────────── */
const AVATAR_COLORS = ["#F5B841","#E0653A","#C97BB8","#7B8CE0","#4FA3A5","#6FBF73","#B5A642"];

function avatarInitials(name){
  const parts = (name || "?").trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  return parts.length === 1
    ? parts[0].slice(0, 2).toUpperCase()
    : (parts[0][0] + parts[1][0]).toUpperCase();
}
function avatarColor(seed){
  let h = 0;
  for (const ch of String(seed)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}

function renderHeaderIdentity(p){
  const shown = p.displayName || p.username;
  const who = $("whoami");
  who.textContent = shown;
  const adminOpen = $("adminOpen");
  if (adminOpen) adminOpen.hidden = !p.admin;
  let av = document.getElementById("headerAvatar");
  if (!av) {
    av = document.createElement("span");
    av.id = "headerAvatar";
    av.className = "avatar avatarSmall";
    who.parentNode.insertBefore(av, who);
  }
  av.textContent = avatarInitials(shown);
  av.style.background = avatarColor(p.username);
  const nextAvatar = $("nextHeaderAvatar");
  if (nextAvatar) {
    nextAvatar.textContent = avatarInitials(shown);
    nextAvatar.style.background = avatarColor(p.username);
    nextAvatar.title = t("Открыть профиль");
  }
  publishLegacyPlatformState();
}

function maybeBirthdayBanner(p){
  if (!p.birthday) return;
  const today = new Date();
  const [ , m, d ] = p.birthday.split("-").map(Number);
  if (today.getMonth() + 1 !== m || today.getDate() !== d) return;
  if (document.getElementById("bdayBanner")) return;
  const el = document.createElement("div");
  el.id = "bdayBanner";
  el.className = "bdayBanner";
  el.textContent = t("🎉 С днём рождения, ") + (p.displayName || p.username) + (state.language === "en" ? "! Skipping the shift today?" : "! Смену сегодня прогуливаем?");
  const tabbar = document.getElementById("tabbar");
  if (tabbar) tabbar.insertAdjacentElement("afterend", el);
}

function setProfileMsg(id, text, ok){
  const el = $(id);
  el.textContent = text || "";
  el.className = "profileMsg" + (text ? (ok ? " ok" : " err") : "");
}

async function loadProfile(){
  try {
    const p = await jfetch("/api/profile");
    state.profile = p;
    renderHeaderIdentity(p);
    maybeBirthdayBanner(p);
    $("profileName").value = p.displayName || "";
    $("profileBirthday").value = p.birthday || "";
    state.preferences = storeLocalAppearance({ themePreference:p.themePreference, accentColor:p.accentColor, themePreset:p.themePreset, themeConfig:p.themeConfig });
    applyAppearance(state.preferences);
    applyLanguage(p.languagePreference || state.language);
    state.timeSettings = {
      ...loadTimeSettings(),
      workTimezone:p.workTimezone || loadTimeSettings().workTimezone,
      displayTimezone:p.workTimezone || loadTimeSettings().workTimezone
    };
    storeTimeSettings(state.timeSettings);
    if (typeof renderTimeSettings === "function") renderTimeSettings();
    const av = $("profileAvatar");
    av.textContent = avatarInitials(p.displayName || p.username);
    av.style.background = avatarColor(p.username);
    if (location.hash === "#admin" && !p.admin) location.hash = "#calendar";
    applyRoute();
    // Route publication is no longer a post-Vue applyRoute side effect. Profile
    // load still must publish authoritative access state so Vue can re-run guards.
    publishLegacyPlatformState();
    maybeShowOnboarding();
  } catch (e) { console.error(e); }
}

$("nextHeaderAvatar")?.addEventListener("click", () => { location.hash = "#settings-profile"; });
$("adminOpen")?.addEventListener("click", () => { location.hash = "#admin"; });
$("adminBack")?.addEventListener("click", () => { location.hash = "#settings"; });
$("adminBackNav")?.addEventListener("click", () => { location.hash = "#settings"; });

$("profileSave").addEventListener("click", async () => {
  try {
    const p = await jfetch("/api/profile", { method: "PUT", body: {
      displayName: $("profileName").value,
      birthday: $("profileBirthday").value || null,
    }});
    state.profile = p;
    renderHeaderIdentity(p);
    const av = $("profileAvatar");
    av.textContent = avatarInitials(p.displayName || p.username);
    setProfileMsg("profileMsg", t("Сохранено"), true);
    setTimeout(() => setProfileMsg("profileMsg", ""), 2000);
  } catch (e) { setProfileMsg("profileMsg", e.message); }
});


function currentProfilePayload(extra = {}){
  return {
    displayName: $('profileName')?.value || "",
    birthday: $('profileBirthday')?.value || null,
    languagePreference: state.language,
    workTimezone: state.timeSettings?.workTimezone || state.profile?.workTimezone || browserTimeZone(),
    displayTimezone: state.timeSettings?.workTimezone || state.profile?.workTimezone || browserTimeZone(),
    ...extra,
  };
}

let appearanceSaveTimer = null;
let appearanceRevision = 0;
let appearanceSaveQueue = Promise.resolve();

function appearanceStatus(text = "", ok = false){
  setProfileMsg('appearanceMsg', text, ok);
}

function localAppearanceSnapshot(){
  const prefs = normalizeAppearance(state.preferences || readAppearanceFromControls());
  state.preferences = storeLocalAppearance(prefs);
  return prefs;
}

function persistAppearanceRevision(revision, snapshot){
  const operation = async () => {
    if (revision < appearanceRevision) return;
    appearanceStatus(t('Сохраняется…'), true);
    try {
      const p = await jfetch('/api/profile', { method:'PUT', body: currentProfilePayload(snapshot) });
      if (revision !== appearanceRevision) return;
      state.profile = p;
      state.preferences = storeLocalAppearance({
        themePreference:p.themePreference,
        accentColor:p.accentColor,
        themePreset:p.themePreset,
        themeConfig:p.themeConfig
      });
      applyAppearance(state.preferences);
      applyLanguage(p.languagePreference || state.language);
      appearanceStatus(t('Сохранено автоматически'), true);
      setTimeout(() => {
        if (revision === appearanceRevision) appearanceStatus('');
      }, 1800);
    } catch (error) {
      if (revision === appearanceRevision) appearanceStatus(error.message || t('Ошибка сохранения'));
      throw error;
    }
  };
  const pending = appearanceSaveQueue.then(operation, operation);
  appearanceSaveQueue = pending.catch(() => {});
  return pending;
}

function scheduleAppearanceAutoSave(delay = 650){
  const snapshot = localAppearanceSnapshot();
  const revision = ++appearanceRevision;
  clearTimeout(appearanceSaveTimer);
  appearanceStatus(t('Сохраняется…'), true);
  appearanceSaveTimer = setTimeout(() => persistAppearanceRevision(revision, snapshot).catch(() => {}), delay);
}

function persistAppearanceNow(){
  clearTimeout(appearanceSaveTimer);
  const snapshot = localAppearanceSnapshot();
  const revision = ++appearanceRevision;
  return persistAppearanceRevision(revision, snapshot);
}
document.querySelectorAll('[data-language-choice]').forEach(btn => btn.addEventListener('click', async () => {
  const lang = normalizeLanguage(btn.dataset.languageChoice);
  applyLanguage(lang);
  try {
    const p = await jfetch('/api/profile', { method:'PUT', body: currentProfilePayload({ languagePreference:lang }) });
    state.profile = p;
    applyLanguage(p.languagePreference || lang);
    setProfileMsg('languageMsg', t('Язык сохранён'), true);
    setTimeout(() => setProfileMsg('languageMsg', ''), 2000);
  } catch (e) {
    setProfileMsg('languageMsg', e.message || 'Language was changed locally');
  }
}));
$('appearancePreset')?.addEventListener('change', e => {
  applyPreset(e.target.value);
  scheduleAppearanceAutoSave();
});
$('appearanceTheme')?.addEventListener('change', () => { updateUiPlatformAndPreview(); scheduleAppearanceAutoSave(); });
$('appearanceAccent')?.addEventListener('input', () => {
  if ($('uiPalette')) $('uiPalette').value = 'custom';
  markPaletteCustomAndPreview();
  scheduleAppearanceAutoSave();
});
$('uiWorkspace')?.addEventListener('change', () => { updateUiPlatformAndPreview(); scheduleAppearanceAutoSave(); });
$('uiLayout')?.addEventListener('change', () => { updateUiPlatformAndPreview(); scheduleAppearanceAutoSave(); });
$('uiDecoration')?.addEventListener('change', () => { updateUiPlatformAndPreview(); scheduleAppearanceAutoSave(); });
$('uiCalendarDensity')?.addEventListener('change', () => { updateUiPlatformAndPreview(); scheduleAppearanceAutoSave(); });
$('uiCalendarLayerStyle')?.addEventListener('change', () => { updateUiPlatformAndPreview(); scheduleAppearanceAutoSave(); });
$('uiPalette')?.addEventListener('change', e => {
  applyPaletteMode(e.target.value);
  scheduleAppearanceAutoSave();
});
$('paletteThemeReset')?.addEventListener('click', () => {
  restoreThemePalette();
  scheduleAppearanceAutoSave(0);
});
$('uiAccentSecondary')?.addEventListener('input', () => {
  if ($('uiPalette')) $('uiPalette').value = 'custom';
  markPaletteCustomAndPreview();
  scheduleAppearanceAutoSave();
});
for (const id of ['themeAppBg','themePanelBg','themePanelAltBg','themeTextColor','themeMutedColor','themeBorderColor','themeButtonStyle','themeCardStyle','themeShadowLevel','themeDensity','themeCardRadius']) {
  $(id)?.addEventListener('input', () => { markThemeCustomAndPreview(); scheduleAppearanceAutoSave(); });
  $(id)?.addEventListener('change', () => { markThemeCustomAndPreview(); scheduleAppearanceAutoSave(); });
}
$('themeCardRadius')?.addEventListener('input', e => { if ($('themeCardRadiusValue')) $('themeCardRadiusValue').textContent = `${e.target.value}px`; });
$('appearanceSave')?.addEventListener('click', () => persistAppearanceNow().catch(() => {}));
$('appearanceReset')?.addEventListener('click', () => {
  state.preferences = normalizeAppearance(DEFAULT_APPEARANCE);
  applyAppearance(state.preferences);
  scheduleAppearanceAutoSave(0);
});
$('dayEmojiClear')?.addEventListener('click', () => setDayEmoji(null));
$('dayEmojiApply')?.addEventListener('click', () => setDayEmoji($('dayEmojiCustom')?.value || ''));
$('dayEmojiCustom')?.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); setDayEmoji(e.target.value); } });

$("pwChange").addEventListener("click", async () => {
  const cur = $("pwCurrent").value, nw = $("pwNew").value, rep = $("pwRepeat").value;
  if (nw !== rep) { setProfileMsg("pwMsg", t("Новые пароли не совпадают")); return; }
  try {
    await jfetch("/api/profile/password", { method: "POST", body: { currentPassword: cur, newPassword: nw } });
    for (const id of ["pwCurrent", "pwNew", "pwRepeat"]) $(id).value = "";
    setProfileMsg("pwMsg", t("Пароль изменён. Активные мобильные сессии завершены."), true);
    loadSessions();
  } catch (e) { setProfileMsg("pwMsg", e.message); }
});

async function loadSessions(){
  const box = $("sessionsList");
  try {
    const list = await jfetch("/api/profile/sessions");
    box.innerHTML = "";
    if (!list.length) {
      box.innerHTML = `<div class="sessionRow"><span class="meta">${esc(t("Мобильных сессий нет — только этот браузер."))}</span></div>`;
      return;
    }
    for (const sess of list) {
      const row = document.createElement("div");
      row.className = "sessionRow";
      const dev = document.createElement("span");
      dev.className = "dev" + (sess.active ? "" : " dead");
      dev.textContent = sess.deviceName || t("устройство");
      const meta = document.createElement("span");
      meta.className = "meta";
      const last = sess.lastUsedAt ? formatAbsoluteInstant(sess.lastUsedAt) : t("не использовалась");
      meta.textContent = (sess.active ? t("активна") + " · " : t("отозвана") + " · ") + last;
      row.append(dev, meta);
      if (sess.active) {
        const del = document.createElement("button");
        del.type = "button";
        del.textContent = t("отозвать");
        del.addEventListener("click", async () => {
          try { await jfetch("/api/profile/sessions/" + sess.id, { method: "DELETE" }); loadSessions(); }
          catch (e) { console.error(e); }
        });
        row.appendChild(del);
      }
      box.appendChild(row);
    }
  } catch (e) {
    box.innerHTML = `<div class="sessionRow"><span class="meta">${esc(t("Не удалось загрузить сессии."))}</span></div>`;
  }
}


/* ─── Telegram: привязка бота ───────────────────────────────── */
function telegramName(status){
  return status?.botUsername ? "@" + status.botUsername : t("бот");
}
function renderTelegramPanel(){
  if (document.documentElement.dataset.vueSettingsWorkspace === "ready") return;
  const box = $("telegramBox");
  if (!box) return;
  const s = state.telegramStatus;
  const status = $("telegramStatus");
  const codeBox = $("telegramCodeBox");
  const unlink = $("telegramUnlinkBtn");
  const notifyToggle = $("telegramNotificationsEnabled");
  if (!s) {
    status.textContent = t("загрузка…");
    status.className = "telegramStatus";
    if (unlink) unlink.disabled = true;
    if (notifyToggle) notifyToggle.disabled = true;
    return;
  }
  if (unlink) unlink.disabled = !s.linked;
  if (notifyToggle) {
    notifyToggle.checked = !!s.notificationsEnabled;
    notifyToggle.disabled = !s.configured || !s.linked;
  }
  if (!s.configured) {
    status.textContent = t("Бот не настроен на сервере: укажите DUTYLOG_TELEGRAM_BOT_TOKEN и включите polling.");
    status.className = "telegramStatus warn";
  } else if (s.linked) {
    const name = s.username ? "@" + s.username : "chat " + s.chatId;
    status.textContent = t("Подключено") + ": " + name + (s.notificationsEnabled ? " · " + t("напоминания включены") : " · " + t("напоминания выключены"));
    status.className = "telegramStatus ok";
  } else {
    status.textContent = state.language === "en"
      ? `Not connected. Create a code and send it to ${telegramName(s)}.`
      : "Не подключено. Создайте код и отправьте его " + telegramName(s) + ".";
    status.className = "telegramStatus";
  }
  if (s.pendingCode && codeBox.hidden) {
    showTelegramCode({ code:s.pendingCode, expiresAt:s.pendingCodeExpiresAt, startCommand:"/start " + s.pendingCode, deepLink:s.botUsername ? "https://t.me/" + s.botUsername + "?start=" + s.pendingCode : null });
  }
}
async function loadTelegramStatus(){
  if (document.documentElement.dataset.vueSettingsWorkspace === "ready") return;
  // moduleEnabled() is intentionally optimistic before module metadata loads so the
  // shell does not flicker. API calls must be stricter or a disabled integration can
  // emit a noisy 403 during boot.
  if (!$("telegramBox") || !state.modulesLoaded || !moduleEnabled("telegram")) return;
  try {
    state.telegramStatus = await api.telegramStatus();
    renderTelegramPanel();
  } catch (e) {
    const status = $("telegramStatus");
    if (status) { status.textContent = t("Не удалось загрузить статус Telegram."); status.className = "telegramStatus warn"; }
  }
}
function showTelegramCode(c){
  const box = $("telegramCodeBox");
  if (!box) return;
  box.hidden = false;
  const exp = c.expiresAt ? c.expiresAt.slice(11,16) : t("через 15 минут");
  const link = c.deepLink ? `<a href="${esc(c.deepLink)}" target="_blank" rel="noreferrer">${esc(t("открыть бота"))}</a>` : esc(t("Укажите username бота в настройках сервера, чтобы появилась ссылка"));
  box.innerHTML = `<div class="code">${esc(c.code)}</div><div>${esc(t("Отправьте боту:"))} <b>${esc(c.startCommand)}</b></div><div class="meta">${esc(t("Код действует до"))} ${esc(exp)} · ${link}</div>`;
}
$("telegramCodeBtn")?.addEventListener("click", async () => {
  const btn = $("telegramCodeBtn");
  try {
    btn.disabled = true;
    const code = await api.telegramCode();
    showTelegramCode(code);
    await loadTelegramStatus();
  } catch (e) {
    const status = $("telegramStatus");
    if (status) { status.textContent = e.message; status.className = "telegramStatus warn"; }
  } finally {
    btn.disabled = false;
  }
});

$("telegramNotificationsEnabled")?.addEventListener("change", async () => {
  const toggle = $("telegramNotificationsEnabled");
  if (!toggle) return;
  try {
    toggle.disabled = true;
    state.telegramStatus = await api.telegramSettings({ notificationsEnabled: toggle.checked });
    renderTelegramPanel();
  } catch (e) {
    toggle.checked = !toggle.checked;
    const status = $("telegramStatus");
    if (status) { status.textContent = e.message; status.className = "telegramStatus warn"; }
  } finally {
    toggle.disabled = !(state.telegramStatus?.configured && state.telegramStatus?.linked);
  }
});

$("telegramUnlinkBtn")?.addEventListener("click", async () => {
  if (!confirm(t("Отключить Telegram от этого аккаунта?"))) return;
  try {
    await api.telegramUnlink();
    $("telegramCodeBox").hidden = true;
    await loadTelegramStatus();
  } catch (e) {
    const status = $("telegramStatus");
    if (status) { status.textContent = e.message; status.className = "telegramStatus warn"; }
  }
});


// v26.0: first-run onboarding i18n.
Object.assign(I18N_EN, {
  "Настрой DutyLog под себя":"Set up DutyLog for yourself",
  "Выбери модули, которые нужны прямо сейчас. Остальное можно включить позже в настройках, данные не удаляются.":"Choose the modules you need right now. You can enable everything else later in Settings; data is not deleted.",
  "Быстрые наборы модулей":"Quick module presets",
  "Минимум":"Minimum",
  "Стандарт":"Standard",
  "Всё включить":"Enable all",
  "Пропустить":"Skip",
  "Начать":"Start",
  "Первый запуск":"First run",
  "выберите модули":"choose modules",
  "Первичная настройка модулей":"Initial module setup",
  "Новый пользователь сначала выбирает нужные функции, чтобы не попадать сразу в перегруженный интерфейс.":"A new user chooses the features they need first, instead of landing in an overloaded interface.",
  "онбординг завершён":"onboarding completed"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
ensureTranslationObserver();
applyLanguage(state.language);
loadSessions();

/* ─── Экспорт заметок ───────────────────────────────────────── */
$("exportNotesBtn")?.addEventListener("click", () => {
  // обычная навигация: сессионная cookie уедет сама, браузер скачает файл
  window.location.href = "/api/export/notes";
});
