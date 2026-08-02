/*
 * 39-vacation-planner.js — Absence & Time-Off factual calendar layer
 *
 * Planned shifts remain authoritative schedule facts. Full-day absences may
 * replace them visually, while partial time off overlays the shift in hours.
 */

"use strict";

Object.assign(I18N_EN, {
  "Отпуск":"Vacation", "Отгул":"Time off", "Отсутствие":"Absence",
  "Отпуск и отсутствия":"Vacation & absences", "Доступно":"Available",
  "Запланировано":"Planned", "Осталось":"Remaining", "Календарные дни":"Calendar days",
  "Рабочие дни Пн–Пт":"Weekdays Mon–Fri", "Периодов пока нет":"No periods yet",
  "Смена пересекается с отсутствием":"A planned shift exists under this absence",
  "Пересечение с другим отсутствием":"Overlaps another absence",
  "Лимит отпуска превышен":"Vacation allowance exceeded",
  "Баланс отгулов превышен":"Time-off balance exceeded",
  "Предпросмотр готов":"Preview ready", "Период сохранён":"Period saved",
  "Правила сохранены":"Rules saved", "Тип отсутствия добавлен":"Absence type added",
  "Больничный":"Sick leave", "Без содержания":"Unpaid leave", "Другое":"Other",
  "Основной отпуск":"Main vacation", "Подтверждено":"Approved",
  "Выходные не списываются":"Weekends are not deducted", "календарных дней":"calendar days",
  "учтённых дней":"counted days", "конфликтов со сменами":"planned shifts preserved",
  "дней":"days", "часов":"hours", "Запланировать отсутствие":"Plan absence",
  "Редактировать отсутствие":"Edit absence", "Удалить этот период отсутствия?":"Delete this absence period?",
  "Удалить пользовательский тип отсутствия?":"Delete this custom absence type?",
  "встроенный":"built-in", "есть смена":"planned shift", "После сохранения":"After saving",
  "Выберите тип и даты периода":"Select an absence type and dates",
  "Выберите даты или используйте быстрый шаблон 14 / 28 / 35 дней.":"Choose dates or use a 14 / 28 / 35 day preset.",
  "На этот день отсутствие не запланировано.":"No absence is planned for this day.",
  "Полный день":"Full day", "Часть дня":"Part of day", "Дни отпуска":"Vacation days",
  "Часы отгулов":"Time-off hours", "Без списания":"No balance deduction",
  "По графику":"Scheduled", "Фактически":"Actual", "заменяет смену":"replaces shift",
  "не заменяет смену":"keeps shift visible", "Баланс часов":"Hours balance",
  "Списано":"Charged", "частичный":"partial", "полный день":"full day",
  "Выберите время частичного отсутствия":"Choose partial absence time",
  "Отгулы и часы":"Time off & hours", "Плановая смена сохранена":"Planned shift preserved",
  "Источник покрытия":"Compensation source", "Отпускной баланс":"Vacation allowance",
  "Банк переработок":"Overtime bank", "Больничная политика":"Sick-pay policy",
  "Без содержания":"Unpaid", "Без покрытия":"Uncovered",
  "Переработки доступны":"Overtime available", "Переработки будут списаны":"Overtime will be used",
  "Недостаточно часов переработки":"Not enough overtime hours",
  "Черновик":"Draft", "Подано":"Submitted", "Отклонено":"Rejected",
  "Отменено":"Cancelled", "Завершено":"Completed", "Зарезервировано":"Reserved",
  "Проведено":"Posted", "Не резервирует баланс":"Does not reserve balance",
  "Период закрыт":"Period closed", "Откройте период или добавьте корректировку":"Reopen the period or add an adjustment"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

let vacationPlannerLoadPromise = null;

function absenceTypeDisplayName(value){
  const code = String(value?.systemCode || "").toUpperCase();
  const fallback = value?.typeName || value?.name || t("Отсутствие");
  const ru = { VACATION:"Отпуск", TIME_OFF:"Отгул", SICK:"Больничный", UNPAID:"Без содержания", OTHER:"Другое" }[code];
  return ru ? t(ru) : fallback;
}
function absenceDisplayTitle(value){ return String(value?.title || "").trim() || absenceTypeDisplayName(value); }
function absencesOf(key){ return moduleEnabled("vacation") ? (state.absencesByDate?.[key] || []) : []; }
function fullDayFactualAbsence(key){ return absencesOf(key).find(item => item.coverage === "FULL_DAY" && item.replacesShift) || null; }
function partialAbsencesOf(key){ return absencesOf(key).filter(item => item.coverage === "PARTIAL"); }
function absenceCoverageLabel(value){ return String(value || "FULL_DAY").toUpperCase() === "PARTIAL" ? t("Часть дня") : t("Полный день"); }
function absenceBalanceLabel(value){
  return ({ VACATION_DAYS:t("Дни отпуска"), TIME_OFF_HOURS:t("Часы отгулов"), NONE:t("Без списания") })[String(value || "NONE").toUpperCase()] || t("Без списания");
}
function absenceCompensationLabel(value){
  return ({
    VACATION_ALLOWANCE:t("Отпускной баланс"), OVERTIME_BANK:t("Банк переработок"),
    SICK_PAY:t("Больничная политика"), UNPAID:t("Без содержания"), NONE:t("Без покрытия")
  })[String(value || "NONE").toUpperCase()] || t("Без покрытия");
}
function defaultAbsenceCompensation(type){
  return ({ VACATION:"VACATION_ALLOWANCE", TIME_OFF:"OVERTIME_BANK", SICK:"SICK_PAY", UNPAID:"UNPAID" })[String(type?.systemCode || "").toUpperCase()] ||
    ({ VACATION_DAYS:"VACATION_ALLOWANCE", TIME_OFF_HOURS:"OVERTIME_BANK" })[String(type?.balancePolicy || "NONE").toUpperCase()] || "NONE";
}
function selectedAbsenceType(){
  const id = Number($("vacationType")?.value || 0);
  return (state.vacationPlanner?.types || []).find(type => Number(type.id) === id) || null;
}
function syncVacationCompensation({ preserve = false } = {}){
  const select = $("vacationCompensation");
  if (!select) return;
  const type = selectedAbsenceType();
  const code = String(type?.systemCode || "").toUpperCase();
  const balance = String(type?.balancePolicy || "NONE").toUpperCase();
  let allowed = new Set(["OVERTIME_BANK", "SICK_PAY", "UNPAID", "NONE"]);
  if (balance === "VACATION_DAYS") allowed = new Set(["VACATION_ALLOWANCE"]);
  else if (balance === "TIME_OFF_HOURS") allowed = new Set(["OVERTIME_BANK"]);
  else if (code === "SICK") allowed = new Set(["SICK_PAY"]);
  else if (code === "UNPAID") allowed = new Set(["UNPAID"]);
  for (const option of select.options) {
    const visible = allowed.has(option.value);
    option.hidden = !visible; option.disabled = !visible;
  }
  if (!preserve || !allowed.has(select.value)) select.value = defaultAbsenceCompensation(type);
}
function minutesLabel(value){
  const minutes = Math.max(0, Number(value || 0));
  const hours = minutes / 60;
  return `${Number.isInteger(hours) ? hours : hours.toFixed(2).replace(/0+$/,"" ).replace(/\.$/,"")} ${state.language === "en" ? "h" : "ч"}`;
}
function absenceTimeLabel(value){
  return value?.coverage === "PARTIAL" ? `${value.startTime || "—"}–${value.endTime || "—"}` : absenceCoverageLabel(value?.coverage);
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
  return new Intl.DateTimeFormat(currentLocale(), { day:"numeric", month:"short", year:"numeric", timeZone:"UTC" }).format(new Date(Date.UTC(y,m-1,d)));
}
function vacationStatusLabel(status){
  return ({
    DRAFT:t("Черновик"), PLANNED:t("Запланировано"), SUBMITTED:t("Подано"),
    APPROVED:t("Подтверждено"), REJECTED:t("Отклонено"), CANCELLED:t("Отменено"),
    COMPLETED:t("Завершено")
  })[String(status || "PLANNED").toUpperCase()] || t("Запланировано");
}
function absencePostingLabel(period){
  const status = String(period?.status || "PLANNED").toUpperCase();
  if (status === "PLANNED" || status === "SUBMITTED") return t("Зарезервировано");
  if (status === "APPROVED" || status === "COMPLETED") return t("Проведено");
  return t("Не резервирует баланс");
}
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
      renderVacationPlanner(); renderVacationDay(); renderCalendar();
      return data;
    })
    .catch(error => { console.error(error); setVacationMessage(error.message || t("Ошибка загрузки"), true); throw error; })
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
  if ($("timeOffRemaining")) {
    $("timeOffRemaining").textContent = minutesLabel(summary.timeOffRemainingMinutes);
    $("timeOffRemaining").classList.toggle("negative", Number(summary.timeOffRemainingMinutes) < 0);
  }
  if ($("timeOffBalanceLabel")) $("timeOffBalanceLabel").textContent = `${t("Использовано")}: ${minutesLabel(summary.timeOffPlannedMinutes)} · ${t("Банк переработок")}: ${minutesLabel(summary.timeOffAvailableMinutes)}`;
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
  if ($("timeOffBalanceHours")) $("timeOffBalanceHours").value = settings.timeOffBalanceHours;
  if ($("defaultTimeOffDayHours")) $("defaultTimeOffDayHours").value = settings.defaultTimeOffDayHours;
}

