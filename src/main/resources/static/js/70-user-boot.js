/*
 * 70-user-boot.js — Boot: loading, routing, notes fullscreen, profile and Telegram UI
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

/* ─── Загрузка данных ───────────────────────────────────────── */
function applyCalendarBundle(bundle){
  applyModulesFromBundle(bundle);
  bundle = sanitizeCalendarBundleForModules(bundle);
  if (Array.isArray(bundle)) {
    // На всякий случай оставлен fallback под старый endpoint.
    state.days = {};
    state.tasksByDate = {};
    state.importantByDate = {};
    state.remindersByDate = {};
    for (const e of bundle) state.days[e.date] = normalizeDay(e);
    return;
  }
  state.days = {};
  state.tasksByDate = {};
  state.importantByDate = {};
  state.remindersByDate = {};
  if (bundle.shiftTypes) state.shiftTypes = bundle.shiftTypes;
  for (const e of bundle.days || []) state.days[e.date] = normalizeDay(e);
  if (moduleEnabled("tasks")) for (const t of bundle.tasks || []) addToDateMap(state.tasksByDate, t);
  if (moduleEnabled("important_dates")) for (const i of bundle.importantDays || []) addToDateMap(state.importantByDate, i);
  state.notificationSettings = moduleEnabled("notifications") ? (bundle.notificationSettings || state.notificationSettings) : null;
  state.quickScenarios = moduleEnabled("scenarios") ? (bundle.quickScenarios || state.quickScenarios || []) : [];
  state.reminders = moduleEnabled("notifications") ? (bundle.reminders || []) : [];
  for (const r of state.reminders) addToDateMap(state.remindersByDate, { ...r, date:r.sourceDate });
  if (moduleEnabled("overtime") && bundle.overtimeAccount) state.overtimeAccount = bundle.overtimeAccount;
}

async function loadMonth(){
  state.ui.loadingCalendar = true;
  renderCalendar();
  try {
    const res = await dataLayer.loadCalendar(state.y, state.m, applyCalendarBundle);
    setSave(res?.fromCache ? "" : "");
    renderNotifications();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  } finally {
    state.ui.loadingCalendar = false;
  }
}

/* ─── Пользователь ──────────────────────────────────────────── */
$("logout").addEventListener("click", async () => {
  try { await flushPendingSave(); } catch (e) { /* не блокируем выход */ }
  try { await fetch("/logout", { method: "POST", headers: csrfToken() ? { "X-XSRF-TOKEN": csrfToken() } : {} }); } catch (e) { /* пофиг, всё равно уходим */ }
  window.location.href = "/login.html";
});

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("/service-worker.js").catch(() => {}));
}

async function init(){
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
    if (moduleEnabled("important_dates")) await refreshImportantSettings();
  } catch (err) {
    console.error(err);
    if (err.status === 401) return; // при 401 нас уже уносит на login.html
    state.offline.online = false;
    setSave("err", t("нет связи — открыта локальная копия"));
  }
  await loadMonth();
  if (moduleEnabled("overtime")) await loadLedgerPage(true);
  if (moduleEnabled("tasks")) await loadTaskBoard(true);
  applyModuleVisibility();
  renderCalendar();
  setAppBooting(false);
  dataLayer.syncQueue();
}
init().catch(err => {
  console.error(err);
  setAppBooting(false);
  setSave("err", err.message || t("ошибка"));
});

