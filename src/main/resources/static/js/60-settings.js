/*
 * 60-settings.js — Settings: shifts, navigation, notifications, modules and diagnostics
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 35-today → 37-calendar-experience → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

/* ─── Управление типами смен ────────────────────────────────── */

Object.assign(I18N_EN, {
  "Типы смен":"Shift types", "Новая смена":"New shift", "Редактирование смены":"Edit shift",
  "Добавить смену":"Add shift", "Сохранить смену":"Save shift", "Смена обновлена":"Shift updated",
  "Смена добавлена":"Shift added", "Создать или настроить смену":"Create or configure a shift",
  "Уведомлять перед этой сменой":"Notify before this shift", "Своё время напоминания, мин":"Custom reminder minutes"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

function shiftTypeEditorMessage(text = "", tone = ""){
  const box = $("shiftTypeMessage");
  if (!box) return;
  box.textContent = text;
  box.className = "appModalMessage" + (tone ? ` ${tone}` : "");
}
function resetShiftTypeForm(){
  state.editingShiftTypeId = null;
  $("shiftTypeForm")?.reset();
  if ($("nsName")) { $("nsName").disabled = false; $("nsName").value = ""; }
  if ($("nsHours")) $("nsHours").value = "";
  if ($("nsStart")) $("nsStart").value = "";
  if ($("nsEnd")) $("nsEnd").value = "";
  if ($("nsBreak")) $("nsBreak").value = "0";
  if ($("nsPlan")) $("nsPlan").value = "";
  if ($("nsNotificationsEnabled")) $("nsNotificationsEnabled").checked = true;
  if ($("nsNotificationMinutes")) $("nsNotificationMinutes").value = "";
  if ($("shiftTypeFormTitle")) $("shiftTypeFormTitle").textContent = t("Новая смена");
  if ($("shiftTypeSave")) $("shiftTypeSave").textContent = t("Добавить смену");
  if ($("shiftTypeCancelEdit")) $("shiftTypeCancelEdit").hidden = true;
  state.swColor = "#F5B841";
  shiftTypeEditorMessage();
  renderSwatches();
  updateShiftPlanHint();
  renderCustomList();
}
function openShiftTypeManager(editId = null){
  renderCustomList();
  if (editId != null) editShiftType(editId);
  else resetShiftTypeForm();
  openAppModal("shiftTypeModal", editId != null ? "nsHours" : "nsName");
}
function closeShiftTypeManager(){
  resetShiftTypeForm();
  closeAppModal("shiftTypeModal");
}
function renderSwatches(){
  const row = $("swRow");
  if (!row) return;
  row.innerHTML = "";
  const colorLocked = !!state.shiftTypes.find(x => Number(x.id) === Number(state.editingShiftTypeId))?.builtin;
  for (const c of SWATCHES) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "sw" + (state.swColor.toLowerCase() === c.toLowerCase() ? " on" : "");
    b.style.background = c;
    b.title = c;
    b.disabled = colorLocked;
    b.addEventListener("click", () => { state.swColor = c; renderSwatches(); });
    row.appendChild(b);
  }
  const picker = document.createElement("input");
  picker.type = "color";
  picker.value = /^#[0-9a-f]{6}$/i.test(state.swColor) ? state.swColor : "#F5B841";
  picker.title = t("Свой цвет");
  picker.disabled = colorLocked;
  picker.addEventListener("input", () => { state.swColor = picker.value; renderSwatches(); });
  row.appendChild(picker);
}
function readShiftTypeFormPayload(){
  const name = $("nsName").value.trim();
  if (!name) throw new Error(t("укажи название смены"));
  const startTime = $("nsStart").value || "";
  const endTime = $("nsEnd").value || "";
  const breakMinutes = Number($("nsBreak").value || 0);
  if (!Number.isFinite(breakMinutes) || breakMinutes < 0 || breakMinutes > 1440) throw new Error(t("обед: от 0 до 1440 минут"));
  const calculatedNorm = shiftDurationHours(startTime, endTime, breakMinutes);
  const rawPlan = $("nsPlan").value.trim().replace(",", ".");
  const rawHours = $("nsHours").value.trim().replace(",", ".");
  const plannedHours = rawPlan ? Number(rawPlan) : (calculatedNorm || (rawHours ? Number(rawHours) : 0));
  const hours = rawHours ? Number(rawHours) : plannedHours;
  if (!Number.isFinite(hours) || hours < 0 || hours > 24) throw new Error(t("часы: от 0 до 24"));
  if (!Number.isFinite(plannedHours) || plannedHours < 0 || plannedHours > 24) throw new Error(t("норма: от 0 до 24 часов"));
  const reminderRaw = $("nsNotificationMinutes").value.trim();
  const notificationMinutesBefore = reminderRaw === "" ? -1 : Number(reminderRaw);
  if (!Number.isFinite(notificationMinutesBefore) || notificationMinutesBefore < -1 || notificationMinutesBefore > 1440) {
    throw new Error(t("напоминание смены: от 0 до 1440 минут"));
  }
  return {
    name,
    hours,
    color:state.swColor,
    startTime,
    endTime,
    breakMinutes:Math.round(breakMinutes),
    plannedHours,
    notificationsEnabled:!!$("nsNotificationsEnabled").checked,
    notificationMinutesBefore:Math.round(notificationMinutesBefore),
  };
}
async function addShiftType(){
  state.editingShiftTypeId = null;
  return saveShiftTypeForm();
}
async function saveShiftTypeForm(){
  let payload;
  try { payload = readShiftTypeFormPayload(); }
  catch (err) { return shiftTypeEditorMessage(err.message, "err"); }
  const editing = state.shiftTypes.find(x => Number(x.id) === Number(state.editingShiftTypeId)) || null;
  if (editing?.builtin) {
    delete payload.name;
    delete payload.color;
  }
  $("shiftTypeSave").disabled = true;
  shiftTypeEditorMessage(t("сохранение…"));
  setSave("saving");
  try {
    if (editing) {
      const updated = await api.updateShiftType(editing.id, payload);
      const idx = state.shiftTypes.findIndex(x => Number(x.id) === Number(editing.id));
      if (idx >= 0) state.shiftTypes[idx] = updated;
      setSave("saved", t("Смена обновлена"));
    } else {
      const created = await api.createShiftType(payload);
      state.shiftTypes.push(created);
      setSave("saved", t("Смена добавлена"));
    }
    resetShiftTypeForm();
    renderChips();
    renderSummary();
    renderCalendar();
    renderOvertimeControls();
    renderCustomList();
  } catch (err) {
    console.error(err);
    shiftTypeEditorMessage(err.message, "err");
    setSave("err", err.message);
  } finally {
    $("shiftTypeSave").disabled = false;
  }
}
function renderCustomList(){
  const box = $("customList");
  if (!box) return;
  box.hidden = state.shiftTypes.length === 0;
  box.innerHTML = "";
  for (const s of state.shiftTypes) {
    const row = document.createElement("div");
    row.className = Number(s.id) === Number(state.editingShiftTypeId) ? "editing" : "";
    row.style.display = "flex";
    row.style.alignItems = "center";
    row.style.gap = "8px";
    row.style.flexWrap = "wrap";
    const meta = shiftMetaText(s);
    const notifyMeta = s.notificationsEnabled === false ? ` · ${t("без уведомлений")}` : (s.notificationMinutesBefore != null ? ` · ${t("напомнить за")} ${s.notificationMinutesBefore}${state.language === "en" ? "min" : "м"}` : "");
    row.innerHTML = `<span class="dot" style="width:10px;height:10px;border-radius:3px;background:${s.color};display:inline-block"></span>
      <span style="flex:1;min-width:150px"><b>${esc(shiftDisplayName(s))}</b>${shiftPlannedHours(s) ? `${state.language === "en" ? " · norm " : " · норма "}${fmtHours(shiftPlannedHours(s))}${state.language === "en" ? "h" : "ч"}` : ""}${meta ? ` <span style="color:var(--dim)">· ${esc(meta)}</span>` : ""}<span style="color:var(--dim)">${esc(notifyMeta)}</span></span>`;
    const edit = document.createElement("button");
    edit.type = "button";
    edit.className = "del";
    edit.textContent = t("настроить");
    edit.addEventListener("click", () => editShiftType(s.id));
    row.appendChild(edit);
    if (s.builtin) {
      const tag = document.createElement("span");
      tag.className = "tag";
      tag.textContent = t("встроенная");
      row.appendChild(tag);
    } else {
      const del = document.createElement("button");
      del.type = "button";
      del.className = "del";
      del.textContent = t("удалить");
      del.addEventListener("click", () => removeShiftType(s.id));
      row.appendChild(del);
    }
    box.appendChild(row);
  }
}
function editShiftType(id){
  const s = state.shiftTypes.find(x => Number(x.id) === Number(id));
  if (!s) return setSave("err", t("смена не найдена"));
  state.editingShiftTypeId = Number(id);
  $("nsName").value = s.name || "";
  $("nsName").disabled = !!s.builtin;
  $("nsHours").value = fmtHours(s.hours);
  $("nsStart").value = s.startTime || "";
  $("nsEnd").value = s.endTime || "";
  $("nsBreak").value = String(s.breakMinutes || 0);
  $("nsPlan").value = fmtHours(shiftPlannedHours(s));
  $("nsNotificationsEnabled").checked = s.notificationsEnabled !== false;
  $("nsNotificationMinutes").value = s.notificationMinutesBefore ?? "";
  state.swColor = s.color || "#F5B841";
  $("shiftTypeFormTitle").textContent = t("Редактирование смены");
  $("shiftTypeSave").textContent = t("Сохранить смену");
  $("shiftTypeCancelEdit").hidden = false;
  shiftTypeEditorMessage();
  renderSwatches();
  renderCustomList();
  updateShiftPlanHint();
  openAppModal("shiftTypeModal", s.builtin ? "nsHours" : "nsName");
}
async function removeShiftType(id){
  const item = state.shiftTypes.find(s => Number(s.id) === Number(id));
  if (!item || item.builtin) return;
  if (!confirm(`${t("Удалить смену")} «${shiftDisplayName(item)}»?`)) return;
  setSave("saving");
  try {
    await api.deleteShiftType(id);
    state.shiftTypes = state.shiftTypes.filter(s => Number(s.id) !== Number(id));
    for (const [k, v] of Object.entries(state.days)) {
      if (Number(v.shiftTypeId) === Number(id)) {
        v.shiftTypeId = null;
        const hasOvertime = Math.abs(numOr0(v.overtimeHours)) > 0.0001 || Math.abs(numOr0(v.timeOffHours)) > 0.0001;
        if (!(v.note || "").trim() && !hasOvertime) delete state.days[k];
      }
    }
    if (Number(state.editingShiftTypeId) === Number(id)) resetShiftTypeForm();
    setSave("saved");
    renderChips();
    renderCalendar();
    renderCustomList();
  } catch (err) { console.error(err); setSave("err", err.message); }
}

for (const id of ["nsHours", "nsStart", "nsEnd", "nsBreak", "nsPlan"]) {
  $(id)?.addEventListener("input", updateShiftPlanHint);
}
$("shiftTypeForm")?.addEventListener("submit", event => { event.preventDefault(); saveShiftTypeForm(); });
$("shiftTypeClose")?.addEventListener("click", closeShiftTypeManager);
$("shiftTypeBackdrop")?.addEventListener("click", closeShiftTypeManager);
$("shiftTypeCancelEdit")?.addEventListener("click", resetShiftTypeForm);
renderSwatches();
updateShiftPlanHint();

/* ─── Навигация по месяцам ──────────────────────────────────── */
window.__dutylogCalendarNavigationReady = Promise.resolve();
function trackCalendarNavigation(operation){
  const ready = Promise.resolve(operation);
  window.__dutylogCalendarNavigationReady = ready;
  return ready;
}
async function goto(y, m){
  await flushPendingSave();
  const d = new Date(y, m, 1);
  state.y = d.getFullYear(); state.m = d.getMonth();
  state.selected = null;
  $("panel").hidden = true; $("layout")?.classList.remove("with-panel");
  await loadMonth();
  if (moduleEnabled("overtime")) await loadLedgerPage(true);
  if (moduleEnabled("tasks")) await loadTaskBoard(true);
  applyModuleVisibility();
  renderCalendar();
}
$("prev").addEventListener("click", () => {
  if (typeof calendarExperienceHeaderNavigate === "function" && calendarExperienceHeaderNavigate(-1)) return;
  trackCalendarNavigation(goto(state.y, state.m - 1));
});
$("next").addEventListener("click", () => {
  if (typeof calendarExperienceHeaderNavigate === "function" && calendarExperienceHeaderNavigate(1)) return;
  trackCalendarNavigation(goto(state.y, state.m + 1));
});
$("todayBtn").addEventListener("click", async () => {
  if (typeof calendarExperienceHeaderToday === "function" && calendarExperienceHeaderToday()) return;
  const t = new Date();
  await trackCalendarNavigation(goto(t.getFullYear(), t.getMonth()));
  selectDay(todayKey());
});




/* ─── Часовой пояс и шаблоны смен ─────────────────────────── */
const TIMEZONE_FALLBACKS = [
  "UTC", "Europe/Chisinau", "Europe/Moscow", "Europe/Berlin", "Europe/Kyiv",
  "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Novosibirsk", "Asia/Irkutsk",
  "Asia/Vladivostok", "Asia/Krasnoyarsk", "Asia/Kamchatka",
  "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "Asia/Tbilisi", "Asia/Yerevan", "Asia/Almaty", "Asia/Dubai", "Asia/Tokyo"
];
const TIMEZONE_RU_LABELS = {
  "UTC":"UTC", "Europe/Chisinau":"Кишинёв", "Europe/Moscow":"Москва",
  "Europe/Berlin":"Берлин", "Europe/Kyiv":"Киев", "Asia/Yekaterinburg":"Екатеринбург",
  "Asia/Omsk":"Омск", "Asia/Novosibirsk":"Новосибирск", "Asia/Irkutsk":"Иркутск",
  "Asia/Vladivostok":"Владивосток", "Asia/Krasnoyarsk":"Красноярск",
  "Asia/Kamchatka":"Камчатка", "America/New_York":"Нью-Йорк",
  "America/Chicago":"Чикаго", "America/Denver":"Денвер",
  "America/Los_Angeles":"Лос-Анджелес", "Asia/Tbilisi":"Тбилиси",
  "Asia/Yerevan":"Ереван", "Asia/Almaty":"Алматы", "Asia/Dubai":"Дубай", "Asia/Tokyo":"Токио"
};
let cachedTimeZones = null;
function availableTimeZones(){
  if (cachedTimeZones) return cachedTimeZones;
  let zones = [];
  try {
    if (typeof Intl.supportedValuesOf === "function") zones = Intl.supportedValuesOf("timeZone");
  } catch (_) { zones = []; }
  cachedTimeZones = [...new Set(["UTC", ...TIMEZONE_FALLBACKS, ...zones])];
  return cachedTimeZones;
}
function timezoneCityLabel(timeZone){
  if (state.language !== "en" && TIMEZONE_RU_LABELS[timeZone]) return TIMEZONE_RU_LABELS[timeZone];
  if (timeZone === "UTC") return "UTC";
  return String(timeZone || "").split("/").pop().replaceAll("_", " ");
}
function timezoneOffsetLabel(timeZone, at = new Date()){
  try {
    const part = new Intl.DateTimeFormat("en-US", {
      timeZone, timeZoneName:"longOffset", hour:"2-digit"
    }).formatToParts(at).find(item => item.type === "timeZoneName")?.value || "GMT";
    if (part === "GMT") return "UTC";
    const match = part.match(/^GMT([+-])(\d{2}):(\d{2})$/);
    if (!match) return part.replace("GMT", "UTC");
    const hours = Number(match[2]);
    const minutes = Number(match[3]);
    return `UTC${match[1]}${hours}${minutes ? `:${String(minutes).padStart(2, "0")}` : ""}`;
  } catch (_) { return "UTC"; }
}
function timezoneOptionLabel(timeZone){
  const city = timezoneCityLabel(timeZone);
  const offset = timezoneOffsetLabel(timeZone);
  return city === offset ? city : `${city} — ${offset}`;
}
function populateTimeZoneSelect(selectId, selected){
  const select = $(selectId);
  if (!select) return;
  const wanted = selected || browserTimeZone();
  const zones = [...new Set([...availableTimeZones(), wanted])]
    .filter(isRecognizedTimeZone)
    .sort((a, b) => timezoneOptionLabel(a).localeCompare(timezoneOptionLabel(b), currentLocale()));
  const current = select.value;
  const fragment = document.createDocumentFragment();
  for (const zone of zones) {
    const option = document.createElement("option");
    option.value = zone;
    option.textContent = timezoneOptionLabel(zone);
    fragment.appendChild(option);
  }
  select.replaceChildren(fragment);
  select.value = zones.includes(wanted) ? wanted : (zones.includes(current) ? current : browserTimeZone());
}
function readTimeSettingsForm(){
  const val = id => ($(id)?.value ?? "").trim();
  const num = (id, fallback = 0) => {
    const raw = val(id).replace(",", ".");
    const n = raw === "" ? fallback : Number(raw);
    return Number.isFinite(n) ? n : fallback;
  };
  return {
    ...(state.timeSettings || loadTimeSettings()),
    workRegionName:"",
    workTimezone: val("workTimezone") || browserTimeZone(),
    displayTimezone: val("workTimezone") || browserTimeZone(),
    workOffsetMoscow:0,
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
function setTimeSettingsStatus(tone = "saved", text = ""){
  const box = $("timeSettingsStatus");
  if (!box) return;
  const label = text || (tone === "dirty"
    ? (state.language === "en" ? "not saved" : "не сохранено")
    : (state.language === "en" ? "saved" : "сохранено"));
  box.className = `status ${tone === "dirty" ? "warn" : ""}`.trim();
  box.textContent = label;
}
function renderTimePreview(timeSettings){
  const box = $("timeNowBox");
  if (!box) return;
  const label = state.language === "en" ? "Current time" : "Текущее время";
  box.innerHTML = `<div><span>${esc(label)}:</span> <b>${esc(safeTzLabel(timeSettings.workTimezone))}</b> <code>${esc(timeSettings.workTimezone)}</code> · ${esc(timezoneOffsetLabel(timeSettings.workTimezone))}</div>`;
}
function syncTimeSettingsFromBuiltins(){
  const current = state.timeSettings || loadTimeSettings();
  const next = { ...current };
  const day = state.shiftTypes.find(s => s.name === "Дневная");
  const night = state.shiftTypes.find(s => s.name === "Ночная");
  if (day) {
    if (day.startTime) next.dayStart = day.startTime;
    if (day.endTime) next.dayEnd = day.endTime;
    next.dayBreakMinutes = Number(day.breakMinutes ?? next.dayBreakMinutes);
    next.dayPlannedHours = Number(day.plannedHours ?? day.hours ?? next.dayPlannedHours);
  }
  if (night) {
    if (night.startTime) next.nightStart = night.startTime;
    if (night.endTime) next.nightEnd = night.endTime;
    next.nightBreakMinutes = Number(night.breakMinutes ?? next.nightBreakMinutes);
    next.nightPlannedHours = Number(night.plannedHours ?? night.hours ?? next.nightPlannedHours);
  }
  storeTimeSettings(next);
  return state.timeSettings;
}

const TIME_SHIFT_DEFAULT_IDS = [
  "defDayStart", "defDayEnd", "defDayBreak", "defDayPlan",
  "defNightStart", "defNightEnd", "defNightBreak", "defNightPlan"
];
let timeSettingsDefaultsRevision = 0;
let timeSettingsDefaultsCommittedRevision = 0;
function timeSettingsDefaultsDirty(){
  return timeSettingsDefaultsRevision !== timeSettingsDefaultsCommittedRevision;
}
function readShiftDefaultsDraft(){
  const value = id => $(id)?.value ?? "";
  return {
    dayStart:value("defDayStart"),
    dayEnd:value("defDayEnd"),
    dayBreakMinutes:value("defDayBreak"),
    dayPlannedHours:value("defDayPlan"),
    nightStart:value("defNightStart"),
    nightEnd:value("defNightEnd"),
    nightBreakMinutes:value("defNightBreak"),
    nightPlannedHours:value("defNightPlan")
  };
}

function renderTimeSettings({ preserveShiftDefaults = timeSettingsDefaultsDirty() } = {}){
  if (!$("timeSettingsCard")) return;
  if (!state.timeSettings) state.timeSettings = loadTimeSettings();
  const preservedDefaults = preserveShiftDefaults ? readShiftDefaultsDraft() : null;
  const timeSettings = syncTimeSettingsFromBuiltins();
  const renderedDefaults = preservedDefaults ? { ...timeSettings, ...preservedDefaults } : timeSettings;
  const set = (id, value) => { if ($(id)) $(id).value = value ?? ""; };
  populateTimeZoneSelect("workTimezone", timeSettings.workTimezone);
  set("workTimezone", timeSettings.workTimezone);
  set("displayTimezone", timeSettings.workTimezone);
  set("timeFormatPref", timeSettings.timeFormat || "24h");
  set("defDayStart", renderedDefaults.dayStart);
  set("defDayEnd", renderedDefaults.dayEnd);
  set("defDayBreak", renderedDefaults.dayBreakMinutes);
  set("defDayPlan", renderedDefaults.dayPlannedHours);
  set("defNightStart", renderedDefaults.nightStart);
  set("defNightEnd", renderedDefaults.nightEnd);
  set("defNightBreak", renderedDefaults.nightBreakMinutes);
  set("defNightPlan", renderedDefaults.nightPlannedHours);
  renderTimePreview(timeSettings);
  const templateZone = $("shiftTemplateZoneHint");
  if (templateZone) templateZone.textContent = state.language === "en"
    ? `Template times are shown in ${timeSettings.workTimezone}. Existing dated shifts keep their immutable instants.`
    : `Время шаблонов показано в ${timeSettings.workTimezone}. Уже назначенные смены сохраняют свои абсолютные интервалы.`;
  setTimeSettingsStatus(preserveShiftDefaults ? "dirty" : "saved");
}
function isRecognizedTimeZone(value){
  try {
    new Intl.DateTimeFormat("en-US", { timeZone:value }).format(new Date());
    return true;
  } catch (_) { return false; }
}
async function saveTimeSettings(){
  state.ui = state.ui || {};
  state.ui.savingTimeSettings = true;
  const next = readTimeSettingsForm();
  if (!isRecognizedTimeZone(next.workTimezone)) {
    setSave("err", t("часовой пояс не распознан"));
    setTimeSettingsStatus("dirty", t("часовой пояс не распознан"));
    state.ui.savingTimeSettings = false;
    return false;
  }
  setTimeSettingsStatus("dirty", state.language === "en" ? "saving…" : "сохранение…");
  try {
    const payload = typeof currentProfilePayload === "function"
      ? currentProfilePayload({ workTimezone:next.workTimezone, displayTimezone:next.workTimezone })
      : { workTimezone:next.workTimezone, displayTimezone:next.workTimezone };
    const profile = await jfetch("/api/profile", { method:"PUT", body:payload });
    state.profile = profile;
    state.timeSettings = {
      ...next,
      workRegionName:"",
      workOffsetMoscow:0,
      workTimezone:profile.workTimezone || next.workTimezone,
      displayTimezone:profile.workTimezone || next.workTimezone
    };
    storeTimeSettings(state.timeSettings);
    renderTimeSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    // A timezone change invalidates every server projection in the cached month.
    // Skip the IndexedDB-first path here: it would repaint the old source timezone
    // and could leave the selected-day card stale even though the profile is saved.
    if (typeof loadMonth === "function") await loadMonth({ fresh:true });
    if (typeof loadTaskBoard === "function" && moduleEnabled("tasks")) await loadTaskBoard(true);
    syncTimeSettingsFromBuiltins();
    renderTimeSettings();
    renderCustomList();
    renderChips();
    if (moduleEnabled("overtime") && typeof loadLedgerPage === "function") await loadLedgerPage(true);
    if (moduleEnabled("notifications") && typeof showMonthNotifications === "function") await showMonthNotifications();
    if (state.selected && typeof renderSelectedDayModules === "function") renderSelectedDayModules();
    await refreshLegacyShiftIndicator();
    await refreshLegacyTaskDeadlineIndicator();
    setSave("saved", t("настройки времени сохранены"));
    return true;
  } catch (err) {
    console.error(err);
    setTimeSettingsStatus("dirty", state.language === "en" ? "save failed" : "ошибка сохранения");
    setSave("err", err.message || t("не удалось сохранить часовой пояс"));
    return false;
  } finally {
    state.ui.savingTimeSettings = false;
  }
}
window.__dutylogTimeSettingsSaveReady = Promise.resolve();
function runTimeSettingsSave(){
  const ready = Promise.resolve(saveTimeSettings());
  window.__dutylogTimeSettingsSaveReady = ready;
  return ready;
}
let timeAutoApplyTimer = null;
let timeSettingsApplyQueue = Promise.resolve();
let timeSettingsApplyRevision = 0;
function cancelTimeSettingsAutoApply(){
  if (!timeAutoApplyTimer) return;
  clearTimeout(timeAutoApplyTimer);
  timeAutoApplyTimer = null;
}
function scheduleTimeSettingsApply(){
  cancelTimeSettingsAutoApply();
  timeAutoApplyTimer = setTimeout(() => {
    timeAutoApplyTimer = null;
    applyTimeSettingsToBuiltins(true);
  }, 700);
}
function fillShiftFormFromDefaults(kind){
  const timeSettings = state.timeSettings || loadTimeSettings();
  resetShiftTypeForm();
  if (kind === "night") {
    $("nsName").value = "Ночная кастомная";
    $("nsHours").value = fmtHours(timeSettings.nightPlannedHours);
    $("nsStart").value = timeSettings.nightStart;
    $("nsEnd").value = timeSettings.nightEnd;
    $("nsBreak").value = timeSettings.nightBreakMinutes;
    $("nsPlan").value = fmtHours(timeSettings.nightPlannedHours);
  } else {
    $("nsName").value = "Дневная кастомная";
    $("nsHours").value = fmtHours(timeSettings.dayPlannedHours);
    $("nsStart").value = timeSettings.dayStart;
    $("nsEnd").value = timeSettings.dayEnd;
    $("nsBreak").value = timeSettings.dayBreakMinutes;
    $("nsPlan").value = fmtHours(timeSettings.dayPlannedHours);
  }
  updateShiftPlanHint();
  renderCustomList();
  openAppModal("shiftTypeModal", "nsName");
  setSave("", "");
}
function patchForBuiltInShift(name, timeSettings){
  if (name === "Ночная") return {
    startTime: timeSettings.nightStart,
    endTime: timeSettings.nightEnd,
    breakMinutes: timeSettings.nightBreakMinutes,
    plannedHours: timeSettings.nightPlannedHours,
    hours: timeSettings.nightPlannedHours,
  };
  return {
    startTime: timeSettings.dayStart,
    endTime: timeSettings.dayEnd,
    breakMinutes: timeSettings.dayBreakMinutes,
    plannedHours: timeSettings.dayPlannedHours,
    hours: timeSettings.dayPlannedHours,
  };
}
function applyTimeSettingsToBuiltins(silent = false){
  if (!silent) cancelTimeSettingsAutoApply();
  const form = readTimeSettingsForm();
  const defaultsRevision = timeSettingsDefaultsRevision;
  const applyRevision = ++timeSettingsApplyRevision;
  const operation = async () => {
    // A newer manual or debounced request already captured fresher form data.
    if (applyRevision !== timeSettingsApplyRevision) return false;
    const timeSettings = {
      ...form,
      workTimezone:state.timeSettings?.workTimezone || loadTimeSettings().workTimezone,
      timeFormat:state.timeSettings?.timeFormat || loadTimeSettings().timeFormat
    };
    storeTimeSettings(timeSettings);
    const targets = state.shiftTypes.filter(s => s.name === "Дневная" || s.name === "Ночная");
    if (!targets.length) {
      if (applyRevision === timeSettingsApplyRevision) setSave("err", t("не нашёл Дневную/Ночную смену"));
      return false;
    }
    if (!silent && applyRevision === timeSettingsApplyRevision) setSave("saving");
    try {
      for (const shift of targets) {
        const updated = await api.updateShiftType(shift.id, patchForBuiltInShift(shift.name, timeSettings));
        const index = state.shiftTypes.findIndex(item => Number(item.id) === Number(shift.id));
        if (index >= 0) state.shiftTypes[index] = updated;
      }
      // Never let an older in-flight autosave repaint a newer manual edit.
      if (applyRevision !== timeSettingsApplyRevision) return true;
      if (defaultsRevision === timeSettingsDefaultsRevision) {
        timeSettingsDefaultsCommittedRevision = defaultsRevision;
      }
      setSave("saved", silent ? t("время смен применено") : t("встроенные смены обновлены"));
      renderTimeSettings();
      renderCustomList();
      renderChips();
      renderCalendar();
      renderOvertimeControls();
      return true;
    } catch (err) {
      console.error(err);
      if (applyRevision === timeSettingsApplyRevision) setSave("err", err.message);
      return false;
    }
  };
  const pending = timeSettingsApplyQueue.then(operation, operation);
  timeSettingsApplyQueue = pending.catch(() => false);
  return pending;
}
let legacyShiftPreviewState = null;
function legacyShiftStatus(text = "", tone = ""){
  const box = $("legacyShiftStatus");
  if (!box) return;
  box.textContent = text;
  box.className = `legacyMigrationStatus ${tone}`.trim();
}
function renderLegacyShiftPreview(preview){
  legacyShiftPreviewState = preview;
  const list = $("legacyShiftList");
  if (!list) return;
  list.innerHTML = "";
  const items = preview?.occurrences || [];
  $("legacyShiftOpen").hidden = items.length === 0;
  if (!items.length) {
    legacyShiftStatus(t("Все смены уже имеют точный часовой пояс."), "ok");
    return;
  }
  legacyShiftStatus(`${t("Найдено старых смен")}: ${items.length}`);
  for (const item of items) {
    const row = document.createElement("label");
    row.className = "legacyMigrationRow";
    row.innerHTML = `
      <input type="checkbox" data-legacy-shift-id="${Number(item.dayEntryId)}" checked>
      <span><b>${esc(item.sourceDate)} · ${esc(item.shiftName || t("Смена"))}</b>
      <small>${esc(displayDateTimeRange(item.localStart, item.localEnd))} · ${esc(item.sourceTimezone || "")}</small>
      <small>→ ${esc(displayDateTimeRange(item.projectedStart, item.projectedEnd))}</small></span>`;
    list.appendChild(row);
  }
}
async function refreshLegacyShiftPreview({ quiet = false } = {}){
  const timezone = $("legacyShiftTimezone")?.value || state.profile?.workTimezone || browserTimeZone();
  try {
    const preview = await api.previewLegacyShifts(timezone);
    renderLegacyShiftPreview(preview);
    return preview;
  } catch (err) {
    if (!quiet) legacyShiftStatus(err.message || t("Не удалось загрузить старые смены"), "err");
    return null;
  }
}
async function refreshLegacyShiftIndicator(){
  const timezone = state.profile?.workTimezone || state.timeSettings?.workTimezone || browserTimeZone();
  const preview = await api.previewLegacyShifts(timezone).catch(() => null);
  if ($("legacyShiftOpen")) $("legacyShiftOpen").hidden = !preview?.legacyCount;
}
async function openLegacyShiftMigration(){
  const timezone = state.profile?.workTimezone || state.timeSettings?.workTimezone || browserTimeZone();
  populateTimeZoneSelect("legacyShiftTimezone", timezone);
  $("legacyShiftTimezone").value = timezone;
  openAppModal("legacyShiftModal", "legacyShiftTimezone");
  legacyShiftStatus(t("Загрузка…"));
  await refreshLegacyShiftPreview();
}
function closeLegacyShiftMigration(){ closeAppModal("legacyShiftModal"); }
async function applyLegacyShiftMigration(){
  const ids = [...document.querySelectorAll("[data-legacy-shift-id]:checked")].map(input => Number(input.dataset.legacyShiftId));
  if (!ids.length) return legacyShiftStatus(t("Выберите хотя бы одну смену"), "err");
  const sourceTimezone = $("legacyShiftTimezone").value;
  $("legacyShiftApply").disabled = true;
  legacyShiftStatus(t("Сохранение…"));
  try {
    const preview = await api.migrateLegacyShifts({ sourceTimezone, dayEntryIds:ids });
    renderLegacyShiftPreview(preview);
    await loadMonth({ fresh:true });
    setSave("saved", t("Старые смены привязаны"));
    if (!preview.legacyCount) closeLegacyShiftMigration();
  } catch (err) {
    legacyShiftStatus(err.message || t("Не удалось привязать смены"), "err");
  } finally {
    $("legacyShiftApply").disabled = false;
  }
}

Object.assign(I18N_EN, {
  "Все задачи со временем уже привязаны к часовому поясу.":"Every timed task is already linked to a timezone.",
  "Найдено старых задач":"Legacy timed tasks found",
  "Не удалось загрузить старые задачи":"Failed to load legacy tasks",
  "Выберите хотя бы одну задачу":"Select at least one task",
  "Старые задачи привязаны":"Legacy tasks linked",
  "Не удалось привязать задачи":"Failed to link tasks"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

let legacyTaskDeadlinePreviewState = null;
function legacyTaskDeadlineStatus(text = "", tone = ""){
  const box = $("legacyTaskDeadlineStatus");
  if (!box) return;
  box.textContent = text;
  box.className = `legacyMigrationStatus ${tone}`.trim();
}
function renderLegacyTaskDeadlinePreview(preview){
  legacyTaskDeadlinePreviewState = preview;
  const list = $("legacyTaskDeadlineList");
  if (!list) return;
  list.innerHTML = "";
  const items = preview?.tasks || [];
  if ($("legacyTaskDeadlineOpen")) $("legacyTaskDeadlineOpen").hidden = items.length === 0;
  if (!items.length) {
    legacyTaskDeadlineStatus(t("Все задачи со временем уже привязаны к часовому поясу."), "ok");
    return;
  }
  legacyTaskDeadlineStatus(`${t("Найдено старых задач")}: ${items.length}`);
  for (const item of items) {
    const row = document.createElement("label");
    row.className = "legacyMigrationRow";
    row.innerHTML = `
      <input type="checkbox" data-legacy-task-deadline-id="${Number(item.taskId)}" checked>
      <span><b>${esc(item.text || t("Задача"))}</b>
      <small>${esc(item.sourceDate)} ${esc(item.sourceTime)} · ${esc(item.sourceTimezone || "")}</small>
      <small>→ ${esc(item.projectedDate)} ${esc(item.projectedTime)} · ${esc(item.targetTimezone || "")}</small></span>`;
    list.appendChild(row);
  }
}
async function refreshLegacyTaskDeadlinePreview({ quiet = false } = {}){
  const timezone = $("legacyTaskDeadlineTimezone")?.value || state.profile?.workTimezone || browserTimeZone();
  try {
    const preview = await api.previewLegacyTaskDeadlines(timezone);
    renderLegacyTaskDeadlinePreview(preview);
    return preview;
  } catch (err) {
    if (!quiet) legacyTaskDeadlineStatus(err.message || t("Не удалось загрузить старые задачи"), "err");
    return null;
  }
}
async function refreshLegacyTaskDeadlineIndicator(){
  const timezone = state.profile?.workTimezone || state.timeSettings?.workTimezone || browserTimeZone();
  const preview = await api.previewLegacyTaskDeadlines(timezone).catch(() => null);
  if ($("legacyTaskDeadlineOpen")) $("legacyTaskDeadlineOpen").hidden = !preview?.legacyCount;
}
async function openLegacyTaskDeadlineMigration(){
  const timezone = state.profile?.workTimezone || state.timeSettings?.workTimezone || browserTimeZone();
  populateTimeZoneSelect("legacyTaskDeadlineTimezone", timezone);
  $("legacyTaskDeadlineTimezone").value = timezone;
  openAppModal("legacyTaskDeadlineModal", "legacyTaskDeadlineTimezone");
  legacyTaskDeadlineStatus(t("Загрузка…"));
  await refreshLegacyTaskDeadlinePreview();
}
function closeLegacyTaskDeadlineMigration(){ closeAppModal("legacyTaskDeadlineModal"); }
async function applyLegacyTaskDeadlineMigration(){
  const ids = [...document.querySelectorAll("[data-legacy-task-deadline-id]:checked")]
    .map(input => Number(input.dataset.legacyTaskDeadlineId));
  if (!ids.length) return legacyTaskDeadlineStatus(t("Выберите хотя бы одну задачу"), "err");
  const sourceTimezone = $("legacyTaskDeadlineTimezone").value;
  $("legacyTaskDeadlineApply").disabled = true;
  legacyTaskDeadlineStatus(t("Сохранение…"));
  try {
    const preview = await api.migrateLegacyTaskDeadlines({ sourceTimezone, taskIds:ids });
    renderLegacyTaskDeadlinePreview(preview);
    if (typeof loadMonth === "function") await loadMonth({ fresh:true });
    if (typeof loadTaskBoard === "function") await loadTaskBoard(true);
    if (typeof showMonthNotifications === "function" && moduleEnabled("notifications")) await showMonthNotifications();
    renderTasks();
    renderTaskBoard();
    renderCalendar();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    setSave("saved", t("Старые задачи привязаны"));
    if (!preview.legacyCount) closeLegacyTaskDeadlineMigration();
  } catch (err) {
    legacyTaskDeadlineStatus(err.message || t("Не удалось привязать задачи"), "err");
  } finally {
    $("legacyTaskDeadlineApply").disabled = false;
  }
}

function initTimeSettingsEvents(){
  if (!$("timeSettingsCard")) return;
  $("timeSaveTimezone")?.addEventListener("click", runTimeSettingsSave);
  $("legacyShiftOpen")?.addEventListener("click", openLegacyShiftMigration);
  $("legacyShiftClose")?.addEventListener("click", closeLegacyShiftMigration);
  $("legacyShiftCancel")?.addEventListener("click", closeLegacyShiftMigration);
  $("legacyShiftBackdrop")?.addEventListener("click", closeLegacyShiftMigration);
  $("legacyShiftPreview")?.addEventListener("click", () => refreshLegacyShiftPreview());
  $("legacyShiftSelectAll")?.addEventListener("click", () => document.querySelectorAll("[data-legacy-shift-id]").forEach(input => { input.checked = true; }));
  $("legacyShiftApply")?.addEventListener("click", applyLegacyShiftMigration);
  $("legacyTaskDeadlineOpen")?.addEventListener("click", openLegacyTaskDeadlineMigration);
  $("legacyTaskDeadlineClose")?.addEventListener("click", closeLegacyTaskDeadlineMigration);
  $("legacyTaskDeadlineCancel")?.addEventListener("click", closeLegacyTaskDeadlineMigration);
  $("legacyTaskDeadlineBackdrop")?.addEventListener("click", closeLegacyTaskDeadlineMigration);
  $("legacyTaskDeadlinePreview")?.addEventListener("click", () => refreshLegacyTaskDeadlinePreview());
  $("legacyTaskDeadlineSelectAll")?.addEventListener("click", () => document.querySelectorAll("[data-legacy-task-deadline-id]").forEach(input => { input.checked = true; }));
  $("legacyTaskDeadlineApply")?.addEventListener("click", applyLegacyTaskDeadlineMigration);
  $("timeDetectBrowser")?.addEventListener("click", () => {
    populateTimeZoneSelect("workTimezone", browserTimeZone());
    $("workTimezone").value = browserTimeZone();
    if ($("displayTimezone")) $("displayTimezone").value = browserTimeZone();
    renderTimePreview(readTimeSettingsForm());
    setTimeSettingsStatus("dirty");
  });
  $("timeApplyBuiltins")?.addEventListener("click", () => applyTimeSettingsToBuiltins(false));
  $("timeFillDayForm")?.addEventListener("click", () => fillShiftFormFromDefaults("day"));
  $("timeFillNightForm")?.addEventListener("click", () => fillShiftFormFromDefaults("night"));
  for (const id of ["workTimezone", "timeFormatPref"]) {
    $(id)?.addEventListener("change", () => {
      renderTimePreview(readTimeSettingsForm());
      setTimeSettingsStatus("dirty");
    });
  }
  for (const id of TIME_SHIFT_DEFAULT_IDS) {
    $(id)?.addEventListener("input", () => {
      timeSettingsDefaultsRevision += 1;
      setTimeSettingsStatus("dirty");
    });
    $(id)?.addEventListener("change", () => {
      const form = readTimeSettingsForm();
      storeTimeSettings({
        ...form,
        workTimezone:state.profile?.workTimezone || state.timeSettings?.workTimezone || browserTimeZone(),
        displayTimezone:state.profile?.workTimezone || state.timeSettings?.workTimezone || browserTimeZone(),
        timeFormat:state.timeSettings?.timeFormat || "24h"
      });
      scheduleTimeSettingsApply();
    });
  }
}

function renderDiagnosticsClient(){
  const set = (id, value) => { if ($(id)) $(id).textContent = value; };
  set("diagFrontend", "v" + DUTYLOG_VERSION);
  set("diagBrowser", navigator.userAgent.replace(/\s+/g, " ").slice(0, 90));
  set("diagCsrf", csrfToken() ? t("cookie есть") : t("cookie не найден"));
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.getRegistration().then(reg => set("diagSw", reg ? t("активен") : t("не зарегистрирован"))).catch(() => set("diagSw", t("ошибка")));
  } else set("diagSw", t("не поддерживается"));
}
function renderRegistrationAdmin(status = null){
  const enabled = status?.enabled === true;
  const statusEl = $("registrationAdminStatus");
  const detailsEl = $("registrationAdminDetails");
  const toggle = $("registrationEnabledToggle");
  const stateLabel = enabled ? t("открыта") : t("закрыта");
  if (statusEl) {
    statusEl.textContent = stateLabel;
    statusEl.className = "status " + (enabled ? "warn" : "ok");
  }
  if (toggle) toggle.checked = enabled;
  if (detailsEl) {
    const source = status?.source === "database" ? t("из админки") : t("значение по умолчанию");
    const changed = status?.updatedAt ? ` · ${t("изменено")} ${fmtSyncTime(status.updatedAt)}${status.updatedBy ? " " + t("пользователем") + " " + status.updatedBy : ""}` : "";
    detailsEl.textContent = `${t("Публичная регистрация")}: ${stateLabel} · ${source}${changed}`;
  }
}
async function refreshRegistrationAdmin(){
  try {
    const status = await api.registrationSettings();
    state.registrationSettings = status;
    renderRegistrationAdmin(status);
  } catch (err) {
    const detailsEl = $("registrationAdminDetails");
    if (detailsEl) detailsEl.textContent = t("Не удалось загрузить настройку регистрации: ") + (err.message || String(err));
  }
}
async function saveRegistrationAdmin(enabled){
  const toggle = $("registrationEnabledToggle");
  const statusEl = $("registrationAdminStatus");
  if (toggle) toggle.disabled = true;
  if (statusEl) statusEl.textContent = t("сохраняю…");
  try {
    const status = await api.updateRegistrationSettings(enabled);
    state.registrationSettings = status;
    renderRegistrationAdmin(status);
    setSave("saved", enabled ? t("публичная регистрация открыта") : t("публичная регистрация закрыта"));
  } catch (err) {
    setSave("err", err.message || t("не удалось сохранить настройку регистрации"));
    renderRegistrationAdmin(state.registrationSettings);
  } finally {
    if (toggle) toggle.disabled = false;
  }
}

function roleLabel(role){ return role === "ADMIN" ? t("админ") : t("пользователь"); }
function renderAdminUsers(users = []){
  const box = $("adminUsersList");
  const status = $("adminUsersStatus");
  if (!box) return;
  const page = { ...(state.adminUsersPage || {}), items: users };
  if (status) {
    const admins = users.filter(u => u.role === "ADMIN").length;
    status.className = "status statusMetrics";
    status.innerHTML = `<span class="statusChip"><b>${esc(t("Показано:"))}</b> ${pageRangeText(page)}</span><span class="statusChip ${admins > 0 ? 'statusChipOk' : 'statusChipWarn'}"><b>${esc(t("Админов на странице:"))}</b> ${admins}</span>`;
  }
  renderPager("adminUsersPager", page, nextPage => { state.adminUsersPage.page = nextPage; refreshAdminUsers(); }, nextSize => { state.adminUsersPage.size = nextSize; state.adminUsersPage.page = 0; refreshAdminUsers(); });
  if (!users.length) {
    box.innerHTML = `<span class="emptyLine">${esc(t("Пользователей пока нет."))}</span>`;
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
          <select data-admin-role="${u.id}" ${canChangeRole ? "" : "disabled"} title="${esc(t("Роль пользователя"))}">
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
    setSave("err", err.message || t("не удалось изменить роль"));
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
    setSave("err", err.message || t("не удалось сменить пароль"));
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
    if (st) st.textContent = t("ошибка");
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
function refreshAdminPanel(){
  if (!state.profile?.admin) return;
  renderDiagnosticsClient();
  refreshDiagnostics();
  refreshRegistrationAdmin();
  refreshAdminUsers();
}
function initAdminNavigation(){
  const root = $("view-admin");
  if (!root || root.dataset.adminNavReady === "1") return;
  root.dataset.adminNavReady = "1";
  const cards = [...root.querySelectorAll(".settingsCard[data-settings-section]")].filter(c => c.dataset.settingsSection !== "admin");
  function setActive(section){
    root.querySelectorAll("[data-admin-jump]").forEach(a => a.classList.toggle("on", a.dataset.adminJump === section));
  }
  function go(section, scroll = true){
    const card = root.querySelector(`[data-settings-section="${section}"]`);
    if (!card) return;
    setActive(section);
    history.replaceState(null, "", "#admin");
    if (scroll) card.scrollIntoView({ behavior:"smooth", block:"start" });
  }
  root.querySelectorAll("[data-admin-jump]").forEach(a => {
    a.addEventListener("click", ev => {
      ev.preventDefault();
      go(a.dataset.adminJump, true);
    });
  });
  $("adminBackNav")?.addEventListener("click", () => { location.hash = "#settings"; });
  const observer = "IntersectionObserver" in window ? new IntersectionObserver(entries => {
    const visible = entries
      .filter(e => e.isIntersecting)
      .sort((a,b) => b.intersectionRatio - a.intersectionRatio)[0];
    if (visible?.target?.dataset?.settingsSection) setActive(visible.target.dataset.settingsSection);
  }, { threshold:[0.22,0.55], rootMargin:"-15% 0px -55% 0px" }) : null;
  if (observer) cards.forEach(c => observer.observe(c));
  setActive("users");
}

function initDiagnosticsEvents(){
  if (!$("diagnosticsCard")) return;
  initAdminNavigation();
  $("diagnosticsRefresh")?.addEventListener("click", refreshDiagnostics);
  $("diagnosticsCopy")?.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(diagnosticsReportText()); setSave("saved", t("отчёт диагностики скопирован")); }
    catch (err) { setSave("err", t("не удалось скопировать отчёт")); }
  });
  renderDiagnosticsClient();
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
    time: "Часовой пояс и шаблоны дневной/ночной смены",
    shifts: "Кастомные и встроенные типы смен",
    scenarios: "Шаблоны, которые заполняют единый редактор переработки",
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
  if (document.documentElement.dataset.vueSettingsWorkspace === "ready") {
    renderTimeSettings();
    renderNotifications();
    if (typeof renderScheduleLayerSettings === "function") renderScheduleLayerSettings();
    refreshLegacyShiftIndicator().catch(() => {});
    refreshLegacyTaskDeadlineIndicator().catch(() => {});
    return;
  }
  initSettingsAccordion();
  renderAppearanceControls();
  renderTimeSettings();
  renderCustomList();
  renderNotifications();
  renderModuleSettings();
  applyModuleVisibility();
  renderTelegramPanel();
  if (typeof renderScheduleLayerSettings === "function") renderScheduleLayerSettings();
  if (moduleEnabled("telegram")) loadTelegramStatus();
  refreshLegacyShiftIndicator().catch(() => {});
  refreshLegacyTaskDeadlineIndicator().catch(() => {});
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

const BROWSER_NOTIFICATION_POLL_MS = 10_000;
const BROWSER_NOTIFICATION_FETCH_MS = 60_000;
const BROWSER_NOTIFICATION_GRACE_MS = 5 * 60_000;
let browserNotificationTimer = null;
let browserNotificationTickRunning = false;
let browserNotificationLastFetchAt = 0;
let browserNotificationReminders = [];
let browserNotificationRuntimeListenersBound = false;

function browserNotificationStorageKey(){
  const username = state.profile?.username || $("whoami")?.textContent?.trim() || "anonymous";
  return `dutylog.browserNotifications.delivered.v1.${username}`;
}
function readDeliveredBrowserNotifications(){
  try {
    const raw = JSON.parse(localStorage.getItem(browserNotificationStorageKey()) || "{}");
    const cutoff = Date.now() - 14 * 24 * 60 * 60 * 1000;
    return Object.fromEntries(Object.entries(raw).filter(([, ts]) => Number(ts) >= cutoff));
  } catch (_) { return {}; }
}
function writeDeliveredBrowserNotifications(items){
  try { localStorage.setItem(browserNotificationStorageKey(), JSON.stringify(items)); }
  catch (_) { /* private mode or storage quota */ }
}
function notificationPollRange(){
  const today = todayKey();
  // The source-date window must follow the saved DutyLog timezone, not the
  // operating-system timezone of the current browser.
  return { from:dateKeyOffset(today, -1), to:dateKeyOffset(today, 365) };
}
function browserReminderInstantValue(reminder){
  // v27.4.3 backend supplies an absolute UTC instant calculated from the
  // user's IANA timezone. The local remindAt value remains for display and
  // Telegram compatibility only.
  return reminder?.remindAtInstant || reminder?.remindAt || "";
}
function browserReminderFingerprint(reminder){
  return `${reminder?.id || "reminder"}|${browserReminderInstantValue(reminder)}`;
}
async function showBrowserReminder(reminder){
  const title = reminder.title || "DutyLog: Time & Overtime";
  const options = {
    body: reminder.details || "",
    icon: "/icons/icon-192.png",
    badge: "/icons/icon-192.png",
    tag: `dutylog:${browserReminderFingerprint(reminder)}`,
    renotify: false,
    data: { url:"/#calendar", sourceDate:reminder.sourceDate || null },
  };
  if ("serviceWorker" in navigator) {
    try {
      const registration = await navigator.serviceWorker.ready;
      await registration.showNotification(title, options);
      return;
    } catch (_) { /* fall back to the page Notification API */ }
  }
  new Notification(title, options);
}
async function browserNotificationTick(){
  if (browserNotificationTickRunning) return;
  if (!state.modulesLoaded || !moduleEnabled("notifications")) return;
  const settings = state.notificationSettings;
  if (!settings?.browserNotificationsEnabled) return;
  if (!("Notification" in window) || Notification.permission !== "granted") return;
  if (!navigator.onLine) return;

  browserNotificationTickRunning = true;
  try {
    const now = Date.now();
    if (browserNotificationLastFetchAt === 0 || now - browserNotificationLastFetchAt >= BROWSER_NOTIFICATION_FETCH_MS) {
      const range = notificationPollRange();
      const fetched = await api.notificationUpcoming(range.from, range.to, true);
      // The user can disable the module while the request is in flight. Never
      // deliver or cache a response that crossed that runtime boundary.
      if (!state.modulesLoaded || !moduleEnabled("notifications")) return;
      browserNotificationReminders = fetched;
      browserNotificationLastFetchAt = now;
    }
    const delivered = readDeliveredBrowserNotifications();
    for (const reminder of browserNotificationReminders || []) {
      const dueAt = new Date(browserReminderInstantValue(reminder)).getTime();
      if (!Number.isFinite(dueAt)) continue;
      const lateBy = now - dueAt;
      if (lateBy < 0 || lateBy > BROWSER_NOTIFICATION_GRACE_MS) continue;
      const fingerprint = browserReminderFingerprint(reminder);
      if (delivered[fingerprint]) continue;
      await showBrowserReminder(reminder);
      delivered[fingerprint] = Date.now();
      writeDeliveredBrowserNotifications(delivered);
    }
  } catch (err) {
    if (err?.status === 403 && err?.moduleKey === "notifications") {
      // Defensive self-healing for a stale client module map. One guarded 403
      // may race with the toggle, but it must never become a 10-second loop.
      stopBrowserNotificationScheduler();
      try { await loadModules(); } catch (_) { /* keep the scheduler stopped */ }
      return;
    }
    if (err?.status !== 401 && err?.status !== 403) console.warn("browser notification poll failed", err);
  } finally {
    browserNotificationTickRunning = false;
  }
}
function stopBrowserNotificationScheduler(){
  if (browserNotificationTimer != null) clearInterval(browserNotificationTimer);
  browserNotificationTimer = null;
  browserNotificationLastFetchAt = 0;
  browserNotificationReminders = [];
}
function invalidateBrowserNotificationSchedule(){
  browserNotificationLastFetchAt = 0;
  browserNotificationReminders = [];
  if (browserNotificationTimer != null && state.modulesLoaded && moduleEnabled("notifications")) {
    browserNotificationTick();
  }
}
function kickBrowserNotificationScheduler(){
  if (!state.modulesLoaded || !moduleEnabled("notifications")) {
    stopBrowserNotificationScheduler();
    return;
  }
  if (browserNotificationTimer == null) {
    browserNotificationTimer = setInterval(browserNotificationTick, BROWSER_NOTIFICATION_POLL_MS);
  }
  browserNotificationTick();
}
function syncBrowserNotificationSchedulerForModules(){
  if (!state.modulesLoaded || !moduleEnabled("notifications")) {
    stopBrowserNotificationScheduler();
    return;
  }
  kickBrowserNotificationScheduler();
}
function startBrowserNotificationScheduler(){
  syncBrowserNotificationSchedulerForModules();
  if (browserNotificationRuntimeListenersBound) return;
  browserNotificationRuntimeListenersBound = true;
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") syncBrowserNotificationSchedulerForModules();
  });
  window.addEventListener("online", syncBrowserNotificationSchedulerForModules);
}

function renderNotifyStatus(count){
  const box = $("notifyStatus");
  if (!box) return;
  const permission = browserPermissionStatus();
  const toneClass = permission.tone === "ok" ? "statusChipOk notifyPermissionGranted" : "statusChipWarn notifyPermissionWarn";
  box.className = "status notifyStatusChips";
  box.innerHTML = `<span class="statusChip statusChipPrimary notifyCountChip"><b>${Number(count) || 0}</b> ${esc(t("шт"))}</span><span class="statusChip ${toneClass}"><b>${esc(permission.label)}:</b> ${esc(permission.value)}</span>`;
}
function renderNotifications(){
  if (!moduleEnabled("notifications")) return;
  const s = state.notificationSettings;
  if (!$("notifyCard") || !s) return;
  const active = !!(s.browserNotificationsEnabled || s.shiftRemindersEnabled || s.tomorrowDigestEnabled || s.taskRemindersEnabled || s.importantDayRemindersEnabled);
  $("notifyCard")?.classList.toggle("notificationsActive", active);
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
  if ($("notifyListTitle")) $("notifyListTitle").textContent = t(state.notificationPreviewTitle || "Напоминания текущего месяца");
  const list = $("notifyList");
  list.innerHTML = "";
  const items = sourceItems.slice(0, 24);
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "notifyItem";
    empty.innerHTML = `<span class="notifyWhen">—</span><span class="notifyType">${esc(t("пусто"))}</span><span class="notifyTitle"><span class="notifyDetails">${esc(t(state.notificationPreview ? "На завтра напоминаний нет." : "На текущий месяц напоминаний нет."))}</span></span>`;
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
  if (!moduleEnabled("notifications")) { setSave("err", t("модуль выключен")); return; }
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
    state.notificationPreviewTitle = t("Напоминания текущего месяца");
    const r = monthFromTo();
    state.reminders = await api.notificationUpcoming(r.from, r.to);
    state.remindersByDate = {};
    for (const x of state.reminders) addToDateMap(state.remindersByDate, { ...x, date:x.sourceDate });
    setSave("saved");
    renderNotifications();
    renderCalendar();
    invalidateBrowserNotificationSchedule();
    syncBrowserNotificationSchedulerForModules();
  } catch (err) { console.error(err); setSave("err", err.message); }
}
async function requestNotificationPermission(){
  if (!moduleEnabled("notifications")) { alert(t("модуль выключен")); return; }
  if (!("Notification" in window)) { alert(t("Этот браузер не поддерживает Notification API")); return; }
  const perm = await Notification.requestPermission();
  await saveNotificationSettings({ browserNotificationsEnabled: perm === "granted" });
}
function testNotification(){
  if (!moduleEnabled("notifications")) { alert(t("модуль выключен")); return; }
  if (!("Notification" in window) || Notification.permission !== "granted") { alert(t("Сначала разрешите уведомления в браузере")); return; }
  new Notification("DutyLog: Time & Overtime", { body:t("Тестовое уведомление отправлено.") });
}
async function showTomorrowNotifications(){
  if (!moduleEnabled("notifications")) return;
  setSave("saving");
  try {
    state.notificationPreview = await api.notificationTomorrow();
    state.notificationPreviewTitle = t("напоминания на завтра");
    setSave("saved");
    renderNotifications();
  } catch (err) { console.error(err); setSave("err", err.message); }
}
async function showMonthNotifications(){
  if (!moduleEnabled("notifications")) return;
  setSave("saving");
  try {
    const r = monthFromTo();
    state.notificationPreview = null;
    state.notificationPreviewTitle = t("Напоминания текущего месяца");
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
