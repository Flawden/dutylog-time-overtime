/*
 * 39-vacation-planner.js — Vacation Planner and absence calendar composition
 *
 * Absences remain independent from shift rows. Calendar views only render
 * server-owned projections returned by CalendarRangeDto.
 */

"use strict";

Object.assign(I18N_EN, {
  "Отпуск":"Vacation",
  "Отсутствие":"Absence",
  "Отпуск и отсутствия":"Vacation & absences",
  "Доступно":"Available",
  "Запланировано":"Planned",
  "Осталось":"Remaining",
  "Календарные дни":"Calendar days",
  "Рабочие дни Пн–Пт":"Weekdays Mon–Fri",
  "Периодов пока нет":"No periods yet",
  "Смена пересекается с отсутствием":"A shift overlaps this absence",
  "Пересечение с другим отсутствием":"Overlaps another absence",
  "Лимит отпуска превышен":"Vacation allowance exceeded",
  "Предпросмотр готов":"Preview ready",
  "Период сохранён":"Period saved",
  "Правила сохранены":"Rules saved",
  "Тип отсутствия добавлен":"Absence type added",
  "Больничный":"Sick leave",
  "Без содержания":"Unpaid leave",
  "Другое":"Other",
  "Основной отпуск":"Main vacation",
  "Подтверждено":"Approved",
  "Выходные не списываются":"Weekends are not deducted",
  "календарных дней":"calendar days",
  "учтённых дней":"counted days",
  "конфликтов со сменами":"shift conflicts",
  "дней":"days",
  "Запланировать отсутствие":"Plan absence",
  "Редактировать отсутствие":"Edit absence",
  "Удалить этот период отсутствия?":"Delete this absence period?",
  "Удалить пользовательский тип отсутствия?":"Delete this custom absence type?",
  "списывает норму":"deducts allowance",
  "списывает из годовой нормы":"deducts annual allowance",
  "не списывает годовую норму":"does not deduct annual allowance",
  "встроенный":"built-in",
  "есть смена":"shift exists",
  "После сохранения":"After saving",
  "Выберите тип и даты периода":"Select an absence type and dates",
  "Выберите даты или используйте быстрый шаблон 14 / 28 / 35 дней.":"Choose dates or use a 14 / 28 / 35 day preset.",
  "На этот день отсутствие не запланировано.":"No absence is planned for this day."
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

let vacationPlannerLoadPromise = null;

function absenceTypeDisplayName(value){
  const code = String(value?.systemCode || "").toUpperCase();
  const fallback = value?.typeName || value?.name || t("Отсутствие");
  const ru = { VACATION:"Отпуск", SICK:"Больничный", UNPAID:"Без содержания", OTHER:"Другое" }[code];
  return ru ? t(ru) : fallback;
}
function absenceDisplayTitle(value){
  return String(value?.title || "").trim() || absenceTypeDisplayName(value);
}
function absencesOf(key){
  return moduleEnabled("vacation") ? (state.absencesByDate?.[key] || []) : [];
}
function vacationIsoAddDays(value, amount){
  const [y,m,d] = String(value || "").split("-").map(Number);
  if (!y || !m || !d) return "";
  const date = new Date(Date.UTC(y, m - 1, d + Number(amount || 0)));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth()+1).padStart(2,"0")}-${String(date.getUTCDate()).padStart(2,"0")}`;
}
function vacationDateLabel(value){
  if (!value) return "—";
  const [y,m,d] = String(value).split("-").map(Number);
  if (!y || !m || !d) return value;
  return new Intl.DateTimeFormat(currentLocale(), { day:"numeric", month:"short", year:"numeric", timeZone:"UTC" })
    .format(new Date(Date.UTC(y,m-1,d)));
}
function vacationStatusLabel(status){ return String(status).toUpperCase() === "APPROVED" ? t("Подтверждено") : t("Запланировано"); }
function setVacationMessage(text = "", error = false){
  const box = $("vacationMessage");
  if (!box) return;
  box.textContent = text;
  box.classList.toggle("error", !!error);
  box.classList.toggle("ok", !!text && !error);
}
function rebuildAbsenceIndex(occurrences = state.absenceOccurrences || []){
  state.absencesByDate = {};
  for (const occurrence of occurrences) addToDateMap(state.absencesByDate, occurrence);
}

async function loadVacationPlanner(force = false){
  if (!moduleEnabled("vacation")) return null;
  if (!force && state.vacationPlanner) return state.vacationPlanner;
  if (vacationPlannerLoadPromise) return vacationPlannerLoadPromise;
  const referenceDate = state.selected || todayKey();
  vacationPlannerLoadPromise = api.vacationPlanner({ referenceDate })
    .then(data => {
      state.vacationPlanner = data;
      if (Array.isArray(data?.occurrences)) {
        state.absenceOccurrences = data.occurrences;
        rebuildAbsenceIndex(data.occurrences);
      }
      renderVacationPlanner();
      renderVacationDay();
      renderCalendar();
      return data;
    })
    .catch(error => {
      console.error(error);
      setVacationMessage(error.message || t("Ошибка загрузки"), true);
      throw error;
    })
    .finally(() => { vacationPlannerLoadPromise = null; });
  return vacationPlannerLoadPromise;
}

function renderVacationSummary(){
  const summary = state.vacationPlanner?.summary;
  if (!summary) return;
  if ($("vacationAvailable")) $("vacationAvailable").textContent = `${summary.availableDays} ${t("дней")}`;
  if ($("vacationPlanned")) $("vacationPlanned").textContent = `${summary.plannedDays} ${t("дней")}`;
  if ($("vacationRemaining")) {
    $("vacationRemaining").textContent = `${summary.remainingDays} ${t("дней")}`;
    $("vacationRemaining").classList.toggle("negative", Number(summary.remainingDays) < 0);
  }
  if ($("vacationWorkYear")) $("vacationWorkYear").textContent = `${vacationDateLabel(summary.workYearStart)} — ${vacationDateLabel(summary.workYearEnd)}`;
  if ($("vacationCountModeLabel")) $("vacationCountModeLabel").textContent = summary.countMode === "WEEKDAYS" ? t("Рабочие дни Пн–Пт") : t("Календарные дни");
}

function renderVacationSettings(){
  const settings = state.vacationPlanner?.settings;
  if (!settings) return;
  if ($("vacationAllowance")) $("vacationAllowance").value = settings.annualAllowanceDays;
  if ($("vacationCarryover")) $("vacationCarryover").value = settings.carryoverDays;
  if ($("vacationCountMode")) $("vacationCountMode").value = settings.countMode;
  if ($("vacationWorkYearStart")) $("vacationWorkYearStart").value = `2026-${String(settings.workYearStartMonth).padStart(2,"0")}-${String(settings.workYearStartDay).padStart(2,"0")}`;
}

function renderVacationTypeControls(){
  const types = state.vacationPlanner?.types || [];
  const select = $("vacationType");
  if (select) {
    const selected = select.value;
    select.innerHTML = types.map(type => `<option value="${type.id}">${esc(absenceTypeDisplayName(type))}${type.countsAgainstAllowance ? ` · ${esc(t("списывает норму"))}` : ""}</option>`).join("");
    if (types.some(type => String(type.id) === selected)) select.value = selected;
  }
  const list = $("vacationTypeList");
  if (!list) return;
  list.innerHTML = types.map(type => `
    <div class="vacationTypeRow" data-type-id="${type.id}">
      <span class="vacationTypeDot" style="background:${esc(type.color)}"></span>
      <span><b>${esc(absenceTypeDisplayName(type))}</b><small>${type.countsAgainstAllowance ? esc(t("списывает из годовой нормы")) : esc(t("не списывает годовую норму"))}</small></span>
      ${type.systemPreset ? `<em>${esc(t("встроенный"))}</em>` : `<button type="button" data-delete-absence-type="${type.id}" aria-label="${esc(t("Удалить"))}">×</button>`}
    </div>`).join("");
  list.querySelectorAll("[data-delete-absence-type]").forEach(button => button.addEventListener("click", async () => {
    if (!confirm(t("Удалить пользовательский тип отсутствия?"))) return;
    try {
      await api.deleteAbsenceType(button.dataset.deleteAbsenceType);
      await loadVacationPlanner(true);
      setVacationMessage(t("Тип удалён"));
    } catch (error) { setVacationMessage(error.message, true); }
  }));
}

function renderVacationPeriods(){
  const list = $("vacationPeriodList");
  if (!list) return;
  const periods = state.vacationPlanner?.absences || [];
  if (!periods.length) {
    list.innerHTML = `<div class="vacationEmpty"><b>${esc(t("Периодов пока нет"))}</b><span>${esc(t("Выберите даты или используйте быстрый шаблон 14 / 28 / 35 дней."))}</span></div>`;
    return;
  }
  list.innerHTML = periods.map(period => `
    <article class="vacationPeriodCard" data-absence-id="${period.id}" style="--absence-color:${esc(period.typeColor)}">
      <div class="vacationPeriodColor"></div>
      <div class="vacationPeriodMain">
        <div class="vacationPeriodTop"><b>${esc(absenceDisplayTitle(period))}</b><span>${esc(vacationStatusLabel(period.status))}</span></div>
        <div class="vacationPeriodDates">${esc(vacationDateLabel(period.startDate))} — ${esc(vacationDateLabel(period.endDate))}</div>
        <div class="vacationPeriodMeta">${esc(absenceTypeDisplayName(period))} · ${period.calendarDays} ${esc(t("календарных дней"))}${period.countsAgainstAllowance ? ` · ${period.countedDays} ${esc(t("учтённых дней"))}` : ""}</div>
        ${period.shiftConflictCount ? `<div class="vacationConflict">⚠ ${period.shiftConflictCount} ${esc(t("конфликтов со сменами"))}</div>` : ""}
        ${period.note ? `<p>${esc(period.note)}</p>` : ""}
      </div>
      <div class="vacationPeriodActions">
        <button type="button" data-edit-absence="${period.id}">${esc(t("Изменить"))}</button>
        <button class="dangerGhost" type="button" data-delete-absence="${period.id}">${esc(t("Удалить"))}</button>
      </div>
    </article>`).join("");
  list.querySelectorAll("[data-edit-absence]").forEach(button => button.addEventListener("click", () => editAbsence(Number(button.dataset.editAbsence))));
  list.querySelectorAll("[data-delete-absence]").forEach(button => button.addEventListener("click", () => deleteAbsence(Number(button.dataset.deleteAbsence))));
}

function renderVacationPlanner(){
  if (!$("view-vacation")) return;
  renderVacationSummary();
  renderVacationSettings();
  renderVacationTypeControls();
  renderVacationPeriods();
}

function renderVacationDay(){
  const box = $("vacationDayList");
  if (!box || !state.selected) return;
  const items = absencesOf(state.selected);
  box.innerHTML = items.length ? items.map(item => `
    <button class="vacationDayItem" type="button" data-day-absence="${item.periodId}" style="--absence-color:${esc(item.typeColor || "#4FA3A5")}">
      <span>☂</span><span><b>${esc(absenceDisplayTitle(item))}</b><small>${esc(absenceTypeDisplayName(item))} · ${esc(vacationStatusLabel(item.status))}${item.shiftConflict ? ` · ⚠ ${esc(t("есть смена"))}` : ""}</small></span><i>›</i>
    </button>`).join("") : `<div class="dayPanelHint">${esc(t("На этот день отсутствие не запланировано."))}</div>`;
  box.querySelectorAll("[data-day-absence]").forEach(button => button.addEventListener("click", () => editAbsence(Number(button.dataset.dayAbsence))));
}

function resetVacationEditor({ keepDates = false } = {}){
  state.editingAbsenceId = null;
  state.vacationPreview = null;
  if ($("vacationEditorTitle")) $("vacationEditorTitle").textContent = t("Запланировать отсутствие");
  if ($("vacationEditorReset")) $("vacationEditorReset").hidden = true;
  if ($("vacationSaveBtn")) $("vacationSaveBtn").textContent = t("Сохранить период");
  if (!keepDates) {
    const date = state.selected || todayKey();
    if ($("vacationStart")) $("vacationStart").value = date;
    if ($("vacationEnd")) $("vacationEnd").value = date;
  }
  if ($("vacationTitle")) $("vacationTitle").value = "";
  if ($("vacationNote")) $("vacationNote").value = "";
  if ($("vacationStatus")) $("vacationStatus").value = "PLANNED";
  if ($("vacationPreview")) { $("vacationPreview").hidden = true; $("vacationPreview").innerHTML = ""; }
  setVacationMessage("");
}

function editAbsence(id){
  const period = (state.vacationPlanner?.absences || []).find(item => Number(item.id) === Number(id));
  if (!period) {
    loadVacationPlanner(true).then(() => editAbsence(id)).catch(console.error);
    return;
  }
  state.editingAbsenceId = period.id;
  state.vacationPreview = null;
  if ($("vacationEditorTitle")) $("vacationEditorTitle").textContent = t("Редактировать отсутствие");
  if ($("vacationEditorReset")) $("vacationEditorReset").hidden = false;
  if ($("vacationSaveBtn")) $("vacationSaveBtn").textContent = t("Сохранить изменения");
  if ($("vacationType")) $("vacationType").value = String(period.typeId);
  if ($("vacationTitle")) $("vacationTitle").value = period.title || "";
  if ($("vacationStart")) $("vacationStart").value = period.startDate;
  if ($("vacationEnd")) $("vacationEnd").value = period.endDate;
  if ($("vacationStatus")) $("vacationStatus").value = period.status || "PLANNED";
  if ($("vacationNote")) $("vacationNote").value = period.note || "";
  if ($("vacationPreview")) $("vacationPreview").hidden = true;
  location.hash = "#vacation";
  requestAnimationFrame(() => $("vacationPeriodForm")?.scrollIntoView({ behavior:"smooth", block:"start" }));
}
function editAbsenceFromOccurrence(occurrence){
  location.hash = "#vacation";
  loadVacationPlanner(false).then(() => editAbsence(occurrence.periodId)).catch(console.error);
}

function readVacationDraft(){
  return {
    typeId:Number($("vacationType")?.value || 0),
    title:$("vacationTitle")?.value?.trim() || null,
    startDate:$("vacationStart")?.value || "",
    endDate:$("vacationEnd")?.value || "",
    status:$("vacationStatus")?.value || "PLANNED",
    note:$("vacationNote")?.value?.trim() || null,
  };
}
function renderVacationPreview(preview){
  const box = $("vacationPreview");
  if (!box) return;
  state.vacationPreview = preview;
  const blocked = preview.absenceConflictCount > 0 || preview.exceedsAllowance;
  box.hidden = false;
  box.classList.toggle("blocked", blocked);
  const warnings = [];
  if (preview.shiftConflictCount) warnings.push(`⚠ ${preview.shiftConflictCount} ${t("конфликтов со сменами")}`);
  if (preview.absenceConflictCount) warnings.push(`⛔ ${t("Пересечение с другим отсутствием")}`);
  if (preview.exceedsAllowance) warnings.push(`⛔ ${t("Лимит отпуска превышен")}: ${preview.exceededBy} ${t("дней")}`);
  box.innerHTML = `
    <div class="vacationPreviewHead"><b>${esc(preview.typeName)}</b><span>${preview.calendarDays} ${esc(t("календарных дней"))} · ${preview.countedDays} ${esc(t("учтённых дней"))}</span></div>
    <div class="vacationPreviewBalance"><span>${esc(t("Запланировано"))}: <b>${preview.plannedBefore}</b></span><span>${esc(t("После сохранения"))}: <b>${preview.projectedPlanned}</b></span><span>${esc(t("Осталось"))}: <b>${preview.remainingAfter}</b></span></div>
    ${warnings.length ? `<div class="vacationPreviewWarnings">${warnings.map(item => `<span>${esc(item)}</span>`).join("")}</div>` : `<div class="vacationPreviewOk">✓ ${esc(t("Предпросмотр готов"))}</div>`}
    <div class="vacationPreviewDays">${(preview.items || []).map(item => `<span class="${item.action === "CONFLICT" ? "conflict" : item.shiftConflict ? "shift" : item.counted ? "counted" : "free"}" title="${esc(item.date)}">${String(item.date).slice(8,10)}</span>`).join("")}</div>`;
  return !blocked;
}

async function previewVacationDraft(){
  const draft = readVacationDraft();
  if (!draft.typeId || !draft.startDate || !draft.endDate) {
    setVacationMessage(t("Выберите тип и даты периода"), true);
    return null;
  }
  try {
    const preview = await api.previewAbsence({ typeId:draft.typeId, startDate:draft.startDate, endDate:draft.endDate, excludePeriodId:state.editingAbsenceId });
    renderVacationPreview(preview);
    setVacationMessage(t("Предпросмотр готов"));
    return preview;
  } catch (error) {
    setVacationMessage(error.message || t("Ошибка"), true);
    return null;
  }
}

async function saveVacationPeriod(event){
  event?.preventDefault();
  const draft = readVacationDraft();
  const preview = await previewVacationDraft();
  if (!preview || preview.absenceConflictCount > 0 || preview.exceedsAllowance) return;
  const button = $("vacationSaveBtn");
  if (button) button.disabled = true;
  try {
    if (state.editingAbsenceId) await api.updateAbsence(state.editingAbsenceId, draft);
    else await api.createAbsence(draft);
    resetVacationEditor();
    await loadVacationPlanner(true);
    await loadMonth({ fresh:true });
    setVacationMessage(t("Период сохранён"));
  } catch (error) {
    const message = error.code === "VACATION_LIMIT_EXCEEDED" ? t("Лимит отпуска превышен")
      : error.code === "ABSENCE_OVERLAP" ? t("Пересечение с другим отсутствием")
      : (error.message || t("Ошибка сохранения"));
    setVacationMessage(message, true);
  } finally { if (button) button.disabled = false; }
}

async function deleteAbsence(id){
  if (!confirm(t("Удалить этот период отсутствия?"))) return;
  try {
    await api.deleteAbsence(id);
    if (Number(state.editingAbsenceId) === Number(id)) resetVacationEditor();
    await loadVacationPlanner(true);
    await loadMonth({ fresh:true });
    setVacationMessage(t("Период удалён"));
  } catch (error) { setVacationMessage(error.message, true); }
}

async function saveVacationSettings(event){
  event?.preventDefault();
  const start = $("vacationWorkYearStart")?.value || "2026-01-01";
  const parts = start.split("-").map(Number);
  const body = {
    annualAllowanceDays:Number($("vacationAllowance")?.value || 0),
    carryoverDays:Number($("vacationCarryover")?.value || 0),
    countMode:$("vacationCountMode")?.value || "CALENDAR_DAYS",
    workYearStartMonth:parts[1] || 1,
    workYearStartDay:parts[2] || 1,
  };
  try {
    await api.updateVacationSettings(body);
    await loadVacationPlanner(true);
    await loadMonth({ fresh:true });
    setVacationMessage(t("Правила сохранены"));
  } catch (error) { setVacationMessage(error.message, true); }
}

async function createVacationType(event){
  event?.preventDefault();
  try {
    await api.createAbsenceType({
      name:$("vacationTypeName")?.value?.trim(),
      color:$("vacationTypeColor")?.value || "#4FA3A5",
      countsAgainstAllowance:!!$("vacationTypeCounts")?.checked,
    });
    if ($("vacationTypeName")) $("vacationTypeName").value = "";
    if ($("vacationTypeCounts")) $("vacationTypeCounts").checked = false;
    await loadVacationPlanner(true);
    setVacationMessage(t("Тип отсутствия добавлен"));
  } catch (error) { setVacationMessage(error.message, true); }
}

function applyVacationDuration(days){
  const start = $("vacationStart")?.value || state.selected || todayKey();
  if ($("vacationStart")) $("vacationStart").value = start;
  if ($("vacationEnd")) $("vacationEnd").value = vacationIsoAddDays(start, Number(days) - 1);
  state.vacationPreview = null;
  if ($("vacationPreview")) $("vacationPreview").hidden = true;
}

function openVacationPlannerForDate(date){
  const key = date || state.selected || todayKey();
  location.hash = "#vacation";
  loadVacationPlanner(false).then(() => {
    resetVacationEditor({ keepDates:true });
    if ($("vacationStart")) $("vacationStart").value = key;
    if ($("vacationEnd")) $("vacationEnd").value = key;
    requestAnimationFrame(() => $("vacationStart")?.focus());
  }).catch(console.error);
}
function openVacationPlannerView(){
  renderVacationPlanner();
  if (!state.vacationPlanner) loadVacationPlanner(false).catch(console.error);
}

$("vacationPeriodForm")?.addEventListener("submit", saveVacationPeriod);
$("vacationPreviewBtn")?.addEventListener("click", previewVacationDraft);
$("vacationEditorReset")?.addEventListener("click", () => resetVacationEditor());
$("vacationReload")?.addEventListener("click", () => loadVacationPlanner(true));
$("vacationSettingsForm")?.addEventListener("submit", saveVacationSettings);
$("vacationTypeForm")?.addEventListener("submit", createVacationType);
$("vacationPlanSelected")?.addEventListener("click", () => openVacationPlannerForDate(state.selected));
document.querySelectorAll("[data-vacation-days]").forEach(button => button.addEventListener("click", () => applyVacationDuration(button.dataset.vacationDays)));
for (const id of ["vacationType","vacationStart","vacationEnd"]) $(id)?.addEventListener("change", () => {
  state.vacationPreview = null;
  if ($("vacationPreview")) $("vacationPreview").hidden = true;
});

resetVacationEditor();