function renderVacationTypeControls(){
  const types = state.vacationPlanner?.types || [];
  const select = $("vacationType");
  if (select) {
    const selected = select.value;
    select.innerHTML = types.map(type => `<option value="${type.id}">${esc(absenceTypeDisplayName(type))} · ${esc(absenceBalanceLabel(type.balancePolicy))}</option>`).join("");
    if (types.some(type => String(type.id) === selected)) select.value = selected;
    syncVacationCompensation({ preserve:!!state.editingAbsenceId });
  }
  const list = $("vacationTypeList");
  if (!list) return;
  list.innerHTML = types.map(type => `
    <div class="vacationTypeRow" data-type-id="${type.id}">
      <span class="vacationTypeDot" style="background:${esc(type.color)}"></span>
      <span><b>${esc(absenceTypeDisplayName(type))}</b><small>${esc(absenceBalanceLabel(type.balancePolicy))} · ${esc(type.fullDayReplacesShift ? t("заменяет смену") : t("не заменяет смену"))}</small></span>
      ${type.systemPreset ? `<em>${esc(t("встроенный"))}</em>` : `<button type="button" data-delete-absence-type="${type.id}" aria-label="${esc(t("Удалить"))}">×</button>`}
    </div>`).join("");
  list.querySelectorAll("[data-delete-absence-type]").forEach(button => button.addEventListener("click", async () => {
    if (!confirm(t("Удалить пользовательский тип отсутствия?"))) return;
    try { await api.deleteAbsenceType(button.dataset.deleteAbsenceType); await loadVacationPlanner(true); setVacationMessage(t("Тип удалён")); }
    catch (error) { setVacationMessage(error.message, true); }
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
  list.innerHTML = periods.map(period => {
    const charged = Number(period.chargedMinutes || 0) > 0 ? ` · ${esc(t("Списано"))}: ${esc(minutesLabel(period.chargedMinutes))}` : "";
    const coverage = period.coverage === "PARTIAL" ? `${esc(t("Часть дня"))} · ${esc(period.startTime)}–${esc(period.endTime)}` : esc(t("Полный день"));
    return `<article class="vacationPeriodCard" data-absence-id="${period.id}" style="--absence-color:${esc(period.typeColor)}">
      <div class="vacationPeriodColor"></div><div class="vacationPeriodMain">
        <div class="vacationPeriodTop"><b>${esc(absenceDisplayTitle(period))}</b><span data-absence-status="${esc(String(period.status || "PLANNED").toLowerCase())}">${esc(vacationStatusLabel(period.status))}</span></div>
        <div class="vacationPeriodDates">${esc(vacationDateLabel(period.startDate))}${period.startDate === period.endDate ? "" : ` — ${esc(vacationDateLabel(period.endDate))}`}</div>
        <div class="vacationPeriodMeta"><span>${esc(absenceTypeDisplayName(period))}</span><span>${coverage}</span><span>${esc(absenceCompensationLabel(period.compensationPolicy))}${charged}</span><span>${esc(absencePostingLabel(period))}</span></div>
        ${period.shiftConflictCount ? `<div class="vacationPlanFactHint">✓ ${esc(t("Плановая смена сохранена"))} · ${period.shiftConflictCount}</div>` : ""}
        ${period.note ? `<p>${esc(period.note)}</p>` : ""}
        <div class="vacationPeriodActions"><button type="button" data-edit-absence="${period.id}">${esc(t("Изменить"))}</button><button class="dangerGhost" type="button" data-delete-absence="${period.id}">${esc(t("Удалить"))}</button></div>
      </div></article>`;
  }).join("");
  list.querySelectorAll("[data-edit-absence]").forEach(button => button.addEventListener("click", () => editAbsence(Number(button.dataset.editAbsence))));
  list.querySelectorAll("[data-delete-absence]").forEach(button => button.addEventListener("click", () => deleteAbsence(Number(button.dataset.deleteAbsence))));
}

function renderVacationPlanner(){
  if (!$("view-vacation")) return;
  renderVacationSummary(); renderVacationSettings(); renderVacationTypeControls(); renderVacationPeriods();
}

function renderVacationDay(){
  const box = $("vacationDayList");
  if (!box || !state.selected) return;
  const items = absencesOf(state.selected);
  box.innerHTML = items.length ? items.map(item => `
    <button class="vacationDayItem ${item.coverage === "PARTIAL" ? "partial" : "full"}" type="button" data-day-absence="${item.periodId}" style="--absence-color:${esc(item.typeColor || "#4FA3A5")}">
      <span class="vacationDayIcon">${item.coverage === "PARTIAL" ? "◴" : "●"}</span>
      <span class="vacationDayPlanFact"><small>${esc(t("Фактически"))}</small><b>${esc(absenceDisplayTitle(item))}</b><em>${esc(absenceTimeLabel(item))} · ${esc(absenceCompensationLabel(item.compensationPolicy))}</em>${item.plannedShiftName ? `<small>${esc(t("По графику"))}: ${esc(item.plannedShiftName)}${item.plannedShiftMinutes ? ` · ${esc(minutesLabel(item.plannedShiftMinutes))}` : ""}</small>` : ""}</span><i>›</i>
    </button>`).join("") : `<div class="dayPanelHint">${esc(t("На этот день отсутствие не запланировано."))}</div>`;
  box.querySelectorAll("[data-day-absence]").forEach(button => button.addEventListener("click", () => editAbsence(Number(button.dataset.dayAbsence))));
}

function syncVacationCoverage(){
  const partial = $("vacationCoverage")?.value === "PARTIAL";
  if ($("vacationPartialTimes")) $("vacationPartialTimes").hidden = !partial;
  if ($("vacationEnd")) {
    $("vacationEnd").disabled = partial;
    if (partial) $("vacationEnd").value = $("vacationStart")?.value || $("vacationEnd").value;
  }
  document.querySelectorAll("[data-vacation-days]").forEach(button => { button.disabled = partial; });
}
function resetVacationEditor({ keepDates = false } = {}){
  state.editingAbsenceId = null; state.vacationPreview = null;
  if ($("vacationEditorTitle")) $("vacationEditorTitle").textContent = t("Запланировать отсутствие");
  if ($("vacationEditorReset")) $("vacationEditorReset").hidden = true;
  if ($("vacationSaveBtn")) $("vacationSaveBtn").textContent = t("Сохранить период");
  if (!keepDates) {
    const date = state.selected || todayKey();
    if ($("vacationStart")) $("vacationStart").value = date;
    if ($("vacationEnd")) $("vacationEnd").value = date;
  }
  if ($("vacationCoverage")) $("vacationCoverage").value = "FULL_DAY";
  if ($("vacationStartTime")) $("vacationStartTime").value = "09:00";
  if ($("vacationEndTime")) $("vacationEndTime").value = "13:00";
  if ($("vacationTitle")) $("vacationTitle").value = "";
  if ($("vacationNote")) $("vacationNote").value = "";
  if ($("vacationStatus")) $("vacationStatus").value = "PLANNED";
  syncVacationCompensation();
  if ($("vacationPreview")) { $("vacationPreview").hidden = true; $("vacationPreview").innerHTML = ""; }
  syncVacationCoverage(); setVacationMessage("");
}

function editAbsence(id){
  const period = (state.vacationPlanner?.absences || []).find(item => Number(item.id) === Number(id));
  if (!period) { loadVacationPlanner(true).then(() => editAbsence(id)).catch(console.error); return; }
  state.editingAbsenceId = period.id; state.vacationPreview = null;
  if ($("vacationEditorTitle")) $("vacationEditorTitle").textContent = t("Редактировать отсутствие");
  if ($("vacationEditorReset")) $("vacationEditorReset").hidden = false;
  if ($("vacationSaveBtn")) $("vacationSaveBtn").textContent = t("Сохранить изменения");
  if ($("vacationType")) $("vacationType").value = String(period.typeId);
  if ($("vacationTitle")) $("vacationTitle").value = period.title || "";
  if ($("vacationStart")) $("vacationStart").value = period.startDate;
  if ($("vacationEnd")) $("vacationEnd").value = period.endDate;
  if ($("vacationCoverage")) $("vacationCoverage").value = period.coverage || "FULL_DAY";
  if ($("vacationStartTime")) $("vacationStartTime").value = period.startTime || "09:00";
  if ($("vacationEndTime")) $("vacationEndTime").value = period.endTime || "13:00";
  if ($("vacationStatus")) $("vacationStatus").value = period.status || "PLANNED";
  if ($("vacationCompensation")) $("vacationCompensation").value = period.compensationPolicy || defaultAbsenceCompensation(selectedAbsenceType());
  syncVacationCompensation({ preserve:true });
  if ($("vacationNote")) $("vacationNote").value = period.note || "";
  if ($("vacationPreview")) $("vacationPreview").hidden = true;
  syncVacationCoverage(); location.hash = "#vacation";
  requestAnimationFrame(() => $("vacationPeriodForm")?.scrollIntoView({ behavior:"smooth", block:"start" }));
}
function editAbsenceFromOccurrence(occurrence){ location.hash = "#vacation"; loadVacationPlanner(false).then(() => editAbsence(occurrence.periodId)).catch(console.error); }

function readVacationDraft(){
  const partial = $("vacationCoverage")?.value === "PARTIAL";
  const startDate = $("vacationStart")?.value || "";
  return {
    typeId:Number($("vacationType")?.value || 0), title:$("vacationTitle")?.value?.trim() || null,
    startDate, endDate:partial ? startDate : ($("vacationEnd")?.value || ""),
    status:$("vacationStatus")?.value || "PLANNED", note:$("vacationNote")?.value?.trim() || null,
    coverage:partial ? "PARTIAL" : "FULL_DAY",
    startTime:partial ? ($("vacationStartTime")?.value || "") : null,
    endTime:partial ? ($("vacationEndTime")?.value || "") : null,
    compensationPolicy:$("vacationCompensation")?.value || defaultAbsenceCompensation(selectedAbsenceType())
  };
}
function renderVacationPreview(preview){
  const box = $("vacationPreview"); if (!box) return;
  state.vacationPreview = preview;
  const timePolicy = preview.compensationPolicy === "OVERTIME_BANK";
  const draftStatus = String($("vacationStatus")?.value || "PLANNED").toUpperCase();
  const blocked = draftStatus !== "DRAFT" && (preview.absenceConflictCount > 0 || preview.exceedsAllowance);
  box.hidden = false; box.classList.toggle("blocked", blocked);
  const warnings = [];
  if (preview.shiftConflictCount) warnings.push(`✓ ${preview.shiftConflictCount} · ${t("Плановая смена сохранена")}`);
  if (preview.absenceConflictCount) warnings.push(`⛔ ${t("Пересечение с другим отсутствием")}`);
  if (preview.exceedsAllowance) warnings.push(`⛔ ${timePolicy ? t("Баланс отгулов превышен") : t("Лимит отпуска превышен")}: ${timePolicy ? minutesLabel(preview.exceededBy) : `${preview.exceededBy} ${t("дней")}`}`);
  const before = timePolicy ? minutesLabel(preview.timeOffPlannedBefore) : preview.plannedBefore;
  const projected = timePolicy ? minutesLabel(preview.timeOffProjected) : preview.projectedPlanned;
  const remaining = timePolicy ? minutesLabel(preview.timeOffRemainingAfter) : preview.remainingAfter;
  box.innerHTML = `<div class="vacationPreviewHead"><b>${esc(preview.typeName)}</b><span>${esc(absenceCoverageLabel(preview.coverage))}${preview.coverage === "PARTIAL" ? ` · ${esc(minutesLabel(preview.durationMinutes))}` : ` · ${preview.calendarDays} ${esc(t("календарных дней"))}`}</span></div>
    <div class="vacationPreviewBalance"><span>${esc(t("Запланировано"))}: <b>${esc(before)}</b></span><span>${esc(t("После сохранения"))}: <b>${esc(projected)}</b></span><span>${esc(t("Осталось"))}: <b>${esc(remaining)}</b></span></div>
    <div class="vacationPreviewSource"><span>${esc(t("Источник покрытия"))}</span><b>${esc(absenceCompensationLabel(preview.compensationPolicy))}</b></div>
    <div class="vacationPreviewSource"><span>${esc(t("Статус"))}</span><b>${esc(vacationStatusLabel(draftStatus))} · ${esc(absencePostingLabel({ status:draftStatus }))}</b></div>
    ${warnings.length ? `<div class="vacationPreviewWarnings">${warnings.map(item => `<span>${esc(item)}</span>`).join("")}</div>` : `<div class="vacationPreviewOk">✓ ${esc(t("Предпросмотр готов"))}</div>`}
    <div class="vacationPreviewDays">${(preview.items || []).map(item => `<span class="${item.action === "CONFLICT" ? "conflict" : item.shiftConflict ? "shift" : item.counted ? "counted" : "free"}" title="${esc([item.date,item.plannedShiftName].filter(Boolean).join(" · "))}">${String(item.date).slice(8,10)}</span>`).join("")}</div>`;
  return !blocked;
}

async function previewVacationDraft(){
  const draft = readVacationDraft();
  if (!draft.typeId || !draft.startDate || !draft.endDate || (draft.coverage === "PARTIAL" && (!draft.startTime || !draft.endTime))) {
    setVacationMessage(draft.coverage === "PARTIAL" ? t("Выберите время частичного отсутствия") : t("Выберите тип и даты периода"), true); return null;
  }
  try {
    const preview = await api.previewAbsence({ typeId:draft.typeId, startDate:draft.startDate, endDate:draft.endDate, excludePeriodId:state.editingAbsenceId, coverage:draft.coverage, startTime:draft.startTime, endTime:draft.endTime, compensationPolicy:draft.compensationPolicy });
    renderVacationPreview(preview); setVacationMessage(t("Предпросмотр готов")); return preview;
  } catch (error) { setVacationMessage(error.message || t("Ошибка"), true); return null; }
}
async function saveVacationPeriod(event){
  event?.preventDefault(); const draft = readVacationDraft(); const preview = await previewVacationDraft();
  const draftOnly = String(draft.status || "PLANNED").toUpperCase() === "DRAFT";
  if (!preview || (!draftOnly && (preview.absenceConflictCount > 0 || preview.exceedsAllowance))) return;
  const button = $("vacationSaveBtn"); if (button) button.disabled = true;
  try {
    if (state.editingAbsenceId) await api.updateAbsence(state.editingAbsenceId, draft); else await api.createAbsence(draft);
    resetVacationEditor(); await loadVacationPlanner(true); await loadMonth({ fresh:true }); setVacationMessage(t("Период сохранён"));
  } catch (error) {
    const message = error.code === "VACATION_LIMIT_EXCEEDED" ? t("Лимит отпуска превышен") : error.code === "TIME_OFF_LIMIT_EXCEEDED" || error.code === "OVERTIME_BALANCE_EXCEEDED" ? t("Недостаточно часов переработки") : error.code === "ABSENCE_OVERLAP" ? t("Пересечение с другим отсутствием") : error.code === "PERIOD_CLOSED" ? `${t("Период закрыт")}. ${t("Откройте период или добавьте корректировку")}` : (error.message || t("Ошибка сохранения"));
    setVacationMessage(message, true);
  } finally { if (button) button.disabled = false; }
}
async function deleteAbsence(id){
  if (!confirm(t("Удалить этот период отсутствия?"))) return;
  try { await api.deleteAbsence(id); if (Number(state.editingAbsenceId) === Number(id)) resetVacationEditor(); await loadVacationPlanner(true); await loadMonth({ fresh:true }); setVacationMessage(t("Период удалён")); }
  catch (error) { setVacationMessage(error.message, true); }
}
async function saveVacationSettings(event){
  event?.preventDefault(); const start = $("vacationWorkYearStart")?.value || "2026-01-01"; const parts = start.split("-").map(Number);
  const body = { annualAllowanceDays:Number($("vacationAllowance")?.value || 0), carryoverDays:Number($("vacationCarryover")?.value || 0), countMode:$("vacationCountMode")?.value || "CALENDAR_DAYS", workYearStartMonth:parts[1] || 1, workYearStartDay:parts[2] || 1, defaultTimeOffDayHours:Number($("defaultTimeOffDayHours")?.value || 8) };
  try { await api.updateVacationSettings(body); await loadVacationPlanner(true); await loadMonth({ fresh:true }); setVacationMessage(t("Правила сохранены")); }
  catch (error) { setVacationMessage(error.message, true); }
}
async function createVacationType(event){
  event?.preventDefault();
  try {
    await api.createAbsenceType({ name:$("vacationTypeName")?.value?.trim(), color:$("vacationTypeColor")?.value || "#4FA3A5", balancePolicy:$("vacationTypeBalance")?.value || "NONE", fullDayReplacesShift:!!$("vacationTypeReplacesShift")?.checked });
    if ($("vacationTypeName")) $("vacationTypeName").value = "";
    if ($("vacationTypeBalance")) $("vacationTypeBalance").value = "NONE";
    if ($("vacationTypeReplacesShift")) $("vacationTypeReplacesShift").checked = true;
    await loadVacationPlanner(true); setVacationMessage(t("Тип отсутствия добавлен"));
  } catch (error) { setVacationMessage(error.message, true); }
}
function applyVacationDuration(days){
  if ($("vacationCoverage")?.value === "PARTIAL") return;
  const start = $("vacationStart")?.value || state.selected || todayKey();
  if ($("vacationStart")) $("vacationStart").value = start;
  if ($("vacationEnd")) $("vacationEnd").value = vacationIsoAddDays(start, Number(days) - 1);
  state.vacationPreview = null; if ($("vacationPreview")) $("vacationPreview").hidden = true;
}
function openVacationPlannerForDate(date){
  const key = date || state.selected || todayKey(); location.hash = "#vacation";
  loadVacationPlanner(false).then(() => { resetVacationEditor({ keepDates:true }); if ($("vacationStart")) $("vacationStart").value = key; if ($("vacationEnd")) $("vacationEnd").value = key; syncVacationCoverage(); requestAnimationFrame(() => $("vacationStart")?.focus()); }).catch(console.error);
}
window.__dutylogVacationReady = Promise.resolve();
function openVacationPlannerView(force = false){
  renderVacationPlanner();
  if (!force && state.vacationPlanner) return Promise.resolve(state.vacationPlanner);
  const ready = Promise.resolve(loadVacationPlanner(force));
  window.__dutylogVacationReady = ready;
  ready.catch(console.error);
  return ready;
}

$("vacationPeriodForm")?.addEventListener("submit", saveVacationPeriod);
$("vacationPreviewBtn")?.addEventListener("click", previewVacationDraft);
$("vacationEditorReset")?.addEventListener("click", () => resetVacationEditor());
$("vacationReload")?.addEventListener("click", () => loadVacationPlanner(true));
$("vacationSettingsForm")?.addEventListener("submit", saveVacationSettings);
$("vacationTypeForm")?.addEventListener("submit", createVacationType);
$("vacationPlanSelected")?.addEventListener("click", () => openVacationPlannerForDate(state.selected));
$("vacationCoverage")?.addEventListener("change", () => { syncVacationCoverage(); state.vacationPreview = null; if ($("vacationPreview")) $("vacationPreview").hidden = true; });
$("vacationStart")?.addEventListener("change", () => { if ($("vacationCoverage")?.value === "PARTIAL" && $("vacationEnd")) $("vacationEnd").value = $("vacationStart").value; });
document.querySelectorAll("[data-vacation-days]").forEach(button => button.addEventListener("click", () => applyVacationDuration(button.dataset.vacationDays)));
$("vacationType")?.addEventListener("change", () => { syncVacationCompensation(); state.vacationPreview = null; if ($("vacationPreview")) $("vacationPreview").hidden = true; });
for (const id of ["vacationStart","vacationEnd","vacationStartTime","vacationEndTime","vacationCompensation"]) $(id)?.addEventListener("change", () => { state.vacationPreview = null; if ($("vacationPreview")) $("vacationPreview").hidden = true; });

resetVacationEditor();
