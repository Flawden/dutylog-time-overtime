/*
 * 10-core.js — Core: state, constants, i18n, themes and shared helpers
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

"use strict";

// Shared DOM/HTML helpers must be available before any boot-time code runs.
// Several ordered scripts call these helpers at top level; keep them in 10-core
// and before applyAppearance(loadLocalAppearance()).
function $(id){ return document.getElementById(id); }
function esc(value){
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

let activeAppModalId = null;
function openAppModal(id, focusId = null){
  const modal = $(id);
  if (!modal) return;
  if (activeAppModalId && activeAppModalId !== id) closeAppModal(activeAppModalId);
  activeAppModalId = id;
  modal.hidden = false;
  document.body.classList.add("app-modal-open");
  requestAnimationFrame(() => {
    modal.classList.add("open");
    const target = focusId ? $(focusId) : modal.querySelector("input, textarea, select, button");
    target?.focus({ preventScroll:true });
  });
}
function closeAppModal(id = activeAppModalId){
  const modal = id ? $(id) : null;
  if (!modal) return;
  modal.classList.remove("open");
  modal.hidden = true;
  if (activeAppModalId === id) activeAppModalId = null;
  if (!activeAppModalId) document.body.classList.remove("app-modal-open");
}
document.addEventListener("keydown", event => {
  if (event.key !== "Escape" || !activeAppModalId) return;
  if (activeAppModalId === "taskEditModal" && typeof closeTaskEditor === "function") closeTaskEditor();
  else if (activeAppModalId === "shiftTypeModal" && typeof closeShiftTypeManager === "function") closeShiftTypeManager();
  else if (activeAppModalId === "overtimeCreditModal" && typeof closeOvertimeCreditModal === "function") closeOvertimeCreditModal();
  else if (activeAppModalId === "overtimeUsageModal" && typeof closeOvertimeUsageModal === "function") closeOvertimeUsageModal();
  else closeAppModal(activeAppModalId);
});

const DUTYLOG_VERSION = "27.8.1"

const LANGUAGE_KEY = "dutylog.language.v1";
function normalizeLanguage(value){
  const lang = String(value || "").trim().toLowerCase();
  return lang === "en" ? "en" : "ru";
}
function initialLanguage(){
  try { return normalizeLanguage(localStorage.getItem(LANGUAGE_KEY) || (navigator.language || "").slice(0,2)); }
  catch (_) { return "ru"; }
}

/* ─── Состояние ─────────────────────────────────────────────── */
const state = {
  y: new Date().getFullYear(),
  m: new Date().getMonth(),      // 0–11
  shiftTypes: [],                 // [{id,name,hours,color,builtin,startTime,endTime,breakMinutes,plannedHours}]
  days: {},                       // { 'YYYY-MM-DD': {shiftTypeId, note, overtimeHours, timeOffHours} }
  tasksByDate: {},                // { 'YYYY-MM-DD': [{id,date,text,done,category,priority,dueDate,dueTime,overdue}] }
  taskFilters: { status:"all", category:"all" },
  taskBoard: { items: [], filters: { status:"open", category:"all", priority:"all", q:"", from:"", to:"" }, page: { page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false } },
  taskMetadata: { categories: [], tags: [] },
  inbox: { items: [], loading:false, includeArchived:false },
  importantByDate: {},            // { 'YYYY-MM-DD': [{id,date,title,repeatMode,color}] }
  importantDays: [],               // самостоятельный список важных дней: [{id,date,title,repeatMode,color}]
  importantFilters: { scope:"all", q:"" },
  editingImportantDayId: null,
  editingTaskId: null,
  editingTaskMode: "create",
  editingTaskInboxId: null,
  editingShiftTypeId: null,
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
  adminUsersPage: { page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false },
  preferences: { themePreference:"system", accentColor:"#F5B841" },
  profile: null,
  language: initialLanguage(),
  onboardingDraft: null,
  modulesLoaded: false,
  modulesList: [],
  modules: { core:true, calendar:true, shifts:true, notes:true, tasks:true, overtime:true, important_dates:true, notifications:true, telegram:false, scenarios:true, admin:false },
  activeScenarioId: null,
  ledgerFilters: { from:"", to:"", status:"all", q:"" },
  ledgerPage: { items: [], page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false },
  editingCreditId: null,
  editingUsageId: null,
  selected: null,                 // ключ даты
  tab: "edit",
  ui: { booting:true, loadingCalendar:false, loadingTasks:false, loadingLedger:false },
  toasts: [],
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

const MONTHS_RU = ["Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"];
const MONTHS_GEN_RU = ["января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"];
const WEEKDAYS_RU = ["Пн","Вт","Ср","Чт","Пт","Сб","Вс"];
const MONTHS_EN = ["January","February","March","April","May","June","July","August","September","October","November","December"];
const MONTHS_GEN_EN = MONTHS_EN;
const WEEKDAYS_EN = ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"];
function monthName(index){ return (state.language === "en" ? MONTHS_EN : MONTHS_RU)[index]; }
function monthNameGen(index){ return (state.language === "en" ? MONTHS_GEN_EN : MONTHS_GEN_RU)[index]; }
function weekdayName(index){ return (state.language === "en" ? WEEKDAYS_EN : WEEKDAYS_RU)[index]; }
function currentLocale(){ return state.language === "en" ? "en-US" : "ru-RU"; }
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


const I18N_EN = {
  "Мои данные": "My data",
  "Скачать все заметки (.zip)": "Download all notes (.zip)",
  "Markdown-файлы по датам — открываются в Obsidian.": "Markdown files by date — opens in Obsidian.",
  "Настройки":"Settings", "Профиль":"Profile", "Язык":"Language", "Модули":"Modules", "русский / English":"Russian / English", "включить нужные функции":"enable needed features",
  "Внешний вид":"Appearance", "Время":"Time", "Смены":"Shifts", "Сценарии":"Scenarios", "Уведомления":"Notifications", "Важные даты":"Important dates",
  "имя, пароль, Telegram":"name, password, Telegram", "тема, акцент, маркеры":"theme, accent, markers", "часовой пояс и шаблоны смен":"timezone and shift presets", "типы, часы, уведомления":"types, hours, notifications", "шаблоны переработок":"overtime templates", "браузер и расписания":"browser and schedules", "общий список событий":"shared event list",
  "развернуть всё":"expand all", "свернуть всё":"collapse all", "открыть":"open", "свернуть":"collapse",
  "Профиль пользователя":"User profile", "Отображаемое имя":"Display name", "День рождения":"Birthday", "Сохранить":"Save",
  "Смена пароля":"Change password", "Текущий пароль":"Current password", "Новый пароль":"New password", "Ещё раз":"Repeat", "Сменить пароль":"Change password", "Активные устройства":"Active devices", "Telegram-бот":"Telegram bot",
  "Интерфейс":"Interface", "Модульность":"Modularity", "Модули приложения":"App modules", "Отключайте функции, которые сейчас не нужны. Данные не удаляются: модуль можно включить обратно в любой момент.":"Turn off features you do not need right now. Data is not deleted: you can enable a module again at any time.", "Ядро, календарь и смены всегда включены. Зависимости включаются автоматически, чтобы приложение не ломалось.":"Core, calendar and shifts are always enabled. Dependencies are enabled automatically so the app stays consistent.", "Скрытые блоки":"Hidden blocks", "Настроить модули":"Configure modules", "Скрыть подсказку":"Hide hint", "Отключённый модуль не удаляет данные — его можно включить обратно в настройках.":"A disabled module does not delete data — you can enable it again in settings.", "Сейчас включены только базовые блоки дня.":"Only basic day blocks are enabled now.", "включено":"enabled", "выключено":"disabled", "всегда включён":"always on", "зависит от":"depends on", "модули сохранены":"modules saved", "модули загружаются…":"modules are loading…", "модуль выключен":"module disabled", "включено модулей":"modules enabled", "snapshot модулей":"modules snapshot", "модули не загружены":"modules not loaded", "операция относится к выключенному модулю":"operation belongs to a disabled module", "контракт":"contract", "слоты":"slots", "API":"API", "offline":"offline", "категория":"category", "ядро":"core", "календарь":"calendar", "продуктивность":"productivity", "учёт времени":"time accounting", "интеграции":"integrations", "администрирование":"administration", "Язык приложения":"App language", "Русский":"Russian", "Основной язык":"Main language", "Дополнительный язык":"Additional language", "Язык сохранён":"Language saved",
  "Персонализация":"Personalization", "Пресет":"Preset", "Готовая тема":"Theme preset", "Базовый режим":"Base mode", "Акцент":"Accent", "Точная настройка":"Fine tuning", "Фон приложения":"App background", "Карточки":"Cards", "Внутренние блоки":"Inner blocks", "Основной текст":"Primary text", "Вторичный текст":"Secondary text", "Границы":"Borders", "Стиль кнопок":"Button style", "Стиль карточек":"Card style", "Тени":"Shadows", "Плотность":"Density", "Скругление карточек":"Card radius", "Сохранить внешний вид":"Save appearance", "Сбросить локально":"Reset locally",
  "как в системе":"system", "тёмная":"dark", "светлая":"light", "заливка":"solid", "мягкие":"soft", "контурные":"outline", "призрачные":"ghost", "стандартные":"standard", "плоские":"flat", "контрастные":"contrast", "тёплые":"warm", "без теней":"no shadows", "лёгкие":"light", "средние":"medium", "сильные":"strong", "компактно":"compact", "обычно":"comfortable", "просторно":"spacious",
  "Время и регион":"Time and region", "Часовой пояс и формат времени":"Timezone and time format", "Рабочий часовой пояс":"Work timezone", "Определить автоматически":"Detect automatically", "Сохранить часовой пояс":"Save timezone", "Формат времени":"Time format", "Сохранить настройки":"Save settings",
  "Типы смен и их время":"Shift types and time", "Короткие часы для календаря":"Short calendar hours", "Календарь, ч":"Calendar label, h", "Норма, ч":"Norm, h", "Название смены":"Shift name", "Добавить":"Add", "Сохранить параметры смен":"Save shift settings", "Дневная":"Day shift", "Ночная":"Night shift", "Выходной":"Day off",
  "Быстрые сценарии":"Quick scenarios", "Мои сценарии":"My scenarios", "Добавить сценарий":"Add scenario", "Название":"Name", "Старт":"Start", "Конец":"End", "Обед":"Break", "План":"Plan", "Норма":"Norm", "Причина по умолчанию":"Default reason", "Описание сценария":"Scenario description",
  "Уведомления браузера":"Browser notifications", "Разрешить в браузере":"Allow in browser", "Напоминания текущего месяца":"Current month reminders", "Проверить":"Check", "Текущий месяц":"Current month", "Завтра":"Tomorrow", "Сервер рассчитывает напоминания для браузера, Telegram и мобильных клиентов.":"The server calculates reminders for browser, Telegram and mobile clients.",
  "Календарь":"Calendar", "Переработки":"Overtime", "Задачи":"Tasks", "Сегодня":"Today", "Система":"System", "Выйти":"Logout", "Фильтры":"Filters",
  "Смена":"Shift", "Маркер":"Marker", "График":"Schedule", "Переработка":"Overtime", "Важные дни":"Important days", "Заметка":"Note", "Превью":"Preview", "Очистить":"Clear", "Поставить":"Apply", "Заполнить":"Fill", "выбранный день":"selected day", "сегодня":"today", "Начислить":"Add credit", "Списать отгул":"Use time off", "отмена":"cancel",
  "Таблица переработок":"Overtime ledger", "Начислено":"Earned", "Использовано":"Used", "Куда списано":"Used for", "Действия":"Actions", "Действия списания":"Usage actions", "Редактировать списание":"Edit usage", "Удалить списание":"Delete usage", "ред. списание":"edit usage", "удалить списание":"delete usage", "Редактировать переработку":"Edit overtime credit", "Удалить переработку":"Delete overtime credit", "Остаток":"Remaining", "Причина":"Reason", "День":"Day", "Время":"Time", "этот месяц":"this month", "всё время":"all time", "сброс":"reset", "все начисления":"all credits", "только с остатком":"only remaining", "частично списанные":"partially used", "полностью списанные":"fully used",
  "Все задачи":"All tasks", "Статус задач":"Task status", "Фильтр задач":"Task filter", "Категория":"Category", "Приоритет":"Priority", "Срок":"Due date", "Время срока":"Due time", "напомнить":"remind", "За минут":"Minutes before", "все задачи":"all tasks", "открытые":"open", "просроченные":"overdue", "выполненные":"done", "все категории":"all categories", "любой приоритет":"any priority", "срочные":"urgent", "важные":"high", "обычные":"normal", "низкие":"low",
  "Пользователи":"Users", "Пользователи и роли":"Users and roles", "Фильтр по роли":"Role filter", "Обновить пользователей":"Refresh users", "Публичная регистрация":"Public registration", "Обновить статус регистрации":"Refresh registration status", "Диагностика":"Diagnostics", "Состояние системы":"System status", "Обновить диагностику":"Refresh diagnostics", "Скопировать отчёт":"Copy report",
  "Оффлайн-режим":"Offline mode", "Синхронизация данных":"Data sync", "Ожидают отправки":"Pending upload", "Неудачные операции":"Failed operations", "Диагностика оффлайна":"Offline diagnostics", "Синхронизировать":"Sync", "Повторить неудачные операции":"Retry failed operations", "Скачать локальные данные":"Download local data", "Очистить неудачные операции":"Clear failed operations", "Скопировать диагностику":"Copy diagnostics", "Подключение":"Connection", "Последняя синхронизация":"Last sync", "Возраст snapshot":"Snapshot age", "Очередь":"Queue", "Sync lock":"Sync lock", "Онлайн":"Online", "Оффлайн":"Offline", "онлайн":"online", "нет":"no", "доступна":"available",
  "Пн":"Mon", "Вт":"Tue", "Ср":"Wed", "Чт":"Thu", "Пт":"Fri", "Сб":"Sat", "Вс":"Sun",
  "загрузка…":"loading…", "Загрузка пользователей…":"Loading users…", "Загрузка настройки регистрации…":"Loading registration setting…", "Маркер не выбран.":"No marker selected.", "Выбранный день":"Selected day", "Главная кнопка":"Primary button", "Обычная":"Secondary", "Карточка":"Card", "Live preview":"Live preview",
  "Внешний вид сохранён":"Appearance saved", "Сохранено":"Saved", "настройки времени сохранены":"time settings saved", "отчёт диагностики скопирован":"diagnostics report copied", "не удалось скопировать отчёт":"failed to copy report",
  "браузер":"browser", "разрешено":"allowed", "запрещено":"blocked", "не разрешено":"not allowed", "не поддерживает":"not supported",
  "Показано:":"Shown:", "Админов на странице:":"Admins on page:", "Пользователей:":"Users:", "Админов:":"Admins:", "Назад":"Back", "Вперёд":"Next", "на странице":"per page", "стр.":"page",
  "смена":"shift", "задача":"task", "важно":"important", "дайджест":"digest", "Раздел настроек":"Settings section"
};
const I18N_RU = Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru]));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие" });
Object.assign(I18N_EN, {
  "Часовой пояс отображения":"Display timezone",
  "Сохранить часовые пояса":"Save timezones",
  "Как рабочий":"Same as work",
  "Рабочее время":"Work time",
  "Время отображения":"Display time",
  "Рабочий часовой пояс определяет календарные расчёты, смены и переработки. Часовой пояс отображения меняет только представление абсолютных моментов.":"Work timezone owns calendar calculations, shifts and overtime. Display timezone only changes how absolute moments are shown.",
  "Рабочая смена":"Work shift", "В часовом поясе отображения":"In display timezone", "Фактическая длительность":"Actual duration", "Исходный часовой пояс":"Source timezone", "Смена отображается в выбранном часовом поясе, но расчёты остаются в рабочем.":"The shift is shown in the selected display timezone while calculations remain in the work timezone.",
  "DutyLog хранит IANA-идентификаторы, например Europe/Chisinau. Плавающие календарные даты не сдвигаются, а абсолютные моменты отображаются в выбранной зоне.":"DutyLog stores IANA identifiers such as Europe/Chisinau. Floating calendar dates never move; absolute moments are shown in the selected zone.",
  "Вставь emoji с клавиатуры":"Paste an emoji from keyboard",
  "Маркер не выбран.":"No marker selected.",
  "2 через 2: день / день / выходной / выходной":"2 on / 2 off: day / day / off / off",
  "День / ночь / 48 часов отдыха":"Day / night / 48 hours off",
  "Пятидневка: Пн–Пт рабочие / Сб–Вс выходные":"Five-day week: Mon–Fri work / Sat–Sun off",
  "День / 72 часа отдыха":"Day / 72 hours off",
  "Ночь / 72 часа отдыха":"Night / 72 hours off",
  "Дней:":"Days:",
  "перезаписывать уже отмеченные смены":"overwrite already marked shifts",
  "Дата начисления":"Credit date",
  "Коротко: 17:00–20:00 или 17–08":"Short: 17:00–20:00 or 17–08",
  "Начало":"Start",
  "Вычесть план, ч":"Subtract plan, h",
  "Календарь, ч — короткая метка для календаря. Норма, ч — сколько часов вычитается при расчёте переработки. Если оставить норму пустой, она посчитается по началу, концу и обеду.":"Calendar label, h is a short label for the calendar. Norm, h is subtracted when calculating overtime. If norm is empty, it is calculated from start, end and break.",
  "Норма рассчитана по времени смены:":"Norm calculated from shift time:",
  "норма по смене":"norm by shift",
  "план по смене":"plan by shift",
  "время по смене":"time by shift",
  "Итого":"Total",
  "Причина переработки: ППР, авария, замена смены…":"Overtime reason: planned work, incident, shift replacement…",
  "Зачем списал: отгул, не вышел после ППР…":"Why used: time off, missed after planned work…",
  "Списать":"Use",
  "Дата":"Date",
  "Например: день рождения Макса":"Example: Max's birthday",
  "каждый год":"every year",
  "каждый месяц":"every month",
  "один раз":"one time",
  "Задача на этот день":"Task for this day",
  "работа, дом, здоровье":"work, home, health",
  "обычная":"normal",
  "низкая":"low",
  "важная":"important",
  "срочная":"urgent",
  "Редактор на весь экран":"Fullscreen editor",
  "⛶ развернуть":"⛶ expand",
  "поиск: причина, дата, куда списано…":"search: reason, date, usage…",
  "поиск: текст, категория, дата…":"search: text, category, date…",
  "с":"from",
  "по":"to",
  "CSV":"CSV",
  "Excel":"Excel",
  "все роли":"all roles",
  "Пользователь":"User",
  "Администратор":"Administrator",
  "Общий список важных дат с удалением":"Shared important-date list with deletion",
  "Имя, пароль, устройства и Telegram":"Name, password, devices and Telegram",
  "Тема, акцентный цвет и emoji-маркеры дней":"Theme, accent color and day emoji markers",
  "Регион, часовой пояс и дефолты дневной/ночной":"Region, timezone and day/night defaults",
  "Кастомные и встроенные типы смен":"Custom and built-in shift types",
  "Шаблоны, которые заполняют переработку в панели дня":"Templates that fill overtime in the day panel",
  "Браузерные, сменные, задачные и важные напоминания":"Browser, shift, task and important reminders",
  "Русский / English":"Russian / English"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие" });

Object.assign(I18N_EN, {
  "Имя отображается в шапке приложения. День рождения используется для поздравительного баннера в календаре.":"The name is shown in the app header. Birthday is used for the greeting banner in the calendar.",
  "Например: Даниил":"Example: Daniel",
  "После смены пароля активные мобильные сессии будут завершены.":"After changing the password, active mobile sessions will be revoked.",
  "Мобильных сессий нет — только этот браузер.":"No mobile sessions — only this browser.",
  "Не удалось загрузить сессии.":"Failed to load sessions.",
  "Пользователей пока нет.":"No users yet.",
  "изменено":"changed",
  "пользователем":"by",
  "Новые пароли не совпадают":"New passwords do not match",
  "Пароль изменён. Активные мобильные сессии завершены.":"Password changed. Active mobile sessions were revoked.",
  "устройство":"device",
  "не использовалась":"not used",
  "активна":"active",
  "отозвана":"revoked",
  "отозвать":"revoke",
  "Telegram-бот":"Telegram bot",
  "загрузка…":"loading…",
  "Создайте код привязки и отправьте его Telegram-боту командой /start DL-123456.":"Create a linking code and send it to the Telegram bot with /start DL-123456.",
  "Получать напоминания в Telegram":"Receive reminders in Telegram",
  "Используются те же правила, что в блоке «Уведомления»: смены, задачи, важные дни и вечерний дайджест.":"Uses the same rules as the Notifications section: shifts, tasks, important dates, and the evening digest.",
  "Создать код привязки":"Create linking code",
  "Отключить Telegram":"Disconnect Telegram",
  "Не подключено. Создайте код и отправьте его боту.":"Not connected. Create a code and send it to the bot.",
  "Бот не настроен на сервере: укажите DUTYLOG_TELEGRAM_BOT_TOKEN и включите polling.":"Bot is not configured on the server: set DUTYLOG_TELEGRAM_BOT_TOKEN and enable polling.",
  "Не удалось загрузить статус Telegram.":"Failed to load Telegram status.",
  "Отключить Telegram от этого аккаунта?":"Disconnect Telegram from this account?",
  "Подключено":"Connected",
  "напоминания включены":"reminders enabled",
  "напоминания выключены":"reminders disabled",
  "бот":"bot",
  "Отправьте боту:":"Send to bot:",
  "Код действует до":"Code is valid until",
  "через 15 минут":"in 15 minutes",
  "открыть бота":"open bot",
  "Укажите username бота в настройках сервера, чтобы появилась ссылка":"Set the bot username in server settings to show a link",
  "Выбор языка хранится в профиле пользователя и применяется к web/PWA интерфейсу. Сейчас доступны русский и английский.":"Language choice is stored in the user profile and applied to the web/PWA interface. Russian and English are available now.",
  "Перевод сделан безопасным словарём приложения: пользовательский JS/CSS не используется, язык не влияет на роли и тариф.":"Translation uses the app's safe dictionary: no user JS/CSS is used, and language does not affect roles or plan.",
  "Безопасный Theme Builder: только пресеты, color picker, списки и ползунки. Пользовательский CSS не поддерживается и не хранится.":"Safe Theme Builder: presets, color pickers, selects, and sliders only. Custom CSS is not supported or stored.",
  "Создание пользовательских смен и настройка встроенных типов: время, обед, норма и уведомления.":"Create custom shifts and configure built-in types: time, break, norm, and notifications.",
  "Календарь, ч — короткая метка для календаря. Норма, ч — сколько часов вычитается при расчёте переработки.":"Calendar label, h is the short label shown in the calendar. Norm, h is subtracted when calculating overtime.",
  "Настройки дневной и ночной смены сохраняются автоматически и применяются к встроенным типам смен.":"Day and night shift settings are saved automatically and applied to built-in shift types.",
  "Полный список важных дат. Новые события добавляются из панели выбранного дня в календаре.":"Full list of important dates. New events are added from the selected-day panel in the calendar.",
  "Публичная регистрация создаёт только USER. Дополнительных админов назначает действующий администратор. Тариф FREE показан как задел под будущие PAID/VIP, но пока не влияет на права.":"Public registration creates USER accounts only. Additional admins are assigned by an existing administrator. FREE is shown as groundwork for future PAID/VIP tiers, but does not affect permissions yet.",
  "Проверка версии приложения, подключения к серверу, базы данных, кэша браузера и Telegram-интеграции.":"Checks app version, server connection, database, browser cache, and Telegram integration.",
  "Нажмите «Обновить диагностику», чтобы получить отчёт.":"Click “Refresh diagnostics” to get a report.",
  "Активные устройства":"Active devices",
  "Серверное время":"Server time",
  "Профили Spring":"Spring profiles",
  "Часовой пояс сервера":"Server timezone",
  "Защита сессии":"Session security",
  "Кэш приложения":"App cache"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие" });



// v24.0.3: broader web/PWA i18n coverage for static markup and common dynamic UI strings.
Object.assign(I18N_EN, {
  "Состояние подключения":"Connection status",
  "Сегодня":"Today",
  "Система":"System",
  "Выйти":"Logout",
  "Календарь":"Calendar",
  "Переработки":"Overtime",
  "Задачи":"Tasks",
  "Закрыть":"Close",
  "Смена":"Shift",
  "Маркер":"Marker",
  "Поставить":"Set",
  "Очистить":"Clear",
  "График":"Schedule",
  "Дней:":"Days:",
  "Заполнить":"Fill",
  "По умолчанию заполняется 31 день вперёд, включая следующий месяц. Заметки не стираются.":"By default, fills 31 days ahead, including the next month. Notes are not deleted.",
  "Начислить":"Add overtime",
  "отмена":"cancel",
  "Быстрые сценарии":"Quick scenarios",
  "Выберите день и смену, чтобы увидеть подходящие сценарии.":"Select a day and shift to see matching scenarios.",
  "очистить поля":"clear fields",
  "Сценарий заполняет поля. Перед сохранением можно изменить время, обед, план и причину.":"A scenario fills the fields. Before saving, you can adjust time, break, plan, and reason.",
  "Списать отгул":"Use time off",
  "При списании отгула система сначала использует самые старые остатки переработки. Интервальные начисления автоматически распределяются по датам.":"When using time off, the system uses the oldest overtime balance first. Interval credits are automatically split by dates.",
  "Важные дни":"Important days",
  "В этот день важных событий нет.":"No important events on this day.",
  "Важные дни добавляются прямо из выбранного дня. Для повтора выбирай: каждый год, каждый месяц или один раз.":"Important days are added directly from the selected day. For repeats, choose every year, every month, or one time.",
  "Фильтр задач":"Task filter",
  "Категория":"Category",
  "все":"all",
  "открытые":"open",
  "просроченные":"overdue",
  "выполненные":"done",
  "все категории":"all categories",
  "Приоритет":"Priority",
  "Срок":"Due date",
  "Время срока":"Due time",
  "напомнить":"remind",
  "За минут":"Minutes before",
  "Заметка":"Note",
  "Превью":"Preview",
  "Таблица переработок":"Overtime table",
  "Начисления живут до полного списания. Отгулы списываются со старых остатков.":"Credits stay until fully used. Time off is taken from the oldest balances.",
  "этот месяц":"this month",
  "всё время":"all time",
  "Статус начисления":"Credit status",
  "все начисления":"all credits",
  "только с остатком":"with balance only",
  "частично списанные":"partially used",
  "полностью списанные":"fully used",
  "сброс":"reset",
  "День":"Day",
  "Время":"Time",
  "Начислено":"Earned",
  "Причина":"Reason",
  "Использовано":"Used",
  "Куда списано":"Used for",
  "Остаток":"Balance",
  "Все задачи":"All tasks",
  "Единый список открытых, просроченных и выполненных задач без прыжков по дням календаря.":"One list of open, overdue, and done tasks without jumping between calendar days.",
  "открытые не просроченные":"open, not overdue",
  "все задачи":"all tasks",
  "любой приоритет":"any priority",
  "срочные":"urgent",
  "важные":"important",
  "обычные":"normal",
  "низкие":"low",
  "Создать задачу":"Create task",
  "Отфильтрованные задачи":"Filtered tasks",
  "Разделы настроек":"Settings sections",
  "Интерфейс":"Interface",
  "Предпросмотр темы":"Theme preview",
  "Дневная":"Day shift",
  "Ночная":"Night shift",
  "Выходной":"Day off",
  "Так будут выглядеть фон, границы, текст, скругление и тени.":"This is how background, borders, text, radius, and shadows will look.",
  "Роли `USER/ADMIN`, будущий тариф `FREE/PAID/VIP` и внешний вид остаются разными слоями. Theme Builder хранит только разрешённые параметры, не CSS.":"Roles `USER/ADMIN`, future `FREE/PAID/VIP` tier, and appearance remain separate layers. Theme Builder stores only allowed parameters, not CSS.",
  "Время и регион":"Time and region",
  "Рабочий часовой пояс":"Work timezone",
  "Формат времени":"Time format",
  "24 часа":"24 hours",
  "Шаблоны смен":"Shift presets",
  "Обед, мин":"Break, min",
  "План, ч":"Plan, h",
  "Заполнить форму смены":"Fill shift form",
  "Сохранить параметры смен":"Save shift parameters",
  "Определить автоматически":"Detect automatically",
  "Сохранить часовой пояс":"Save timezone",
  "Типы смен и их время":"Shift types and times",
  "Название":"Name",
  "Календарь, ч":"Calendar, h",
  "Короткая метка часов в календаре; если пусто — берётся норма":"Short hour label in the calendar; if empty, norm is used",
  "Норма, ч":"Norm, h",
  "Норма для расчёта переработки; если пусто — считается по началу, концу и обеду":"Norm for overtime calculation; if empty, calculated from start, end, and break",
  "Шаблоны для переработок":"Overtime templates",
  "Сценарии применяются в выбранном дне и заполняют форму переработки. Начисление подтверждается отдельно.":"Scenarios are applied on the selected day and fill the overtime form. The credit is confirmed separately.",
  "Мои сценарии":"My scenarios",
  "Название, например: После дневной до 22":"Name, e.g. After day shift until 22",
  "Метка: после смены":"Tag: after shift",
  "Описание сценария":"Scenario description",
  "Старт":"Start",
  "от конца смены":"from shift end",
  "от начала смены":"from shift start",
  "Конец":"End",
  "через N минут":"after N minutes",
  "в указанное время":"at fixed time",
  "конец смены":"shift end",
  "+ минут":"+ minutes",
  "время":"time",
  "следующий день":"next day",
  "Обед":"Break",
  "из смены":"from shift",
  "свой":"custom",
  "мин":"min",
  "План":"Plan",
  "ч":"h",
  "Причина по умолчанию":"Default reason",
  "Добавить сценарий":"Add scenario",
  "Очистить форму":"Clear form",
  "Сценарии сохраняются в профиле пользователя и доступны в панели выбранного дня.":"Scenarios are saved in the user profile and available in the selected-day panel.",
  "Сервер рассчитывает напоминания для браузера, Telegram и мобильных клиентов.":"The server calculates reminders for browser, Telegram, and mobile clients.",
  "Уведомления браузера":"Browser notifications",
  "перед сменой":"before shift",
  "за":"before",
  "вечерний дайджест":"evening digest",
  "задачи дня":"day tasks",
  "дн.":"days",
  "1 час":"1 hour",
  "1.5 часа":"1.5 hours",
  "2 часа":"2 hours",
  "Сохранить настройки":"Save settings",
  "Разрешить в браузере":"Allow in browser",
  "Проверить":"Test",
  "Текущий месяц":"Current month",
  "Завтра":"Tomorrow",
  "Напоминания текущего месяца":"Current month reminders",
  "Администрирование":"Administration",
  "Служебный профиль":"System profile",
  "Техническая диагностика вынесена отдельно от пользовательских настроек. Этот раздел доступен только администратору приложения.":"Technical diagnostics are separated from user settings. This section is available only to the app administrator.",
  "Назад к настройкам":"Back to settings",
  "Пользователи":"Users",
  "Пользователи и роли":"Users and roles",
  "поиск: логин, имя, роль…":"search: login, name, role…",
  "Фильтр по роли":"Role filter",
  "Обновить пользователей":"Refresh users",
  "Доступ":"Access",
  "Публичная регистрация":"Public registration",
  "Администратор может открыть или закрыть создание новых обычных аккаунтов. Стартовый админ по-прежнему создаётся только через переменные окружения.":"An administrator can open or close creation of regular accounts. The bootstrap admin is still created only through environment variables.",
  "Разрешить публичную регистрацию пользователей":"Allow public user registration",
  "Обновить статус регистрации":"Refresh registration status",
  "Диагностика":"Diagnostics",
  "Состояние системы":"System status",
  "Интерфейс":"Interface",
  "проверяется…":"checking…",
  "Браузер":"Browser",
  "Нажмите «Обновить диагностику», чтобы получить отчёт.":"Click “Refresh diagnostics” to get a report.",
  "Обновить диагностику":"Refresh diagnostics",
  "Скопировать отчёт":"Copy report",
  "Заметка пустая — нечего показывать.":"Note is empty — nothing to preview.",
  "Пусто. Пиши слева — превью живое.":"Empty. Type on the left — preview is live.",
  "сохраняется автоматически":"autosaves",
  "редактор":"editor",
  "Синхронизация данных":"Data sync",
  "Оффлайн-режим":"Offline mode",
  "Ожидают отправки":"Waiting to upload",
  "Диагностика оффлайна":"Offline diagnostics",
  "Синхронизировать":"Sync",
  "Повторить неудачные операции":"Retry failed operations",
  "Скачать локальные данные":"Download local data",
  "Очистить неудачные операции":"Clear failed operations",
  "Неудачных операций синхронизации нет.":"No failed sync operations.",
  "Нет изменений, ожидающих отправки.":"No pending changes.",
  "Локальные данные старше суток. Проверьте их после подключения к серверу.":"Local data is older than a day. Check it after reconnecting to the server.",
  "Повторить операцию":"Retry operation",
  "Убрать из списка":"Remove from list"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие", "Browser":"Браузер" });
Object.assign(I18N_EN, {
  "Создание пользовательских смен и настройка встроенных типов: время, обед, норма для расчёта переработки и уведомления.":"Create custom shifts and configure built-in types: time, break, overtime norm, and notifications.",
  "Можно ввести полный интервал датами или коротко: дата начисления + 17:00–20:00. Если вводишь только переработку — план оставь 0; если всю фактическую смену — вычти план.":"You can enter a full date-time interval or a short one: credit date + 17:00–20:00. If you enter only overtime, leave plan as 0; if you enter the whole actual shift, subtract the plan.",
  "по смене":"by shift",
  "перед сменой:":"before shift:",
  "дн. в":"days at",
  "важные дни":"important dates",
  "в":"at",
  "авто":"auto",
  "0 ч":"0 h",
  "15 мин":"15 min",
  "30 мин":"30 min",
  "20–08 обед 60":"20–08 break 60",
  "Закрыть (Esc)":"Close (Esc)",
  "Цвет важного дня":"Important day color",
  "превью":"preview",
  "🔥 Дневная":"🔥 Day shift",
  "🌙 Ночная":"🌙 Night shift",
  "# Заголовок\n**жирный**, *курсив*, `код`\n- [ ] задача\n- список":"# Heading\n**bold**, *italic*, `code`\n- [ ] task\n- list",
  "# Заголовок&#10;**жирный**, *курсив*, `код`&#10;- [ ] задача&#10;> цитата&#10;```&#10;код блоком&#10;```":"# Heading&#10;**bold**, *italic*, `code`&#10;- [ ] task&#10;> quote&#10;```&#10;code block&#10;```"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_EN, {
  "Январь":"January", "Февраль":"February", "Март":"March", "Апрель":"April", "Май":"May", "Июнь":"June", "Июль":"July", "Август":"August", "Сентябрь":"September", "Октябрь":"October", "Ноябрь":"November", "Декабрь":"December",
  "января":"January", "февраля":"February", "марта":"March", "апреля":"April", "мая":"May", "июня":"June", "июля":"July", "августа":"August", "сентября":"September", "октября":"October", "ноября":"November", "декабря":"December",
  "День / 72":"Day / 72", "Ночь / 72":"Night / 72", "День / ночь / 48":"Day / night / 48", "2 через 2":"2 on / 2 off", "Пятидневка":"Five-day week",
  "Дневная кастомная":"Custom day shift", "Ночная кастомная":"Custom night shift",
  "Администраторы":"Administrators", "Будущие тарифы":"Future tiers", "Роли доступа":"Access roles", "Роль пользователя":"User role", "База данных":"Database", "Версия сервера":"Server version", "Источник настройки регистрации":"Registration setting source",
  "Аккаунт подключен к Telegram":"Account connected to Telegram", "Telegram уведомления":"Telegram notifications",
  "Служебная диагностика вынесена в отдельный профиль":"Service diagnostics are moved to a separate profile",
  "Время срока HH:mm, пусто — без времени":"Due time HH:mm, empty means no time", "Срок yyyy-MM-dd, пусто — без срока":"Due date yyyy-MM-dd, empty means no due date",
  "Текст задачи":"Task text", "Сценарий":"Scenario", "сценарий":"scenario", "сценарий переработки":"overtime scenario",
  "Конец смены HH:mm, можно пусто":"Shift end HH:mm, can be empty", "Начало смены HH:mm, можно пусто":"Shift start HH:mm, can be empty", "Обед/перерыв, минут":"Break, minutes", "Норма для расчёта переработки, ч":"Overtime calculation norm, h", "Цвет #RRGGBB":"Color #RRGGBB", "Уведомлять перед этой сменой? да/нет":"Notify before this shift? yes/no", "За сколько минут напоминать именно эту смену? Пусто = глобальная настройка":"How many minutes before this shift to remind? Empty = global setting",
  "время не настроено":"time is not configured", "обед 0 мин":"break 0 min", "план 0 ч":"plan 0 h", "короткий ввод":"short input", "полный интервал":"full interval", "сервер разобьёт по датам":"server will split by dates", "вычтено плана":"plan subtracted", "взято":"taken from",
  "от конца смены":"from shift end", "до конца смены":"until shift end", "от начала смены":"from shift start", "через 15 минут":"in 15 minutes",
  "значение по умолчанию":"default value", "по умолчанию":"default", "не задан":"not set", "не зарегистрирован":"not registered", "выключен":"off", "выключены":"off",
  "публичная регистрация закрыта":"public registration closed", "публичная регистрация открыта":"public registration open", "отчёт диагностики скопирован":"diagnostics report copied", "диагностика оффлайна скопирована":"offline diagnostics copied", "тестовое уведомление отправлено":"test notification sent", "Тестовое уведомление отправлено.":"Test notification sent.",
  "ошибка сети":"network error", "синхронизация не удалась":"sync failed", "операция не применена":"operation was not applied", "сервер не применил операцию":"server did not apply the operation", "Неизвестный тип операции: ":"Unknown operation type: ", "cookie не найден":"cookie not found",
  "Эта операция требует связи с сервером. Смена дня, заметки и галочки задач сохраняются оффлайн.":"This operation requires connection to the server. Day shift, notes, and task checkboxes are saved offline.",
  "Переработки и отгулы можно изменять только при подключении к серверу. Смены, заметки и галочки задач сохраняются оффлайн.":"Overtime and time off can be changed only when connected to the server. Shifts, notes, and task checkboxes are saved offline.",
  "Автозаполнение графика требует связи с сервером. Отдельную смену выбранного дня можно изменить оффлайн.":"Schedule autofill requires connection to the server. The selected day's shift can be changed offline.",
  "Типы смен и их расписание меняются только при подключении к серверу.":"Shift types and their schedule can be changed only when connected to the server.",
  "Шаблоны переработок меняются только при подключении к серверу.":"Overtime templates can be changed only when connected to the server.",
  "Настройки уведомлений требуют связи с сервером.":"Notification settings require connection to the server.",
  "Telegram-интеграция настраивается только при подключении к серверу.":"Telegram integration can be configured only when connected to the server.",
  "Профиль и сессии меняются только при подключении к серверу.":"Profile and sessions can be changed only when connected to the server.",
  "Важные даты меняются только при подключении к серверу.":"Important dates can be changed only when connected to the server.",
  "Админские настройки меняются только при подключении к серверу.":"Admin settings can be changed only when connected to the server.",
  "На текущий месяц напоминаний нет.":"No reminders for the current month.", "На завтра напоминаний нет.":"No reminders for tomorrow.", "Напоминания на завтра":"Tomorrow's reminders", "напоминания на завтра":"tomorrow's reminders",
  "На этот день в журнале переработок записей нет. Начисления не сгорают при переходе между месяцами.":"No overtime journal entries for this day. Credits do not expire when moving between months.",
  "Начислений переработки пока нет. Новые записи добавляются из панели выбранного дня и сохраняются до полного списания.":"No overtime credits yet. New entries are added from the selected-day panel and remain until fully used.",
  "По текущим фильтрам записей нет. Сбрось фильтры или выбери другой период.":"No entries for current filters. Reset filters or choose another period.", "По фильтрам задач нет.":"No tasks for the filters.", "По этим фильтрам задач нет.":"No tasks for these filters.",
  "День не выбран. Сначала ткни дату в календаре.":"No day selected. Pick a date in the calendar first.", "Поставь дневную, ночную или кастомную смену — тогда сценарии смогут взять время начала/конца.":"Set a day, night, or custom shift so scenarios can use start/end time.", "Карточки разблокируются, когда у выбранного дня будет смена со временем.":"Cards unlock when the selected day has a shift with time.", "Карточки только заполняют поля. Перед начислением можно поправить время, обед, план и причину.":"Cards only fill fields. You can adjust time, break, plan, and reason before crediting.", "У смены не указано время окончания. Откройте настройки смены и задайте время.":"The shift has no end time. Open shift settings and set it.", "Доступны сценарии от конца смены. Для сценариев от начала смены укажите время начала.":"Scenarios from shift end are available. For start-based scenarios, set shift start time.",
  "Все задачи выполнены":"All tasks done", "Есть заметка":"Has note", "Маркер дня":"Day marker", "Смены ещё не отмечены. Выберите день в календаре.":"No shifts marked yet. Select a day in the calendar.", "Сначала удали списания, которые используют это начисление":"Delete usages that use this credit first", "сначала списания":"delete usages first",
  "Создать или настроить смену в настройках":"Create or configure a shift in settings", "Изменить время, обед и плановые часы смены":"Change shift time, break, and planned hours", "Смена снимется с дней, где стояла. Заметки останутся.":"The shift will be removed from days where it was set. Notes will remain.",
  "Удалить быстрый сценарий?":"Delete quick scenario?", "Удалить важный день целиком, включая повторения?":"Delete the important day completely, including repeats?", "Удалить задачу?":"Delete task?", "Включить напоминание для этой задачи?":"Enable reminder for this task?",
  "назови сценарий":"name the scenario", "сценарий добавлен":"scenario added", "не получилось собрать интервал сценария":"failed to build scenario interval", "сценарию не хватает времени начала/конца смены":"scenario needs shift start/end time", "не получилось определить конец сценария":"failed to determine scenario end", "конец сценария должен быть позже начала":"scenario end must be after start",
  "для автоподсчёта нужны и начало, и конец":"start and end are required for auto calculation", "укажи часы переработки больше 0":"enter overtime hours greater than 0", "укажи дату переработки":"enter overtime date", "начисление не найдено":"credit not found", "укажи часы списания больше 0":"enter usage hours greater than 0", "укажи дату списания":"enter usage date", "списание не найдено":"usage not found",
  "укажи дату важного дня":"enter important day date", "укажи название важного дня":"enter important day title", "напиши текст задачи":"enter task text", "укажи название смены":"enter shift name", "название не может быть пустым":"name cannot be empty", "смена не найдена":"shift not found", "не хватает смен для шаблона":"not enough shifts for template", "количество дней: от 1 до 366":"number of days: 1 to 366",
  "обед: от 0 до 1440 минут":"break: 0 to 1440 minutes", "часы: от 0 до 24":"hours: 0 to 24", "норма: от 0 до 24 часов":"norm: 0 to 24 hours", "напоминание смены: от 0 до 1440 минут":"shift reminder: 0 to 1440 minutes", "не нашёл Дневную/Ночную смену":"could not find Day/Night shift", "встроенные смены обновлены":"built-in shifts updated", "время смен применено":"shift time applied", "настройки времени сохранены":"time settings saved",
  "на этом дне нет смены с плановыми часами":"selected day has no shift with planned hours", "у выбранной смены не указано время начала/конца":"selected shift has no start/end time", "на этом дне нет смены с плановыми часами для списания":"selected day has no shift with planned hours for usage",
  "Ошибка диагностики":"Diagnostics error", "Ошибка списка пользователей":"Users list error", "только администратор":"administrator only", "не удалось изменить роль":"failed to change role", "не удалось сменить пароль":"failed to change password", "пароль должен быть минимум 12 символов":"password must be at least 12 characters", "не удалось сохранить настройку регистрации":"failed to save registration setting",
  "Не удалось загрузить настройку регистрации: ":"Failed to load registration setting: ", "Состояние подключения. ":"Connection status. ", "Есть изменения, ожидающие отправки. Нажмите, чтобы открыть синхронизацию. ":"There are changes waiting to upload. Click to open sync. ", "Есть операции, которые сервер не принял. Нажмите, чтобы открыть синхронизацию. ":"There are operations the server did not accept. Click to open sync. "
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие", "Browser":"Браузер" });
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие", "Browser":"Браузер" });



// v24.0.4: final small i18n cleanups reported from real UI.
Object.assign(I18N_EN, {
  "не отмечена":"not marked",
  "Не отмечена":"Not marked",
  "Формат даты":"Date format",
  "Формат даты: дд.мм.гггг":"Date format: yyyy-mm-dd",
  "Формат даты и времени: дд.мм.гггг чч:мм":"Date and time format: yyyy-mm-dd hh:mm",
  "дд.мм.гггг":"yyyy-mm-dd",
  "ДД.ММ.ГГГГ":"YYYY-MM-DD",
  "сохраняется автоматически":"saved automatically",
  "редактор":"editor",
  "превью":"preview",
  "Пусто. Пиши слева — превью живое.":"Empty. Write on the left — preview updates live.",
  "# Заголовок\n**жирный**, *курсив*, `код`\n- [ ] задача\n- список":"# Heading\n**bold**, *italic*, `code`\n- [ ] task\n- list",
  "# Заголовок\n**жирный**, *курсив*, `код`\n- [ ] задача\n> цитата\n```\nкод блоком\n```":"# Heading\n**bold**, *italic*, `code`\n- [ ] task\n> quote\n```\ncode block\n```",
  "рабочее время":"work time",
  "пометка":"note",
  "Москва":"Moscow",
  "24 часа":"24 hours",
  "Шаблоны смен":"Shift templates",
  "Закрыть (Esc)":"Close (Esc)",
  "Итого:":"Total:",
  "Итого":"Total",
  "шт":"pcs",
  "автосохранение":"autosave",
  "рабочее время":"work time",
  "браузер":"browser",
  "браузер:":"browser:",
  "рабочее время:":"work time:",
  "Дневная":"Day shift",
  "Ночная":"Night shift",
  "Выходной":"Day off",
  "Дневная кастомная":"Custom day shift",
  "Ночная кастомная":"Custom night shift",
  "конец должен быть позже":"end must be later",
  "проверь обед/план":"check break/plan",
  "итого 0 или меньше":"total is 0 or less",
  "нужны начало и конец":"start and end required",
  "Сценарии пока не созданы. Добавьте первый сценарий в редакторе переработки.":"No scenarios yet. Add the first scenario in the overtime editor.",
  "сценарий":"scenario",
  "Сценарий":"Scenario",
  "без уведомлений":"notifications off",
  "напомнить за":"remind before",
  "да":"yes",
  "cookie есть":"cookie present",
  "активен":"active",
  "не зарегистрирован":"not registered",
  "не поддерживается":"not supported",
  "из админки":"from admin",
  "админ":"admin",
  "пользователь":"user",
  "Роль пользователя":"User role",
  "Не удалось загрузить сессии.":"Failed to load sessions.",
  "удалить сценарий":"delete scenario",
  "на следующий день":"next day",
  "план":"plan",
  "обед":"break",
  "2 через 2":"2 on / 2 off",
  "День / ночь / 48":"Day / night / 48",
  "Пятидневка":"Five-day week",
  "День / 72":"Day / 72",
  "Ночь / 72":"Night / 72",
  "2 через 2: день / день / выходной / выходной":"2 on / 2 off: day / day / off / off",
  "День / ночь / 48 часов отдыха":"Day / night / 48 hours off",
  "Пятидневка: Пн–Пт рабочие / Сб–Вс выходные":"Five-day week: Mon–Fri work / Sat–Sun off",
  "День / 72 часа отдыха":"Day / 72 hours off",
  "Ночь / 72 часа отдыха":"Night / 72 hours off",
  "Дней:":"Days:"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие", "Browser":"Браузер" });


// v27.2.5: admin navigation labels and notification polish copy.
Object.assign(I18N_EN, {
  "Админка":"Admin",
  "Регистрация":"Registration",
  "роли и пароли":"roles and passwords",
  "доступ новых аккаунтов":"new account access",
  "сервер, база, кэш":"server, database, cache",
  "назад к настройкам":"back to settings"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));
Object.assign(I18N_RU, { "open":"открыть", "Time":"Время", "normal":"обычные", "light":"светлая", "soft":"мягкие", "Browser":"Браузер" });

function translateDynamicEn(core){
  let s = String(core ?? "");
  const exact = I18N_EN[s];
  if (exact) return exact;
  const patterns = [
    [/^(\d+) шт$/, "$1 pcs"],
    [/^(\d+) из (\d+)$/, "$1 of $2"],
    [/^(\d+) просроч\. · (\d+)\/(\d+)$/, "$1 overdue · $2/$3"],
    [/^(\d+)\/(\d+) сделано$/, "$1/$2 done"],
    [/^за день ([+\-]?\d+(?:[.,]\d+)?) ч$/, "day: $1 h"],
    [/^баланс ([+\-]?\d+(?:[.,]\d+)?) ч$/, "balance $1 h"],
    [/^доступно ([+\-]?\d+(?:[.,]\d+)?) ч$/, "available $1 h"],
    [/^переработка: ([+\-]?\d+(?:[.,]\d+)?) ч$/, "overtime: $1 h"],
    [/^общий остаток переработки: ([+\-]?\d+(?:[.,]\d+)?) ч$/, "total overtime balance: $1 h"],
    [/^Начислено: ([\d.,]+) ч, списано: ([\d.,]+) ч$/, "Earned: $1 h, used: $2 h"],
    [/^Всего начислено: ([\d.,]+) ч, всего списано: ([\d.,]+) ч$/, "Total earned: $1 h, total used: $2 h"],
    [/^Просроченные задачи: (\d+)$/, "Overdue tasks: $1"],
    [/^Невыполненные задачи: (\d+)$/, "Open tasks: $1"],
    [/^Напоминания: (\d+)$/, "Reminders: $1"],
    [/^Открыть день (.+)$/, "Open day $1"],
    [/^Маркер дня$/, "Day marker"],
    [/^Поставить маркер (.+)$/, "Set marker $1"],
    [/^В календаре будет видно: (.+)$/, "Visible in calendar: $1"],
    [/^Публичная регистрация: (.+) · (.+)$/, "Public registration: $1 · $2"],
    [/^Не удалось загрузить настройку регистрации: (.+)$/, "Failed to load registration setting: $1"],
    [/^роль (.+): (.+)$/, "role $1: $2"],
    [/^пароль (.+) обновлён$/, "password for $1 updated"],
    [/^Новый пароль для (.+) \(минимум 12 символов\)$/, "New password for $1 (minimum 12 characters)"],
    [/^Редактируется начисление #(\d+)\. Если сделать период через несколько дат, сервер заменит строку на несколько начислений\.$/, "Editing credit #$1. If you make a multi-day period, the server will replace the row with several credits."],
    [/^Редактируется списание #(\d+)\. Если изменить часы, FIFO-распределение пересоберётся заново\.$/, "Editing usage #$1. If you change hours, FIFO allocation will be rebuilt."],
    [/^Удалить начисление переработки (.+)\?\n\nЭто действие нельзя отменить\.$/, "Delete overtime credit $1?\n\nThis action cannot be undone."],
    [/^Удалить списание отгула (.+)\?\n\nЧасы вернутся в остаток переработки\.$/, "Delete time-off usage $1?\n\nHours will return to the overtime balance."],
    [/^С днём рождения, (.+)! Смену сегодня прогуливаем\?$/, "Happy birthday, $1! Skipping the shift today?"],
    [/^🎉 С днём рождения, (.+)! Смену сегодня прогуливаем\?$/, "🎉 Happy birthday, $1! Skipping the shift today?"],
    [/^(\d+) мин назад$/, "$1 min ago"],
    [/^(\d+) ч назад$/, "$1 h ago"],
    [/^(\d+) дн назад$/, "$1 d ago"],
    [/^(\d+) ожидает отправки$/, "$1 pending upload"],
    [/^(\d+) не применилось$/, "$1 failed"],
    [/^Задача #(\d+): выполнена$/, "Task #$1: done"],
    [/^Задача #(\d+): открыта$/, "Task #$1: open"],
    [/^последняя синхронизация: (.+)$/i, "last sync: $1"],
    [/^Последняя синхронизация: (.+)$/, "Last sync: $1"],
    [/^Шаблон от выбранного дня: (.+)\. Заметки не стираются, меняется только тип смены\.$/, "Template from selected day: $1. Notes are not deleted; only shift type changes."],
    [/^Не хватает смен: (.+)\. Перезагрузи страницу или создай их вручную\.$/, "Missing shifts: $1. Reload the page or create them manually."],
    [/^Пятидневка привязана к дням недели: Пн–Пт рабочие, Сб–Вс выходные\. От выбранного дня пойдёт так: (.+)\.$/, "Five-day week is tied to weekdays: Mon–Fri work, Sat–Sun off. From the selected day it will go: $1."],
    [/^(.+) · смена не выбрана$/, "$1 · no shift selected"],
    [/^(.+) · (.+): (.+), (.+), (.+)$/, "$1 · $2: $3, $4, $5"]
  ];
  for (const [re, repl] of patterns) {
    if (re.test(s)) return s.replace(re, repl);
  }
  const replacePairs = [
    ["пользователем", "by"], ["создан", "created"], ["обновлён", "updated"],
    ["на странице", "on page"], ["показано", "shown"], ["всего по фильтрам", "total filtered"],
    ["просроченных", "overdue"], ["открытых", "open"], ["выполненных", "done"],
    ["закрыто", "closed"], ["использовано", "used"], ["остаток", "balance"],
    ["начислено", "earned"], ["списано", "used"], ["не списывалось", "not used"],
    ["сначала списания", "delete usages first"], ["удалить", "delete"], ["ред.", "edit"],
    ["настроить", "configure"], ["встроенная", "built-in"], ["автосохранение", "autosave"],
    ["сохраняю…", "saving…"], ["ошибка сети", "network error"], ["ошибка", "error"],
    ["проверяю…", "checking…"], ["проверь", "check"], ["срок", "due"],
    ["просрочено", "overdue"], ["выполнено", "done"], ["обед", "break"], ["план", "plan"], ["норма", "norm"],
    ["сегодня", "today"], ["выбранный день", "selected day"], ["свой цвет", "custom color"],
    ["частично", "partial"], ["закрыта", "closed"], ["дата", "date"], ["смена", "shift"], ["заметка", "note"], ["день", "day"],
    ["выполнена", "done"], ["открыта", "open"], ["только что", "just now"], ["ещё нет", "not yet"],
    ["локальной копии пока нет", "no local copy yet"], ["нет локальной копии", "no local copy"], ["нет синхронизации", "no sync"],
    ["данные устарели", "data is stale"], ["данные от", "data from"], ["не отправлено", "not uploaded"],
    ["синхронизация…", "syncing…"], ["синхронизация в другой вкладке", "syncing in another tab"],
    ["активен в другой вкладке", "active in another tab"], ["активен в этой вкладке", "active in this tab"], ["протух", "expired"], ["не читается", "unreadable"],
    ["доступна", "available"], ["недоступна", "unavailable"], ["онлайн", "online"], ["оффлайн", "offline"]
  ];
  if (/[А-Яа-яЁё]/.test(s) && /(ч|мин|дн|обед|план|норма|срок|создан|обновл|страниц|показано|на странице|синхронизац|остаток|баланс|ошибка|удалить|ред\.|настроить|встроенная|просроч|выполн|открыт|данные|локальн|онлайн|оффлайн|пользовател)/i.test(s)) {
    for (const [from, to] of replacePairs) s = s.split(from).join(to);
    s = s.replace(/([\d.,]+) ч\b/g, "$1 h").replace(/([\d.,]+)м\b/g, "$1 min").replace(/([\d.,]+) мин\b/g, "$1 min").replace(/([\d.,]+) дн\b/g, "$1 d");
    return s;
  }
  return core;
}

function t(value){
  const s = String(value ?? "");
  if (state.language === "en") return I18N_EN[s] || translateDynamicEn(s) || s;
  return I18N_RU[s] || s;
}
function shiftDisplayName(shiftOrName){
  const name = typeof shiftOrName === "string" ? shiftOrName : (shiftOrName?.name || "");
  return ["Дневная", "Ночная", "Выходной", "Дневная кастомная", "Ночная кастомная"].includes(name) ? t(name) : name;
}
function scheduleTemplateLabel(tpl){
  return tpl ? t(tpl.label) : "";
}
function localUnit(value){ return state.language === "en" ? String(value || "").replace(/ч/g, "h").replace(/м/g, "min") : value; }

function htmlSafe(value){
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
function emptyStateHtml({ icon = "•", title = "", text = "", actionText = "", actionId = "", variant = "" } = {}){
  const action = actionText && actionId ? `<button type="button" id="${htmlSafe(actionId)}">${htmlSafe(t(actionText))}</button>` : "";
  return `<div class="emptyState${variant ? " " + htmlSafe(variant) : ""}" role="status">
    <div class="emptyIcon" aria-hidden="true">${htmlSafe(icon)}</div>
    <div class="emptyBody">
      ${title ? `<b>${htmlSafe(t(title))}</b>` : ""}
      ${text ? `<span>${htmlSafe(t(text))}</span>` : ""}
      ${action}
    </div>
  </div>`;
}
function renderEmptyState(target, options = {}){
  const el = typeof target === "string" ? $(target) : target;
  if (!el) return;
  el.innerHTML = emptyStateHtml(options);
}
function renderLoadingState(target, text = "загрузка…", rows = 3){
  const el = typeof target === "string" ? $(target) : target;
  if (!el) return;
  el.innerHTML = `<div class="loadingState" role="status" aria-live="polite">
    <span>${htmlSafe(t(text))}</span>
    ${Array.from({ length: rows }).map(() => '<i></i>').join("")}
  </div>`;
}
function setAppBooting(booting, text = "загрузка…"){
  if (!state.ui) state.ui = {};
  state.ui.booting = !!booting;
  if (document.body) document.body.classList.toggle("appBooting", !!booting);
  const el = $("appBootStatus");
  if (el) el.textContent = t(text);
}
function hideBootOnStartupError(message = "ошибка загрузки"){
  try { setAppBooting(false); } catch (_) { if (document.body) document.body.classList.remove("appBooting"); }
  try { setSave("err", t(message)); } catch (_) { /* UI may not be ready yet */ }
}

Object.assign(I18N_EN, {
  "Подготавливаем интерфейс…":"Preparing the interface…",
  "Загружаю модули…":"Loading modules…",
  "Загружаю календарь…":"Loading calendar…",
  "Загружаю задачи…":"Loading tasks…",
  "Загружаю переработки…":"Loading overtime…",
  "Данных пока нет":"No data yet",
  "Пустой список":"Empty list",
  "Ничего не найдено":"Nothing found",
  "Попробуй сбросить фильтры или выбрать другой период.":"Try clearing filters or choosing another period.",
  "Добавь запись из панели выбранного дня в календаре.":"Add a record from the selected day panel in Calendar.",
  "Выбери день в календаре и добавь первую задачу.":"Select a day in Calendar and add your first task.",
  "Важных дат пока нет.":"No important dates yet.",
  "В этот день важных событий нет.":"No important events on this day.",
  "Задач пока нет. После добавления открытые задачи будут отмечены в календаре.":"No tasks yet. After adding, open tasks will be marked in the calendar.",
  "По фильтрам задач нет.":"No tasks match the filters.",
  "По этим фильтрам задач нет.":"No tasks match these filters.",
  "Начислений переработки пока нет. Новые записи добавляются из панели выбранного дня и сохраняются до полного списания.":"No overtime credits yet. New records are added from the selected day panel and remain until fully used.",
  "По текущим фильтрам записей нет. Сбрось фильтры или выбери другой период.":"No records match the current filters. Clear filters or choose another period.",
  "Отключено, данные сохранены":"Disabled, data preserved",
  "Отключено. Данные сохранены.":"Disabled. Data preserved.",
  "Можно включить обратно.":"Can be enabled again.",
  "Технические детали":"Technical details",
  "Модуль можно включить обратно в любой момент. Данные остаются в базе и локальный оффлайн-снимок очищается только от лишнего отображения.":"You can enable the module again at any time. Data stays in the database; the local offline snapshot is only cleaned for display.",
  "Включены":"Enabled",
  "Выключены":"Disabled",
  "Базовые":"Basic",
  "скрыто":"hidden",
  "модуль отключён":"module disabled",
  "Данные не удаляются":"Data is not deleted",
  "Отлично. Здесь пока чисто.":"Great. Nothing here yet.",
  "нет записей":"no records",
  "загрузка заняла слишком много времени — интерфейс разблокирован":"Loading is taking too long — the interface has been unlocked",
  "ошибка загрузки":"loading error"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

function translateTextValue(value){
  if (!value || !String(value).trim()) return value;
  const raw = String(value);
  const leading = raw.match(/^\s*/)?.[0] || "";
  const trailing = raw.match(/\s*$/)?.[0] || "";
  const core = raw.trim();
  const map = state.language === "en" ? I18N_EN : I18N_RU;
  if (Object.prototype.hasOwnProperty.call(map, core)) return leading + map[core] + trailing;
  if (state.language === "en") {
    const dynamic = translateDynamicEn(core);
    if (dynamic !== core) return leading + dynamic + trailing;
  }
  return value;
}
let translationBusy = false;
function translateStaticTree(root = document.body){
  if (!root || translationBusy) return;
  translationBusy = true;
  try {
    const skip = el => el && el.closest && el.closest('script,style,textarea,code,pre,[data-no-i18n]');
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, { acceptNode(node){
      const parent = node.parentElement;
      if (!parent || skip(parent)) return NodeFilter.FILTER_REJECT;
      return /[A-Za-zА-Яа-яЁё]/.test(node.nodeValue || "") ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
    }});
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    for (const node of nodes) {
      const next = translateTextValue(node.nodeValue);
      if (next !== node.nodeValue) node.nodeValue = next;
    }
    const attrs = ['placeholder','title','aria-label'];
    const skipAttr = el => el && el.closest && el.closest('script,style,code,pre,[data-no-i18n]');
    for (const el of root.querySelectorAll ? root.querySelectorAll('*') : []) {
      if (skipAttr(el)) continue;
      for (const attr of attrs) {
        if (el.hasAttribute(attr)) {
          const old = el.getAttribute(attr);
          const next = translateTextValue(old);
          if (next !== old) el.setAttribute(attr, next);
        }
      }
    }
  } finally {
    translationBusy = false;
  }
}
let translationObserverReady = false;
function ensureTranslationObserver(){
  if (translationObserverReady || !document.body) return;
  translationObserverReady = true;
  const observer = new MutationObserver(() => {
    if (translationBusy) return;
    clearTimeout(window.__dutylogI18nTimer);
    window.__dutylogI18nTimer = setTimeout(() => translateStaticTree(), 40);
  });
  observer.observe(document.body, { childList:true, subtree:true, characterData:true, attributes:true, attributeFilter:['placeholder','title','aria-label'] });
}
function applyLanguagePolish(){
  const en = state.language === "en";
  const dateLang = en ? "en-CA" : "ru-RU";
  document.querySelectorAll('input[type="date"], input[type="datetime-local"]').forEach(el => {
    el.setAttribute('lang', dateLang);
    if (el.type === 'date') el.setAttribute('title', en ? 'Date format: yyyy-mm-dd' : 'Формат даты: дд.мм.гггг');
    if (el.type === 'datetime-local') el.setAttribute('title', en ? 'Date and time format: yyyy-mm-dd hh:mm' : 'Формат даты и времени: дд.мм.гггг чч:мм');
  });
  const setText = (id, ru) => { const el = $(id); if (el) el.textContent = t(ru); };
  setText('todayBtn', 'Сегодня');
  setText('creditDateToday', 'сегодня');
  setText('impDateToday', 'сегодня');
  setText('tabEdit', 'Заметка');
  setText('tabPrev', 'Превью');
  const noteExpand = $('noteExpand');
  if (noteExpand) { noteExpand.textContent = t('⛶ развернуть'); noteExpand.title = t('Редактор на весь экран'); }
  const noteEdit = $('noteEdit');
  if (noteEdit) noteEdit.placeholder = en
    ? '# Heading\n**bold**, *italic*, `code`\n- [ ] task\n- list'
    : '# Заголовок\n**жирный**, *курсив*, `код`\n- [ ] задача\n- список';
  const noteFsEdit = $('noteFsEdit');
  if (noteFsEdit) noteFsEdit.placeholder = en
    ? '# Heading\n**bold**, *italic*, `code`\n- [ ] task\n> quote\n```\ncode block\n```'
    : '# Заголовок\n**жирный**, *курсив*, `код`\n- [ ] задача\n> цитата\n```\nкод блоком\n```';
  const noteFsTitle = document.querySelector('.noteFsTitle');
  if (noteFsTitle) noteFsTitle.innerHTML = `${esc(t('Заметка'))} <span class="noteFsAuto">${esc(t('сохраняется автоматически'))}</span>`;
  const noteFsTab = $('noteFsTab');
  if (noteFsTab) noteFsTab.textContent = t(($('noteFullscreen')?.classList.contains('showPrev')) ? 'редактор' : 'превью');
  const noteFsClose = $('noteFsClose');
  if (noteFsClose) noteFsClose.title = t('Закрыть (Esc)');
}
function applyLanguage(lang){
  state.language = normalizeLanguage(lang);
  try { localStorage.setItem(LANGUAGE_KEY, state.language); } catch (_) {}
  document.documentElement.lang = state.language;
  document.title = 'DutyLog: Time & Overtime';
  renderLanguageControls();
  if (typeof renderCalendar === 'function') renderCalendar();
  if (typeof updateShiftPlanHint === 'function') updateShiftPlanHint();
  if (typeof renderTelegramPanel === 'function') renderTelegramPanel();
  if (typeof renderModuleSettings === 'function') renderModuleSettings();
  if (typeof renderSettingsPanels === 'function') renderSettingsPanels();
  if (typeof renderSelectedDayModules === 'function') renderSelectedDayModules();
  applyLanguagePolish();
  translateStaticTree();
  applyLanguagePolish();
}
function renderLanguageControls(){
  document.querySelectorAll('[data-language-choice]').forEach(btn => btn.classList.toggle('on', btn.dataset.languageChoice === state.language));
  const status = document.getElementById('languageStatus');
  if (status) status.textContent = state.language === 'en' ? 'English' : 'Русский';
}

const TIME_SETTINGS_KEY = "shiftCalendar.timeRegionSettings.v1";
const DEFAULT_TIME_SETTINGS = {
  workRegionName: "",
  workTimezone: "Europe/Moscow",
  displayTimezone: "Europe/Moscow",
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
  const browserZone = browserTimeZone();
  return { ...DEFAULT_TIME_SETTINGS, workTimezone:browserZone, displayTimezone:browserZone, ...saved };
}
function storeTimeSettings(settings){
  state.timeSettings = { ...DEFAULT_TIME_SETTINGS, ...settings };
  try { localStorage.setItem(TIME_SETTINGS_KEY, JSON.stringify(state.timeSettings)); }
  catch (e) { console.warn("time settings not saved", e); }
}
function safeTzLabel(tz){
  try {
    return new Intl.DateTimeFormat(currentLocale(), { dateStyle:"short", timeStyle:"short", timeZone:tz }).format(new Date());
  } catch (e) {
    return t("часовой пояс не распознан");
  }
}
function displayTimeZone(){
  return state.timeSettings?.displayTimezone
    || state.profile?.displayTimezone
    || state.timeSettings?.workTimezone
    || state.profile?.workTimezone
    || browserTimeZone();
}
function timestampHasExplicitZone(value){
  return /(?:Z|[+-]\d{2}:?\d{2})$/i.test(String(value || "").trim());
}
function legacyLocalTimestampLabel(value){
  return String(value || "").slice(0, 16).replace("T", " ");
}
function formatAbsoluteInstant(value, options = { dateStyle:"short", timeStyle:"short" }){
  if (!value) return "";
  // A LocalDateTime without Z/offset has no globally correct projection.
  // Keep legacy values stable instead of guessing the browser or server zone.
  if (!timestampHasExplicitZone(value)) return legacyLocalTimestampLabel(value);
  try {
    const instant = new Date(value);
    if (!Number.isFinite(instant.getTime())) return legacyLocalTimestampLabel(value);
    return new Intl.DateTimeFormat(currentLocale(), { ...options, timeZone:displayTimeZone() }).format(instant);
  } catch (_) {
    return legacyLocalTimestampLabel(value);
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
  const modeLabel = t(prefs.themePreference === "system" ? "как в системе" : prefs.themePreference === "light" ? "светлая" : "тёмная");
  if (preview) {
    preview.className = "status statusThemeSummary";
    preview.innerHTML = `<span class="statusChip statusChipPrimary">${esc(presetLabel)}</span><span class="statusChip">${esc(modeLabel)}</span><span class="statusChip statusChipAccent"><span class="statusChipSwatch" style="background:${prefs.accentColor}"></span>${esc(prefs.accentColor)}</span>`;
  }
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
function dateKeyInTimeZone(timeZone){
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: timeZone || browserTimeZone(), year:"numeric", month:"2-digit", day:"2-digit"
    }).formatToParts(new Date());
    const values = Object.fromEntries(parts.filter(p => p.type !== "literal").map(p => [p.type, p.value]));
    return `${values.year}-${values.month}-${values.day}`;
  } catch (_) {
    const d = new Date();
    return keyOf(d.getFullYear(), d.getMonth(), d.getDate());
  }
}
const todayKey = () => dateKeyInTimeZone(state.timeSettings?.workTimezone || browserTimeZone());
const numOr0 = v => { const n = Number(v ?? 0); return Number.isFinite(n) ? n : 0; };
const fmtHours = v => {
  const n = Math.round(numOr0(v) * 100) / 100;
  return n.toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1");
};
function timeToMinutes(hhmm){
  const m = String(hhmm || "").match(/^(\d{2}):(\d{2})$/);
  if (!m) return null;
  const h = Number(m[1]), min = Number(m[2]);
  if (!Number.isFinite(h) || !Number.isFinite(min) || h > 23 || min > 59) return null;
  return h * 60 + min;
}
function shiftDurationHours(startTime, endTime, breakMinutes = 0){
  const start = timeToMinutes(startTime);
  const endRaw = timeToMinutes(endTime);
  const br = Number(breakMinutes || 0);
  if (start == null || endRaw == null || !Number.isFinite(br) || br < 0) return 0;
  let end = endRaw;
  if (end <= start) end += 24 * 60;
  const total = Math.max(0, end - start - br);
  return Math.round((total / 60) * 100) / 100;
}
function currentShiftFormNorm(){
  return shiftDurationHours($("nsStart")?.value, $("nsEnd")?.value, readIntInput("nsBreak"));
}
function updateShiftPlanHint(){
  const hint = $("shiftPlanHint");
  const norm = currentShiftFormNorm();
  if ($("nsPlan") && !String($("nsPlan").value || "").trim()) $("nsPlan").placeholder = norm ? fmtHours(norm) : "авто";
  if ($("nsHours") && !String($("nsHours").value || "").trim()) $("nsHours").placeholder = norm ? fmtHours(norm) : "ч";
  if (hint) hint.textContent = norm
    ? `${t("Норма рассчитана по времени смены:")} ${fmtHours(norm)} ${state.language === "en" ? "h" : "ч"}. ${t("Календарь, ч — короткая метка для календаря. Норма, ч — сколько часов вычитается при расчёте переработки. Если оставить норму пустой, она посчитается по началу, концу и обеду.")}`
    : t("Календарь, ч — короткая метка для календаря. Норма, ч — сколько часов вычитается при расчёте переработки. Если оставить норму пустой, она посчитается по началу, концу и обеду.");
}
const normalizeDay = e => ({
  shiftTypeId: e?.shiftTypeId ?? null,
  note: e?.note ?? null,
  dayEmoji: e?.dayEmoji ?? null,
  overtimeHours: numOr0(e?.overtimeHours),
  timeOffHours: numOr0(e?.timeOffHours),
  shiftInterval: e?.shiftInterval ?? null,
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
function taskPriorityLabel(p){ return p === "URGENT" ? t("срочные") : p === "HIGH" ? t("важные") : p === "LOW" ? t("низкие") : t("обычно"); }
function taskDueLabel(t){
  if (!t.dueDate) return "";
  const d = t.dueDate.split("-").reverse().join(".");
  return `📅 ${d}${t.dueTime ? " " + t.dueTime : ""}`;
}
function allTaskCategories(){
  const set = new Set(state.taskMetadata?.categories || []);
  for (const arr of Object.values(state.tasksByDate)) for (const t of arr) if ((t.category || "").trim()) set.add(t.category.trim().toLowerCase());
  for (const t of state.taskBoard?.items || []) if ((t.category || "").trim()) set.add(t.category.trim().toLowerCase());
  return Array.from(set).filter(Boolean).sort((a,b) => a.localeCompare(b, currentLocale()));
}
function allTaskTags(){
  const set = new Set(state.taskMetadata?.tags || []);
  for (const arr of Object.values(state.tasksByDate)) for (const task of arr) for (const tag of task.tags || []) if (String(tag).trim()) set.add(String(tag).trim().toLowerCase());
  for (const task of state.taskBoard?.items || []) for (const tag of task.tags || []) if (String(tag).trim()) set.add(String(tag).trim().toLowerCase());
  return Array.from(set).filter(Boolean).sort((a,b) => a.localeCompare(b, currentLocale()));
}
const repeatLabel = mode => t(mode === "YEARLY" ? "каждый год" : mode === "MONTHLY" ? "каждый месяц" : "один раз");
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
  return `${s.startTime}–${s.endTime}${br ? ` · ${t("обед")} ${br}${t("м")}` : ""}`;
}
function shiftMetaText(s){
  if (!s) return "";
  const parts = [];
  const time = shiftTimeText(s);
  if (time) parts.push(time);
  const plan = shiftPlannedHours(s);
  if (plan) parts.push(`${state.language === "en" ? "norm" : "норма"} ${fmtHours(plan)}ч`);
  return parts.join(" · " );
}
