
"use strict";

const DUTYLOG_VERSION = "23.1";

/* ─── Состояние ─────────────────────────────────────────────── */
const state = {
  y: new Date().getFullYear(),
  m: new Date().getMonth(),      // 0–11
  shiftTypes: [],                 // [{id,name,hours,color,builtin,startTime,endTime,breakMinutes,plannedHours}]
  days: {},                       // { 'YYYY-MM-DD': {shiftTypeId, note, overtimeHours, timeOffHours} }
  tasksByDate: {},                // { 'YYYY-MM-DD': [{id,date,text,done,category,priority,dueDate,dueTime,overdue}] }
  taskFilters: { status:"all", category:"all" },
  taskBoard: { items: [], filters: { status:"open", category:"all", priority:"all", q:"", from:"", to:"" } },
  importantByDate: {},            // { 'YYYY-MM-DD': [{id,date,title,repeatMode,color}] }
  importantDays: [],               // настройки важных дней: [{id,date,title,repeatMode,color}]
  overtimeAccount: { totalEarnedHours:0, totalUsedHours:0, balanceHours:0, credits:[], usages:[] },
  notificationSettings: null,
  reminders: [],
  notificationPreview: null,
  notificationPreviewTitle: "Напоминания текущего месяца",
  remindersByDate: {},
  quickScenarios: [],
  timeSettings: null,
  telegramStatus: null,
  registrationSettings: null,
  adminUsers: [],
  preferences: { themePreference:"system", accentColor:"#F5B841" },
  activeScenarioId: null,
  ledgerFilters: { from:"", to:"", status:"all", q:"" },
  editingCreditId: null,
  editingUsageId: null,
  selected: null,                 // ключ даты
  tab: "edit",
  swColor: "#F5B841",
  offline: {
    online: navigator.onLine,
    lastSyncAt: null,
    pending: 0,
    failed: [],
    syncing: false,
    cacheReady: false,
    syncLockedByOther: false,
    stale: false,
  },
};

const MONTHS = ["Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"];
const MONTHS_GEN = ["января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"];
const WEEKDAYS = ["Пн","Вт","Ср","Чт","Пт","Сб","Вс"];
const SWATCHES = ["#F5B841","#E0653A","#C97BB8","#7B8CE0","#4FA3A5","#6FBF73","#B5A642","#8B929E"];
const APPEARANCE_SWATCHES = ["#F5B841","#E0653A","#C97BB8","#7B8CE0","#4FA3A5","#6FBF73","#9B7BE0","#E05780"];
const DAY_EMOJI_PRESETS = ["🔥","😴","✅","⚠️","💰","🏥","🎉","🛠️","🌙","☕","🚗","💪","📌","🧠","🛌","❤️"];

const DEFAULT_SCHEDULE_DAYS = 31;
const APPEARANCE_KEY = "dutylog.appearance.v2";
const THEME_PRESETS = {
  default: {
    label:"DutyLog Default",
    themePreference:"system",
    accentColor:"#F5B841",
    themeConfig:{ appBg:"", panelBg:"", panelAltBg:"", textColor:"", mutedColor:"", borderColor:"", buttonStyle:"solid", cardStyle:"default", cardRadius:14, shadowLevel:"medium", density:"comfortable" }
  },
  custom: {
    label:"Custom",
    themePreference:"system",
    accentColor:"#F5B841",
    themeConfig:{ appBg:"", panelBg:"", panelAltBg:"", textColor:"", mutedColor:"", borderColor:"", buttonStyle:"solid", cardStyle:"default", cardRadius:14, shadowLevel:"medium", density:"comfortable" }
  },
  midnight: {
    label:"Midnight",
    themePreference:"dark",
    accentColor:"#7B8CE0",
    themeConfig:{ appBg:"#0F1220", panelBg:"#181C2B", panelAltBg:"#20263A", textColor:"#EEF2FF", mutedColor:"#A7B0C9", borderColor:"#2D3550", buttonStyle:"soft", cardStyle:"contrast", cardRadius:16, shadowLevel:"medium", density:"comfortable" }
  },
  oled: {
    label:"OLED Black",
    themePreference:"dark",
    accentColor:"#00D1B2",
    themeConfig:{ appBg:"#000000", panelBg:"#080A0D", panelAltBg:"#11151A", textColor:"#F2F5F7", mutedColor:"#9AA4AE", borderColor:"#20262E", buttonStyle:"solid", cardStyle:"flat", cardRadius:12, shadowLevel:"none", density:"compact" }
  },
  forest: {
    label:"Forest",
    themePreference:"dark",
    accentColor:"#6FBF73",
    themeConfig:{ appBg:"#101812", panelBg:"#182219", panelAltBg:"#203020", textColor:"#EAF4EA", mutedColor:"#9CAF9E", borderColor:"#314335", buttonStyle:"soft", cardStyle:"default", cardRadius:18, shadowLevel:"soft", density:"comfortable" }
  },
  sunset: {
    label:"Sunset",
    themePreference:"dark",
    accentColor:"#E0653A",
    themeConfig:{ appBg:"#1C1413", panelBg:"#2A1B19", panelAltBg:"#35231F", textColor:"#FFF0E8", mutedColor:"#C9A397", borderColor:"#4A302A", buttonStyle:"solid", cardStyle:"warm", cardRadius:18, shadowLevel:"medium", density:"comfortable" }
  },
  industrial: {
    label:"Industrial",
    themePreference:"dark",
    accentColor:"#B5A642",
    themeConfig:{ appBg:"#121417", panelBg:"#1B1F24", panelAltBg:"#242A31", textColor:"#ECEFF3", mutedColor:"#A0A8B2", borderColor:"#343C46", buttonStyle:"outline", cardStyle:"contrast", cardRadius:8, shadowLevel:"low", density:"compact" }
  },
  softPurple: {
    label:"Soft Purple",
    themePreference:"light",
    accentColor:"#9B7BE0",
    themeConfig:{ appBg:"#F7F3FF", panelBg:"#FFFFFF", panelAltBg:"#EFE7FF", textColor:"#231B33", mutedColor:"#685B79", borderColor:"#D8C9F5", buttonStyle:"soft", cardStyle:"soft", cardRadius:20, shadowLevel:"soft", density:"comfortable" }
  }
};
const DEFAULT_THEME_CONFIG = THEME_PRESETS.default.themeConfig;
const DEFAULT_APPEARANCE = { themePreference:"system", accentColor:"#F5B841", themePreset:"default", themeConfig:{ ...DEFAULT_THEME_CONFIG } };

const TIME_SETTINGS_KEY = "shiftCalendar.timeRegionSettings.v1";
const DEFAULT_TIME_SETTINGS = {
  workRegionName: "",
  workTimezone: "Europe/Moscow",
  workOffsetMoscow: 0,
  timeFormat: "24h",
  dayStart: "08:30",
  dayEnd: "17:00",
  dayBreakMinutes: 30,
  dayPlannedHours: 8,
  nightStart: "20:00",
  nightEnd: "08:00",
  nightBreakMinutes: 60,
  nightPlannedHours: 11,
};

function browserTimeZone(){
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "Europe/Moscow"; }
  catch (e) { return "Europe/Moscow"; }
}
function loadTimeSettings(){
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(TIME_SETTINGS_KEY) || "{}"); }
  catch (e) { saved = {}; }
  return { ...DEFAULT_TIME_SETTINGS, workTimezone: browserTimeZone(), ...saved };
}
function storeTimeSettings(settings){
  state.timeSettings = { ...DEFAULT_TIME_SETTINGS, ...settings };
  try { localStorage.setItem(TIME_SETTINGS_KEY, JSON.stringify(state.timeSettings)); }
  catch (e) { console.warn("time settings not saved", e); }
}
function safeTzLabel(tz){
  try {
    return new Intl.DateTimeFormat("ru-RU", { dateStyle:"short", timeStyle:"short", timeZone:tz }).format(new Date());
  } catch (e) {
    return "часовой пояс не распознан";
  }
}

function isHexColor(value){ return /^#[0-9a-fA-F]{6}$/.test(String(value || "")); }
function clampNumber(value, min, max, fallback){
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}
function normalizeThemeConfig(config = {}){
  const base = { ...DEFAULT_THEME_CONFIG };
  const c = (config && typeof config === "object") ? config : {};
  const out = { ...base };
  for (const key of ["appBg","panelBg","panelAltBg","textColor","mutedColor","borderColor"]) {
    const val = String(c[key] || "").trim();
    out[key] = isHexColor(val) ? val.toUpperCase() : "";
  }
  out.buttonStyle = ["solid","soft","outline","ghost"].includes(String(c.buttonStyle || "")) ? String(c.buttonStyle) : base.buttonStyle;
  out.cardStyle = ["default","flat","soft","contrast","warm"].includes(String(c.cardStyle || "")) ? String(c.cardStyle) : base.cardStyle;
  out.shadowLevel = ["none","low","soft","medium","strong"].includes(String(c.shadowLevel || "")) ? String(c.shadowLevel) : base.shadowLevel;
  out.density = ["compact","comfortable","spacious"].includes(String(c.density || "")) ? String(c.density) : base.density;
  out.cardRadius = Math.round(clampNumber(c.cardRadius, 6, 28, base.cardRadius));
  return out;
}
function normalizeAppearance(p = {}){
  const theme = ["system","light","dark"].includes(String(p.themePreference || "").toLowerCase())
    ? String(p.themePreference).toLowerCase()
    : DEFAULT_APPEARANCE.themePreference;
  const accent = isHexColor(p.accentColor) ? String(p.accentColor).toUpperCase() : DEFAULT_APPEARANCE.accentColor;
  const preset = Object.prototype.hasOwnProperty.call(THEME_PRESETS, String(p.themePreset || ""))
    ? String(p.themePreset)
    : DEFAULT_APPEARANCE.themePreset;
  return { themePreference:theme, accentColor:accent, themePreset:preset, themeConfig:normalizeThemeConfig(p.themeConfig) };
}
function loadLocalAppearance(){
  try { return normalizeAppearance(JSON.parse(localStorage.getItem(APPEARANCE_KEY) || "{}")); }
  catch (_) { return normalizeAppearance(DEFAULT_APPEARANCE); }
}
function storeLocalAppearance(p){
  const prefs = normalizeAppearance(p);
  try { localStorage.setItem(APPEARANCE_KEY, JSON.stringify(prefs)); } catch (_) {}
  return prefs;
}
function effectiveTheme(themePreference){
  if (themePreference === "light" || themePreference === "dark") return themePreference;
  try { return matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark"; }
  catch (_) { return "dark"; }
}
function themeShadow(level){
  return {
    none:"none",
    low:"0 4px 14px rgba(0,0,0,.12)",
    soft:"0 10px 28px rgba(0,0,0,.14)",
    medium:"0 14px 40px rgba(0,0,0,.20)",
    strong:"0 22px 60px rgba(0,0,0,.32)"
  }[level] || "0 14px 40px rgba(0,0,0,.20)";
}
function applyThemeCssVariables(prefs){
  const cfg = normalizeThemeConfig(prefs.themeConfig);
  const root = document.documentElement;
  const variables = {
    "--accent": prefs.accentColor,
    "--theme-card-radius": `${cfg.cardRadius}px`,
    "--theme-shadow": themeShadow(cfg.shadowLevel),
  };
  if (cfg.appBg) variables["--bg"] = cfg.appBg;
  if (cfg.panelBg) variables["--panel"] = cfg.panelBg;
  if (cfg.panelAltBg) variables["--panel2"] = cfg.panelAltBg;
  if (cfg.textColor) variables["--text"] = cfg.textColor;
  if (cfg.mutedColor) variables["--mut"] = cfg.mutedColor;
  if (cfg.borderColor) variables["--line"] = cfg.borderColor;
  for (const name of ["--bg","--panel","--panel2","--text","--mut","--line","--accent","--theme-card-radius","--theme-shadow"]) {
    root.style.removeProperty(name);
  }
  for (const [name, value] of Object.entries(variables)) {
    root.style.setProperty(name, value);
  }
  root.dataset.buttonStyle = cfg.buttonStyle;
  root.dataset.cardStyle = cfg.cardStyle;
  root.dataset.density = cfg.density;
}
function applyAppearance(p = state.preferences){
  const prefs = normalizeAppearance(p);
  state.preferences = prefs;
  const theme = effectiveTheme(prefs.themePreference);
  document.documentElement.dataset.theme = theme;
  applyThemeCssVariables(prefs);
  const meta = document.querySelector('meta[name="theme-color"]');
  const metaColor = prefs.themeConfig.appBg || (theme === "light" ? "#F6F7FB" : "#14171C");
  if (meta) meta.setAttribute("content", metaColor);
  renderAppearanceControls();
}
function setPickerValue(id, value){
  const el = document.getElementById(id);
  if (el && isHexColor(value)) el.value = value;
}
function readAppearanceFromControls(){
  return normalizeAppearance({
    themePreference:$('appearanceTheme')?.value || state.preferences.themePreference,
    accentColor:$('appearanceAccent')?.value || state.preferences.accentColor,
    themePreset:$('appearancePreset')?.value || state.preferences.themePreset,
    themeConfig:{
      appBg:$('themeAppBg')?.value || "",
      panelBg:$('themePanelBg')?.value || "",
      panelAltBg:$('themePanelAltBg')?.value || "",
      textColor:$('themeTextColor')?.value || "",
      mutedColor:$('themeMutedColor')?.value || "",
      borderColor:$('themeBorderColor')?.value || "",
      buttonStyle:$('themeButtonStyle')?.value || "solid",
      cardStyle:$('themeCardStyle')?.value || "default",
      cardRadius:$('themeCardRadius')?.value || 14,
      shadowLevel:$('themeShadowLevel')?.value || "medium",
      density:$('themeDensity')?.value || "comfortable",
    }
  });
}
function setThemeBuilderControls(prefs){
  const byId = id => document.getElementById(id);
  const cfg = normalizeThemeConfig(prefs.themeConfig);
  if (byId('appearanceTheme')) byId('appearanceTheme').value = prefs.themePreference;
  if (byId('appearancePreset')) byId('appearancePreset').value = prefs.themePreset;
  setPickerValue('appearanceAccent', prefs.accentColor);
  setPickerValue('themeAppBg', cfg.appBg || (effectiveTheme(prefs.themePreference) === "light" ? "#F6F7FB" : "#14171C"));
  setPickerValue('themePanelBg', cfg.panelBg || (effectiveTheme(prefs.themePreference) === "light" ? "#FFFFFF" : "#1C2027"));
  setPickerValue('themePanelAltBg', cfg.panelAltBg || (effectiveTheme(prefs.themePreference) === "light" ? "#EEF1F6" : "#22262E"));
  setPickerValue('themeTextColor', cfg.textColor || (effectiveTheme(prefs.themePreference) === "light" ? "#18202B" : "#E8EAED"));
  setPickerValue('themeMutedColor', cfg.mutedColor || (effectiveTheme(prefs.themePreference) === "light" ? "#586274" : "#8B929E"));
  setPickerValue('themeBorderColor', cfg.borderColor || (effectiveTheme(prefs.themePreference) === "light" ? "#D7DDE8" : "#2E333C"));
  if (byId('themeButtonStyle')) byId('themeButtonStyle').value = cfg.buttonStyle;
  if (byId('themeCardStyle')) byId('themeCardStyle').value = cfg.cardStyle;
  if (byId('themeShadowLevel')) byId('themeShadowLevel').value = cfg.shadowLevel;
  if (byId('themeDensity')) byId('themeDensity').value = cfg.density;
  if (byId('themeCardRadius')) byId('themeCardRadius').value = cfg.cardRadius;
  if (byId('themeCardRadiusValue')) byId('themeCardRadiusValue').textContent = `${cfg.cardRadius}px`;
}
function renderAppearanceControls(){
  const byId = id => document.getElementById(id);
  if (!byId('appearanceTheme')) return;
  const prefs = normalizeAppearance(state.preferences);
  const presetSelect = byId('appearancePreset');
  if (presetSelect && !presetSelect.dataset.ready) {
    presetSelect.innerHTML = Object.entries(THEME_PRESETS).map(([key, preset]) => `<option value="${key}">${preset.label}</option>`).join("");
    presetSelect.dataset.ready = "1";
  }
  setThemeBuilderControls(prefs);
  const row = byId('appearanceAccentRow');
  if (row) {
    row.innerHTML = "";
    for (const color of APPEARANCE_SWATCHES) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "accentChoice" + (color.toUpperCase() === prefs.accentColor ? " on" : "");
      b.style.background = color;
      b.title = color;
      b.addEventListener("click", () => {
        state.preferences = normalizeAppearance({ ...readAppearanceFromControls(), accentColor:color, themePreset:"custom" });
        if ($('appearancePreset')) $('appearancePreset').value = state.preferences.themePreset;
        applyAppearance(state.preferences);
      });
      row.appendChild(b);
    }
  }
  const preview = byId('appearancePreview');
  const presetLabel = THEME_PRESETS[prefs.themePreset]?.label || "Custom";
  if (preview) preview.textContent = `${presetLabel} · ${prefs.themePreference === "system" ? "как в системе" : prefs.themePreference === "light" ? "светлая" : "тёмная"} · ${prefs.accentColor}`;
}
function applyPreset(key){
  const preset = THEME_PRESETS[key] || THEME_PRESETS.default;
  state.preferences = normalizeAppearance({ ...preset, themePreset:key });
  applyAppearance(state.preferences);
}
function markCustomAndPreview(){
  const prefs = readAppearanceFromControls();
  state.preferences = normalizeAppearance({ ...prefs, themePreset:"custom" });
  if ($('appearancePreset')) $('appearancePreset').value = "custom";
  applyAppearance(state.preferences);
}
applyAppearance(loadLocalAppearance());
try { matchMedia("(prefers-color-scheme: light)").addEventListener("change", () => applyAppearance(state.preferences)); } catch (_) {}


const SCHEDULE_TEMPLATES = {
  "2x2-day": { label:"2 через 2", names:["Дневная","Дневная","Выходной","Выходной"] },
  "day-night-48": { label:"День / ночь / 48", names:["Дневная","Ночная","Выходной","Выходной"] },
  "5x2": {
    label:"Пятидневка",
    names:["Дневная","Дневная","Дневная","Дневная","Дневная","Выходной","Выходной"],
    weekly:true
  },
  "1x3-day": { label:"День / 72", names:["Дневная","Выходной","Выходной","Выходной"] },
  "1x3-night": { label:"Ночь / 72", names:["Ночная","Выходной","Выходной","Выходной"] },
};

const pad = n => String(n).padStart(2, "0");
const keyOf = (y, m, d) => `${y}-${pad(m + 1)}-${pad(d)}`;
const todayKey = () => { const t = new Date(); return keyOf(t.getFullYear(), t.getMonth(), t.getDate()); };
const numOr0 = v => { const n = Number(v ?? 0); return Number.isFinite(n) ? n : 0; };
const fmtHours = v => {
  const n = Math.round(numOr0(v) * 100) / 100;
  return n.toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1");
};
const normalizeDay = e => ({
  shiftTypeId: e?.shiftTypeId ?? null,
  note: e?.note ?? null,
  dayEmoji: e?.dayEmoji ?? null,
  overtimeHours: numOr0(e?.overtimeHours),
  timeOffHours: numOr0(e?.timeOffHours),
});

function addToDateMap(map, item){
  const k = item.date;
  if (!map[k]) map[k] = [];
  map[k].push(item);
}
function tasksOf(k){ return state.tasksByDate[k] || []; }
function importantOf(k){ return state.importantByDate[k] || []; }
function remindersOf(k){ return state.remindersByDate[k] || []; }
function activeTasksOf(k){ return tasksOf(k).filter(t => !t.done); }
function overdueTasksOf(k){ return tasksOf(k).filter(t => t.overdue && !t.done); }
function taskPriorityLabel(p){ return p === "URGENT" ? "срочно" : p === "HIGH" ? "важно" : p === "LOW" ? "низкий" : "обычно"; }
function taskDueLabel(t){
  if (!t.dueDate) return "";
  const d = t.dueDate.split("-").reverse().join(".");
  return `срок ${d}${t.dueTime ? " " + t.dueTime : ""}`;
}
function allTaskCategories(){
  const set = new Set();
  for (const arr of Object.values(state.tasksByDate)) for (const t of arr) if ((t.category || "").trim()) set.add(t.category.trim());
  for (const t of state.taskBoard?.items || []) if ((t.category || "").trim()) set.add(t.category.trim());
  return Array.from(set).sort((a,b) => a.localeCompare(b, "ru"));
}
const repeatLabel = mode => mode === "YEARLY" ? "каждый год" : mode === "MONTHLY" ? "каждый месяц" : "один раз";
function creditsOf(k){ return (state.overtimeAccount?.credits || []).filter(x => x.workedDate === k); }
function usagesOf(k){ return (state.overtimeAccount?.usages || []).filter(x => x.usageDate === k); }
function ledgerNetOf(k){
  const earned = creditsOf(k).reduce((sum, x) => sum + numOr0(x.hours), 0);
  const used = usagesOf(k).reduce((sum, x) => sum + numOr0(x.hours), 0);
  return earned - used;
}

const monthPrefix = () => `${state.y}-${pad(state.m + 1)}-`;
function monthFromTo(y = state.y, m = state.m){
  const last = new Date(y, m + 1, 0).getDate();
  return { from: keyOf(y, m, 1), to: keyOf(y, m, last) };
}
function weekdayIndex(k){
  const [y, m, d] = k.split("-").map(Number);
  return (new Date(y, m - 1, d).getDay() + 6) % 7; // Пн=0, Вс=6
}
function dateKeyOffset(k, days){
  const [y, m, d] = k.split("-").map(Number);
  const dt = new Date(y, m - 1, d + days);
  return keyOf(dt.getFullYear(), dt.getMonth(), dt.getDate());
}
function setTimeOnDate(k, hhmm){ return `${k}T${hhmm}`; }
function displayDateTimeRange(startValue, endValue){
  if (!startValue || !endValue) return "";
  const sd = startValue.slice(0, 10), ed = endValue.slice(0, 10);
  const st = startValue.slice(11, 16), et = endValue.slice(11, 16);
  if (sd === ed) return `${st}–${et}`;
  const [, sm, sday] = sd.split("-");
  const [, em, eday] = ed.split("-");
  return `${sday}.${sm} ${st}–${eday}.${em} ${et}`;
}
function effectiveTemplateNames(tpl, startDateKey){
  if (!tpl.weekly) return tpl.names;
  const offset = weekdayIndex(startDateKey);
  return tpl.names.slice(offset).concat(tpl.names.slice(0, offset));
}
function findShiftByName(name){
  return state.shiftTypes.find(s => s.name === name) || null;
}
function shiftPlannedHours(s){
  if (!s) return 0;
  const p = Number(s.plannedHours);
  return Number.isFinite(p) ? p : numOr0(s.hours);
}
function shiftTimeText(s){
  if (!s || !s.startTime || !s.endTime) return "";
  const br = numOr0(s.breakMinutes);
  return `${s.startTime}–${s.endTime}${br ? ` · обед ${br}м` : ""}`;
}
function shiftMetaText(s){
  if (!s) return "";
  const parts = [];
  const time = shiftTimeText(s);
  if (time) parts.push(time);
  const plan = shiftPlannedHours(s);
  if (plan) parts.push(`план ${fmtHours(plan)}ч`);
  return parts.join(" · " );
}
const $ = id => document.getElementById(id);

/* ─── API ───────────────────────────────────────────────────── */
const api = {
  async shiftTypes()        { return jfetch("/api/shift-types"); },
  async createShiftType(b)  { return jfetch("/api/shift-types", { method:"POST", body:b }); },
  async updateShiftType(id, b) { return jfetch(`/api/shift-types/${id}`, { method:"PATCH", body:b }); },
  async deleteShiftType(id) { return jfetch(`/api/shift-types/${id}`, { method:"DELETE" }); },
  async month(y, m)         { const r = monthFromTo(y, m); return jfetch(`/api/calendar?from=${r.from}&to=${r.to}`); },
  async upsertDay(k, b)     { return jfetch(`/api/days/${k}`, { method:"PUT", body:b }); },
  async fillDays(b)        { return jfetch("/api/days/fill", { method:"POST", body:b }); },
  async createTask(b)      { return jfetch("/api/tasks", { method:"POST", body:b }); },
  async updateTask(id, b)  { return jfetch(`/api/tasks/${id}`, { method:"PATCH", body:b }); },
  async deleteTask(id)     { return jfetch(`/api/tasks/${id}`, { method:"DELETE" }); },
  async taskBoard(filters = {}) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(filters)) if (v !== undefined && v !== null && String(v).trim() !== "") qs.set(k, v);
    return jfetch(`/api/tasks/board?${qs.toString()}`);
  },
  async importantDays() { return jfetch("/api/important-days"); },
  async createImportantDay(b) { return jfetch("/api/important-days", { method:"POST", body:b }); },
  async deleteImportantDay(id) { return jfetch(`/api/important-days/${id}`, { method:"DELETE" }); },
  async overtimeAccount() { return jfetch("/api/overtime/account"); },
  async createOvertimeCredit(b) { return jfetch("/api/overtime/credits", { method:"POST", body:b }); },
  async updateOvertimeCredit(id, b) { return jfetch(`/api/overtime/credits/${id}`, { method:"PATCH", body:b }); },
  async deleteOvertimeCredit(id) { return jfetch(`/api/overtime/credits/${id}`, { method:"DELETE" }); },
  async createOvertimeUsage(b) { return jfetch("/api/overtime/usages", { method:"POST", body:b }); },
  async updateOvertimeUsage(id, b) { return jfetch(`/api/overtime/usages/${id}`, { method:"PATCH", body:b }); },
  async deleteOvertimeUsage(id) { return jfetch(`/api/overtime/usages/${id}`, { method:"DELETE" }); },
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
  async adminUsers() { return jfetch("/api/admin/users"); },
  async updateAdminUserRole(id, role) { return jfetch(`/api/admin/users/${id}/role`, { method:"PATCH", body:{ role } }); },
  async resetAdminUserPassword(id, newPassword) { return jfetch(`/api/admin/users/${id}/password`, { method:"POST", body:{ newPassword } }); },
  async registrationSettings() { return jfetch("/api/admin/settings/registration"); },
  async updateRegistrationSettings(enabled) { return jfetch("/api/admin/settings/registration", { method:"PATCH", body:{ enabled } }); },
};

