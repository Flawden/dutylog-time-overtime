
"use strict";

const DUTYLOG_VERSION = "24.0.4"

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
  adminUsersPage: { page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false },
  preferences: { themePreference:"system", accentColor:"#F5B841" },
  language: initialLanguage(),
  activeScenarioId: null,
  ledgerFilters: { from:"", to:"", status:"all", q:"" },
  ledgerPage: { items: [], page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false },
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
  "Настройки":"Settings", "Профиль":"Profile", "Язык":"Language", "русский / English":"Russian / English",
  "Внешний вид":"Appearance", "Время":"Time", "Смены":"Shifts", "Сценарии":"Scenarios", "Уведомления":"Notifications", "Важные даты":"Important dates",
  "имя, пароль, Telegram":"name, password, Telegram", "тема, акцент, маркеры":"theme, accent, markers", "регион, пояс, дефолты":"region, timezone, defaults", "типы, часы, уведомления":"types, hours, notifications", "шаблоны переработок":"overtime templates", "браузер и расписания":"browser and schedules", "общий список событий":"shared event list",
  "развернуть всё":"expand all", "свернуть всё":"collapse all", "открыть":"open", "свернуть":"collapse",
  "Профиль пользователя":"User profile", "Отображаемое имя":"Display name", "День рождения":"Birthday", "Сохранить":"Save",
  "Смена пароля":"Change password", "Текущий пароль":"Current password", "Новый пароль":"New password", "Ещё раз":"Repeat", "Сменить пароль":"Change password", "Активные устройства":"Active devices", "Telegram-бот":"Telegram bot",
  "Интерфейс":"Interface", "Язык приложения":"App language", "Русский":"Russian", "Основной язык":"Main language", "Дополнительный язык":"Additional language", "Язык сохранён":"Language saved",
  "Персонализация":"Personalization", "Пресет":"Preset", "Готовая тема":"Theme preset", "Базовый режим":"Base mode", "Акцент":"Accent", "Точная настройка":"Fine tuning", "Фон приложения":"App background", "Карточки":"Cards", "Внутренние блоки":"Inner blocks", "Основной текст":"Primary text", "Вторичный текст":"Secondary text", "Границы":"Borders", "Стиль кнопок":"Button style", "Стиль карточек":"Card style", "Тени":"Shadows", "Плотность":"Density", "Скругление карточек":"Card radius", "Сохранить внешний вид":"Save appearance", "Сбросить локально":"Reset locally",
  "как в системе":"system", "тёмная":"dark", "светлая":"light", "заливка":"solid", "мягкие":"soft", "контурные":"outline", "призрачные":"ghost", "стандартные":"standard", "плоские":"flat", "контрастные":"contrast", "тёплые":"warm", "без теней":"no shadows", "лёгкие":"light", "средние":"medium", "сильные":"strong", "компактно":"compact", "обычно":"comfortable", "просторно":"spacious",
  "Время и регион":"Time and region", "Рабочее время и часовой пояс":"Working hours and timezone", "Регион / объект":"Region / site", "Рабочий часовой пояс":"Work timezone", "Определить часовой пояс":"Detect timezone", "Формат времени":"Time format", "Сохранить настройки":"Save settings",
  "Типы смен и их время":"Shift types and time", "Короткие часы для календаря":"Short calendar hours", "Календарь, ч":"Calendar label, h", "Норма, ч":"Norm, h", "Название смены":"Shift name", "Добавить":"Add", "Сохранить параметры смен":"Save shift settings", "Дневная":"Day shift", "Ночная":"Night shift", "Выходной":"Day off",
  "Быстрые сценарии":"Quick scenarios", "Мои сценарии":"My scenarios", "Добавить сценарий":"Add scenario", "Название":"Name", "Старт":"Start", "Конец":"End", "Обед":"Break", "План":"Plan", "Норма":"Norm", "Причина по умолчанию":"Default reason", "Описание сценария":"Scenario description",
  "Уведомления браузера":"Browser notifications", "Разрешить в браузере":"Allow in browser", "Напоминания текущего месяца":"Current month reminders", "Проверить":"Check", "Текущий месяц":"Current month", "Завтра":"Tomorrow", "Сервер рассчитывает напоминания для браузера, Telegram и мобильных клиентов.":"The server calculates reminders for browser, Telegram and mobile clients.",
  "Календарь":"Calendar", "Переработки":"Overtime", "Задачи":"Tasks", "Сегодня":"Today", "Система":"System", "Выйти":"Logout",
  "Смена":"Shift", "Маркер":"Marker", "График":"Schedule", "Переработка":"Overtime", "Важные дни":"Important days", "Заметка":"Note", "Превью":"Preview", "Очистить":"Clear", "Поставить":"Apply", "Заполнить":"Fill", "выбранный день":"selected day", "сегодня":"today", "Начислить":"Add credit", "Списать отгул":"Use time off", "отмена":"cancel",
  "Таблица переработок":"Overtime ledger", "Начислено":"Earned", "Использовано":"Used", "Куда списано":"Used for", "Остаток":"Remaining", "Причина":"Reason", "День":"Day", "Время":"Time", "этот месяц":"this month", "всё время":"all time", "сброс":"reset", "все начисления":"all credits", "только с остатком":"only remaining", "частично списанные":"partially used", "полностью списанные":"fully used",
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
  "Регион / объект":"Region / site",
  "Например: участок, город, объект":"Example: site, city, facility",
  "Рабочий часовой пояс":"Work timezone",
  "Сдвиг от Москвы, ч":"Offset from Moscow, h",
  "Формат времени":"Time format",
  "24 часа":"24 hours",
  "Шаблоны смен":"Shift presets",
  "Обед, мин":"Break, min",
  "План, ч":"Plan, h",
  "Заполнить форму смены":"Fill shift form",
  "Сохранить параметры смен":"Save shift parameters",
  "Определить часовой пояс":"Detect timezone",
  "Регион и часовой пояс используются для отображения времени. Параметры дневной и ночной смены применяются к встроенным типам смен и используются в уведомлениях, сценариях и расчётах.":"Region and timezone are used to display time. Day/night shift parameters are applied to built-in shift types and used in notifications, scenarios, and calculations.",
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
  "Закрыть (Esc)":"Close (Esc)"
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
    return new Intl.DateTimeFormat(currentLocale(), { dateStyle:"short", timeStyle:"short", timeZone:tz }).format(new Date());
  } catch (e) {
    return t("часовой пояс не распознан");
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
const todayKey = () => { const t = new Date(); return keyOf(t.getFullYear(), t.getMonth(), t.getDate()); };
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
  return `${state.language === "en" ? "due" : "срок"} ${d}${t.dueTime ? " " + t.dueTime : ""}`;
}
function allTaskCategories(){
  const set = new Set();
  for (const arr of Object.values(state.tasksByDate)) for (const t of arr) if ((t.category || "").trim()) set.add(t.category.trim());
  for (const t of state.taskBoard?.items || []) if ((t.category || "").trim()) set.add(t.category.trim());
  return Array.from(set).sort((a,b) => a.localeCompare(b, "ru"));
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
  async overtimeAccountPage(filters = {}) { const qs = new URLSearchParams(); for (const [k, v] of Object.entries(filters)) if (v !== undefined && v !== null && String(v).trim() !== "") qs.set(k, v); return jfetch(`/api/overtime/account-page?${qs.toString()}`); },
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
  async adminUsers(params = {}) { const qs = new URLSearchParams(); for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && String(v).trim() !== "") qs.set(k, v); return jfetch(`/api/admin/users?${qs.toString()}`); },
  async updateAdminUserRole(id, role) { return jfetch(`/api/admin/users/${id}/role`, { method:"PATCH", body:{ role } }); },
  async resetAdminUserPassword(id, newPassword) { return jfetch(`/api/admin/users/${id}/password`, { method:"POST", body:{ newPassword } }); },
  async registrationSettings() { return jfetch("/api/admin/settings/registration"); },
  async updateRegistrationSettings(enabled) { return jfetch("/api/admin/settings/registration", { method:"PATCH", body:{ enabled } }); },
};

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
    throw new Error(t("401: не авторизован"));
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
    if (!state.offline.cacheReady) throw new Error(t("Нет связи с сервером, а локальная очередь недоступна"));
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
      throw new Error(t("Нет связи и ещё нет локальной копии данных"));
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
  $("monthName").textContent = monthName(state.m);
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
      ear.title = t("Есть заметка");
      cell.appendChild(ear);
    }
    if ((entry?.dayEmoji || "").trim()) {
      const em = document.createElement("span");
      em.className = "dayEmoji";
      em.textContent = entry.dayEmoji;
      em.title = t("Маркер дня");
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
        tm.title = t("Все задачи выполнены");
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
  el.innerHTML = `<span class="lbl">${esc(t("Итого:"))}</span>`;
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
    s.style.color = "var(--dim)"; s.textContent = t("Смены ещё не отмечены. Выберите день в календаре.");
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
    $("pWeekday").textContent = weekdayName((date.getDay() + 6) % 7);
    $("pDate").innerHTML = state.language === "en" ? `${monthNameGen(m - 1)} ${d} <span class="yr mono">${y}</span>` : `${d} ${monthNameGen(m - 1)} <span class="yr mono">${y}</span>`;
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
    return setSave("err", t("не хватает смен для шаблона"));
  }

  const count = Number($("tplDays").value);
  if (!Number.isInteger(count) || count < 1 || count > 366) {
    return setSave("err", t("количество дней: от 1 до 366"));
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
    : t("не отмечена");

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
  if ($("creditCalcHint")) $("creditCalcHint").textContent = t("можно вручную");
  clearScenarioHighlight();
  if ($("creditEditNotice")) { $("creditEditNotice").hidden = true; $("creditEditNotice").textContent = ""; }
  if ($("creditCancel")) $("creditCancel").hidden = true;
  if ($("creditAdd")) $("creditAdd").textContent = t("Начислить");

  if ($("usageDate")) $("usageDate").value = k || todayKey();
  if ($("usageHours")) $("usageHours").value = "0";
  if ($("usageReason")) $("usageReason").value = "";
  if ($("usageEditNotice")) { $("usageEditNotice").hidden = true; $("usageEditNotice").textContent = ""; }
  if ($("usageCancel")) $("usageCancel").hidden = true;
  if ($("usageAdd")) $("usageAdd").textContent = t("Списать отгул");
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
  if ($("usageAdd")) $("usageAdd").textContent = t("Списать отгул");
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

  $("creditCalcHint").textContent = t("можно вручную");
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
  if (!state.selected) { setSave("err", t("сначала выбери день в календаре")); return null; }
  if (!st) { setSave("err", t("на выбранном дне нет смены")); return null; }
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
  if (!start) return setSave("err", t("сценарию не хватает времени начала/конца смены"));
  const end = scenarioEndValue(base, st, sc, start);
  if (!end) return setSave("err", t("не получилось определить конец сценария"));
  const sdt = localDateTimeToDate(start), edt = localDateTimeToDate(end);
  if (!sdt || !edt || edt <= sdt) return setSave("err", t("конец сценария должен быть позже начала"));
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
  if (!name) { setSave("err", t("назови сценарий")); return null; }
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
    setSave("saved", t("сценарий добавлен"));
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function deleteQuickScenario(id){
  if (!confirm(t("Удалить быстрый сценарий?"))) return;
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
    setSave("err", t("для автоподсчёта нужны и начало, и конец"));
    return null;
  }
  const hours = calc ? calc.hours : readHoursInput("creditHours");
  if (!Number.isFinite(hours) || hours <= 0) {
    setSave("err", t("укажи часы переработки больше 0"));
    return null;
  }
  const manualDate = $("creditDate")?.value || state.selected;
  if (!calc && !manualDate) {
    setSave("err", t("укажи дату переработки"));
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
    await loadLedgerPage(true);
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function findUsageById(id){
  const fromAccount = (state.overtimeAccount?.usages || []).find(u => Number(u.id) === Number(id));
  if (fromAccount) return fromAccount;
  for (const credit of state.ledgerPage?.items || []) {
    const usage = (credit.usages || []).find(u => Number(u.usageId) === Number(id));
    if (usage) return { id:usage.usageId, usageDate:usage.usageDate, hours:usage.hours, reason:usage.reason || "" };
  }
  return null;
}

function startEditOvertimeCredit(id){
  const c = (state.overtimeAccount?.credits || []).find(x => Number(x.id) === Number(id)) || (state.ledgerPage?.items || []).find(x => Number(x.id) === Number(id));
  if (!c) return setSave("err", t("начисление не найдено"));
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
  if (!Number.isFinite(hours) || hours <= 0) return setSave("err", t("укажи часы списания больше 0"));
  const date = $("usageDate")?.value || state.selected;
  if (!date) return setSave("err", t("укажи дату списания"));
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
    await loadLedgerPage(true);
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function startEditOvertimeUsage(id){
  const u = findUsageById(id);
  if (!u) return setSave("err", t("списание не найдено"));
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
  const credit = (state.overtimeAccount?.credits || []).find(c => Number(c.id) === Number(id)) || (state.ledgerPage?.items || []).find(c => Number(c.id) === Number(id));
  const label = credit ? `${credit.workedDate} ${credit.timeRange || ""} +${fmtHours(credit.hours)} ч` : `#${id}`;
  if (!confirm(`Удалить начисление переработки ${label}?\n\nЭто действие нельзя отменить.`)) return;
  setSave("saving");
  try {
    state.overtimeAccount = await api.deleteOvertimeCredit(id);
    if (state.editingCreditId === Number(id)) resetOvertimeForms(state.selected);
    setSave("saved");
    renderOvertimeControls();
    renderCalendar();
    await loadLedgerPage(true);
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
    await loadLedgerPage(true);
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
  if (status === "closed") return t("списано");
  if (status === "partial") return t("частично");
  return t("остаток");
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

  const page = { ...(state.ledgerPage || {}), items: (state.ledgerPage?.items || []) };
  const allCredits = acc.credits || [];
  const credits = page.items || [];
  const totalEarned = credits.reduce((sum, c) => sum + numOr0(c.hours), 0);
  const totalUsed = credits.reduce((sum, c) => sum + numOr0(c.usedHours), 0);
  const totalRemain = credits.reduce((sum, c) => sum + numOr0(c.remainingHours), 0);
  const openCount = credits.filter(c => numOr0(c.remainingHours) > 0.0001).length;
  const closedCount = credits.filter(c => numOr0(c.remainingHours) <= 0.0001).length;
  if (statsEl) {
    statsEl.innerHTML = `
      <span class="pill">показано: <b>${pageRangeText(page)}</b></span>
      <span class="pill">на странице начислено: <b>+${fmtHours(totalEarned)} ч</b></span>
      <span class="pill">использовано: <b>${fmtHours(totalUsed)} ч</b></span>
      <span class="pill">остаток на странице: <b>${fmtHours(totalRemain)} ч</b></span>
      <span class="pill">с остатком: <b>${openCount}</b></span>
      <span class="pill">закрыто: <b>${closedCount}</b></span>
    `;
  }
  renderPager("ledgerPager", page, nextPage => { state.ledgerPage.page = nextPage; loadLedgerPage(false); }, nextSize => { state.ledgerPage.size = nextSize; resetLedgerPage(); loadLedgerPage(false); });

  if (!allCredits.length && !(page.total || 0)) {
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

async function loadLedgerPage(silent = true){
  try {
    const f = state.ledgerFilters || {};
    const page = state.ledgerPage || { page:0, size:50 };
    const query = {
      from: f.from || "",
      to: f.to || "",
      status: f.status && f.status !== "all" ? f.status : "",
      q: f.q || "",
      page: page.page || 0,
      size: page.size || 50,
    };
    const res = await api.overtimeAccountPage(query);
    const creditsPage = normalizePageResponse(res?.credits, page.size || 50);
    state.ledgerPage = creditsPage;
    state.overtimeAccount = {
      ...(state.overtimeAccount || {}),
      totalEarnedHours: numOr0(res?.totalEarnedHours),
      totalUsedHours: numOr0(res?.totalUsedHours),
      balanceHours: numOr0(res?.balanceHours),
    };
    renderLedgerTable();
    updateOvertimeBalanceLabel();
  } catch (err) {
    console.error(err);
    if (!silent) setSave("err", err.message);
  }
}
function resetLedgerPage(){
  state.ledgerPage = { ...(state.ledgerPage || {}), page:0 };
}

function setLedgerThisMonth(){
  const r = currentMonthRange();
  state.ledgerFilters.from = r.from;
  state.ledgerFilters.to = r.to;
  resetLedgerPage();
  loadLedgerPage(false);
}
function setLedgerAllTime(){
  state.ledgerFilters.from = "";
  state.ledgerFilters.to = "";
  resetLedgerPage();
  loadLedgerPage(false);
}
function clearLedgerFilters(){
  state.ledgerFilters = { from:"", to:"", status:"all", q:"" };
  resetLedgerPage();
  loadLedgerPage(false);
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
$("ledgerFrom").addEventListener("input", e => { state.ledgerFilters.from = e.target.value; resetLedgerPage(); loadLedgerPage(false); });
$("ledgerTo").addEventListener("input", e => { state.ledgerFilters.to = e.target.value; resetLedgerPage(); loadLedgerPage(false); });
$("ledgerStatus").addEventListener("change", e => { state.ledgerFilters.status = e.target.value; resetLedgerPage(); loadLedgerPage(false); });
$("ledgerSearch").addEventListener("input", e => { clearTimeout(window.__ledgerTimer); window.__ledgerTimer = setTimeout(() => { state.ledgerFilters.q = e.target.value.trim(); resetLedgerPage(); loadLedgerPage(true); }, 350); });
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
  if (!st || !plan) return setSave("err", t("на этом дне нет смены с плановыми часами"));
  $("creditPlanned").value = fmtHours(plan);
  updateOvertimeCalcPreview();
});
$("creditTimeByShift").addEventListener("click", () => {
  const st = stOf(state.selected);
  const base = state.selected || $("creditDate")?.value;
  if (!st || !base || !st.startTime || !st.endTime) return setSave("err", t("у выбранной смены не указано время начала/конца"));
  $("creditDate").value = base;
  $("creditStart").value = setTimeOnDate(base, st.startTime);
  const endDate = st.endTime <= st.startTime ? dateKeyOffset(base, 1) : base;
  $("creditEnd").value = setTimeOnDate(endDate, st.endTime);
  $("creditBreak").value = String(st.breakMinutes || 0);
  $("creditPlanned").value = fmtHours(shiftPlannedHours(st));
  updateOvertimeCalcPreview();
});
$("creditDateSelected").addEventListener("click", () => {
  if (!state.selected) return setSave("err", t("сначала выбери день в календаре"));
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
  if (!st || !plan) return setSave("err", t("на этом дне нет смены с плановыми часами для списания"));
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
    empty.textContent = t("Важных дат пока нет.");
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
    del.textContent = t("удалить");
    del.title = t("Удалить важный день полностью, включая повторения");
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
    empty.textContent = t("В этот день важных событий нет.");
    box.appendChild(empty);
    updateAccSummaries();
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
    del.textContent = t("удалить");
    del.title = t("Удалить важный день полностью, включая повторения");
    del.addEventListener("click", () => removeImportantDay(item.id));
    row.append(dot, title, mode, del);
    box.appendChild(row);
  }
  updateAccSummaries();
}

async function addImportantDay(){
  const k = $("impDate")?.value || state.selected;
  if (!k) return setSave("err", t("укажи дату важного дня"));
  const title = $("impTitle").value.trim();
  if (!title) return setSave("err", t("укажи название важного дня"));
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
    updateAccSummaries();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function removeImportantDay(id){
  if (!confirm(t("Удалить важный день целиком, включая повторения?"))) return;
  setSave("saving");
  try {
    await api.deleteImportantDay(id);
    await refreshImportantSettings();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantSettings();
    renderCalendar();
    updateAccSummaries();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

$("impAdd").addEventListener("click", addImportantDay);
$("impTitle").addEventListener("keydown", e => { if (e.key === "Enter") addImportantDay(); });
$("impDateSelected")?.addEventListener("click", () => { if (!state.selected) return setSave("err", t("сначала выбери день в календаре")); $("impDate").value = state.selected; });
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
    empty.textContent = t("Задач пока нет. После добавления открытые задачи будут отмечены в календаре.");
    box.appendChild(empty);
    updateAccSummaries();
    return;
  }
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("По фильтрам задач нет.");
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
    if (task.overdue && !task.done) { const b = document.createElement("span"); b.className = "taskBadge overdue"; b.textContent = t("просрочено"); meta.appendChild(b); }
    body.append(text, meta);
    const edit = document.createElement("button");
    edit.className = "tinyDel"; edit.type = "button"; edit.textContent = t("ред."); edit.title = t("Редактировать задачу");
    edit.addEventListener("click", () => editTask(task));
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = "×";
    del.title = t("Удалить задачу");
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
  const reminder = confirm(t("Включить напоминание для этой задачи?"));
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
  if (!text) return setSave("err", t("напиши текст задачи"));
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
  if (!confirm(t("Удалить задачу?"))) return;
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
    const page = state.taskBoard.page || { page:0, size:50 };
    const query = {
      status: f.status || "open",
      category: f.category !== "all" ? f.category : "",
      priority: f.priority !== "all" ? f.priority : "",
      q: f.q || "",
      from: f.from || "",
      to: f.to || "",
      page: page.page || 0,
      size: page.size || 50,
    };
    const res = normalizePageResponse(await api.taskBoard(query), page.size || 50);
    state.taskBoard.items = res.items;
    state.taskBoard.page = res;
    renderTaskBoard();
  } catch (err) {
    console.error(err);
    if (!silent) setSave("err", err.message);
  }
}
function resetTaskBoardPage(){
  state.taskBoard.page = { ...(state.taskBoard.page || {}), page:0 };
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
  if (task.overdue && !task.done) { const b = document.createElement("span"); b.className = "taskBadge overdue"; b.textContent = t("просрочено"); meta.appendChild(b); }
  if (task.done) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = t("выполнено"); meta.appendChild(b); }
  return meta;
}
function renderTaskBoard(){
  const list = $("taskBoardList");
  if (!list) return;
  syncTaskBoardFiltersToInputs();
  renderTaskBoardCategoryFilter();
  const items = state.taskBoard.items || [];
  const page = { ...(state.taskBoard.page || {}), items };
  const open = items.filter(t => !t.done).length;
  const overdue = items.filter(t => t.overdue && !t.done).length;
  const done = items.filter(t => t.done).length;
  $("taskBoardStatus").textContent = `${pageRangeText(page)}`;
  $("taskBoardStats").innerHTML = `
    <span class="pill">на странице <b>${items.length}</b></span>
    <span class="pill">всего по фильтрам <b>${page.total || items.length}</b></span>
    <span class="pill">открытых на странице <b>${open}</b></span>
    <span class="pill">просроченных на странице <b>${overdue}</b></span>
    <span class="pill">выполненных на странице <b>${done}</b></span>`;
  renderPager("taskBoardPager", page, nextPage => { state.taskBoard.page.page = nextPage; loadTaskBoard(false); }, nextSize => { state.taskBoard.page.size = nextSize; resetTaskBoardPage(); loadTaskBoard(false); });
  list.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("По этим фильтрам задач нет.");
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
    edit.type = "button"; edit.textContent = t("ред."); edit.title = t("Редактировать задачу");
    edit.addEventListener("click", () => editTask(task));
    const del = document.createElement("button");
    del.type = "button"; del.textContent = "×"; del.title = t("Удалить задачу");
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
  resetTaskBoardPage();
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
$("taskBoardThisMonth").addEventListener("click", () => { const r = monthFromTo(); state.taskBoard.filters.from = r.from; state.taskBoard.filters.to = r.to; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardClear").addEventListener("click", () => { state.taskBoard.filters = { status:"open", category:"all", priority:"all", q:"", from:"", to:"" }; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardStatusFilter").addEventListener("change", () => { state.taskBoard.filters.status = $("taskBoardStatusFilter").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardCategory").addEventListener("change", () => { state.taskBoard.filters.category = $("taskBoardCategory").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardPriority").addEventListener("change", () => { state.taskBoard.filters.priority = $("taskBoardPriority").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardFrom").addEventListener("change", () => { state.taskBoard.filters.from = $("taskBoardFrom").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardTo").addEventListener("change", () => { state.taskBoard.filters.to = $("taskBoardTo").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardSearch").addEventListener("input", () => { clearTimeout(window.__taskBoardTimer); window.__taskBoardTimer = setTimeout(() => { state.taskBoard.filters.q = $("taskBoardSearch").value.trim(); resetTaskBoardPage(); loadTaskBoard(true); }, 350); });

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
  plus.title = t("Создать или настроить смену в настройках");
  plus.addEventListener("click", () => {
    openSettingsSection("shifts", true, "nsName");
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
    b.title = t("Поставить маркер ") + emoji;
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
    $("notePrev").innerHTML = note.trim() ? renderMd(note) : `<span class="empty">${esc(t("Заметка пустая — нечего показывать."))}</span>`;
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
for (const id of ["nsHours", "nsStart", "nsEnd", "nsBreak", "nsPlan"]) {
  $(id)?.addEventListener("input", updateShiftPlanHint);
}
updateShiftPlanHint();

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
  picker.type = "color"; picker.value = state.swColor; picker.title = t("Свой цвет");
  picker.addEventListener("input", () => { state.swColor = picker.value; });
  row.appendChild(picker);
  const add = document.createElement("button");
  add.className = "add"; add.textContent = t("Добавить");
  add.addEventListener("click", addShiftType);
  row.appendChild(add);
}

async function addShiftType(){
  const name = $("nsName").value.trim();
  if (!name) return setSave("err", t("укажи название смены"));
  const startTime = $("nsStart").value || null;
  const endTime = $("nsEnd").value || null;
  const breakMinutes = readIntInput("nsBreak");
  if (!Number.isFinite(breakMinutes) || breakMinutes < 0 || breakMinutes > 1440) {
    return setSave("err", t("обед: от 0 до 1440 минут"));
  }
  const calculatedNorm = shiftDurationHours(startTime, endTime, breakMinutes);
  const rawPlan = $("nsPlan").value.trim().replace(",", ".");
  const rawHours = $("nsHours").value.trim().replace(",", ".");
  const plannedHours = rawPlan ? Number(rawPlan) : (calculatedNorm || (rawHours ? Number(rawHours) : 0));
  const hours = rawHours ? Number(rawHours) : plannedHours;
  if (!Number.isFinite(hours) || hours < 0 || hours > 24) {
    return setSave("err", t("часы: от 0 до 24"));
  }
  if (!Number.isFinite(plannedHours) || plannedHours < 0 || plannedHours > 24) {
    return setSave("err", t("норма: от 0 до 24 часов"));
  }

  setSave("saving");
  try {
    const created = await api.createShiftType({
      name,
      hours,
      color: state.swColor,
      startTime,
      endTime,
      breakMinutes,
      plannedHours,
    });
    state.shiftTypes.push(created);
    $("nsName").value = ""; $("nsHours").value = ""; $("nsStart").value = ""; $("nsEnd").value = ""; $("nsBreak").value = "0"; $("nsPlan").value = "";
    updateShiftPlanHint();
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
      <span>${esc(s.name)}${shiftPlannedHours(s) ? `${state.language === "en" ? " · norm " : " · норма "}${fmtHours(shiftPlannedHours(s))}ч` : ""}${meta ? ` <span style="color:var(--dim)">· ${esc(meta)}</span>` : ""}<span style="color:var(--dim)">${esc(notifyMeta)}</span></span>`;

    const edit = document.createElement("button");
    edit.className = "del"; edit.textContent = t("настроить");
    edit.title = t("Изменить время, обед и плановые часы смены");
    edit.addEventListener("click", () => editShiftType(s.id));
    row.appendChild(edit);

    if (s.builtin) {
      const tag = document.createElement("span");
      tag.className = "tag"; tag.textContent = t("встроенная");
      row.appendChild(tag);
    } else {
      const del = document.createElement("button");
      del.className = "del"; del.textContent = t("удалить");
      del.title = t("Смена снимется с дней, где стояла. Заметки останутся.");
      del.addEventListener("click", () => removeShiftType(s.id));
      row.appendChild(del);
    }
    box.appendChild(row);
  }
}

async function editShiftType(id){
  const s = state.shiftTypes.find(x => Number(x.id) === Number(id));
  if (!s) return setSave("err", t("смена не найдена"));

  const patch = {};
  if (!s.builtin) {
    const name = prompt("Название смены", s.name || "");
    if (name === null) return;
    if (!name.trim()) return setSave("err", t("название не может быть пустым"));
    patch.name = name.trim();

    const color = prompt("Цвет #RRGGBB", s.color || state.swColor);
    if (color === null) return;
    patch.color = color.trim();
  }

  const hoursRaw = prompt("Короткие часы для календаря", fmtHours(s.hours));
  if (hoursRaw === null) return;
  const hours = Number(hoursRaw.replace(",", "."));
  if (!Number.isFinite(hours) || hours < 0 || hours > 24) return setSave("err", t("часы: от 0 до 24"));
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
  if (!Number.isFinite(breakMinutes) || breakMinutes < 0 || breakMinutes > 1440) return setSave("err", t("обед: от 0 до 1440 минут"));
  patch.breakMinutes = Math.round(breakMinutes);

  const planRaw = prompt("Норма для расчёта переработки, ч", fmtHours(shiftPlannedHours(s)));
  if (planRaw === null) return;
  const plannedHours = Number(planRaw.replace(",", "."));
  if (!Number.isFinite(plannedHours) || plannedHours < 0 || plannedHours > 24) return setSave("err", t("норма: от 0 до 24 часов"));
  patch.plannedHours = plannedHours;

  const notifRaw = prompt("Уведомлять перед этой сменой? да/нет", s.notificationsEnabled === false ? "нет" : "да");
  if (notifRaw === null) return;
  const notifClean = notifRaw.trim().toLowerCase();
  patch.notificationsEnabled = !(notifClean === "нет" || notifClean === "no" || notifClean === "0" || notifClean === "false");

  const minutesRaw = prompt("За сколько минут напоминать именно эту смену? Пусто = глобальная настройка", s.notificationMinutesBefore ?? "");
  if (minutesRaw === null) return;
  if (minutesRaw.trim()) {
    const minutes = Number(minutesRaw);
    if (!Number.isFinite(minutes) || minutes < 0 || minutes > 1440) return setSave("err", t("напоминание смены: от 0 до 1440 минут"));
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
  await loadLedgerPage(true);
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
  $("timeNowBox").innerHTML = `${region}${esc(t("рабочее время"))}: <b>${esc(safeTzLabel(t.workTimezone))}</b> <span>(${esc(t.workTimezone)})</span><br>` +
    `${esc(t("браузер"))}: <b>${esc(safeTzLabel(browserTz))}</b> <span>(${esc(browserTz)})</span>` +
    (Number(t.workOffsetMoscow || 0) ? `<br>${esc(t("пометка"))}: ${esc(t("Москва"))} ${Number(t.workOffsetMoscow) > 0 ? "+" : ""}${Number(t.workOffsetMoscow)} ${state.language === "en" ? "h" : "ч"}` : "");
  $("timeSettingsStatus").className = "status statusAutoSave";
  $("timeSettingsStatus").innerHTML = `<span class="statusChip statusChipAuto"><span class="statusDot"></span>${esc(t("автосохранение"))}</span>`;
}
function saveTimeSettings(){
  storeTimeSettings(readTimeSettingsForm());
  renderTimeSettings();
  setSave("saved", t("настройки времени сохранены"));
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
  if (!targets.length) return setSave("err", t("не нашёл Дневную/Ночную смену"));
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
  const page = { ...(state.adminUsersPage || {}), items: users };
  if (status) {
    const admins = users.filter(u => u.role === "ADMIN").length;
    status.className = "status statusMetrics";
    status.innerHTML = `<span class="statusChip"><b>Показано:</b> ${pageRangeText(page)}</span><span class="statusChip ${admins > 0 ? 'statusChipOk' : 'statusChipWarn'}"><b>Админов на странице:</b> ${admins}</span>`;
  }
  renderPager("adminUsersPager", page, nextPage => { state.adminUsersPage.page = nextPage; refreshAdminUsers(); }, nextSize => { state.adminUsersPage.size = nextSize; state.adminUsersPage.page = 0; refreshAdminUsers(); });
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
  if (status) status.textContent = t("загрузка…");
  try {
    const page = state.adminUsersPage || { page:0, size:50 };
    const res = normalizePageResponse(await api.adminUsers({
      page: page.page || 0,
      size: page.size || 50,
      q: $("adminUsersSearch")?.value || "",
      role: $("adminUsersRoleFilter")?.value || "all",
    }), page.size || 50);
    state.adminUsers = res.items || [];
    state.adminUsersPage = res;
    renderAdminUsers(state.adminUsers);
  } catch (err) {
    if (status) status.textContent = t("ошибка");
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
  if (password.length < 12) return setSave("err", t("пароль должен быть минимум 12 символов"));
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
  if (st) st.textContent = t("проверяю…");
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
    try { await navigator.clipboard.writeText(diagnosticsReportText()); setSave("saved", t("отчёт диагностики скопирован")); }
    catch (err) { setSave("err", t("не удалось скопировать отчёт")); }
  });
  renderDiagnosticsClient();
  refreshRegistrationAdmin();
  refreshAdminUsers();
  $("registrationRefresh")?.addEventListener("click", refreshRegistrationAdmin);
  $("registrationEnabledToggle")?.addEventListener("change", e => saveRegistrationAdmin(e.target.checked));
  $("adminUsersRefresh")?.addEventListener("click", refreshAdminUsers);
  $("adminUsersRoleFilter")?.addEventListener("change", () => { state.adminUsersPage.page = 0; refreshAdminUsers(); });
  $("adminUsersSearch")?.addEventListener("input", () => { clearTimeout(window.__adminUsersTimer); window.__adminUsersTimer = setTimeout(() => { state.adminUsersPage.page = 0; refreshAdminUsers(); }, 350); });
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
    language: "Русский / English",
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
      btn.textContent = open ? t("свернуть") : t("открыть");
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
      note.textContent = t(titles[section] || "Раздел настроек");
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

  root.__openSettingsSection = openSection;

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

function openSettingsSection(section, scroll = true, focusId = null){
  const hash = `#settings-${section}`;
  if (location.hash !== hash) location.hash = hash;
  setTimeout(() => {
    renderSettingsPanels();
    const root = $("view-settings");
    root?.__openSettingsSection?.(section, scroll);
    const card = document.querySelector(`[data-settings-section="${section}"]`);
    if (card) {
      card.classList.add("is-attention");
      setTimeout(() => card.classList.remove("is-attention"), 1200);
    }
    if (focusId) $(focusId)?.focus();
  }, 80);
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
  return type === "SHIFT" ? t("смена") : type === "TASK" ? t("задача") : type === "IMPORTANT_DAY" ? t("важно") : type === "TOMORROW_DIGEST" ? t("дайджест") : type;
}
function fmtReminderAt(value){
  if (!value) return "";
  const d = value.slice(0,10), t = value.slice(11,16);
  const [,m,day] = d.split("-");
  return `${day}.${m} ${t}`;
}
function browserPermissionStatus(){
  if (!("Notification" in window)) return { label:t("браузер"), value:t("не поддерживает"), tone:"warn" };
  if (Notification.permission === "granted") return { label:t("браузер"), value:t("разрешено"), tone:"ok" };
  if (Notification.permission === "denied") return { label:t("браузер"), value:t("запрещено"), tone:"warn" };
  return { label:t("браузер"), value:t("не разрешено"), tone:"warn" };
}
function renderNotifyStatus(count){
  const box = $("notifyStatus");
  if (!box) return;
  const permission = browserPermissionStatus();
  box.className = "status notifyStatusChips";
  box.innerHTML = `<span class="statusChip statusChipPrimary"><b>${Number(count) || 0}</b> шт</span><span class="statusChip ${permission.tone === "ok" ? "statusChipOk" : "statusChipWarn"}"><b>${esc(permission.label)}:</b> ${esc(permission.value)}</span>`;
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
  renderNotifyStatus(sourceItems.length);
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
  if (!("Notification" in window)) { alert(t("Этот браузер не поддерживает Notification API")); return; }
  const perm = await Notification.requestPermission();
  await saveNotificationSettings({ browserNotificationsEnabled: perm === "granted" });
}
function testNotification(){
  if (!("Notification" in window) || Notification.permission !== "granted") { alert(t("Сначала разрешите уведомления в браузере")); return; }
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
    setSave("err", t("нет связи — открыта локальная копия"));
  }
  await loadMonth();
  await loadLedgerPage(true);
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
  if (!$("telegramBox")) return;
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

ensureTranslationObserver();
applyLanguage(state.language);
loadProfile();
loadSessions();