/* ─── Вкладки: hash-роутинг ─────────────────────────────────── */
const VIEWS = { calendar:"view-calendar", overtime:"view-overtime", tasks:"view-tasks", settings:"view-settings", admin:"view-admin" };
function applyRoute(){
  const rawRoute = (location.hash || "#calendar").slice(1);
  const name = rawRoute.startsWith("settings-") ? "settings" : rawRoute;
  let active = VIEWS[name] ? name : "calendar";
  if (active === "admin" && state.profile && !state.profile.admin) active = "calendar";
  if (active === "tasks" && !moduleEnabled("tasks")) active = "calendar";
  if (active === "overtime" && !moduleEnabled("overtime")) active = "calendar";
  document.body.dataset.view = active;
  if (active !== "calendar") selectDay(null);
  for (const [key, id] of Object.entries(VIEWS)) {
    const el = document.getElementById(id);
    if (el) el.hidden = key !== active;
  }
  document.querySelectorAll("#tabbar a").forEach(a =>
    a.classList.toggle("on", a.dataset.view === active));
  // месячная навигация в шапке осмысленна только в календаре
  document.querySelector(".nav #prev").style.visibility =
  document.querySelector(".nav #todayBtn").style.visibility =
  document.querySelector(".nav #next").style.visibility =
    active === "calendar" ? "visible" : "hidden";
  if (active === "settings") {
    renderSettingsPanels();
    if (rawRoute.startsWith("settings-")) {
      const section = rawRoute.replace("settings-", "");
      $("view-settings")?.__openSettingsSection?.(section, true);
    }
  }
  if (active === "admin") {
    renderDiagnosticsClient();
    refreshDiagnostics();
  }
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
  $("noteFsEdit").value = $("noteEdit").value;
  $("noteFsDate").textContent = ($("pWeekday")?.textContent || "") + " · " + ($("pDate")?.textContent || state.selected);
  renderNoteFsPrev();
  $("noteFullscreen").hidden = false;
  document.body.style.overflow = "hidden"; // страница под оверлеем не скроллится
  $("noteFsEdit").focus();
}

function closeNoteFullscreen(){
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
  if (e.key === "Escape" && !$("noteFullscreen").hidden) closeNoteFullscreen();
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
    const av = $("profileAvatar");
    av.textContent = avatarInitials(p.displayName || p.username);
    av.style.background = avatarColor(p.username);
    if (location.hash === "#admin" && !p.admin) location.hash = "#calendar";
    applyRoute();
    maybeShowOnboarding();
  } catch (e) { console.error(e); }
}

$("adminOpen")?.addEventListener("click", () => { location.hash = "#admin"; });
$("adminBack")?.addEventListener("click", () => { location.hash = "#settings"; });

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
    ...extra,
  };
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
$('appearancePreset')?.addEventListener('change', e => applyPreset(e.target.value));
$('appearanceTheme')?.addEventListener('change', markCustomAndPreview);
$('appearanceAccent')?.addEventListener('input', markCustomAndPreview);
for (const id of ['themeAppBg','themePanelBg','themePanelAltBg','themeTextColor','themeMutedColor','themeBorderColor','themeButtonStyle','themeCardStyle','themeShadowLevel','themeDensity','themeCardRadius']) {
  $(id)?.addEventListener('input', markCustomAndPreview);
  $(id)?.addEventListener('change', markCustomAndPreview);
}
$('themeCardRadius')?.addEventListener('input', e => { if ($('themeCardRadiusValue')) $('themeCardRadiusValue').textContent = `${e.target.value}px`; });
$('appearanceSave')?.addEventListener('click', async () => {
  try {
    const prefs = readAppearanceFromControls();
    const p = await jfetch('/api/profile', { method:'PUT', body: currentProfilePayload(prefs) });
    state.profile = p;
    state.preferences = storeLocalAppearance({ themePreference:p.themePreference, accentColor:p.accentColor, themePreset:p.themePreset, themeConfig:p.themeConfig });
    applyAppearance(state.preferences);
    applyLanguage(p.languagePreference || state.language);
    setProfileMsg('appearanceMsg', t('Внешний вид сохранён'), true);
    setTimeout(() => setProfileMsg('appearanceMsg', ''), 2000);
  } catch (e) { setProfileMsg('appearanceMsg', e.message); }
});
$('appearanceReset')?.addEventListener('click', () => {
  state.preferences = normalizeAppearance(DEFAULT_APPEARANCE);
  applyAppearance(state.preferences);
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
      const last = sess.lastUsedAt ? sess.lastUsedAt.slice(0, 16).replace("T", " ") : t("не использовалась");
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
  if (!$("telegramBox") || !moduleEnabled("telegram")) return;
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
  "Работа + переработки":"Work + overtime",
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
loadProfile();
loadSessions();