/* CSRF: Spring кладёт токен в cookie XSRF-TOKEN, мы возвращаем его заголовком */
function csrfToken(){
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

function offlineRequiredMessage(url){
  if (url.startsWith("/api/overtime")) return "Переработки и отгулы можно изменять только при подключении к серверу. Смены, заметки и галочки задач сохраняются оффлайн.";
  if (url.startsWith("/api/days/fill")) return "Автозаполнение графика требует связи с сервером. Отдельную смену выбранного дня можно изменить оффлайн.";
  if (url.startsWith("/api/shift-types")) return "Типы смен и их расписание меняются только при подключении к серверу.";
  if (url.startsWith("/api/quick-scenarios")) return "Шаблоны переработок меняются только при подключении к серверу.";
  if (url.startsWith("/api/notifications")) return "Настройки уведомлений требуют связи с сервером.";
  if (url.startsWith("/api/telegram")) return "Telegram-интеграция настраивается только при подключении к серверу.";
  if (url.startsWith("/api/profile")) return "Профиль и сессии меняются только при подключении к серверу.";
  if (url.startsWith("/api/important-days")) return "Важные даты меняются только при подключении к серверу.";
  if (url.startsWith("/api/admin")) return "Админские настройки меняются только при подключении к серверу.";
  return "Эта операция требует связи с сервером. Смена дня, заметки и галочки задач сохраняются оффлайн.";
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
  });
  if (res.status === 401) {
    // Сессия истекла или не залогинен — на страницу входа
    window.location.href = "/login.html";
    throw new Error("401: не авторизован");
  }
  if (!res.ok) {
    let msg = `${opts.method || "GET"} ${url} → ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) msg = body.error;
    } catch (_) { /* ответ был не JSON */ }
    const err = new Error(msg);
    err.status = res.status;
    err.url = url;
    err.method = method;
    throw err;
  }
  if (res.status === 204) return null;
  return res.json();
}

function setSave(s, msg = "") {
  const el = $("saveState");
  el.classList.toggle("err", s === "err");
  el.textContent = s === "saving" ? "сохраняю…" : s === "saved" ? "✓" : s === "err" ? (msg || "ошибка сети") : "";
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
  if (!iso) return "нет синхронизации";
  try { return new Intl.DateTimeFormat("ru-RU", { dateStyle:"short", timeStyle:"short" }).format(new Date(iso)); }
  catch (_) { return String(iso).slice(0, 16).replace("T", " "); }
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
  if (min < 1) return "только что";
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
  if (!item) return "Операция";
  if (item.type === "putDay") {
    const d = item.payload?.day || {};
    const parts = [];
    if (d.shiftTypeId) parts.push("смена");
    if ((d.note || "").trim()) parts.push("заметка");
    if ((d.dayEmoji || "").trim()) parts.push("emoji " + d.dayEmoji);
    if (!parts.length) parts.push("день");
    return `${item.payload?.date || "дата"}: ${parts.join(" + ")}`;
  }
  if (item.type === "toggleTask") return `Задача #${item.payload?.taskId}: ${item.payload?.done ? "выполнена" : "открыта"}`;
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
    if (!raw) return { active:false, label:"нет", raw:null };
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
      label: expired ? "протух" : (mine ? "активен в этой вкладке" : "активен в другой вкладке"),
      raw:lock,
    };
  } catch (err) {
    return { active:false, label:"не читается", error:err.message || String(err) };
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
    ["Очередь", `${queue.length} ожидает отправки`, queue.length === 0],
    ["Не применилось", `${failed.length} ошибок`, failed.length === 0],
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
    return `<div class="diagRow${cls}"><span>${escapeHtml(label)}</span><b>${escapeHtml(value)}</b></div>`;
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
    if (!('indexedDB' in window)) throw new Error("Браузер не поддерживает локальное хранилище");
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
    await offlineDb.put("snapshot", { key:OFFLINE_SNAPSHOT_KEY, y, m, savedAt, bundle });
    state.offline.lastSyncAt = savedAt;
    updateOfflineStatus();
  },
  async updateSnapshotDay(date, day){
    const snap = await this.readSnapshot();
    if (!snap?.bundle) return;
    const days = Array.isArray(snap.bundle.days) ? snap.bundle.days.slice() : [];
    const clean = normalizeDay(day);
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
  async enqueue(type, payload){
    if (!state.offline.cacheReady) throw new Error("Нет связи с сервером, а локальная очередь недоступна");
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
    if (!state.offline.cacheReady) throw new Error("Локальное хранилище недоступно");
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
  async loadCalendar(y, m, applyBundle){
    let hadCache = false;
    const snap = await this.readSnapshot();
    if (snap?.bundle) {
      hadCache = true;
      state.offline.lastSyncAt = snap.savedAt || null;
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
      throw new Error("Нет связи и ещё нет локальной копии данных");
    }
    try {
      const bundle = await api.month(y, m);
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
    await this.updateSnapshotDay(date, day);
    if (navigator.onLine) {
      try {
        await api.upsertDay(date, {
          shiftTypeId: day.shiftTypeId ?? null,
          note: day.note ?? null,
          dayEmoji: day.dayEmoji ?? null,
          overtimeHours: numOr0(day.overtimeHours),
          timeOffHours: numOr0(day.timeOffHours),
        });
        state.offline.online = true;
        updateOfflineStatus();
        return { queued:false };
      } catch (err) {
        if (!isNetworkError(err)) throw err;
      }
    }
    state.offline.online = false;
    await this.enqueue("putDay", { date, day: normalizeDay(day) });
    return { queued:true };
  },
  async setTaskDone(taskId, done){
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
  async syncQueue(){
    if (!state.offline.cacheReady || state.offline.syncing) return;
    if (!navigator.onLine) { state.offline.online = false; updateOfflineStatus(); return; }
    const lock = acquireOfflineSyncLock();
    if (!lock) {
      state.offline.syncLockedByOther = true;
      updateOfflineStatus();
      setSave("err", "Синхронизация уже запущена в другой вкладке");
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
          if (item.type === "putDay") {
            await api.upsertDay(item.payload.date, item.payload.day);
          } else if (item.type === "toggleTask") {
            await api.updateTask(item.payload.taskId, { done: !!item.payload.done });
          } else {
            throw Object.assign(new Error("Неизвестный тип операции: " + item.type), { status:400 });
          }
          await offlineDb.delete("queue", item.id);
        } catch (err) {
          if (err.status === 401) throw err;
          if (err.status === 400 || err.status === 404 || err.status === 409) {
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
        await loadTaskBoard(true);
      }
      state.offline.online = true;
    } catch (err) {
      console.error(err);
      if (err.status !== 401) setSave("err", err.message || "синхронизация не удалась");
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
  if (state.offline.syncing) parts.push("синхронизация…");
  else if (state.offline.syncLockedByOther) parts.push("синхронизация в другой вкладке");
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

async function renderOfflineSyncDialog(){
  const pendingList = $("offlinePendingList");
  const failedList = $("offlineFailedList");
  const meta = $("offlineSyncMeta");
  if (!pendingList || !failedList || !meta) return;
  const queue = await dataLayer.getQueueItems();
  const failed = await dataLayer.getFailedItems();
  const online = navigator.onLine && state.offline.online !== false;
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
        <button type="button" data-failed-retry="${idx}">Повторить ошибку</button>
        <button type="button" data-failed-remove="${idx}">Удалить ошибочную операцию</button>
      </div>
    </div>`).join("") : '<span class="emptyLine">Ошибок синхронизации нет.</span>';
  renderOfflineDiagnostics(queue, failed);
}

async function openOfflineSyncDialog(){
  const dlg = $("offlineSyncDialog");
  if (!dlg) return;
  await dataLayer.refreshQueueState();
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
  if (e.target?.id === "offlineSyncNow") { await dataLayer.syncQueue(); await renderOfflineSyncDialog(); return; }
  if (e.target?.id === "offlineFailedRetryAll") { await dataLayer.retryAllFailed(); await renderOfflineSyncDialog(); return; }
  if (e.target?.id === "offlineExport") { await dataLayer.exportOfflineData(); return; }
  if (e.target?.id === "offlineDiagnosticsCopy") {
    try { await navigator.clipboard.writeText(offlineDiagnosticsReportText()); setSave("saved", "диагностика оффлайна скопирована"); }
    catch (err) { setSave("err", "не удалось скопировать диагностику"); }
    return;
  }
  if (e.target?.id === "offlineFailedClear") { await dataLayer.setFailedItems([]); await renderOfflineSyncDialog(); return; }
  const retry = e.target?.dataset?.failedRetry;
  if (retry != null) { await dataLayer.retryFailed(Number(retry)); await renderOfflineSyncDialog(); return; }
  const remove = e.target?.dataset?.failedRemove;
  if (remove != null) { await dataLayer.removeFailed(Number(remove)); await renderOfflineSyncDialog(); return; }
});

/* ─── Markdown (мини-парсер) ────────────────────────────────── */
function esc(s){ return s.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;"); }
function inlineMd(s){
  return s
    .replace(/`([^`]+)`/g, '<code class="mdc">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>")
    .replace(/~~([^~]+)~~/g, "<del>$1</del>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener" class="mda">$1</a>');
}
function renderMd(src){
  const lines = esc(src).split("\n");
  const out = [];
  let list = null, inCode = false, codeBuf = [];
  const closeList = () => { if (list) { out.push(`</${list}>`); list = null; } };
  for (const raw of lines) {
    if (raw.trim().startsWith("```")) {
      if (inCode) { out.push(`<pre class="mdpre"><code>${codeBuf.join("\n")}</code></pre>`); codeBuf = []; inCode = false; }
      else { closeList(); inCode = true; }
      continue;
    }
    if (inCode) { codeBuf.push(raw); continue; }
    const h = raw.match(/^(#{1,4})\s+(.*)$/);
    if (h) { closeList(); out.push(`<div class="mdh mdh${h[1].length}">${inlineMd(h[2])}</div>`); continue; }
    if (/^(-{3,}|\*{3,})\s*$/.test(raw)) { closeList(); out.push('<hr class="mdhr">'); continue; }
    const q = raw.match(/^>\s?(.*)$/);
    if (q) { closeList(); out.push(`<blockquote class="mdq">${inlineMd(q[1])}</blockquote>`); continue; }
    const task = raw.match(/^[-*]\s+\[( |x|X)\]\s+(.*)$/);
    if (task) {
      if (list !== "ul") { closeList(); out.push('<ul class="mdul">'); list = "ul"; }
      const done = task[1].toLowerCase() === "x";
      out.push(`<li class="mdtask"><span class="mdbox${done ? " on" : ""}">${done ? "✓" : ""}</span><span class="${done ? "mddone" : ""}">${inlineMd(task[2])}</span></li>`);
      continue;
    }
    const ul = raw.match(/^[-*]\s+(.*)$/);
    if (ul) { if (list !== "ul") { closeList(); out.push('<ul class="mdul">'); list = "ul"; } out.push(`<li>${inlineMd(ul[1])}</li>`); continue; }
    const ol = raw.match(/^\d+[.)]\s+(.*)$/);
    if (ol) { if (list !== "ol") { closeList(); out.push('<ol class="mdol">'); list = "ol"; } out.push(`<li>${inlineMd(ol[1])}</li>`); continue; }
    closeList();
    if (raw.trim() === "") continue;
    out.push(`<p class="mdp">${inlineMd(raw)}</p>`);
  }
  if (inCode) out.push(`<pre class="mdpre"><code>${codeBuf.join("\n")}</code></pre>`);
  closeList();
  return out.join("");
}

/* ─── Рендер календаря ──────────────────────────────────────── */
function stOf(k){ const e = state.days[k]; return e ? state.shiftTypes.find(s => s.id === e.shiftTypeId) : null; }

function renderCalendar(){
  $("monthName").textContent = MONTHS[state.m];
  $("yearName").textContent = state.y;

  const grid = $("grid");
  grid.innerHTML = "";
  const first = new Date(state.y, state.m, 1);
  const offset = (first.getDay() + 6) % 7;
  const count = new Date(state.y, state.m + 1, 0).getDate();
  const tk = todayKey();

  for (let i = 0; i < offset; i++) {
    const c = document.createElement("div");
    c.className = "cell empty";
    grid.appendChild(c);
  }
  for (let d = 1; d <= count; d++) {
    const k = keyOf(state.y, state.m, d);
    const st = stOf(k);
    const entry = state.days[k];
    const cell = document.createElement("button");
    cell.className = "cell" + (state.selected === k ? " sel" : "");
    if (st) {
      cell.style.background = st.color + "26";
      if (state.selected !== k) cell.style.borderColor = st.color + "55";
      const bar = document.createElement("div");
      bar.className = "bar"; bar.style.background = st.color;
      cell.appendChild(bar);
    }
    if (entry?.note?.trim()) {
      const ear = document.createElement("div");
      ear.className = "ear";
      ear.style.borderTop = `14px solid ${st ? st.color : "var(--dim)"}`;
      ear.title = "Есть заметка";
      cell.appendChild(ear);
    }
    if ((entry?.dayEmoji || "").trim()) {
      const em = document.createElement("span");
      em.className = "dayEmoji";
      em.textContent = entry.dayEmoji;
      em.title = "Маркер дня";
      cell.appendChild(em);
    }
    const num = document.createElement("span");
    num.className = "num" + (k === tk ? " today" : "");
    num.textContent = d;
    cell.appendChild(num);
    if (st) {
      const nm = document.createElement("span");
      nm.className = "shift"; nm.style.color = st.color; nm.textContent = st.name;
      cell.appendChild(nm);
    }
    const ledgerBal = ledgerNetOf(k);
    const legacyBal = numOr0(entry?.overtimeHours) - numOr0(entry?.timeOffHours);
    const bal = Math.abs(ledgerBal) > 0.0001 ? ledgerBal : legacyBal;
    if (Math.abs(bal) > 0.0001) {
      const ot = document.createElement("span");
      ot.className = "otMark";
      ot.textContent = `${bal > 0 ? "+" : ""}${fmtHours(bal)}ч`;
      cell.appendChild(ot);
    }

    const important = importantOf(k);
    const activeTasks = activeTasksOf(k);
    const allTasks = tasksOf(k);
    const reminders = remindersOf(k);
    if (important.length || activeTasks.length || allTasks.length || reminders.length) {
      const marks = document.createElement("span");
      marks.className = "miniMarks";
      if (important.length) {
        const im = document.createElement("span");
        im.className = "importantMark";
        im.textContent = "★";
        im.style.background = important[0].color || "var(--accent)";
        im.title = important.map(x => x.title).join(", ");
        marks.appendChild(im);
      }
      const overdueTasks = overdueTasksOf(k);
      if (overdueTasks.length) {
        const tm = document.createElement("span");
        tm.className = "taskMark overdue";
        tm.textContent = "!!";
        tm.title = `Просроченные задачи: ${overdueTasks.length}`;
        marks.appendChild(tm);
      } else if (activeTasks.length) {
        const tm = document.createElement("span");
        tm.className = "taskMark";
        tm.textContent = "!";
        tm.title = `Невыполненные задачи: ${activeTasks.length}`;
        marks.appendChild(tm);
      } else if (allTasks.length) {
        const tm = document.createElement("span");
        tm.className = "taskMark done";
        tm.textContent = "✓";
        tm.title = "Все задачи выполнены";
        marks.appendChild(tm);
      }
      if (reminders.length) {
        const nm = document.createElement("span");
        nm.className = "notifyMark";
        nm.textContent = "🔔";
        nm.title = `Напоминания: ${reminders.length}`;
        marks.appendChild(nm);
      }
      cell.appendChild(marks);
    }
    cell.addEventListener("click", () => selectDay(state.selected === k ? null : k));
    grid.appendChild(cell);
  }
  renderSummary();
}

function renderSummary(){
  const counts = {}; let hours = 0, overtime = 0, timeOff = 0;
  for (const [k, v] of Object.entries(state.days)) {
    overtime += numOr0(v.overtimeHours);
    timeOff += numOr0(v.timeOffHours);
    if (!v.shiftTypeId) continue;
    counts[v.shiftTypeId] = (counts[v.shiftTypeId] || 0) + 1;
    const st = state.shiftTypes.find(s => s.id === v.shiftTypeId);
    if (st) hours += shiftPlannedHours(st);
  }
  const el = $("summary");
  el.innerHTML = '<span class="lbl">Итого:</span>';
  let any = Math.abs(overtime) > 0.0001 || Math.abs(timeOff) > 0.0001;
  for (const s of state.shiftTypes) {
    if (!counts[s.id]) continue;
    any = true;
    const span = document.createElement("span");
    span.innerHTML = `<span class="dot" style="background:${s.color}"></span>${esc(s.name)} — <b>${counts[s.id]}</b>`;
    el.appendChild(span);
  }
  const balance = overtime - timeOff;
  if (Math.abs(overtime) > 0.0001 || Math.abs(timeOff) > 0.0001) {
    const o = document.createElement("span");
    o.className = "over";
    o.textContent = `переработка: ${balance > 0 ? "+" : ""}${fmtHours(balance)} ч`;
    o.title = `Начислено: ${fmtHours(overtime)} ч, списано: ${fmtHours(timeOff)} ч`;
    el.appendChild(o);
  }
  if (hours > 0) {
    const h = document.createElement("span");
    h.className = "hrs"; h.textContent = `${fmtHours(hours)} ч`;
    el.appendChild(h);
  }
  const acc = state.overtimeAccount;
  if (acc && (numOr0(acc.totalEarnedHours) > 0 || numOr0(acc.totalUsedHours) > 0)) {
    const global = document.createElement("span");
    global.className = "over";
    global.textContent = `общий остаток переработки: ${numOr0(acc.balanceHours) > 0 ? "+" : ""}${fmtHours(acc.balanceHours)} ч`;
    global.title = `Всего начислено: ${fmtHours(acc.totalEarnedHours)} ч, всего списано: ${fmtHours(acc.totalUsedHours)} ч`;
    el.appendChild(global);
    any = true;
  }
  if (!any) {
    const s = document.createElement("span");
    s.style.color = "var(--dim)"; s.textContent = "Смены ещё не отмечены. Выберите день в календаре.";
    el.appendChild(s);
  }
  renderLedgerTable();
}

/* ─── Панель дня ────────────────────────────────────────────── */
function selectDay(k){
  state.selected = k;
  $("layout").classList.toggle("with-panel", !!k);
  document.body.classList.toggle("panel-open", !!k);
  $("panel").hidden = !k;
  if (k) {
    const [y, m, d] = k.split("-").map(Number);
    const date = new Date(y, m - 1, d);
    $("pWeekday").textContent = WEEKDAYS[(date.getDay() + 6) % 7];
    $("pDate").innerHTML = `${d} ${MONTHS_GEN[m - 1]} <span class="yr mono">${y}</span>`;
    $("noteEdit").value = state.days[k]?.note || "";
    resetOvertimeForms(k);
    setTab("edit");
    renderChips();
    renderDayEmojiControls();
    renderScheduleControls();
    renderOvertimeControls();
    renderImportantDays();
    renderTasks();
  }
  renderCalendar();
}


function renderScheduleControls(){
  if (!state.selected) return;
  const input = $("tplDays");
  if (!input.dataset.userTouched) input.value = DEFAULT_SCHEDULE_DAYS;

  const tpl = SCHEDULE_TEMPLATES[$("tplPreset").value];
  const names = effectiveTemplateNames(tpl, state.selected);
  const missing = [...new Set(tpl.names)].filter(name => !findShiftByName(name));
  const hint = $("tplHint");
  if (missing.length) {
    hint.textContent = `Не хватает смен: ${missing.join(", ")}. Перезагрузи страницу или создай их вручную.`;
    $("tplApply").disabled = true;
  } else if (tpl.weekly) {
    hint.textContent = `Пятидневка привязана к дням недели: Пн–Пт рабочие, Сб–Вс выходные. От выбранного дня пойдёт так: ${names.join(" → ")}.`;
    $("tplApply").disabled = false;
  } else {
    hint.textContent = `Шаблон от выбранного дня: ${names.join(" → ")}. Заметки не стираются, меняется только тип смены.`;
    $("tplApply").disabled = false;
  }
}

async function applyScheduleTemplate(){
  const k = state.selected;
  if (!k) return;
  await flushPendingSave();

  const tpl = SCHEDULE_TEMPLATES[$("tplPreset").value];
  const names = effectiveTemplateNames(tpl, k);
  const shifts = names.map(findShiftByName);
  if (shifts.some(s => !s)) {
    renderScheduleControls();
    return setSave("err", "не хватает смен для шаблона");
  }

  const count = Number($("tplDays").value);
  if (!Number.isInteger(count) || count < 1 || count > 366) {
    return setSave("err", "количество дней: от 1 до 366");
  }

  setSave("saving");
  try {
    const changed = await api.fillDays({
      startDate: k,
      days: count,
      shiftTypeIds: shifts.map(s => s.id),
      overwriteExistingShift: $("tplOverwrite").checked,
    });

    const prefix = monthPrefix();
    for (const e of changed) {
      if (e.date.startsWith(prefix)) {
        state.days[e.date] = normalizeDay(e);
      }
    }
    setSave("saved");
    renderChips();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

/* ─── Аккордеон панели дня ──────────────────────────────────── */
const ACC_IDS = ["accShift", "accSched", "accOt", "accImp", "accTasks", "accNote"];
const ACC_STORE = "acc-open-v1";

// Восстанавливаем, какие секции пользователь держал открытыми
(function initAccordion(){
  let open = null;
  try { open = JSON.parse(localStorage.getItem(ACC_STORE)); } catch (e) { /* битые данные — дефолт */ }
  if (Array.isArray(open)) {
    for (const id of ACC_IDS) $(id).open = open.includes(id);
  }
  for (const id of ACC_IDS) {
    $(id).addEventListener("toggle", () => {
      localStorage.setItem(ACC_STORE, JSON.stringify(ACC_IDS.filter(i => $(i).open)));
      if (id === "accSched" && $(id).open) renderScheduleControls();
    });
  }
})();

/* Выжимки в заголовках свёрнутых секций — панель читается не раскрывая */
function updateAccSummaries(){
  const k = state.selected;
  if (!k) return;

  // Смена
  const st = state.shiftTypes.find(s => s.id === state.days[k]?.shiftTypeId);
  $("sumShift").innerHTML = st
    ? `<span class="dot" style="background:${st.color}"></span><span style="color:${st.color}">${esc(st.name)}${shiftPlannedHours(st) ? " · " + fmtHours(shiftPlannedHours(st)) + "ч" : ""}</span>`
    : "не отмечена";

  // График — какой шаблон сейчас выбран
  const tpl = SCHEDULE_TEMPLATES[$("tplPreset").value];
  $("sumSched").textContent = tpl ? tpl.label : "";

  // Переработка: движение за день, иначе общий баланс
  const dayNet = ledgerNetOf(k);
  const bal = numOr0(state.overtimeAccount?.balanceHours);
  $("sumOt").textContent = Math.abs(dayNet) > 0.001
    ? `за день ${dayNet > 0 ? "+" : ""}${fmtHours(dayNet)} ч`
    : `баланс ${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
  $("sumOt").style.color = Math.abs(dayNet) > 0.001 ? "var(--accent)" : "";

  // Важные дни
  const imp = importantOf(k);
  $("sumImp").innerHTML = imp.length
    ? `<span style="color:var(--accent)">★ ${imp.length}</span>&nbsp;${esc(imp[0].title)}${imp.length > 1 ? "…" : ""}`
    : "—";

  // Задачи: сделано/всего
  const all = tasksOf(k);
  const undone = activeTasksOf(k).length;
  const overdue = overdueTasksOf(k).length;
  $("sumTasks").textContent = all.length ? (overdue ? `${overdue} просроч. · ${all.length - undone}/${all.length}` : `${all.length - undone}/${all.length} сделано`) : "—";
  $("sumTasks").style.color = overdue ? "var(--danger)" : (undone > 0 ? "var(--accent)" : "");

  // Emoji-маркер
  const emoji = (state.days[k]?.dayEmoji || "").trim();
  if ($("sumEmoji")) $("sumEmoji").textContent = emoji || "—";

  // Заметка: первая строка
  const note = (state.days[k]?.note || "").trim();
  const firstLine = note.split("\n")[0].replace(/^#+\s*/, "");
  $("sumNote").textContent = note
    ? (firstLine.length > 34 ? firstLine.slice(0, 34) + "…" : firstLine)
    : "—";
}

$("tplPreset").addEventListener("change", renderScheduleControls);
$("tplDays").addEventListener("input", () => { $("tplDays").dataset.userTouched = "1"; });
$("tplApply").addEventListener("click", applyScheduleTemplate);

/* ─── Журнал переработки и отгулов ─────────────────────────── */
function updateOvertimeBalanceLabel(){
  const acc = state.overtimeAccount || { balanceHours:0 };
  const bal = numOr0(acc.balanceHours);
  $("otBalance").textContent = `доступно ${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
  $("ledgerBalance").textContent = `${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
}

function renderOvertimeControls(){
  updateOvertimeBalanceLabel();
  renderOvertimeDayDetails();
  renderQuickScenarioContext();
  updateAccSummaries();
}

function toDateTimeLocal(value){
  return value ? String(value).slice(0, 16) : "";
}

function resetOvertimeForms(k = state.selected){
  state.editingCreditId = null;
  state.editingUsageId = null;
  if ($("creditDate")) $("creditDate").value = k || todayKey();
  if ($("creditTimeRange")) $("creditTimeRange").value = "";
  if ($("creditStart")) $("creditStart").value = "";
  if ($("creditEnd")) $("creditEnd").value = "";
  if ($("creditBreak")) $("creditBreak").value = "0";
  if ($("creditPlanned")) $("creditPlanned").value = "0";
  if ($("creditHours")) $("creditHours").value = "0";
  if ($("creditReason")) $("creditReason").value = "";
  if ($("creditCalcHint")) $("creditCalcHint").textContent = "можно вручную";
  clearScenarioHighlight();
  if ($("creditEditNotice")) { $("creditEditNotice").hidden = true; $("creditEditNotice").textContent = ""; }
  if ($("creditCancel")) $("creditCancel").hidden = true;
  if ($("creditAdd")) $("creditAdd").textContent = "Начислить";

  if ($("usageDate")) $("usageDate").value = k || todayKey();
  if ($("usageHours")) $("usageHours").value = "0";
  if ($("usageReason")) $("usageReason").value = "";
  if ($("usageEditNotice")) { $("usageEditNotice").hidden = true; $("usageEditNotice").textContent = ""; }
  if ($("usageCancel")) $("usageCancel").hidden = true;
  if ($("usageAdd")) $("usageAdd").textContent = "Списать отгул";
}

function cancelCreditEdit(){
  const k = state.selected || ($("creditDate")?.value || todayKey());
  resetOvertimeForms(k);
}

function cancelUsageEdit(){
  const creditId = state.editingCreditId;
  const k = state.selected || ($("usageDate")?.value || todayKey());
  state.editingUsageId = null;
  if ($("usageDate")) $("usageDate").value = k;
  if ($("usageHours")) $("usageHours").value = "0";
  if ($("usageReason")) $("usageReason").value = "";
  if ($("usageEditNotice")) { $("usageEditNotice").hidden = true; $("usageEditNotice").textContent = ""; }
  if ($("usageCancel")) $("usageCancel").hidden = true;
  if ($("usageAdd")) $("usageAdd").textContent = "Списать отгул";
  state.editingCreditId = creditId;
}

function readHoursInput(id){
  const raw = String($(id).value || "").trim().replace(",", ".");
  if (!raw) return 0;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : NaN;
}

function readIntInput(id){
  const raw = String($(id).value || "").trim().replace(",", ".");
  if (!raw) return 0;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? Math.round(n) : NaN;
}

function renderOvertimeDayDetails(){
  const k = state.selected;
  const el = $("otDayDetails");
  if (!k || !el) return;
  const credits = creditsOf(k);
  const usages = usagesOf(k);
  if (!credits.length && !usages.length) {
    el.textContent = "На этот день в журнале переработок записей нет. Начисления не сгорают при переходе между месяцами.";
    return;
  }
  const parts = [];
  for (const c of credits) {
    const calc = c.calculated ? `; обед ${c.breakMinutes || 0} мин${numOr0(c.plannedHours) ? "; вычтено плана " + fmtHours(c.plannedHours) + " ч" : ""}` : "";
    parts.push(`+${fmtHours(c.hours)} ч${c.timeRange ? " (" + c.timeRange + ")" : ""}${calc}${c.reason ? " — " + c.reason : ""}; остаток: ${fmtHours(c.remainingHours)} ч`);
  }
  for (const u of usages) {
    const from = (u.allocations || []).map(a => `${fmtHours(a.hours)} ч с ${a.workedDate}`).join(", ");
    parts.push(`-${fmtHours(u.hours)} ч${u.reason ? " — " + u.reason : ""}${from ? "; взято: " + from : ""}`);
  }
  el.textContent = parts.join(" | ");
}

function parseShortTimePart(raw){
  const t = String(raw || "").trim();
  const m = t.match(/^(\d{1,2})(?:[:.](\d{1,2}))?$/);
  if (!m) return null;
  const hh = Number(m[1]);
  const mm = m[2] == null ? 0 : Number(m[2]);
  if (!Number.isInteger(hh) || !Number.isInteger(mm) || hh < 0 || hh > 23 || mm < 0 || mm > 59) return null;
  return `${pad(hh)}:${pad(mm)}`;
}

function parseManualTimeRange(text){
  const raw = String(text || "").trim();
  if (!raw) return null;
  const normalized = raw.replace(/[—–−]/g, "-").replace(/\s+/g, "");
  const parts = normalized.split("-");
  if (parts.length !== 2) return null;
  const from = parseShortTimePart(parts[0]);
  const to = parseShortTimePart(parts[1]);
  if (!from || !to) return null;
  return { from, to };
}

function calcOvertimeInterval(startValue, endValue, sourceLabel){
  const start = new Date(startValue);
  const end = new Date(endValue);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
    $("creditCalcHint").textContent = "конец должен быть позже";
    return null;
  }
  const breakMinutes = readIntInput("creditBreak");
  const plannedHours = readHoursInput("creditPlanned");
  if (!Number.isFinite(breakMinutes) || !Number.isFinite(plannedHours)) {
    $("creditCalcHint").textContent = "проверь обед/план";
    return null;
  }
  const totalMinutes = Math.round((end.getTime() - start.getTime()) / 60000);
  const creditedMinutes = totalMinutes - breakMinutes - Math.round(plannedHours * 60);
  const hours = Math.round((creditedMinutes / 60) * 100) / 100;
  if (hours <= 0) {
    $("creditCalcHint").textContent = "итого 0 или меньше";
    return null;
  }
  $("creditHours").value = fmtHours(hours);
  let splitHint = "";
  if (startValue.slice(0, 10) !== endValue.slice(0, 10)) {
    const sameClock = startValue.slice(11, 16) === endValue.slice(11, 16);
    splitHint = sameClock ? "; сервер разобьёт ровные сутки пополам" : "; сервер разобьёт по датам";
  }
  const prefix = sourceLabel ? `${sourceLabel}: ` : "";
  $("creditCalcHint").textContent = `${prefix}${fmtHours(totalMinutes / 60)}ч - ${breakMinutes}м${plannedHours ? " - " + fmtHours(plannedHours) + "ч плана" : ""} = ${fmtHours(hours)}ч${splitHint}`;
  return { startValue, endValue, breakMinutes, plannedHours, hours, timeRange: displayDateTimeRange(startValue, endValue) };
}

function overtimeCalcFromInputs(){
  const startValue = $("creditStart").value;
  const endValue = $("creditEnd").value;
  if (startValue || endValue) {
    if (!startValue || !endValue) {
      $("creditCalcHint").textContent = "нужны начало и конец";
      return null;
    }
    return calcOvertimeInterval(startValue, endValue, "полный интервал");
  }

  const shortRange = parseManualTimeRange($("creditTimeRange")?.value);
  if (shortRange) {
    const base = $("creditDate")?.value || state.selected || todayKey();
    let start = setTimeOnDate(base, shortRange.from);
    let endDate = base;
    if (shortRange.to <= shortRange.from) endDate = dateKeyOffset(base, 1);
    let end = setTimeOnDate(endDate, shortRange.to);
    return calcOvertimeInterval(start, end, "короткий ввод");
  }

  $("creditCalcHint").textContent = "можно вручную";
  return null;
}

function updateOvertimeCalcPreview(){
  overtimeCalcFromInputs();
}

function setNightShiftPreset(){
  const base = $("creditDate")?.value || state.selected;
  if (!base) return;
  $("creditStart").value = setTimeOnDate(base, "20:00");
  $("creditEnd").value = setTimeOnDate(dateKeyOffset(base, 1), "08:00");
  $("creditBreak").value = "60";
  $("creditPlanned").value = "0";
  updateOvertimeCalcPreview();
}

function setNightOvertimePreset(){
  const base = $("creditDate")?.value || state.selected;
  if (!base) return;
  $("creditStart").value = setTimeOnDate(base, "17:00");
  $("creditEnd").value = setTimeOnDate(dateKeyOffset(base, 1), "08:00");
  $("creditBreak").value = "0";
  $("creditPlanned").value = "0";
  updateOvertimeCalcPreview();
}
function selectedCreditBaseDate(){
  return state.selected || $("creditDate")?.value || todayKey();
}

function localDateTimeToDate(value){
  if (!value) return null;
  const [date, time] = value.split("T");
  if (!date || !time) return null;
  const [y, m, d] = date.split("-").map(Number);
  const [hh, mm] = time.split(":").map(Number);
  const dt = new Date(y, m - 1, d, hh, mm || 0);
  return Number.isFinite(dt.getTime()) ? dt : null;
}

function dateToLocalInputValue(dt){
  return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}`;
}

function addMinutesToLocalInput(value, minutes){
  const dt = localDateTimeToDate(value);
  if (!dt) return "";
  dt.setMinutes(dt.getMinutes() + minutes);
  return dateToLocalInputValue(dt);
}

function defaultOvertimeEndAfterShift(base, st){
  // Для дневной смены обычно удобно предлагать окончание в 08:00 следующего дня.
  // Для ночной или неизвестной смены оставляем конец через 2 часа после окончания как безопасную заготовку.
  const name = String(st?.name || "").toLowerCase();
  if (name.includes("днев") || (st?.endTime && st.endTime >= "12:00" && st.endTime <= "23:59")) {
    return setTimeOnDate(dateKeyOffset(base, 1), "08:00");
  }
  return addMinutesToLocalInput(setTimeOnDate(base, st?.endTime || "17:00"), 120);
}

function clearScenarioHighlight(){
  document.querySelectorAll(".scenarioCard.active").forEach(x => x.classList.remove("active"));
}

function highlightScenario(id){
  clearScenarioHighlight();
  if (!id) return;
  const el = document.querySelector(`[data-scenario="${id}"]`);
  if (el) el.classList.add("active");
}

function formatDateHuman(k){
  if (!k) return "—";
  const [y, m, d] = k.split("-").map(Number);
  if (!y || !m || !d) return k;
  return `${pad(d)}.${pad(m)}.${y}`;
}

function quickScenarioRequirements(){
  const st = stOf(state.selected);
  return {
    hasSelected: !!state.selected,
    hasShift: !!st,
    hasEnd: !!(st && st.endTime),
    hasStart: !!(st && st.startTime),
    hasFullTime: !!(st && st.startTime && st.endTime),
    shift: st
  };
}

function scenarioNeeds(sc){
  const needsStart = sc.startMode === "SHIFT_START";
  const needsEnd = sc.startMode === "SHIFT_END" || sc.endMode === "SHIFT_END";
  return { needsStart, needsEnd };
}

function scenarioAvailable(sc){
  const r = quickScenarioRequirements();
  if (!r.hasSelected || !r.hasShift) return false;
  const needs = scenarioNeeds(sc);
  if (needs.needsStart && !r.hasStart) return false;
  if (needs.needsEnd && !r.hasEnd) return false;
  if (sc.endMode === "FIXED_TIME" && !sc.endFixedTime) return false;
  return true;
}

function renderQuickScenarios(){
  const grid = $("scenarioGrid");
  if (!grid) return;
  const scenarios = (state.quickScenarios || []).slice().sort((a,b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0));
  if (!scenarios.length) {
    grid.innerHTML = `<div class="emptyLine">Сценарии пока не созданы. Добавьте первый сценарий в настройках.</div>`;
    return;
  }
  grid.innerHTML = scenarios.map(sc => {
    const disabled = !scenarioAvailable(sc);
    const active = String(state.activeScenarioId || "") === String(sc.id || "");
    return `<div class="scenarioWrap">
      <button type="button" class="scenarioCard ${active ? "active" : ""}" data-scenario-id="${sc.id}" ${disabled ? "disabled" : ""}>
        <span>${esc(sc.groupLabel || "сценарий")}</span>
        <b>${esc(sc.name || "Сценарий")}</b>
        <small>${esc(sc.description || scenarioHumanDescription(sc))}</small>
      </button>
      <button type="button" class="scenarioDel" data-scenario-del="${sc.id}" title="удалить сценарий">×</button>
    </div>`;
  }).join("");
}

function scenarioHumanDescription(sc){
  const start = sc.startMode === "SHIFT_START" ? "от начала смены" : "от конца смены";
  let end = "";
  if (sc.endMode === "ADD_MINUTES") end = `+${sc.endOffsetMinutes || 0} мин`;
  else if (sc.endMode === "FIXED_TIME") end = `${sc.endFixedTime || "--:--"}${sc.endNextDay ? " на следующий день" : ""}`;
  else end = "до конца смены";
  return `${start} → ${end}`;
}

function renderQuickScenarioContext(){
  const ctx = $("quickScenarioContext");
  const tips = $("quickScenarioTips");
  if (!ctx || !tips) return;
  const r = quickScenarioRequirements();

  if (!r.hasSelected) {
    ctx.textContent = "День не выбран. Сначала ткни дату в календаре.";
    tips.textContent = "Карточки разблокируются, когда у выбранного дня будет смена со временем.";
    renderQuickScenarios();
    return;
  }
  if (!r.hasShift) {
    ctx.textContent = `${formatDateHuman(state.selected)} · смена не выбрана`;
    tips.textContent = "Поставь дневную, ночную или кастомную смену — тогда сценарии смогут взять время начала/конца.";
    renderQuickScenarios();
    return;
  }

  const st = r.shift;
  const time = st.startTime && st.endTime ? `${st.startTime}–${st.endTime}` : "время не настроено";
  const plan = shiftPlannedHours(st) ? `план ${fmtHours(shiftPlannedHours(st))} ч` : "план 0 ч";
  const br = st.breakMinutes ? `обед ${st.breakMinutes} мин` : "обед 0 мин";
  ctx.textContent = `${formatDateHuman(state.selected)} · ${st.name}: ${time}, ${plan}, ${br}`;
  if (!r.hasEnd) {
    tips.textContent = "У смены не указано время окончания. Откройте настройки смены и задайте время.";
  } else if (!r.hasFullTime) {
    tips.textContent = "Доступны сценарии от конца смены. Для сценариев от начала смены укажите время начала.";
  } else {
    tips.textContent = "Карточки только заполняют поля. Перед начислением можно поправить время, обед, план и причину.";
  }
  renderQuickScenarios();
}

function fillCreditScenario({start, end, breakMinutes = 0, plannedHours = 0, reason = "", hint = "сценарий"}){
  if (!start || !end) return setSave("err", "не получилось собрать интервал сценария");
  $("creditDate").value = start.slice(0, 10);
  $("creditTimeRange").value = "";
  $("creditStart").value = start;
  $("creditEnd").value = end;
  $("creditBreak").value = String(breakMinutes || 0);
  $("creditPlanned").value = fmtHours(plannedHours || 0);
  if (reason && !$("creditReason").value.trim()) $("creditReason").value = reason;
  updateOvertimeCalcPreview();
  setSave("saved", hint);
}

function currentSelectedShiftOrError(){
  const st = stOf(state.selected);
  if (!state.selected) { setSave("err", "сначала выбери день в календаре"); return null; }
  if (!st) { setSave("err", "на выбранном дне нет смены"); return null; }
  return st;
}

function shiftEndDateKey(base, st){
  if (!st?.endTime) return base;
  if (st.startTime && st.endTime <= st.startTime) return dateKeyOffset(base, 1);
  return base;
}

function scenarioStartValue(base, st, sc){
  if (sc.startMode === "SHIFT_START") {
    if (!st.startTime) return null;
    return setTimeOnDate(base, st.startTime);
  }
  if (!st.endTime) return null;
  return setTimeOnDate(shiftEndDateKey(base, st), st.endTime);
}

function scenarioEndValue(base, st, sc, start){
  if (sc.endMode === "SHIFT_END") {
    if (!st.endTime) return null;
    return setTimeOnDate(shiftEndDateKey(base, st), st.endTime);
  }
  if (sc.endMode === "ADD_MINUTES") {
    return addMinutesToLocalInput(start, Number(sc.endOffsetMinutes || 0));
  }
  if (sc.endMode === "FIXED_TIME") {
    if (!sc.endFixedTime) return null;
    return setTimeOnDate(dateKeyOffset(base, sc.endNextDay ? 1 : 0), sc.endFixedTime);
  }
  return null;
}

function scenarioBreakMinutes(st, sc){
  if (sc.breakMode === "SHIFT") return numOr0(st.breakMinutes);
  if (sc.breakMode === "CUSTOM") return numOr0(sc.customBreakMinutes);
  return 0;
}

function scenarioPlannedHours(st, sc){
  if (sc.plannedMode === "SHIFT") return shiftPlannedHours(st);
  if (sc.plannedMode === "CUSTOM") return numOr0(sc.customPlannedHours);
  return 0;
}

function applyQuickScenario(sc){
  state.activeScenarioId = sc.id;
  renderQuickScenarios();
  const st = currentSelectedShiftOrError();
  if (!st) return;
  const base = selectedCreditBaseDate();
  const start = scenarioStartValue(base, st, sc);
  if (!start) return setSave("err", "сценарию не хватает времени начала/конца смены");
  const end = scenarioEndValue(base, st, sc, start);
  if (!end) return setSave("err", "не получилось определить конец сценария");
  const sdt = localDateTimeToDate(start), edt = localDateTimeToDate(end);
  if (!sdt || !edt || edt <= sdt) return setSave("err", "конец сценария должен быть позже начала");
  fillCreditScenario({
    start,
    end,
    breakMinutes: scenarioBreakMinutes(st, sc),
    plannedHours: scenarioPlannedHours(st, sc),
    reason: sc.reasonTemplate || sc.name || "сценарий переработки",
    hint: sc.name || "сценарий"
  });
}

function resetScenarioEditor(){
  if (!$("scName")) return;
  $("scName").value = "";
  $("scGroup").value = "";
  $("scDesc").value = "";
  $("scStartMode").value = "SHIFT_END";
  $("scEndMode").value = "ADD_MINUTES";
  $("scEndOffset").value = "120";
  $("scEndFixed").value = "08:00";
  $("scEndNextDay").checked = false;
  $("scBreakMode").value = "ZERO";
  $("scBreakCustom").value = "0";
  $("scPlannedMode").value = "ZERO";
  $("scPlannedCustom").value = "0";
  $("scReason").value = "";
}

function buildScenarioPayload(){
  const name = $("scName").value.trim();
  if (!name) { setSave("err", "назови сценарий"); return null; }
  return {
    name,
    groupLabel: $("scGroup").value.trim() || null,
    description: $("scDesc").value.trim() || null,
    startMode: $("scStartMode").value,
    endMode: $("scEndMode").value,
    endOffsetMinutes: Number($("scEndOffset").value || 0),
    endFixedTime: $("scEndFixed").value || null,
    endNextDay: $("scEndNextDay").checked,
    breakMode: $("scBreakMode").value,
    customBreakMinutes: Number($("scBreakCustom").value || 0),
    plannedMode: $("scPlannedMode").value,
    customPlannedHours: Number($("scPlannedCustom").value || 0),
    reasonTemplate: $("scReason").value.trim() || name,
    sortOrder: 100 + (state.quickScenarios || []).length * 10
  };
}

async function saveQuickScenario(){
  const payload = buildScenarioPayload();
  if (!payload) return;
  setSave("saving");
  try {
    await api.createQuickScenario(payload);
    state.quickScenarios = await api.quickScenarios();
    resetScenarioEditor();
    renderQuickScenarioContext();
    setSave("saved", "сценарий добавлен");
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function deleteQuickScenario(id){
  if (!confirm("Удалить быстрый сценарий?")) return;
  setSave("saving");
  try {
    await api.deleteQuickScenario(id);
    state.quickScenarios = await api.quickScenarios();
    if (String(state.activeScenarioId) === String(id)) state.activeScenarioId = null;
    renderQuickScenarioContext();
    setSave("saved");
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}


function buildCreditPayload(){
  const calc = overtimeCalcFromInputs();
  if (!calc && ($("creditStart").value || $("creditEnd").value)) {
    setSave("err", "для автоподсчёта нужны и начало, и конец");
    return null;
  }
  const hours = calc ? calc.hours : readHoursInput("creditHours");
  if (!Number.isFinite(hours) || hours <= 0) {
    setSave("err", "укажи часы переработки больше 0");
    return null;
  }
  const manualDate = $("creditDate")?.value || state.selected;
  if (!calc && !manualDate) {
    setSave("err", "укажи дату переработки");
    return null;
  }
  const payload = {
    date: calc ? calc.startValue.slice(0, 10) : manualDate,
    timeRange: calc ? calc.timeRange : ($("creditTimeRange")?.value.trim() || null),
    hours,
    reason: $("creditReason").value.trim() || null,
  };
  if (calc) {
    payload.startDateTime = calc.startValue;
    payload.endDateTime = calc.endValue;
    payload.breakMinutes = calc.breakMinutes;
    payload.plannedHours = calc.plannedHours;
  } else {
    payload.startDateTime = "";
    payload.endDateTime = "";
    payload.breakMinutes = 0;
    payload.plannedHours = 0;
  }
  return payload;
}

async function addOvertimeCredit(){
  const payload = buildCreditPayload();
  if (!payload) return;
  setSave("saving");
  try {
    if (state.editingCreditId) {
      state.overtimeAccount = await api.updateOvertimeCredit(state.editingCreditId, payload);
    } else {
      state.overtimeAccount = await api.createOvertimeCredit(payload);
    }
    resetOvertimeForms(state.selected || payload.date);
    setSave("saved");
    renderOvertimeControls();
    renderCalendar();
    renderLedgerTable();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function findUsageById(id){
  return (state.overtimeAccount?.usages || []).find(u => Number(u.id) === Number(id)) || null;
}

function startEditOvertimeCredit(id){
  const c = (state.overtimeAccount?.credits || []).find(x => Number(x.id) === Number(id));
  if (!c) return setSave("err", "начисление не найдено");
  if (state.selected !== c.workedDate) selectDay(c.workedDate);
  state.editingCreditId = Number(id);
  $("creditDate").value = c.workedDate;
  $("creditTimeRange").value = c.calculated ? "" : (c.timeRange || "");
  $("creditStart").value = toDateTimeLocal(c.startDateTime);
  $("creditEnd").value = toDateTimeLocal(c.endDateTime);
  $("creditBreak").value = String(c.breakMinutes || 0);
  $("creditPlanned").value = fmtHours(c.plannedHours || 0);
  $("creditHours").value = fmtHours(c.hours);
  $("creditReason").value = c.reason || "";
  $("creditAdd").textContent = "Сохранить";
  $("creditCancel").hidden = false;
  $("creditEditNotice").hidden = false;
  $("creditEditNotice").textContent = `Редактируется начисление #${id}. Если сделать период через несколько дат, сервер заменит строку на несколько начислений.`;
  $("accOt").open = true;
  updateOvertimeCalcPreview();
  $("creditReason").focus();
}

async function addOvertimeUsage(){
  const hours = readHoursInput("usageHours");
  if (!Number.isFinite(hours) || hours <= 0) return setSave("err", "укажи часы списания больше 0");
  const date = $("usageDate")?.value || state.selected;
  if (!date) return setSave("err", "укажи дату списания");
  setSave("saving");
  try {
    const payload = {
      date,
      hours,
      reason: $("usageReason").value.trim() || null,
    };
    if (state.editingUsageId) {
      state.overtimeAccount = await api.updateOvertimeUsage(state.editingUsageId, payload);
    } else {
      state.overtimeAccount = await api.createOvertimeUsage(payload);
    }
    resetOvertimeForms(state.selected || date);
    setSave("saved");
    renderOvertimeControls();
    renderCalendar();
    renderLedgerTable();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function startEditOvertimeUsage(id){
  const u = findUsageById(id);
  if (!u) return setSave("err", "списание не найдено");
  if (state.selected !== u.usageDate) selectDay(u.usageDate);
  state.editingUsageId = Number(id);
  $("usageDate").value = u.usageDate;
  $("usageHours").value = fmtHours(u.hours);
  $("usageReason").value = u.reason || "";
  $("usageAdd").textContent = "Сохранить";
  $("usageCancel").hidden = false;
  $("usageEditNotice").hidden = false;
  $("usageEditNotice").textContent = `Редактируется списание #${id}. Если изменить часы, FIFO-распределение пересоберётся заново.`;
  $("accOt").open = true;
  $("usageReason").focus();
}

async function removeOvertimeCredit(id){
  const credit = (state.overtimeAccount?.credits || []).find(c => Number(c.id) === Number(id));
  const label = credit ? `${credit.workedDate} ${credit.timeRange || ""} +${fmtHours(credit.hours)} ч` : `#${id}`;
  if (!confirm(`Удалить начисление переработки ${label}?\n\nЭто действие нельзя отменить.`)) return;
  setSave("saving");
  try {
    state.overtimeAccount = await api.deleteOvertimeCredit(id);
    if (state.editingCreditId === Number(id)) resetOvertimeForms(state.selected);
    setSave("saved");
    renderOvertimeControls();
    renderCalendar();
    renderLedgerTable();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function removeOvertimeUsage(id){
  let usage = findUsageById(id);
  const label = usage ? `${usage.usageDate} -${fmtHours(usage.hours)} ч${usage.reason ? " — " + usage.reason : ""}` : `#${id}`;
  if (!confirm(`Удалить списание отгула ${label}?\n\nЧасы вернутся в остаток переработки.`)) return;
  setSave("saving");
  try {
    state.overtimeAccount = await api.deleteOvertimeUsage(id);
    if (state.editingUsageId === Number(id)) cancelUsageEdit();
    setSave("saved");
    renderOvertimeControls();
    renderCalendar();
    renderLedgerTable();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function currentMonthRange(){
  const from = keyOf(state.y, state.m, 1);
  const to = keyOf(state.y, state.m, new Date(state.y, state.m + 1, 0).getDate());
  return { from, to };
}

function creditStatus(c){
  const remaining = numOr0(c.remainingHours);
  const used = numOr0(c.usedHours);
  if (remaining <= 0.0001) return "closed";
  if (used > 0.0001) return "partial";
  return "open";
}

function statusLabel(status){
  if (status === "closed") return "списано";
  if (status === "partial") return "частично";
  return "остаток";
}

function creditSearchHaystack(c){
  const usages = (c.usages || []).map(u => `${u.usageDate} ${u.hours} ${u.reason || ""}`).join(" " );
  return `${c.workedDate} ${c.timeRange || ""} ${c.hours} ${c.reason || ""} ${usages}`.toLowerCase();
}

function getFilteredLedgerCredits(){
  const acc = state.overtimeAccount || { credits:[] };
  const f = state.ledgerFilters || { from:"", to:"", status:"all", q:"" };
  const q = String(f.q || "").trim().toLowerCase();
  return (acc.credits || []).filter(c => {
    if (f.from && c.workedDate < f.from) return false;
    if (f.to && c.workedDate > f.to) return false;
    const st = creditStatus(c);
    if (f.status === "open" && st === "closed") return false;
    if (f.status === "partial" && st !== "partial") return false;
    if (f.status === "closed" && st !== "closed") return false;
    if (q && !creditSearchHaystack(c).includes(q)) return false;
    return true;
  });
}

function syncLedgerFilterInputs(){
  const f = state.ledgerFilters || {};
  if ($("ledgerFrom")) $("ledgerFrom").value = f.from || "";
  if ($("ledgerTo")) $("ledgerTo").value = f.to || "";
  if ($("ledgerStatus")) $("ledgerStatus").value = f.status || "all";
  if ($("ledgerSearch")) $("ledgerSearch").value = f.q || "";
}

function renderLedgerTable(){
  const tbody = $("ledgerRows");
  const balanceEl = $("ledgerBalance");
  const statsEl = $("ledgerStats");
  if (!tbody || !balanceEl) return;

  const acc = state.overtimeAccount || { credits:[], balanceHours:0 };
  const bal = numOr0(acc.balanceHours);
  balanceEl.textContent = `${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
  tbody.innerHTML = "";
  syncLedgerFilterInputs();

  const allCredits = acc.credits || [];
  const credits = getFilteredLedgerCredits();
  const totalEarned = credits.reduce((sum, c) => sum + numOr0(c.hours), 0);
  const totalUsed = credits.reduce((sum, c) => sum + numOr0(c.usedHours), 0);
  const totalRemain = credits.reduce((sum, c) => sum + numOr0(c.remainingHours), 0);
  const openCount = credits.filter(c => numOr0(c.remainingHours) > 0.0001).length;
  const closedCount = credits.filter(c => numOr0(c.remainingHours) <= 0.0001).length;
  if (statsEl) {
    statsEl.innerHTML = `
      <span class="pill">показано: <b>${credits.length}</b> из <b>${allCredits.length}</b></span>
      <span class="pill">начислено: <b>+${fmtHours(totalEarned)} ч</b></span>
      <span class="pill">использовано: <b>${fmtHours(totalUsed)} ч</b></span>
      <span class="pill">остаток в таблице: <b>${fmtHours(totalRemain)} ч</b></span>
      <span class="pill">с остатком: <b>${openCount}</b></span>
      <span class="pill">закрыто: <b>${closedCount}</b></span>
    `;
  }

  if (!allCredits.length) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 8;
    td.className = "small";
    td.textContent = "Начислений переработки пока нет. Новые записи добавляются из панели выбранного дня и сохраняются до полного списания.";
    tr.appendChild(td);
    tbody.appendChild(tr);
    return;
  }

  if (!credits.length) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 8;
    td.className = "small";
    td.textContent = "По текущим фильтрам записей нет. Сбрось фильтры или выбери другой период.";
    tr.appendChild(td);
    tbody.appendChild(tr);
    return;
  }

  for (const c of credits) {
    const tr = document.createElement("tr");
    if (state.selected && c.workedDate === state.selected) tr.style.background = "rgba(245,184,65,.06)";
    const status = creditStatus(c);
    const usedText = (c.usages || []).length
      ? (c.usages || []).map(u => `${esc(u.usageDate)}: ${fmtHours(u.hours)} ч${u.reason ? " — " + esc(u.reason) : ""} · <button type="button" data-edit-usage="${u.usageId}">ред.</button> · <button type="button" data-del-usage="${u.usageId}">удалить</button>`).join("<br>")
      : '<span class="small">не списывалось</span>';
    const calcInfo = c.calculated ? `<div class="small">обед: ${c.breakMinutes || 0} мин${numOr0(c.plannedHours) ? ` · план: ${fmtHours(c.plannedHours)} ч` : ""}</div>` : "";
    const deleteBtn = numOr0(c.usedHours) <= 0.0001
      ? `<button type="button" data-del-credit="${c.id}">удалить</button>`
      : `<span class="small" title="Сначала удали списания, которые используют это начисление">сначала списания</span>`;
    tr.innerHTML = `
      <td class="mono">${esc(c.workedDate)}<div class="ledgerStatus ${status}">${statusLabel(status)}</div></td>
      <td>${esc(c.timeRange || "—")}${calcInfo}</td>
      <td class="numc">+${fmtHours(c.hours)} ч</td>
      <td class="reason">${esc(c.reason || "—")}</td>
      <td class="numc used">${fmtHours(c.usedHours)} ч</td>
      <td class="small">${usedText}</td>
      <td class="numc remain">${fmtHours(c.remainingHours)} ч</td>
      <td><button type="button" data-edit-credit="${c.id}">ред.</button><br>${deleteBtn}</td>
    `;
    tbody.appendChild(tr);
  }
  tbody.querySelectorAll("[data-edit-credit]").forEach(btn => {
    btn.addEventListener("click", () => startEditOvertimeCredit(Number(btn.dataset.editCredit)));
  });
  tbody.querySelectorAll("[data-del-credit]").forEach(btn => {
    btn.addEventListener("click", () => removeOvertimeCredit(Number(btn.dataset.delCredit)));
  });
  tbody.querySelectorAll("[data-edit-usage]").forEach(btn => {
    btn.addEventListener("click", () => startEditOvertimeUsage(Number(btn.dataset.editUsage)));
  });
  tbody.querySelectorAll("[data-del-usage]").forEach(btn => {
    btn.addEventListener("click", () => removeOvertimeUsage(Number(btn.dataset.delUsage)));
  });
}

function setLedgerThisMonth(){
  const r = currentMonthRange();
  state.ledgerFilters.from = r.from;
  state.ledgerFilters.to = r.to;
  renderLedgerTable();
}
function setLedgerAllTime(){
  state.ledgerFilters.from = "";
  state.ledgerFilters.to = "";
  renderLedgerTable();
}
function clearLedgerFilters(){
  state.ledgerFilters = { from:"", to:"", status:"all", q:"" };
  renderLedgerTable();
}

function ledgerExportUrl(ext){
  const f = state.ledgerFilters || {};
  const p = new URLSearchParams();
  if (f.from) p.set("from", f.from);
  if (f.to) p.set("to", f.to);
  if (f.status && f.status !== "all") p.set("status", f.status);
  if (f.q && f.q.trim()) p.set("q", f.q.trim());
  const qs = p.toString();
  return `/api/overtime/export.${ext}${qs ? "?" + qs : ""}`;
}
function exportLedger(ext){
  window.location.href = ledgerExportUrl(ext);
}

$("ledgerThisMonth").addEventListener("click", setLedgerThisMonth);
$("ledgerAllTime").addEventListener("click", setLedgerAllTime);
$("ledgerClear").addEventListener("click", clearLedgerFilters);
$("ledgerFrom").addEventListener("input", e => { state.ledgerFilters.from = e.target.value; renderLedgerTable(); });
$("ledgerTo").addEventListener("input", e => { state.ledgerFilters.to = e.target.value; renderLedgerTable(); });
$("ledgerStatus").addEventListener("change", e => { state.ledgerFilters.status = e.target.value; renderLedgerTable(); });
$("ledgerSearch").addEventListener("input", e => { state.ledgerFilters.q = e.target.value; renderLedgerTable(); });
$("ledgerExportCsv").addEventListener("click", () => exportLedger("csv"));
$("ledgerExportXls").addEventListener("click", () => exportLedger("xls"));

$("creditAdd").addEventListener("click", addOvertimeCredit);
$("creditCancel").addEventListener("click", cancelCreditEdit);
$("creditNightShiftPreset").addEventListener("click", setNightShiftPreset);
$("creditNightPreset").addEventListener("click", setNightOvertimePreset);
$("scenarioGrid").addEventListener("click", e => {
  const del = e.target.closest("[data-scenario-del]");
  if (del) {
    e.stopPropagation();
    deleteQuickScenario(del.dataset.scenarioDel);
    return;
  }
  const btn = e.target.closest("[data-scenario-id]");
  if (!btn || btn.disabled) return;
  const sc = (state.quickScenarios || []).find(x => String(x.id) === String(btn.dataset.scenarioId));
  if (sc) applyQuickScenario(sc);
});
$("quickClearScenario").addEventListener("click", () => resetOvertimeForms(state.selected || $("creditDate")?.value || todayKey()));
$("scSave").addEventListener("click", saveQuickScenario);
$("scReset").addEventListener("click", resetScenarioEditor);
$("creditPlanByShift").addEventListener("click", () => {
  const st = stOf(state.selected);
  const plan = shiftPlannedHours(st);
  if (!st || !plan) return setSave("err", "на этом дне нет смены с плановыми часами");
  $("creditPlanned").value = fmtHours(plan);
  updateOvertimeCalcPreview();
});
$("creditTimeByShift").addEventListener("click", () => {
  const st = stOf(state.selected);
  const base = state.selected || $("creditDate")?.value;
  if (!st || !base || !st.startTime || !st.endTime) return setSave("err", "у выбранной смены не указано время начала/конца");
  $("creditDate").value = base;
  $("creditStart").value = setTimeOnDate(base, st.startTime);
  const endDate = st.endTime <= st.startTime ? dateKeyOffset(base, 1) : base;
  $("creditEnd").value = setTimeOnDate(endDate, st.endTime);
  $("creditBreak").value = String(st.breakMinutes || 0);
  $("creditPlanned").value = fmtHours(shiftPlannedHours(st));
  updateOvertimeCalcPreview();
});
$("creditDateSelected").addEventListener("click", () => {
  if (!state.selected) return setSave("err", "сначала выбери день в календаре");
  $("creditDate").value = state.selected;
  updateOvertimeCalcPreview();
});
$("creditDateToday").addEventListener("click", () => {
  $("creditDate").value = todayKey();
  updateOvertimeCalcPreview();
});
for (const id of ["creditDate", "creditTimeRange", "creditStart", "creditEnd", "creditBreak", "creditPlanned"]) {
  $(id).addEventListener("input", updateOvertimeCalcPreview);
}
$("usageAdd").addEventListener("click", addOvertimeUsage);
$("usageCancel").addEventListener("click", cancelUsageEdit);
$("usageByShift").addEventListener("click", () => {
  const st = stOf(state.selected);
  const plan = shiftPlannedHours(st);
  if (!st || !plan) return setSave("err", "на этом дне нет смены с плановыми часами для списания");
  $("usageHours").value = fmtHours(plan);
});
for (const id of ["creditDate", "creditTimeRange", "creditStart", "creditEnd", "creditBreak", "creditPlanned", "creditHours", "creditReason", "usageDate", "usageHours", "usageReason"]) {
  $(id).addEventListener("keydown", e => {
    if (e.key !== "Enter") return;
    if (id.startsWith("credit")) addOvertimeCredit();
    else addOvertimeUsage();
  });
}

/* ─── Важные дни ───────────────────────────────────────────── */
async function refreshImportantSettings(){
  try {
    state.importantDays = await api.importantDays();
    renderImportantSettings();
  } catch (err) {
    console.error(err);
  }
}

function renderImportantSettings(){
  const box = document.getElementById("importantSettingsList");
  if (!box) return;
  const items = (state.importantDays || []).slice().sort((a,b) => String(a.date).localeCompare(String(b.date)) || String(a.title).localeCompare(String(b.title), "ru"));
  box.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = "Важных дат пока нет.";
    box.appendChild(empty);
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "importantItem settingsImportantItem";
    const dot = document.createElement("span");
    dot.className = "importantDot";
    dot.style.background = item.color || "var(--accent)";
    const title = document.createElement("span");
    title.className = "importantTitle";
    title.textContent = item.title;
    const date = document.createElement("span");
    date.className = "importantMode mono";
    date.textContent = (item.date || "").split("-").reverse().join(".");
    const mode = document.createElement("span");
    mode.className = "importantMode";
    mode.textContent = repeatLabel(item.repeatMode);
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = "удалить";
    del.title = "Удалить важный день полностью, включая повторения";
    del.addEventListener("click", () => removeImportantDay(item.id));
    row.append(dot, title, date, mode, del);
    box.appendChild(row);
  }
}

function renderImportantDays(){
  const box = $("importantList");
  if (!box || !state.selected) return;
  const items = importantOf(state.selected);
  box.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = "В этот день важных событий нет.";
    box.appendChild(empty);
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "importantItem";
    const dot = document.createElement("span");
    dot.className = "importantDot";
    dot.style.background = item.color || "var(--accent)";
    const title = document.createElement("span");
    title.className = "importantTitle";
    title.textContent = item.title;
    const mode = document.createElement("span");
    mode.className = "importantMode";
    mode.textContent = repeatLabel(item.repeatMode);
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = "удалить";
    del.title = "Удалить важный день полностью, включая повторения";
    del.addEventListener("click", () => removeImportantDay(item.id));
    row.append(dot, title, mode, del);
    box.appendChild(row);
  }
  updateAccSummaries();
}

async function addImportantDay(){
  const k = $("impDate")?.value || state.selected;
  if (!k) return setSave("err", "укажи дату важного дня");
  const title = $("impTitle").value.trim();
  if (!title) return setSave("err", "укажи название важного дня");
  setSave("saving");
  try {
    await api.createImportantDay({
      title,
      date: k,
      repeatMode: $("impRepeat").value,
      color: $("impColor").value || "#F5B841",
    });
    $("impTitle").value = "";
    if ($("impDate")) $("impDate").value = k;
    await refreshImportantSettings();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantSettings();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function removeImportantDay(id){
  if (!confirm("Удалить важный день целиком, включая повторения?")) return;
  setSave("saving");
  try {
    await api.deleteImportantDay(id);
    await refreshImportantSettings();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantSettings();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

$("impAdd").addEventListener("click", addImportantDay);
$("impTitle").addEventListener("keydown", e => { if (e.key === "Enter") addImportantDay(); });
$("impDateSelected")?.addEventListener("click", () => { if (!state.selected) return setSave("err", "сначала выбери день в календаре"); $("impDate").value = state.selected; });
$("impDateToday")?.addEventListener("click", () => { $("impDate").value = todayKey(); });

/* ─── Задачи дня ────────────────────────────────────────────── */
function renderTaskCategoryFilter(){
  const sel = $("taskCategoryFilter");
  if (!sel) return;
  const current = sel.value || state.taskFilters.category || "all";
  sel.innerHTML = `<option value="all">все категории</option>`;
  for (const cat of allTaskCategories()) {
    const opt = document.createElement("option");
    opt.value = cat; opt.textContent = cat;
    sel.appendChild(opt);
  }
  sel.value = [...sel.options].some(o => o.value === current) ? current : "all";
  state.taskFilters.category = sel.value;
}
function filteredTasksForSelected(){
  const items = tasksOf(state.selected);
  const status = state.taskFilters.status || "all";
  const category = state.taskFilters.category || "all";
  return items.filter(t => {
    if (category !== "all" && (t.category || "") !== category) return false;
    if (status === "open" && t.done) return false;
    if (status === "done" && !t.done) return false;
    if (status === "overdue" && (!t.overdue || t.done)) return false;
    return true;
  });
}
function renderTasks(){
  const box = $("taskList");
  if (!box || !state.selected) return;
  renderTaskCategoryFilter();
  const all = tasksOf(state.selected);
  const items = filteredTasksForSelected();
  box.innerHTML = "";
  if (!all.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = "Задач пока нет. После добавления открытые задачи будут отмечены в календаре.";
    box.appendChild(empty);
    updateAccSummaries();
    return;
  }
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = "По фильтрам задач нет.";
    box.appendChild(empty);
    updateAccSummaries();
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.className = "taskItem" + (task.done ? " done" : "") + (task.overdue && !task.done ? " overdue" : "");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !!task.done;
    cb.addEventListener("change", () => toggleTask(task.id, cb.checked));
    const body = document.createElement("div");
    body.style.flex = "1";
    body.style.minWidth = "0";
    const text = document.createElement("span");
    text.className = "taskText";
    text.textContent = task.text;
    const meta = document.createElement("div");
    meta.className = "taskMeta";
    if (task.category) {
      const b = document.createElement("span"); b.className = "taskBadge cat"; b.textContent = task.category; meta.appendChild(b);
    }
    if (task.priority && task.priority !== "NORMAL") {
      const b = document.createElement("span"); b.className = "taskBadge " + task.priority.toLowerCase(); b.textContent = taskPriorityLabel(task.priority); meta.appendChild(b);
    }
    const due = taskDueLabel(task);
    if (due) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = due; meta.appendChild(b); }
    if (task.reminderEnabled) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = `🔔 ${task.reminderMinutesBefore ?? 0}м`; meta.appendChild(b); }
    if (task.overdue && !task.done) { const b = document.createElement("span"); b.className = "taskBadge overdue"; b.textContent = "просрочено"; meta.appendChild(b); }
    body.append(text, meta);
    const edit = document.createElement("button");
    edit.className = "tinyDel"; edit.type = "button"; edit.textContent = "ред."; edit.title = "Редактировать задачу";
    edit.addEventListener("click", () => editTask(task));
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = "×";
    del.title = "Удалить задачу";
    del.addEventListener("click", () => removeTask(task.id));
    row.append(cb, body, edit, del);
    box.appendChild(row);
  }
  updateAccSummaries();
}
function editTask(task){
  const text = prompt("Текст задачи", task.text || "");
  if (text === null) return;
  const category = prompt("Категория", task.category || "") ?? task.category;
  const dueDate = prompt("Срок yyyy-MM-dd, пусто — без срока", task.dueDate || "") ?? task.dueDate;
  const dueTime = prompt("Время срока HH:mm, пусто — без времени", task.dueTime || "") ?? task.dueTime;
  const reminder = confirm("Включить напоминание для этой задачи?");
  updateTaskDetails(task.id, { text, category, dueDate, dueTime, reminderEnabled: reminder, reminderMinutesBefore: reminder ? (task.reminderMinutesBefore ?? 60) : null });
}

function removeTaskFromMaps(id){
  for (const k of Object.keys(state.tasksByDate)) {
    state.tasksByDate[k] = state.tasksByDate[k].filter(t => t.id !== id);
    if (!state.tasksByDate[k].length) delete state.tasksByDate[k];
  }
}
function upsertTaskInMaps(task){
  if (!task) return;
  removeTaskFromMaps(task.id);
  addToDateMap(state.tasksByDate, task);
}

async function addTask(){
  const k = state.selected;
  if (!k) return;
  const text = $("taskText").value.trim();
  if (!text) return setSave("err", "напиши текст задачи");
  setSave("saving");
  try {
    const created = await api.createTask({
      date: k,
      text,
      category: $("taskCategory").value.trim() || null,
      priority: $("taskPriority").value || "NORMAL",
      dueDate: $("taskDueDate").value || null,
      dueTime: $("taskDueTime").value || null,
      reminderEnabled: $("taskReminderEnabled").checked,
      reminderMinutesBefore: $("taskReminderEnabled").checked ? Number($("taskReminderBefore").value || 0) : null,
    });
    upsertTaskInMaps(created);
    $("taskText").value = "";
    await loadTaskBoard(true);
    setSave("saved");
    renderTasks();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function updateTaskDetails(id, patch){
  setSave("saving");
  try {
    const updated = await api.updateTask(id, patch);
    upsertTaskInMaps(updated);
    await loadTaskBoard(true);
    setSave("saved");
    renderTasks();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function toggleTask(id, done){
  setSave("saving");
  const oldTask = Object.values(state.tasksByDate).flat().find(t => Number(t.id) === Number(id)) || null;
  if (oldTask) upsertTaskInMaps({ ...oldTask, done: !!done });
  renderTasks();
  renderCalendar();
  try {
    const res = await dataLayer.setTaskDone(id, done);
    if (res.task) upsertTaskInMaps(res.task);
    if (!res.queued) await loadTaskBoard(true);
    else {
      state.taskBoard.items = (state.taskBoard.items || []).map(t => Number(t.id) === Number(id) ? { ...t, done: !!done } : t);
      renderTaskBoard();
    }
    setSave("saved");
    renderTasks();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
    if (oldTask) upsertTaskInMaps(oldTask);
    await loadMonth();
    renderTasks();
    renderCalendar();
  }
}

async function removeTask(id){
  if (!confirm("Удалить задачу?")) return;
  setSave("saving");
  try {
    await api.deleteTask(id);
    removeTaskFromMaps(id);
    state.taskBoard.items = (state.taskBoard.items || []).filter(t => t.id !== id);
    await loadTaskBoard(true);
    setSave("saved");
    renderTasks();
    renderTaskBoard();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}


/* ─── Общий экран задач ─────────────────────────────────────── */
function renderTaskBoardCategoryFilter(){
  const sel = $("taskBoardCategory");
  if (!sel) return;
  const current = sel.value || state.taskBoard.filters.category || "all";
  sel.innerHTML = `<option value="all">все категории</option>`;
  for (const cat of allTaskCategories()) {
    const opt = document.createElement("option");
    opt.value = cat; opt.textContent = cat;
    sel.appendChild(opt);
  }
  sel.value = [...sel.options].some(o => o.value === current) ? current : "all";
  state.taskBoard.filters.category = sel.value;
}
function syncTaskBoardFiltersToInputs(){
  const f = state.taskBoard.filters;
  if ($("taskBoardStatusFilter")) $("taskBoardStatusFilter").value = f.status || "open";
  if ($("taskBoardPriority")) $("taskBoardPriority").value = f.priority || "all";
  if ($("taskBoardSearch")) $("taskBoardSearch").value = f.q || "";
  if ($("taskBoardFrom")) $("taskBoardFrom").value = f.from || "";
  if ($("taskBoardTo")) $("taskBoardTo").value = f.to || "";
}
async function loadTaskBoard(silent = true){
  try {
    const f = state.taskBoard.filters;
    const query = {
      status: f.status || "open",
      category: f.category !== "all" ? f.category : "",
      priority: f.priority !== "all" ? f.priority : "",
      q: f.q || "",
      from: f.from || "",
      to: f.to || "",
    };
    state.taskBoard.items = await api.taskBoard(query);
    renderTaskBoard();
  } catch (err) {
    console.error(err);
    if (!silent) setSave("err", err.message);
  }
}
function taskBoardDateLabel(task){
  const main = (task.dueDate || task.date || "").split("-").reverse().join(".");
  if (!task.dueDate) return main;
  return `${main}${task.dueTime ? " " + task.dueTime : ""}`;
}
function buildTaskMeta(task){
  const meta = document.createElement("div");
  meta.className = "taskMeta";
  if (task.category) {
    const b = document.createElement("span"); b.className = "taskBadge cat"; b.textContent = task.category; meta.appendChild(b);
  }
  if (task.priority && task.priority !== "NORMAL") {
    const b = document.createElement("span"); b.className = "taskBadge " + task.priority.toLowerCase(); b.textContent = taskPriorityLabel(task.priority); meta.appendChild(b);
  }
  const due = taskDueLabel(task);
  if (due) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = due; meta.appendChild(b); }
  if (task.reminderEnabled) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = `🔔 ${task.reminderMinutesBefore ?? 0}м`; meta.appendChild(b); }
  if (task.overdue && !task.done) { const b = document.createElement("span"); b.className = "taskBadge overdue"; b.textContent = "просрочено"; meta.appendChild(b); }
  if (task.done) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = "выполнено"; meta.appendChild(b); }
  return meta;
}
function renderTaskBoard(){
  const list = $("taskBoardList");
  if (!list) return;
  syncTaskBoardFiltersToInputs();
  renderTaskBoardCategoryFilter();
  const items = state.taskBoard.items || [];
  const open = items.filter(t => !t.done).length;
  const overdue = items.filter(t => t.overdue && !t.done).length;
  const done = items.filter(t => t.done).length;
  $("taskBoardStatus").textContent = `${open} откр. · ${overdue} проср.`;
  $("taskBoardStats").innerHTML = `
    <span class="pill">показано <b>${items.length}</b></span>
    <span class="pill">открытых <b>${open}</b></span>
    <span class="pill">просроченных <b>${overdue}</b></span>
    <span class="pill">выполненных <b>${done}</b></span>`;
  list.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = "По этим фильтрам задач нет.";
    list.appendChild(empty);
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.className = "taskBoardItem" + (task.done ? " done" : "") + (task.overdue && !task.done ? " overdue" : "");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !!task.done;
    cb.addEventListener("change", () => toggleTask(task.id, cb.checked));
    const date = document.createElement("button");
    date.type = "button";
    date.className = "taskBoardDate";
    date.textContent = taskBoardDateLabel(task);
    date.title = `Открыть день ${task.date}`;
    date.addEventListener("click", () => goToTaskDate(task.date));
    const body = document.createElement("div");
    body.className = "taskBoardBody";
    const text = document.createElement("div");
    text.className = "taskText";
    text.textContent = task.text;
    body.append(text, buildTaskMeta(task));
    const actions = document.createElement("div");
    actions.className = "taskBoardActions";
    const edit = document.createElement("button");
    edit.type = "button"; edit.textContent = "ред."; edit.title = "Редактировать задачу";
    edit.addEventListener("click", () => editTask(task));
    const del = document.createElement("button");
    del.type = "button"; del.textContent = "×"; del.title = "Удалить задачу";
    del.addEventListener("click", () => removeTask(task.id));
    actions.append(edit, del);
    row.append(cb, date, body, actions);
    list.appendChild(row);
  }
}
async function goToTaskDate(k){
  if (!k) return;
  const [y, m] = k.split("-").map(Number);
  const targetYear = y, targetMonth = m - 1;
  if (state.y !== targetYear || state.m !== targetMonth) {
    state.y = targetYear; state.m = targetMonth;
    await loadMonth();
  }
  selectDay(k);
}
function setTaskBoardQuickStatus(status){
  state.taskBoard.filters.status = status;
  loadTaskBoard(false);
}

$("taskAdd").addEventListener("click", addTask);
$("taskText").addEventListener("keydown", e => { if (e.key === "Enter") addTask(); });
$("taskStatusFilter").addEventListener("change", () => { state.taskFilters.status = $("taskStatusFilter").value; renderTasks(); });
$("taskCategoryFilter").addEventListener("change", () => { state.taskFilters.category = $("taskCategoryFilter").value; renderTasks(); });
$("taskDueDate").addEventListener("change", () => { if ($("taskDueDate").value) $("taskReminderEnabled").checked = true; });

$("taskBoardOpen").addEventListener("click", () => setTaskBoardQuickStatus("open"));
$("taskBoardOverdue").addEventListener("click", () => setTaskBoardQuickStatus("overdue"));
$("taskBoardAll").addEventListener("click", () => setTaskBoardQuickStatus("all"));
$("taskBoardThisMonth").addEventListener("click", () => { const r = monthFromTo(); state.taskBoard.filters.from = r.from; state.taskBoard.filters.to = r.to; loadTaskBoard(false); });
$("taskBoardClear").addEventListener("click", () => { state.taskBoard.filters = { status:"open", category:"all", priority:"all", q:"", from:"", to:"" }; loadTaskBoard(false); });
$("taskBoardStatusFilter").addEventListener("change", () => { state.taskBoard.filters.status = $("taskBoardStatusFilter").value; loadTaskBoard(false); });
$("taskBoardCategory").addEventListener("change", () => { state.taskBoard.filters.category = $("taskBoardCategory").value; loadTaskBoard(false); });
$("taskBoardPriority").addEventListener("change", () => { state.taskBoard.filters.priority = $("taskBoardPriority").value; loadTaskBoard(false); });
$("taskBoardFrom").addEventListener("change", () => { state.taskBoard.filters.from = $("taskBoardFrom").value; loadTaskBoard(false); });
$("taskBoardTo").addEventListener("change", () => { state.taskBoard.filters.to = $("taskBoardTo").value; loadTaskBoard(false); });
$("taskBoardSearch").addEventListener("input", () => { clearTimeout(window.__taskBoardTimer); window.__taskBoardTimer = setTimeout(() => { state.taskBoard.filters.q = $("taskBoardSearch").value.trim(); loadTaskBoard(true); }, 350); });

function renderChips(){
  const box = $("chips");
  box.innerHTML = "";
  const cur = state.days[state.selected]?.shiftTypeId ?? null;
  for (const s of state.shiftTypes) {
    const b = document.createElement("button");
    const on = cur === s.id;
    b.className = "chip";
    b.style.background = on ? s.color : s.color + "1F";
    b.style.color = on ? "#14171C" : s.color;
    b.style.border = `1px solid ${on ? s.color : s.color + "55"}`;
    b.innerHTML = esc(s.name) + (shiftPlannedHours(s) ? ` <span class="h">·${fmtHours(shiftPlannedHours(s))}ч</span>` : "");
    const meta = shiftMetaText(s);
    if (meta) b.title = meta;
    b.addEventListener("click", () => toggleShift(s.id));
    box.appendChild(b);
  }
  // Плюсик — переход к настройкам смен
  const plus = document.createElement("button");
  plus.className = "chip plus";
  plus.textContent = "+";
  plus.title = "Создать или настроить смену в настройках";
  plus.addEventListener("click", () => {
    location.hash = "#settings";
    setTimeout(() => { $("shiftSettingsCard")?.scrollIntoView({behavior:"smooth", block:"start"}); $("nsName")?.focus(); }, 50);
  });
  box.appendChild(plus);
  renderCustomList();
  if (!$("tplBox")?.hidden) renderScheduleControls();
  renderQuickScenarioContext();
  updateAccSummaries();
}


function normalizeDayEmojiValue(value){
  const raw = String(value || "").trim();
  return raw.length > 32 ? raw.slice(0, 32) : raw;
}
function renderDayEmojiControls(){
  if (!$('dayEmojiGrid')) return;
  const k = state.selected;
  const cur = k ? normalizeDayEmojiValue(state.days[k]?.dayEmoji) : "";
  const grid = $('dayEmojiGrid');
  grid.innerHTML = "";
  for (const emoji of DAY_EMOJI_PRESETS) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "emojiChoice" + (emoji === cur ? " on" : "");
    b.textContent = emoji;
    b.title = "Поставить маркер " + emoji;
    b.addEventListener("click", () => setDayEmoji(emoji));
    grid.appendChild(b);
  }
  const input = $('dayEmojiCustom');
  if (input && document.activeElement !== input) input.value = cur;
  if ($('dayEmojiPreview')) $('dayEmojiPreview').textContent = cur ? `В календаре будет видно: ${cur}` : "Маркер не выбран.";
}
async function setDayEmoji(value){
  const k = state.selected;
  if (!k) return;
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId ?? null,
    note: cur.note || null,
    dayEmoji: normalizeDayEmojiValue(value) || "",
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  renderCalendar();
  renderDayEmojiControls();
  updateAccSummaries();
  await pushDaySnapshot(k, next);
}

async function toggleShift(id){
  const k = state.selected;
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId === id ? null : id,
    note: cur.note || null,
    dayEmoji: cur.dayEmoji || null,
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  updateAccSummaries();
  renderChips(); renderCalendar();
  await pushDaySnapshot(k, next);
}

function applyLocal(k, next){
  const clean = {
    shiftTypeId: next.shiftTypeId ?? null,
    note: next.note ?? null,
    dayEmoji: next.dayEmoji ?? null,
    overtimeHours: numOr0(next.overtimeHours),
    timeOffHours: numOr0(next.timeOffHours),
  };
  const hasOvertime = Math.abs(clean.overtimeHours) > 0.0001 || Math.abs(clean.timeOffHours) > 0.0001;
  if (!clean.shiftTypeId && !(clean.note || "").trim() && !(clean.dayEmoji || "").trim() && !hasOvertime) delete state.days[k];
  else state.days[k] = clean;
}

/*
 * Отправка дня на сервер. Важно: сохраняем снимок данных, а не читаем
 * state.days[k] внутри setTimeout. Иначе можно потерять заметку, если
 * пользователь напечатал текст и сразу переключил месяц.
 */
async function pushDaySnapshot(k, payload){
  setSave("saving");
  try {
    const res = await dataLayer.putDay(k, {
      shiftTypeId: payload.shiftTypeId ?? null,
      note: payload.note ?? null,
      dayEmoji: payload.dayEmoji ?? null,
      overtimeHours: numOr0(payload.overtimeHours),
      timeOffHours: numOr0(payload.timeOffHours),
    });
    setSave(res.queued ? "saved" : "saved");
    if (res.queued) updateOfflineStatus();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

/* Заметка: локально сразу, на сервер — с задержкой */
let noteTimer = null;
let pendingDaySave = null;

function scheduleDaySave(k, payload){
  clearTimeout(noteTimer);
  pendingDaySave = { k, payload: { ...payload } };
  noteTimer = setTimeout(() => {
    const p = pendingDaySave;
    pendingDaySave = null;
    noteTimer = null;
    if (p) pushDaySnapshot(p.k, p.payload);
  }, 600);
}

async function flushPendingSave(){
  if (!pendingDaySave) return;
  clearTimeout(noteTimer);
  const p = pendingDaySave;
  pendingDaySave = null;
  noteTimer = null;
  await pushDaySnapshot(p.k, p.payload);
}

$("noteEdit").addEventListener("input", () => {
  const k = state.selected;
  if (!k) return;
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId ?? null,
    note: $("noteEdit").value,
    dayEmoji: cur.dayEmoji || null,
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  updateAccSummaries();
  renderCalendar();
  scheduleDaySave(k, next);
});

function setTab(t){
  state.tab = t;
  $("tabEdit").classList.toggle("on", t === "edit");
  $("tabPrev").classList.toggle("on", t === "preview");
  $("noteEdit").hidden = t !== "edit";
  $("notePrev").hidden = t !== "preview";
  if (t === "preview") {
    const note = $("noteEdit").value;
    $("notePrev").innerHTML = note.trim() ? renderMd(note) : '<span class="empty">Заметка пустая — нечего показывать.</span>';
  }
}
$("tabEdit").addEventListener("click", () => setTab("edit"));
$("tabPrev").addEventListener("click", () => setTab("preview"));
$("pClose").addEventListener("click", () => selectDay(null));

/* ─── Управление типами смен ────────────────────────────────── */

// Enter в полях новой смены = «Добавить» (вешается один раз)
for (const id of ["nsName", "nsHours", "nsStart", "nsEnd", "nsBreak", "nsPlan"]) {
  $(id).addEventListener("keydown", e => { if (e.key === "Enter") addShiftType(); });
}

function renderSwatches(){
  const row = $("swRow");
  row.innerHTML = "";
  for (const c of SWATCHES) {
    const b = document.createElement("button");
    b.className = "sw" + (state.swColor === c ? " on" : "");
    b.style.background = c;
    b.addEventListener("click", () => { state.swColor = c; renderSwatches(); });
    row.appendChild(b);
  }
  const picker = document.createElement("input");
  picker.type = "color"; picker.value = state.swColor; picker.title = "Свой цвет";
  picker.addEventListener("input", () => { state.swColor = picker.value; });
  row.appendChild(picker);
  const add = document.createElement("button");
  add.className = "add"; add.textContent = "Добавить";
  add.addEventListener("click", addShiftType);
  row.appendChild(add);
}

async function addShiftType(){
  const name = $("nsName").value.trim();
  if (!name) return setSave("err", "укажи название смены");
  const rawHours = $("nsHours").value.trim().replace(",", ".");
  const hours = rawHours ? Number(rawHours) : 0;
  if (!Number.isFinite(hours) || hours < 0 || hours > 24) {
    return setSave("err", "часы: от 0 до 24");
  }
  const breakMinutes = readIntInput("nsBreak");
  const rawPlan = $("nsPlan").value.trim().replace(",", ".");
  const plannedHours = rawPlan ? Number(rawPlan) : hours;
  if (!Number.isFinite(breakMinutes) || breakMinutes < 0 || breakMinutes > 1440) {
    return setSave("err", "обед: от 0 до 1440 минут");
  }
  if (!Number.isFinite(plannedHours) || plannedHours < 0 || plannedHours > 24) {
    return setSave("err", "план: от 0 до 24 часов");
  }

  setSave("saving");
  try {
    const created = await api.createShiftType({
      name,
      hours,
      color: state.swColor,
      startTime: $("nsStart").value || null,
      endTime: $("nsEnd").value || null,
      breakMinutes,
      plannedHours,
    });
    state.shiftTypes.push(created);
    $("nsName").value = ""; $("nsHours").value = ""; $("nsStart").value = ""; $("nsEnd").value = ""; $("nsBreak").value = "0"; $("nsPlan").value = "";
    setSave("saved");
    renderChips(); renderSummary(); renderCustomList();
  } catch (err) { console.error(err); setSave("err", err.message); }
}

function renderCustomList(){
  const box = $("customList");
  box.hidden = state.shiftTypes.length === 0;
  box.innerHTML = "";
  for (const s of state.shiftTypes) {
    const row = document.createElement("div");
    row.style.display = "flex"; row.style.alignItems = "center"; row.style.gap = "8px"; row.style.flexWrap = "wrap";
    const meta = shiftMetaText(s);
    const notifyMeta = s.notificationsEnabled === false ? " · без уведомлений" : (s.notificationMinutesBefore != null ? ` · напомнить за ${s.notificationMinutesBefore}м` : "");
    row.innerHTML = `<span class="dot" style="width:10px;height:10px;border-radius:3px;background:${s.color};display:inline-block"></span>
      <span>${esc(s.name)}${shiftPlannedHours(s) ? ` · план ${fmtHours(shiftPlannedHours(s))}ч` : ""}${meta ? ` <span style="color:var(--dim)">· ${esc(meta)}</span>` : ""}<span style="color:var(--dim)">${esc(notifyMeta)}</span></span>`;

    const edit = document.createElement("button");
    edit.className = "del"; edit.textContent = "настроить";
    edit.title = "Изменить время, обед и плановые часы смены";
    edit.addEventListener("click", () => editShiftType(s.id));
    row.appendChild(edit);

    if (s.builtin) {
      const tag = document.createElement("span");
      tag.className = "tag"; tag.textContent = "встроенная";
      row.appendChild(tag);
    } else {
      const del = document.createElement("button");
      del.className = "del"; del.textContent = "удалить";
      del.title = "Смена снимется с дней, где стояла. Заметки останутся.";
      del.addEventListener("click", () => removeShiftType(s.id));
      row.appendChild(del);
    }
    box.appendChild(row);
  }
}

async function editShiftType(id){
  const s = state.shiftTypes.find(x => Number(x.id) === Number(id));
  if (!s) return setSave("err", "смена не найдена");

  const patch = {};
  if (!s.builtin) {
    const name = prompt("Название смены", s.name || "");
    if (name === null) return;
    if (!name.trim()) return setSave("err", "название не может быть пустым");
    patch.name = name.trim();

    const color = prompt("Цвет #RRGGBB", s.color || state.swColor);
    if (color === null) return;
    patch.color = color.trim();
  }

  const hoursRaw = prompt("Короткие часы для календаря", fmtHours(s.hours));
  if (hoursRaw === null) return;
  const hours = Number(hoursRaw.replace(",", "."));
  if (!Number.isFinite(hours) || hours < 0 || hours > 24) return setSave("err", "часы: от 0 до 24");
  patch.hours = hours;

  const startTime = prompt("Начало смены HH:mm, можно пусто", s.startTime || "");
  if (startTime === null) return;
  patch.startTime = startTime.trim();

  const endTime = prompt("Конец смены HH:mm, можно пусто", s.endTime || "");
  if (endTime === null) return;
  patch.endTime = endTime.trim();

  const brRaw = prompt("Обед/перерыв, минут", String(s.breakMinutes || 0));
  if (brRaw === null) return;
  const breakMinutes = Number(brRaw);
  if (!Number.isFinite(breakMinutes) || breakMinutes < 0 || breakMinutes > 1440) return setSave("err", "обед: от 0 до 1440 минут");
  patch.breakMinutes = Math.round(breakMinutes);

  const planRaw = prompt("Плановые часы", fmtHours(shiftPlannedHours(s)));
  if (planRaw === null) return;
  const plannedHours = Number(planRaw.replace(",", "."));
  if (!Number.isFinite(plannedHours) || plannedHours < 0 || plannedHours > 24) return setSave("err", "план: от 0 до 24 часов");
  patch.plannedHours = plannedHours;

  const notifRaw = prompt("Уведомлять перед этой сменой? да/нет", s.notificationsEnabled === false ? "нет" : "да");
  if (notifRaw === null) return;
  const notifClean = notifRaw.trim().toLowerCase();
  patch.notificationsEnabled = !(notifClean === "нет" || notifClean === "no" || notifClean === "0" || notifClean === "false");

  const minutesRaw = prompt("За сколько минут напоминать именно эту смену? Пусто = глобальная настройка", s.notificationMinutesBefore ?? "");
  if (minutesRaw === null) return;
  if (minutesRaw.trim()) {
    const minutes = Number(minutesRaw);
    if (!Number.isFinite(minutes) || minutes < 0 || minutes > 1440) return setSave("err", "напоминание смены: от 0 до 1440 минут");
    patch.notificationMinutesBefore = Math.round(minutes);
  } else {
    patch.notificationMinutesBefore = -1;
  }

  setSave("saving");
  try {
    const updated = await api.updateShiftType(id, patch);
    const idx = state.shiftTypes.findIndex(x => Number(x.id) === Number(id));
    if (idx >= 0) state.shiftTypes[idx] = updated;
    setSave("saved");
    renderChips(); renderSummary(); renderCalendar(); renderOvertimeControls();
  } catch (err) { console.error(err); setSave("err", err.message); }
}

async function removeShiftType(id){
  setSave("saving");
  try {
    await api.deleteShiftType(id);
    state.shiftTypes = state.shiftTypes.filter(s => s.id !== id);
    // локально снимаем смену с дней, где она стояла
    for (const [k, v] of Object.entries(state.days)) {
      if (v.shiftTypeId === id) {
        v.shiftTypeId = null;
        const hasOvertime = Math.abs(numOr0(v.overtimeHours)) > 0.0001 || Math.abs(numOr0(v.timeOffHours)) > 0.0001;
        if (!(v.note || "").trim() && !hasOvertime) delete state.days[k];
      }
    }
    setSave("saved");
    renderChips(); renderCalendar();
  } catch (err) { console.error(err); setSave("err", err.message); }
}

/* ─── Навигация по месяцам ──────────────────────────────────── */
async function goto(y, m){
  await flushPendingSave();
  const d = new Date(y, m, 1);
  state.y = d.getFullYear(); state.m = d.getMonth();
  state.selected = null;
  $("panel").hidden = true; $("layout").classList.remove("with-panel");
  await loadMonth();
  await loadTaskBoard(true);
  renderCalendar();
}
$("prev").addEventListener("click", () => goto(state.y, state.m - 1));
$("next").addEventListener("click", () => goto(state.y, state.m + 1));
$("todayBtn").addEventListener("click", async () => {
  const t = new Date();
  await goto(t.getFullYear(), t.getMonth());
  selectDay(todayKey());
});




/* ─── Время и регион ───────────────────────────────────────── */
function readTimeSettingsForm(){
  const val = id => ($(id)?.value ?? "").trim();
  const num = (id, fallback = 0) => {
    const raw = val(id).replace(",", ".");
    const n = raw === "" ? fallback : Number(raw);
    return Number.isFinite(n) ? n : fallback;
  };
  return {
    workRegionName: val("workRegionName"),
    workTimezone: val("workTimezone") || browserTimeZone(),
    workOffsetMoscow: Math.round(num("workOffsetMoscow", 0)),
    timeFormat: val("timeFormatPref") || "24h",
    dayStart: val("defDayStart") || "08:30",
    dayEnd: val("defDayEnd") || "17:00",
    dayBreakMinutes: Math.max(0, Math.min(1440, Math.round(num("defDayBreak", 30)))),
    dayPlannedHours: Math.max(0, Math.min(24, num("defDayPlan", 8))),
    nightStart: val("defNightStart") || "20:00",
    nightEnd: val("defNightEnd") || "08:00",
    nightBreakMinutes: Math.max(0, Math.min(1440, Math.round(num("defNightBreak", 60)))),
    nightPlannedHours: Math.max(0, Math.min(24, num("defNightPlan", 11))),
  };
}
function renderTimeSettings(){
  if (!$("timeSettingsCard")) return;
  if (!state.timeSettings) state.timeSettings = loadTimeSettings();
  const t = state.timeSettings;
  const set = (id, v) => { if ($(id)) $(id).value = v ?? ""; };
  set("workRegionName", t.workRegionName);
  set("workTimezone", t.workTimezone);
  set("workOffsetMoscow", t.workOffsetMoscow);
  set("timeFormatPref", t.timeFormat || "24h");
  set("defDayStart", t.dayStart);
  set("defDayEnd", t.dayEnd);
  set("defDayBreak", t.dayBreakMinutes);
  set("defDayPlan", t.dayPlannedHours);
  set("defNightStart", t.nightStart);
  set("defNightEnd", t.nightEnd);
  set("defNightBreak", t.nightBreakMinutes);
  set("defNightPlan", t.nightPlannedHours);

  const browserTz = browserTimeZone();
  const region = t.workRegionName ? `${esc(t.workRegionName)} · ` : "";
  $("timeNowBox").innerHTML = `${region}рабочее время: <b>${esc(safeTzLabel(t.workTimezone))}</b> <span>(${esc(t.workTimezone)})</span><br>` +
    `браузер: <b>${esc(safeTzLabel(browserTz))}</b> <span>(${esc(browserTz)})</span>` +
    (Number(t.workOffsetMoscow || 0) ? `<br>пометка: Москва ${Number(t.workOffsetMoscow) > 0 ? "+" : ""}${Number(t.workOffsetMoscow)} ч` : "");
  $("timeSettingsStatus").textContent = "автосохранение";
}
function saveTimeSettings(){
  storeTimeSettings(readTimeSettingsForm());
  renderTimeSettings();
  setSave("saved", "настройки времени сохранены");
}
let timeAutoApplyTimer = null;
function scheduleTimeSettingsApply(){
  if (timeAutoApplyTimer) clearTimeout(timeAutoApplyTimer);
  timeAutoApplyTimer = setTimeout(() => applyTimeSettingsToBuiltins(true), 700);
}
function fillShiftFormFromDefaults(kind){
  const t = state.timeSettings || loadTimeSettings();
  if (kind === "night") {
    $("nsName").value = $("nsName").value || "Ночная кастомная";
    $("nsHours").value = fmtHours(t.nightPlannedHours);
    $("nsStart").value = t.nightStart;
    $("nsEnd").value = t.nightEnd;
    $("nsBreak").value = t.nightBreakMinutes;
    $("nsPlan").value = fmtHours(t.nightPlannedHours);
  } else {
    $("nsName").value = $("nsName").value || "Дневная кастомная";
    $("nsHours").value = fmtHours(t.dayPlannedHours);
    $("nsStart").value = t.dayStart;
    $("nsEnd").value = t.dayEnd;
    $("nsBreak").value = t.dayBreakMinutes;
    $("nsPlan").value = fmtHours(t.dayPlannedHours);
  }
  location.hash = "#settings";
  setSave("", "");
}
function patchForBuiltInShift(name, t){
  if (name === "Ночная") return {
    startTime: t.nightStart,
    endTime: t.nightEnd,
    breakMinutes: t.nightBreakMinutes,
    plannedHours: t.nightPlannedHours,
    hours: t.nightPlannedHours,
  };
  return {
    startTime: t.dayStart,
    endTime: t.dayEnd,
    breakMinutes: t.dayBreakMinutes,
    plannedHours: t.dayPlannedHours,
    hours: t.dayPlannedHours,
  };
}
async function applyTimeSettingsToBuiltins(silent = false){
  const t = readTimeSettingsForm();
  storeTimeSettings(t);
  const targets = state.shiftTypes.filter(s => s.name === "Дневная" || s.name === "Ночная");
  if (!targets.length) return setSave("err", "не нашёл Дневную/Ночную смену");
  if (!silent) setSave("saving");
  try {
    for (const s of targets) {
      const updated = await api.updateShiftType(s.id, patchForBuiltInShift(s.name, t));
      const idx = state.shiftTypes.findIndex(x => Number(x.id) === Number(s.id));
      if (idx >= 0) state.shiftTypes[idx] = updated;
    }
    setSave("saved", silent ? "время смен применено" : "встроенные смены обновлены");
    renderTimeSettings();
    renderCustomList();
    renderChips();
    renderCalendar();
    renderOvertimeControls();
  } catch (err) { console.error(err); setSave("err", err.message); }
}
function initTimeSettingsEvents(){
  if (!$("timeSettingsCard")) return;
  $("timeDetectBrowser")?.addEventListener("click", () => { $("workTimezone").value = browserTimeZone(); saveTimeSettings(); });
  $("timeApplyBuiltins")?.addEventListener("click", () => applyTimeSettingsToBuiltins(false));
  $("timeFillDayForm")?.addEventListener("click", () => fillShiftFormFromDefaults("day"));
  $("timeFillNightForm")?.addEventListener("click", () => fillShiftFormFromDefaults("night"));
  const shiftDefaultIds = ["defDayStart","defDayEnd","defDayBreak","defDayPlan","defNightStart","defNightEnd","defNightBreak","defNightPlan"];
  for (const id of ["workRegionName","workTimezone","workOffsetMoscow","timeFormatPref", ...shiftDefaultIds]) {
    const el = $(id);
    if (!el) continue;
    el.addEventListener("change", () => {
      storeTimeSettings(readTimeSettingsForm());
      renderTimeSettings();
      if (shiftDefaultIds.includes(id)) scheduleTimeSettingsApply();
    });
  }
}

function renderDiagnosticsClient(){
  const set = (id, value) => { if ($(id)) $(id).textContent = value; };
  set("diagFrontend", "v" + DUTYLOG_VERSION);
  set("diagBrowser", navigator.userAgent.replace(/\s+/g, " ").slice(0, 90));
  set("diagCsrf", csrfToken() ? "cookie есть" : "cookie не найден");
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.getRegistration().then(reg => set("diagSw", reg ? "активен" : "не зарегистрирован")).catch(() => set("diagSw", "ошибка"));
  } else set("diagSw", "не поддерживается");
}
function renderRegistrationAdmin(status = null){
  const enabled = status?.enabled === true;
  const statusEl = $("registrationAdminStatus");
  const detailsEl = $("registrationAdminDetails");
  const toggle = $("registrationEnabledToggle");
  const stateLabel = enabled ? "открыта" : "закрыта";
  if (statusEl) {
    statusEl.textContent = stateLabel;
    statusEl.className = "status " + (enabled ? "warn" : "ok");
  }
  if (toggle) toggle.checked = enabled;
  if (detailsEl) {
    const source = status?.source === "database" ? "из админки" : "значение по умолчанию";
    const changed = status?.updatedAt ? ` · изменено ${fmtSyncTime(status.updatedAt)}${status.updatedBy ? " пользователем " + status.updatedBy : ""}` : "";
    detailsEl.textContent = `Публичная регистрация: ${stateLabel} · ${source}${changed}`;
  }
}
async function refreshRegistrationAdmin(){
  try {
    const status = await api.registrationSettings();
    state.registrationSettings = status;
    renderRegistrationAdmin(status);
  } catch (err) {
    const detailsEl = $("registrationAdminDetails");
    if (detailsEl) detailsEl.textContent = "Не удалось загрузить настройку регистрации: " + (err.message || String(err));
  }
}
async function saveRegistrationAdmin(enabled){
  const toggle = $("registrationEnabledToggle");
  const statusEl = $("registrationAdminStatus");
  if (toggle) toggle.disabled = true;
  if (statusEl) statusEl.textContent = "сохраняю…";
  try {
    const status = await api.updateRegistrationSettings(enabled);
    state.registrationSettings = status;
    renderRegistrationAdmin(status);
    setSave("saved", enabled ? "публичная регистрация открыта" : "публичная регистрация закрыта");
  } catch (err) {
    setSave("err", err.message || "не удалось сохранить настройку регистрации");
    renderRegistrationAdmin(state.registrationSettings);
  } finally {
    if (toggle) toggle.disabled = false;
  }
}

function roleLabel(role){ return role === "ADMIN" ? "админ" : "пользователь"; }
function renderAdminUsers(users = []){
  const box = $("adminUsersList");
  const status = $("adminUsersStatus");
  if (!box) return;
  if (status) {
    const admins = users.filter(u => u.role === "ADMIN").length;
    status.textContent = `${users.length} / админов ${admins}`;
    status.className = "status " + (admins > 0 ? "ok" : "warn");
  }
  if (!users.length) {
    box.innerHTML = '<span class="emptyLine">Пользователей пока нет.</span>';
    return;
  }
  box.innerHTML = users.map(u => {
    const role = u.role || "USER";
    const canChangeRole = !(u.bootstrapAdmin && role === "ADMIN") && !u.currentUser;
    const badges = [
      u.bootstrapAdmin ? '<span class="miniBadge warn">env admin</span>' : '',
      u.currentUser ? '<span class="miniBadge">это вы</span>' : '',
      `<span class="miniBadge">${esc(u.accountTier || "FREE")}</span>`
    ].filter(Boolean).join(" ");
    const created = u.createdAt ? fmtSyncTime(u.createdAt) : "—";
    const updated = u.updatedAt ? fmtSyncTime(u.updatedAt) : "—";
    return `
      <div class="adminUserRow" data-user-id="${u.id}">
        <div class="adminUserMain">
          <b>${esc(u.displayName || u.username)}</b>
          <span>@${esc(u.username)} · создан ${esc(created)} · обновлён ${esc(updated)}</span>
          <div class="adminUserBadges">${badges}</div>
        </div>
        <div class="adminUserActions">
          <select data-admin-role="${u.id}" ${canChangeRole ? "" : "disabled"} title="Роль пользователя">
            <option value="USER" ${role === "USER" ? "selected" : ""}>USER</option>
            <option value="ADMIN" ${role === "ADMIN" ? "selected" : ""}>ADMIN</option>
          </select>
          <button data-admin-password="${u.id}" data-username="${esc(u.username)}" type="button">Сменить пароль</button>
        </div>
      </div>`;
  }).join("");
}
async function refreshAdminUsers(){
  const status = $("adminUsersStatus");
  if (status) status.textContent = "загрузка…";
  try {
    const users = await api.adminUsers();
    state.adminUsers = users || [];
    renderAdminUsers(state.adminUsers);
  } catch (err) {
    if (status) status.textContent = "ошибка";
    const box = $("adminUsersList");
    if (box) box.innerHTML = diagnosticRow("Ошибка списка пользователей", err.message || String(err), false);
  }
}
async function saveAdminUserRole(id, role){
  const previous = [...(state.adminUsers || [])];
  try {
    const updated = await api.updateAdminUserRole(id, role);
    state.adminUsers = (state.adminUsers || []).map(u => Number(u.id) === Number(id) ? updated : u);
    renderAdminUsers(state.adminUsers);
    setSave("saved", `роль ${updated.username}: ${updated.role}`);
  } catch (err) {
    state.adminUsers = previous;
    renderAdminUsers(previous);
    setSave("err", err.message || "не удалось изменить роль");
  }
}
async function resetAdminUserPassword(id, username){
  const password = prompt(`Новый пароль для ${username} (минимум 12 символов)`);
  if (password == null) return;
  if (password.length < 12) return setSave("err", "пароль должен быть минимум 12 символов");
  try {
    const updated = await api.resetAdminUserPassword(id, password);
    state.adminUsers = (state.adminUsers || []).map(u => Number(u.id) === Number(id) ? updated : u);
    renderAdminUsers(state.adminUsers);
    setSave("saved", `пароль ${updated.username} обновлён`);
  } catch (err) {
    setSave("err", err.message || "не удалось сменить пароль");
  }
}

function diagnosticRow(label, value, ok = null){
  const cls = ok === true ? " ok" : ok === false ? " warn" : "";
  return `<div class="diagRow${cls}"><span>${esc(label)}</span><b>${esc(value ?? "—")}</b></div>`;
}
function renderDiagnosticsStatus(data){
  const box = $("diagnosticsList");
  if (!box) return;
  const rows = [];
  rows.push(diagnosticRow("Версия сервера", data.version || "—"));
  rows.push(diagnosticRow("Профили Spring", (data.profiles || []).join(", ") || "default/dev"));
  rows.push(diagnosticRow("Серверное время", data.serverTime || "—"));
  rows.push(diagnosticRow("Часовой пояс сервера", data.serverTimezone || "—"));
  rows.push(diagnosticRow("База данных", data.database?.ok ? "ok" : (data.database?.error || "ошибка"), !!data.database?.ok));
  rows.push(diagnosticRow("Пользователи", data.users?.total != null ? String(data.users.total) : "—"));
  rows.push(diagnosticRow("Администраторы", data.users?.admins != null ? String(data.users.admins) : "—", Number(data.users?.admins || 0) > 0));
  rows.push(diagnosticRow("Роли доступа", (data.users?.rolesAllowed || []).join(", ") || "USER, ADMIN"));
  rows.push(diagnosticRow("Будущие тарифы", (data.users?.accountTiersReserved || []).join(", ") || "FREE, PAID, VIP"));
  rows.push(diagnosticRow("Публичная регистрация", data.registration?.enabled ? "открыта" : "закрыта", data.registration?.enabled ? false : true));
  rows.push(diagnosticRow("Источник настройки регистрации", data.registration?.source === "database" ? "админка" : "по умолчанию"));
  rows.push(diagnosticRow("Telegram bot", data.telegram?.enabled ? "включён" : "выключен", data.telegram?.enabled ? true : null));
  rows.push(diagnosticRow("Telegram token", data.telegram?.tokenConfigured ? "задан" : "не задан", data.telegram?.tokenConfigured ? true : null));
  rows.push(diagnosticRow("Telegram polling", data.telegram?.pollingEnabled ? "включён" : "выключен", data.telegram?.pollingEnabled ? true : null));
  rows.push(diagnosticRow("Telegram уведомления", data.telegram?.notificationsEnabled ? "включены" : "выключены", data.telegram?.notificationsEnabled ? true : null));
  rows.push(diagnosticRow("Аккаунт подключен к Telegram", data.telegram?.linked ? "да" : "нет", data.telegram?.linked ? true : null));
  box.innerHTML = rows.join("");
  const st = $("diagnosticsStatus");
  if (st) st.textContent = data.database?.ok ? "ok" : "проверь";
}
async function refreshDiagnostics(){
  renderDiagnosticsClient();
  const st = $("diagnosticsStatus");
  if (st) st.textContent = "проверяю…";
  try {
    const data = await api.systemStatus();
    state.lastDiagnostics = data;
    renderDiagnosticsStatus(data);
  } catch (err) {
    if (st) st.textContent = "ошибка";
    const box = $("diagnosticsList");
    if (box) box.innerHTML = diagnosticRow("Ошибка диагностики", err.message || String(err), false) + diagnosticRow("Доступ", "только администратор", false);
  }
}
function diagnosticsReportText(){
  const d = state.lastDiagnostics || {};
  return [
    `DutyLog UI: v${DUTYLOG_VERSION}`,
    `Client: web/PWA inside Spring Boot monolith`,
    `Native mobile app: not present`,
    `Server: ${d.version || "—"}`,
    `Profiles: ${(d.profiles || []).join(", ") || "default/dev"}`,
    `Server time: ${d.serverTime || "—"}`,
    `Server timezone: ${d.serverTimezone || "—"}`,
    `Database: ${d.database?.ok ? "ok" : (d.database?.error || "unknown")}`,
    `Users total: ${d.users?.total ?? "unknown"}`,
    `Admins total: ${d.users?.admins ?? "unknown"}`,
    `Roles allowed: ${(d.users?.rolesAllowed || []).join(", ") || "USER, ADMIN"}`,
    `Account tiers reserved: ${(d.users?.accountTiersReserved || []).join(", ") || "FREE, PAID, VIP"}`,
    `Registration enabled: ${!!d.registration?.enabled}`,
    `Registration source: ${d.registration?.source || "unknown"}`,
    `Telegram enabled: ${!!d.telegram?.enabled}`,
    `Telegram token: ${!!d.telegram?.tokenConfigured}`,
    `Telegram polling: ${!!d.telegram?.pollingEnabled}`,
    `Telegram notifications: ${!!d.telegram?.notificationsEnabled}`,
    `Telegram linked: ${!!d.telegram?.linked}`,
    `Browser: ${navigator.userAgent}`,
  ].join("\n");
}
function initDiagnosticsEvents(){
  if (!$("diagnosticsCard")) return;
  $("diagnosticsRefresh")?.addEventListener("click", refreshDiagnostics);
  $("diagnosticsCopy")?.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(diagnosticsReportText()); setSave("saved", "отчёт диагностики скопирован"); }
    catch (err) { setSave("err", "не удалось скопировать отчёт"); }
  });
  renderDiagnosticsClient();
  refreshRegistrationAdmin();
  refreshAdminUsers();
  $("registrationRefresh")?.addEventListener("click", refreshRegistrationAdmin);
  $("registrationEnabledToggle")?.addEventListener("change", e => saveRegistrationAdmin(e.target.checked));
  $("adminUsersRefresh")?.addEventListener("click", refreshAdminUsers);
  $("adminUsersList")?.addEventListener("change", e => {
    const id = e.target?.dataset?.adminRole;
    if (id) saveAdminUserRole(id, e.target.value);
  });
  $("adminUsersList")?.addEventListener("click", e => {
    const id = e.target?.dataset?.adminPassword;
    if (id) resetAdminUserPassword(id, e.target.dataset.username || `#${id}`);
  });
}

function initSettingsAccordion(){
  const root = $("view-settings");
  if (!root || root.dataset.accordionReady === "1") return;
  root.dataset.accordionReady = "1";
  const cards = [...root.querySelectorAll(".settingsCard[data-settings-section]")];
  const titles = {
    profile: "Имя, пароль, устройства и Telegram",
    appearance: "Тема, акцентный цвет и emoji-маркеры дней",
    time: "Регион, часовой пояс и дефолты дневной/ночной",
    shifts: "Кастомные и встроенные типы смен",
    scenarios: "Шаблоны, которые заполняют переработку в панели дня",
    notifications: "Браузерные, сменные, задачные и важные напоминания",
    important: "Общий список важных дат с удалением",
    admin: "Служебная диагностика вынесена в отдельный профиль"
  };
  let saved = localStorage.getItem("dutylog.settings.openSection") || "profile";
  const known = new Set(cards.map(c => c.dataset.settingsSection));
  if (!known.has(saved)) saved = "profile";

  function setNavActive(section){
    root.querySelectorAll("[data-settings-jump]").forEach(a => a.classList.toggle("on", a.dataset.settingsJump === section));
  }
  function setCardOpen(card, open){
    card.classList.toggle("is-collapsed", !open);
    card.classList.toggle("is-open", open);
    const btn = card.querySelector(".settingsToggle");
    if (btn) {
      btn.textContent = open ? "свернуть" : "открыть";
      btn.setAttribute("aria-expanded", String(open));
    }
  }
  function openSection(section, scroll = false){
    for (const card of cards) setCardOpen(card, card.dataset.settingsSection === section);
    localStorage.setItem("dutylog.settings.openSection", section);
    setNavActive(section);
    if (scroll) document.getElementById("settings-" + section)?.scrollIntoView({ behavior:"smooth", block:"start" });
  }
  function expandAll(){
    for (const card of cards) setCardOpen(card, true);
    localStorage.setItem("dutylog.settings.openSection", "all");
    root.querySelectorAll("[data-settings-jump]").forEach(a => a.classList.remove("on"));
  }
  function collapseAll(){
    for (const card of cards) setCardOpen(card, false);
    localStorage.setItem("dutylog.settings.openSection", "none");
    root.querySelectorAll("[data-settings-jump]").forEach(a => a.classList.remove("on"));
  }

  for (const card of cards) {
    const section = card.dataset.settingsSection;
    const head = card.querySelector(":scope > .settingsHead, :scope > .notifyHead");
    if (!head) continue;
    if (!card.querySelector(":scope > .settingsCollapsedNote")) {
      const note = document.createElement("div");
      note.className = "settingsCollapsedNote";
      note.textContent = titles[section] || "Раздел настроек";
      head.after(note);
    }
    if (!head.querySelector(".settingsToggle")) {
      const toggle = document.createElement("button");
      toggle.className = "settingsToggle";
      toggle.type = "button";
      toggle.setAttribute("aria-controls", card.id || "settings-" + section);
      toggle.addEventListener("click", (ev) => {
        ev.preventDefault(); ev.stopPropagation();
        if (card.classList.contains("is-collapsed")) openSection(section, false);
        else collapseAll();
      });
      head.appendChild(toggle);
    }
    head.addEventListener("click", (ev) => {
      if (ev.target.closest("button,a,input,select,textarea,label")) return;
      if (card.classList.contains("is-collapsed")) openSection(section, false);
      else collapseAll();
    });
  }

  root.querySelectorAll("[data-settings-jump]").forEach(a => {
    a.addEventListener("click", (ev) => {
      ev.preventDefault();
      openSection(a.dataset.settingsJump, true);
      history.replaceState(null, "", "#settings");
    });
  });
  $("settingsExpandAll")?.addEventListener("click", expandAll);
  $("settingsCollapseAll")?.addEventListener("click", collapseAll);

  if (location.hash.startsWith("#settings-") && known.has(location.hash.replace("#settings-", ""))) {
    saved = location.hash.replace("#settings-", "");
  }
  if (saved === "all") expandAll();
  else if (saved === "none") collapseAll();
  else openSection(saved, false);
}

function renderSettingsPanels(){
  initSettingsAccordion();
  renderAppearanceControls();
  renderTimeSettings();
  renderCustomList();
  renderImportantSettings();
  renderNotifications();
  renderTelegramPanel();
  loadTelegramStatus();
}

/* ─── Уведомления ───────────────────────────────────────────── */
function typeLabel(type){
  return type === "SHIFT" ? "смена" : type === "TASK" ? "задача" : type === "IMPORTANT_DAY" ? "важно" : type === "TOMORROW_DIGEST" ? "дайджест" : type;
}
function fmtReminderAt(value){
  if (!value) return "";
  const d = value.slice(0,10), t = value.slice(11,16);
  const [,m,day] = d.split("-");
  return `${day}.${m} ${t}`;
}
function browserPermissionText(){
  if (!("Notification" in window)) return "браузер не поддерживает";
  return Notification.permission === "granted" ? "браузер: разрешено" : Notification.permission === "denied" ? "браузер: запрещено" : "браузер: не разрешено";
}
function renderNotifications(){
  const s = state.notificationSettings;
  if (!$("notifyCard") || !s) return;
  $("notifBrowser").checked = !!s.browserNotificationsEnabled;
  $("notifShift").checked = !!s.shiftRemindersEnabled;
  $("notifShiftBefore").value = s.shiftReminderMinutesBefore ?? 60;
  $("notifDigest").checked = !!s.tomorrowDigestEnabled;
  $("notifDigestTime").value = s.tomorrowDigestTime || "19:00";
  $("notifTasks").checked = !!s.taskRemindersEnabled;
  $("notifTaskTime").value = s.taskReminderTime || "09:00";
  $("notifImportant").checked = !!s.importantDayRemindersEnabled;
  $("notifImportantDays").value = s.importantDayDaysBefore ?? 1;
  $("notifImportantTime").value = s.importantDayReminderTime || "09:00";
  const sourceItems = state.notificationPreview || state.reminders;
  $("notifyStatus").textContent = `${sourceItems.length} шт · ${browserPermissionText()}`;
  if ($("notifyListTitle")) $("notifyListTitle").textContent = state.notificationPreviewTitle || "Напоминания текущего месяца";
  const list = $("notifyList");
  list.innerHTML = "";
  const items = sourceItems.slice(0, 24);
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "notifyItem";
    empty.innerHTML = `<span class="notifyWhen">—</span><span class="notifyType">пусто</span><span class="notifyTitle"><span class="notifyDetails">${esc(state.notificationPreview ? "На завтра напоминаний нет." : "На текущий месяц напоминаний нет.")}</span></span>`;
    list.appendChild(empty);
    return;
  }
  for (const r of items) {
    const row = document.createElement("div");
    row.className = "notifyItem";
    row.innerHTML = `<span class="notifyWhen">${esc(fmtReminderAt(r.remindAt))}</span><span class="notifyType">${esc(typeLabel(r.type))}</span><span class="notifyTitle">${esc(r.title || "")}<div class="notifyDetails">${esc(r.details || "")}</div></span>`;
    list.appendChild(row);
  }
}
async function saveNotificationSettings(extra = {}){
  setSave("saving");
  try {
    const body = {
      browserNotificationsEnabled: $("notifBrowser").checked,
      shiftRemindersEnabled: $("notifShift").checked,
      shiftReminderMinutesBefore: Number($("notifShiftBefore").value || 0),
      tomorrowDigestEnabled: $("notifDigest").checked,
      tomorrowDigestTime: $("notifDigestTime").value || "19:00",
      taskRemindersEnabled: $("notifTasks").checked,
      taskReminderTime: $("notifTaskTime").value || "09:00",
      importantDayRemindersEnabled: $("notifImportant").checked,
      importantDayDaysBefore: Number($("notifImportantDays").value || 0),
      importantDayReminderTime: $("notifImportantTime").value || "09:00",
      ...extra
    };
    state.notificationSettings = await api.updateNotificationSettings(body);
    state.notificationPreview = null;
    state.notificationPreviewTitle = "Напоминания текущего месяца";
    const r = monthFromTo();
    state.reminders = await api.notificationUpcoming(r.from, r.to);
    state.remindersByDate = {};
    for (const x of state.reminders) addToDateMap(state.remindersByDate, { ...x, date:x.sourceDate });
    setSave("saved");
    renderNotifications();
    renderCalendar();
  } catch (err) { console.error(err); setSave("err", err.message); }
}
async function requestNotificationPermission(){
  if (!("Notification" in window)) { alert("Этот браузер не поддерживает Notification API"); return; }
  const perm = await Notification.requestPermission();
  await saveNotificationSettings({ browserNotificationsEnabled: perm === "granted" });
}
function testNotification(){
  if (!("Notification" in window) || Notification.permission !== "granted") { alert("Сначала разрешите уведомления в браузере"); return; }
  new Notification("DutyLog: Time & Overtime", { body:"Тестовое уведомление отправлено." });
}
async function showTomorrowNotifications(){
  setSave("saving");
  try {
    state.notificationPreview = await api.notificationTomorrow();
    state.notificationPreviewTitle = "напоминания на завтра";
    setSave("saved");
    renderNotifications();
  } catch (err) { console.error(err); setSave("err", err.message); }
}
async function showMonthNotifications(){
  setSave("saving");
  try {
    const r = monthFromTo();
    state.notificationPreview = null;
    state.notificationPreviewTitle = "Напоминания текущего месяца";
    state.reminders = await api.notificationUpcoming(r.from, r.to, true);
    state.remindersByDate = {};
    for (const x of state.reminders) addToDateMap(state.remindersByDate, { ...x, date:x.sourceDate });
    setSave("saved");
    renderNotifications();
    renderCalendar();
  } catch (err) { console.error(err); setSave("err", err.message); }
}

$("notifSave").addEventListener("click", () => saveNotificationSettings());
$("notifPermission").addEventListener("click", requestNotificationPermission);
$("notifTest").addEventListener("click", testNotification);
$("notifRefresh").addEventListener("click", showMonthNotifications);
$("notifTomorrow").addEventListener("click", showTomorrowNotifications);
document.querySelectorAll("[data-notif-shift-before]").forEach(btn => btn.addEventListener("click", () => {
  $("notifShiftBefore").value = btn.dataset.notifShiftBefore;
  $("notifShift").checked = true;
}));

/* ─── Загрузка данных ───────────────────────────────────────── */
function applyCalendarBundle(bundle){
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
  for (const t of bundle.tasks || []) addToDateMap(state.tasksByDate, t);
  for (const i of bundle.importantDays || []) addToDateMap(state.importantByDate, i);
  state.notificationSettings = bundle.notificationSettings || state.notificationSettings;
  state.quickScenarios = bundle.quickScenarios || state.quickScenarios || [];
  state.reminders = bundle.reminders || [];
  for (const r of state.reminders) addToDateMap(state.remindersByDate, { ...r, date:r.sourceDate });
  if (bundle.overtimeAccount) state.overtimeAccount = bundle.overtimeAccount;
}

async function loadMonth(){
  try {
    const res = await dataLayer.loadCalendar(state.y, state.m, applyCalendarBundle);
    setSave(res?.fromCache ? "" : "");
    renderNotifications();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
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
  state.timeSettings = loadTimeSettings();
  renderSwatches();
  initTimeSettingsEvents();
  initDiagnosticsEvents();
  initSettingsAccordion();
  await dataLayer.init();
  try {
    const me = await jfetch("/api/auth/me");
    $("whoami").textContent = me.username;
    state.shiftTypes = await api.shiftTypes();
    state.quickScenarios = await api.quickScenarios();
    await refreshImportantSettings();
  } catch (err) {
    console.error(err);
    if (err.status === 401) return; // при 401 нас уже уносит на login.html
    state.offline.online = false;
    setSave("err", "нет связи — открыта локальная копия");
  }
  await loadMonth();
  await loadTaskBoard(true);
  renderCalendar();
  dataLayer.syncQueue();
}
init();

/* ─── Вкладки: hash-роутинг ─────────────────────────────────── */
const VIEWS = { calendar:"view-calendar", overtime:"view-overtime", tasks:"view-tasks", settings:"view-settings", admin:"view-admin" };
function applyRoute(){
  const rawRoute = (location.hash || "#calendar").slice(1);
  const name = rawRoute.startsWith("settings-") ? "settings" : rawRoute;
  let active = VIEWS[name] ? name : "calendar";
  if (active === "admin" && state.profile && !state.profile.admin) active = "calendar";
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
  if (active === "settings") renderSettingsPanels();
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
    : '<span class="noteFsEmpty">Пусто. Пиши слева — превью живое.</span>';
}

function openNoteFullscreen(){
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
  $("noteFsTab").textContent = prev ? "редактор" : "превью";
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
  el.textContent = "🎉 С днём рождения, " + (p.displayName || p.username) + "! Смену сегодня прогуливаем?";
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
    const av = $("profileAvatar");
    av.textContent = avatarInitials(p.displayName || p.username);
    av.style.background = avatarColor(p.username);
    if (location.hash === "#admin" && !p.admin) location.hash = "#calendar";
    applyRoute();
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
    setProfileMsg("profileMsg", "Сохранено", true);
    setTimeout(() => setProfileMsg("profileMsg", ""), 2000);
  } catch (e) { setProfileMsg("profileMsg", e.message); }
});


function currentProfilePayload(extra = {}){
  return {
    displayName: $('profileName')?.value || "",
    birthday: $('profileBirthday')?.value || null,
    ...extra,
  };
}
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
    setProfileMsg('appearanceMsg', 'Внешний вид сохранён', true);
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
  if (nw !== rep) { setProfileMsg("pwMsg", "Новые пароли не совпадают"); return; }
  try {
    await jfetch("/api/profile/password", { method: "POST", body: { currentPassword: cur, newPassword: nw } });
    for (const id of ["pwCurrent", "pwNew", "pwRepeat"]) $(id).value = "";
    setProfileMsg("pwMsg", "Пароль изменён. Активные мобильные сессии завершены.", true);
    loadSessions();
  } catch (e) { setProfileMsg("pwMsg", e.message); }
});

async function loadSessions(){
  const box = $("sessionsList");
  try {
    const list = await jfetch("/api/profile/sessions");
    box.innerHTML = "";
    if (!list.length) {
      box.innerHTML = '<div class="sessionRow"><span class="meta">Мобильных сессий нет — только этот браузер.</span></div>';
      return;
    }
    for (const sess of list) {
      const row = document.createElement("div");
      row.className = "sessionRow";
      const dev = document.createElement("span");
      dev.className = "dev" + (sess.active ? "" : " dead");
      dev.textContent = sess.deviceName || "устройство";
      const meta = document.createElement("span");
      meta.className = "meta";
      const last = sess.lastUsedAt ? sess.lastUsedAt.slice(0, 16).replace("T", " ") : "не использовалась";
      meta.textContent = (sess.active ? "активна · " : "отозвана · ") + last;
      row.append(dev, meta);
      if (sess.active) {
        const del = document.createElement("button");
        del.type = "button";
        del.textContent = "отозвать";
        del.addEventListener("click", async () => {
          try { await jfetch("/api/profile/sessions/" + sess.id, { method: "DELETE" }); loadSessions(); }
          catch (e) { console.error(e); }
        });
        row.appendChild(del);
      }
      box.appendChild(row);
    }
  } catch (e) {
    box.innerHTML = '<div class="sessionRow"><span class="meta">Не удалось загрузить сессии.</span></div>';
  }
}


/* ─── Telegram: привязка бота ───────────────────────────────── */
function telegramName(status){
  return status?.botUsername ? "@" + status.botUsername : "бот";
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
    status.textContent = "загрузка…";
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
    status.textContent = "Бот не настроен на сервере: укажите DUTYLOG_TELEGRAM_BOT_TOKEN и включите polling.";
    status.className = "telegramStatus warn";
  } else if (s.linked) {
    const name = s.username ? "@" + s.username : "chat " + s.chatId;
    status.textContent = "Подключено: " + name + (s.notificationsEnabled ? " · напоминания включены" : " · напоминания выключены");
    status.className = "telegramStatus ok";
  } else {
    status.textContent = "Не подключено. Создайте код и отправьте его " + telegramName(s) + ".";
    status.className = "telegramStatus";
  }
  if (s.pendingCode && codeBox.hidden) {
    showTelegramCode({ code:s.pendingCode, expiresAt:s.pendingCodeExpiresAt, startCommand:"/start " + s.pendingCode, deepLink:s.botUsername ? "https://t.me/" + s.botUsername + "?start=" + s.pendingCode : null });
  }
}
async function loadTelegramStatus(){
  if (!$("telegramBox")) return;
  try {
    state.telegramStatus = await api.telegramStatus();
    renderTelegramPanel();
  } catch (e) {
    const status = $("telegramStatus");
    if (status) { status.textContent = "Не удалось загрузить статус Telegram."; status.className = "telegramStatus warn"; }
  }
}
function showTelegramCode(c){
  const box = $("telegramCodeBox");
  if (!box) return;
  box.hidden = false;
  const exp = c.expiresAt ? c.expiresAt.slice(11,16) : "через 15 минут";
  const link = c.deepLink ? `<a href="${esc(c.deepLink)}" target="_blank" rel="noreferrer">открыть бота</a>` : "Укажите username бота в настройках сервера, чтобы появилась ссылка";
  box.innerHTML = `<div class="code">${esc(c.code)}</div><div>Отправьте боту: <b>${esc(c.startCommand)}</b></div><div class="meta">Код действует до ${esc(exp)} · ${link}</div>`;
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
  if (!confirm("Отключить Telegram от этого аккаунта?")) return;
  try {
    await api.telegramUnlink();
    $("telegramCodeBox").hidden = true;
    await loadTelegramStatus();
  } catch (e) {
    const status = $("telegramStatus");
    if (status) { status.textContent = e.message; status.className = "telegramStatus warn"; }
  }
});

loadProfile();
loadSessions();
