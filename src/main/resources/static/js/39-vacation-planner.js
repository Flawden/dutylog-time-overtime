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
  "Период закрыт":"Period closed", "Откройте период или добавьте корректировку":"Reopen the period or add an adjustment",
  "Оформить отсутствие":"Create absence", "Единый конструктор":"Unified composer",
  "Баланс не используется":"No balance is used", "Будет использовано":"Will be used",
  "После оформления":"After saving", "Причина обязательна":"Reason is required",
  "Доступно для использования":"Available to use", "Источник выбран автоматически":"Source selected automatically",
  "Объём часов":"Hours only", "Интервал не указан":"Time interval is unknown",
  "Уточните интервал":"Specify the time interval", "Импортировано из старого журнала":"Imported from the legacy ledger"
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
function absenceGlyph(value){
  return ({ VACATION:"☀", TIME_OFF:"◷", SICK:"✚", UNPAID:"○", OTHER:"◆" })[String(value?.systemCode || "").toUpperCase()] || "●";
}
function absenceSystemClass(value){
  const code = String(value?.systemCode || "OTHER").toLowerCase().replace(/[^a-z0-9_-]/g, "");
  return `absence-${code || "other"}`;
}
function absencesOf(key){ return moduleEnabled("vacation") ? (state.absencesByDate?.[key] || []) : []; }
function fullDayFactualAbsence(key){ return absencesOf(key).find(item => item.coverage === "FULL_DAY" && item.replacesShift) || null; }
function partialAbsencesOf(key){ return absencesOf(key).filter(item => ["PARTIAL","HOURS_ONLY"].includes(String(item.coverage || "").toUpperCase())); }
function absenceCoverageLabel(value){
  const coverage = String(value || "FULL_DAY").toUpperCase();
  return coverage === "PARTIAL" ? t("Часть дня") : coverage === "HOURS_ONLY" ? t("Объём часов") : t("Полный день");
}
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
  select.disabled = allowed.size === 1;
  renderAbsenceComposerContext();
}
function minutesLabel(value){
  const minutes = Math.max(0, Number(value || 0));
  const hours = minutes / 60;
  return `${Number.isInteger(hours) ? hours : hours.toFixed(2).replace(/0+$/,"" ).replace(/\.$/,"")} ${state.language === "en" ? "h" : "ч"}`;
}
function absenceTimeLabel(value){
  const coverage = String(value?.coverage || "FULL_DAY").toUpperCase();
  if (coverage === "PARTIAL") return `${value.startTime || "—"}–${value.endTime || "—"}`;
  if (coverage === "HOURS_ONLY") return `${minutesLabel(value?.chargedMinutes)} · ${t("Интервал не указан")}`;
  return absenceCoverageLabel(coverage);
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

let absenceComposerOrigin = null;
let absenceComposerSource = "vacation";

function renderAbsenceComposerContext(preview = state.vacationPreview){
  const box = $("absenceComposerContext");
  if (!box) return;
  const type = selectedAbsenceType();
  if (!type) { box.innerHTML = ""; return; }
  const policy = $("vacationCompensation")?.value || defaultAbsenceCompensation(type);
  const summary = state.vacationPlanner?.summary || {};
  let available = t("Баланс не используется");
  let after = t("Баланс не используется");
  if (policy === "VACATION_ALLOWANCE") {
    available = `${Number(summary.remainingDays || 0)} ${t("дней")}`;
    after = preview ? `${Number(preview.remainingAfter || 0)} ${t("дней")}` : "—";
  } else if (policy === "OVERTIME_BANK") {
    available = minutesLabel(summary.timeOffRemainingMinutes || 0);
    after = preview ? minutesLabel(preview.timeOffRemainingAfter || 0) : "—";
  }
  box.className = `absenceComposerContext vacationWide ${absenceSystemClass(type)}`;
  box.innerHTML = `<div class="absenceComposerIdentity"><span>${esc(absenceGlyph(type))}</span><div><small>${esc(t("Тип"))}</small><b>${esc(absenceTypeDisplayName(type))}</b></div></div>
    <div><small>${esc(t("Источник покрытия"))}</small><b>${esc(absenceCompensationLabel(policy))}</b></div>
    <div><small>${esc(t("Доступно для использования"))}</small><b>${esc(available)}</b></div>
    <div><small>${esc(t("После оформления"))}</small><b>${esc(after)}</b></div>`;
}

function moveAbsenceComposerToModal(){
  const card = $("absenceComposerCard");
  const mount = $("absenceComposerModalMount");
  if (!card || !mount) return;
  if (!absenceComposerOrigin) absenceComposerOrigin = { parent:card.parentNode, next:card.nextSibling };
  mount.appendChild(card);
  card.classList.add("inComposerModal");
}
function restoreAbsenceComposerHome(){
  const card = $("absenceComposerCard");
  if (!card || !absenceComposerOrigin?.parent) return;
  const { parent, next } = absenceComposerOrigin;
  if (next && next.parentNode === parent) parent.insertBefore(card, next); else parent.appendChild(card);
  card.classList.remove("inComposerModal");
}
async function openAbsenceComposer({ date = null, systemCode = null, source = "vacation", reason = "" } = {}){
  const vueDomain = window.DutyLogVueDomains?.absenceTimeBank;
  if (vueDomain) {
    return vueDomain.openAbsenceComposer({ date, systemCode, source, reason });
  }
  if (!moduleEnabled("vacation")) {
    setSave("err", t("модуль выключен"));
    return;
  }
  // The composer must show the current allowance/FIFO balance. Credits, usages or
  // absence edits may have changed since the planner was last rendered.
  await loadVacationPlanner(true);
  if (moduleEnabled("overtime") && typeof loadLedgerPage === "function") await loadLedgerPage(true);
  absenceComposerSource = source;
  resetVacationEditor({ keepDates:true });
  const key = date || state.selected || todayKey();
  if ($("vacationStart")) $("vacationStart").value = key;
  if ($("vacationEnd")) $("vacationEnd").value = key;
  if (systemCode) {
    const type = (state.vacationPlanner?.types || []).find(item => String(item.systemCode || "").toUpperCase() === String(systemCode).toUpperCase());
    if (type && $("vacationType")) $("vacationType").value = String(type.id);
  }
  if (reason && $("vacationTitle")) $("vacationTitle").value = reason;
  syncVacationCompensation();
  syncVacationCoverage();
  renderAbsenceComposerContext();
  moveAbsenceComposerToModal();
  openAppModal("absenceComposerModal", "vacationTitle");
}
function closeAbsenceComposer({ keepEditor = false } = {}){
  closeAppModal("absenceComposerModal");
  restoreAbsenceComposerHome();
  if (!keepEditor) resetVacationEditor({ keepDates:true });
  absenceComposerSource = "vacation";
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


function absenceStatusGroup(status){
  const value = String(status || "PLANNED").toUpperCase();
  if (["PLANNED","SUBMITTED"].includes(value)) return "reserved";
  if (["APPROVED","COMPLETED"].includes(value)) return "completed";
  if (["CANCELLED","REJECTED"].includes(value)) return "cancelled";
  return "active";
}
function absenceMatchesFilters(period){
  const filters = state.absenceFilters || { scope:"upcoming", type:"all", status:"all", q:"" };
  const today = todayKey();
  if (filters.scope === "upcoming" && String(period.endDate || "") < today) return false;
  if (filters.scope === "history" && String(period.endDate || "") >= today) return false;
  const code = String(period.systemCode || "OTHER").toUpperCase();
  if (filters.type !== "all" && (filters.type === "OTHER" ? ["VACATION","TIME_OFF","SICK","UNPAID"].includes(code) : code !== filters.type)) return false;
  const group = absenceStatusGroup(period.status);
  if (filters.status === "active" && ["cancelled"].includes(group)) return false;
  if (filters.status !== "all" && filters.status !== "active" && group !== filters.status) return false;
  const query = String(filters.q || "").trim().toLowerCase();
  return !query || `${period.title || ""} ${period.typeName || ""} ${period.note || ""} ${period.startDate || ""} ${period.endDate || ""}`.toLowerCase().includes(query);
}
function syncAbsenceFilterInputs(){
  const filters = state.absenceFilters || {};
  if ($("absenceScope")) $("absenceScope").value = filters.scope || "upcoming";
  if ($("absenceTypeFilter")) $("absenceTypeFilter").value = filters.type || "all";
  if ($("absenceStatusFilter")) $("absenceStatusFilter").value = filters.status || "all";
  if ($("absenceSearch")) $("absenceSearch").value = filters.q || "";
}
function openTimeBankUsageForAbsence(absenceId){
  const vueDomain = window.DutyLogVueDomains?.absenceTimeBank;
  if (vueDomain) {
    location.hash = "#overtime";
    return vueDomain.openTimeBankUsage(Number(absenceId) || null);
  }
  const targetId = Number(absenceId) || null;
  state.timeBankFocusAbsenceId = targetId;
  location.hash = "#overtime";
  if (typeof setTimeBankView === "function") setTimeBankView("usage");
  Promise.resolve(window.__dutylogLedgerRouteReady)
    .then(() => Promise.resolve(window.__dutylogLedgerReady))
    .catch(() => null)
    .then(() => {
      if (typeof renderTimeBankUsageList === "function") renderTimeBankUsageList();
      const card = document.querySelector(`[data-source-absence-id="${targetId}"]`);
      card?.scrollIntoView({ behavior:"smooth", block:"center" });
      card?.classList.add("focusPulse");
      setTimeout(() => card?.classList.remove("focusPulse"), 1800);
    });
}
function renderAbsenceFifoForecast(preview = state.vacationPreview){
  const box = $("absenceFifoForecast");
  if (!box) return;
  const policy = $("vacationCompensation")?.value || preview?.compensationPolicy;
  if (policy !== "OVERTIME_BANK" || !preview || typeof timeBankForecast !== "function") { box.hidden = true; box.innerHTML = ""; return; }
  const forecast = timeBankForecast(Number(preview.durationMinutes || 0), { excludeAbsenceId:state.editingAbsenceId });
  box.hidden = false;
  const rows = forecast.allocations.map((item, index) => `<div><span>${index + 1}</span><b>${esc(vacationDateLabel(item.credit.workedDate))}</b><small>${esc(item.credit.reason || t("переработка"))}</small><strong>−${esc(minutesLabel(item.minutes))}</strong></div>`).join("");
  box.innerHTML = `<header><div><small>${esc(t("FIFO-детализация"))}</small><b>${esc(t("Будет списано"))}: ${esc(minutesLabel(forecast.requestedMinutes - forecast.shortageMinutes))}</b></div><button type="button" data-open-time-bank>${esc(t("Детализация банка"))} →</button></header>
    <section>${rows || `<p>${esc(t("Свободных часов нет"))}</p>`}</section>
    ${forecast.shortageMinutes ? `<p class="errorText">⛔ ${esc(t("Недостаточно часов переработки"))}: ${esc(minutesLabel(forecast.shortageMinutes))}</p>` : `<p>✓ ${esc(t("Сначала используется самый старый свободный остаток"))}</p>`}`;
  box.querySelector("[data-open-time-bank]")?.addEventListener("click", () => { closeAbsenceComposer({ keepEditor:true }); location.hash = "#overtime"; setTimeBankView("fifo"); renderFifoForecast(preview.durationMinutes); });
}

function renderVacationPeriods(){
  const list = $("vacationPeriodList");
  if (!list) return;
  syncAbsenceFilterInputs();
  const allPeriods = state.vacationPlanner?.absences || [];
  const periods = allPeriods.filter(absenceMatchesFilters).sort((a,b) => String(a.startDate || "").localeCompare(String(b.startDate || "")) || Number(a.id || 0) - Number(b.id || 0));
  const timeOff = periods.filter(item => String(item.compensationPolicy || "") === "OVERTIME_BANK");
  if ($("absenceListStats")) $("absenceListStats").innerHTML = `<span>${esc(t("Событий"))}: <b>${periods.length}</b></span><span>${esc(t("Отгулы"))}: <b>${timeOff.length}</b></span><span>${esc(t("Зарезервировано"))}: <b>${esc(minutesLabel(timeOff.filter(item => ["PLANNED","SUBMITTED"].includes(String(item.status || "").toUpperCase())).reduce((sum,item) => sum + Number(item.chargedMinutes || 0), 0)))}</b></span>`;
  if (!allPeriods.length) {
    list.innerHTML = emptyStateHtml({ icon:"☂", title:"Отсутствий пока нет", text:"Оформите отпуск, отгул, больничный или отсутствие без содержания. DutyLog сам свяжет событие с нужным балансом.", variant:"board" });
    return;
  }
  if (!periods.length) {
    list.innerHTML = emptyStateHtml({ icon:"⌕", title:"Ничего не найдено", text:"Измените фильтры или очистите поиск.", variant:"compact" });
    return;
  }
  list.innerHTML = periods.map(period => {
    const charged = Number(period.chargedMinutes || 0) > 0 ? ` · ${esc(t("Списано"))}: ${esc(minutesLabel(period.chargedMinutes))}` : "";
    const coverage = period.coverage === "PARTIAL"
      ? `${esc(t("Часть дня"))} · ${esc(period.startTime)}–${esc(period.endTime)}`
      : period.coverage === "HOURS_ONLY"
        ? `${esc(t("Объём часов"))} · ${esc(minutesLabel(period.chargedMinutes))} · ${esc(t("Интервал не указан"))}`
        : esc(t("Полный день"));
    const bankLinked = String(period.compensationPolicy || "") === "OVERTIME_BANK";
    return `<article class="vacationPeriodCard" data-absence-id="${period.id}" style="--absence-color:${esc(period.typeColor)}">
      <div class="vacationPeriodColor"></div><div class="vacationPeriodMain">
        <div class="vacationPeriodTop"><b>${esc(absenceDisplayTitle(period))}</b><span data-absence-status="${esc(String(period.status || "PLANNED").toLowerCase())}">${esc(vacationStatusLabel(period.status))}</span></div>
        <div class="vacationPeriodDates">${esc(vacationDateLabel(period.startDate))}${period.startDate === period.endDate ? "" : ` — ${esc(vacationDateLabel(period.endDate))}`}</div>
        <div class="vacationPeriodMeta"><span>${esc(absenceTypeDisplayName(period))}</span><span>${coverage}</span><span>${esc(absenceCompensationLabel(period.compensationPolicy))}${charged}</span><span>${esc(absencePostingLabel(period))}</span></div>
        ${period.shiftConflictCount ? `<div class="vacationPlanFactHint">✓ ${esc(t("Плановая смена сохранена"))} · ${period.shiftConflictCount}</div>` : ""}
        ${period.note ? `<p>${esc(period.note)}</p>` : ""}
        <div class="vacationPeriodActions"><button type="button" data-edit-absence="${period.id}">${esc(t("Изменить"))}</button>${bankLinked ? `<button type="button" data-bank-absence="${period.id}">${esc(t("Посмотреть списание"))}</button>` : ""}<button class="dangerGhost" type="button" data-delete-absence="${period.id}">${esc(t("Удалить"))}</button></div>
      </div></article>`;
  }).join("");
  list.querySelectorAll("[data-edit-absence]").forEach(button => button.addEventListener("click", () => editAbsence(Number(button.dataset.editAbsence))));
  list.querySelectorAll("[data-bank-absence]").forEach(button => button.addEventListener("click", () => openTimeBankUsageForAbsence(Number(button.dataset.bankAbsence))));
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
    <button class="vacationDayItem ${item.coverage === "FULL_DAY" ? "full" : "partial"}" type="button" data-day-absence="${item.periodId}" style="--absence-color:${esc(item.typeColor || "#4FA3A5")}">
      <span class="vacationDayIcon">${item.coverage === "FULL_DAY" ? "●" : item.coverage === "HOURS_ONLY" ? "◷" : "◴"}</span>
      <span class="vacationDayPlanFact"><small>${esc(t("Фактически"))}</small><b>${esc(absenceDisplayTitle(item))}</b><em>${esc(absenceTimeLabel(item))} · ${esc(absenceCompensationLabel(item.compensationPolicy))}</em>${item.plannedShiftName ? `<small>${esc(t("По графику"))}: ${esc(item.plannedShiftName)}${item.plannedShiftMinutes ? ` · ${esc(minutesLabel(item.plannedShiftMinutes))}` : ""}</small>` : ""}</span><i>›</i>
    </button>`).join("") : `<div class="dayPanelHint">${esc(t("На этот день отсутствие не запланировано."))}</div>`;
  box.querySelectorAll("[data-day-absence]").forEach(button => button.addEventListener("click", () => {
    openAbsenceEditor(Number(button.dataset.dayAbsence), { source:"calendar" }).catch(console.error);
  }));
}

function syncVacationCoverage(){
  const coverage = $("vacationCoverage")?.value || "FULL_DAY";
  const partial = coverage === "PARTIAL";
  const singleDay = partial || coverage === "HOURS_ONLY";
  if ($("vacationPartialTimes")) $("vacationPartialTimes").hidden = !partial;
  if ($("vacationEnd")) {
    $("vacationEnd").disabled = singleDay;
    if (singleDay) $("vacationEnd").value = $("vacationStart")?.value || $("vacationEnd").value;
  }
  document.querySelectorAll("[data-vacation-days]").forEach(button => { button.disabled = singleDay; });
}
function resetVacationEditor({ keepDates = false } = {}){
  state.editingAbsenceId = null; state.vacationPreview = null;
  if ($("vacationEditorTitle")) $("vacationEditorTitle").textContent = t("Оформить отсутствие");
  if ($("vacationEditorReset")) $("vacationEditorReset").hidden = true;
  if ($("vacationSaveBtn")) $("vacationSaveBtn").textContent = t("Сохранить период");
  if (!keepDates) {
    const date = state.selected || todayKey();
    if ($("vacationStart")) $("vacationStart").value = date;
    if ($("vacationEnd")) $("vacationEnd").value = date;
  }
  const hoursOnlyOption = $("vacationCoverageHoursOnly");
  if (hoursOnlyOption) { hoursOnlyOption.hidden = true; hoursOnlyOption.disabled = true; }
  if ($("vacationCoverage")) $("vacationCoverage").value = "FULL_DAY";
  if ($("vacationStartTime")) $("vacationStartTime").value = "09:00";
  if ($("vacationEndTime")) $("vacationEndTime").value = "13:00";
  if ($("vacationTitle")) $("vacationTitle").value = "";
  if ($("vacationNote")) $("vacationNote").value = "";
  if ($("vacationStatus")) $("vacationStatus").value = "PLANNED";
  syncVacationCompensation();
  if ($("vacationPreview")) { $("vacationPreview").hidden = true; $("vacationPreview").innerHTML = ""; }
  if ($("absenceFifoForecast")) { $("absenceFifoForecast").hidden = true; $("absenceFifoForecast").innerHTML = ""; }
  syncVacationCoverage(); renderAbsenceComposerContext(); setVacationMessage("");
}

function editAbsence(id, { navigate = true, scroll = true } = {}){
  const period = (state.vacationPlanner?.absences || []).find(item => Number(item.id) === Number(id));
  if (!period) { loadVacationPlanner(true).then(() => editAbsence(id, { navigate, scroll })).catch(console.error); return null; }
  state.editingAbsenceId = period.id; state.vacationPreview = null;
  if ($("vacationEditorTitle")) $("vacationEditorTitle").textContent = t("Редактировать отсутствие");
  if ($("vacationEditorReset")) $("vacationEditorReset").hidden = false;
  if ($("vacationSaveBtn")) $("vacationSaveBtn").textContent = t("Сохранить изменения");
  if ($("vacationType")) $("vacationType").value = String(period.typeId);
  if ($("vacationTitle")) $("vacationTitle").value = period.title || "";
  if ($("vacationStart")) $("vacationStart").value = period.startDate;
  if ($("vacationEnd")) $("vacationEnd").value = period.endDate;
  const hoursOnlyOption = $("vacationCoverageHoursOnly");
  if (hoursOnlyOption) {
    const imported = period.coverage === "HOURS_ONLY";
    hoursOnlyOption.hidden = !imported; hoursOnlyOption.disabled = !imported;
  }
  if ($("vacationCoverage")) $("vacationCoverage").value = period.coverage || "FULL_DAY";
  if ($("vacationStartTime")) $("vacationStartTime").value = period.startTime || "09:00";
  if ($("vacationEndTime")) $("vacationEndTime").value = period.endTime || "13:00";
  if ($("vacationStatus")) $("vacationStatus").value = period.status || "PLANNED";
  if ($("vacationCompensation")) $("vacationCompensation").value = period.compensationPolicy || defaultAbsenceCompensation(selectedAbsenceType());
  syncVacationCompensation({ preserve:true });
  if ($("vacationNote")) $("vacationNote").value = period.note || "";
  if ($("vacationPreview")) $("vacationPreview").hidden = true;
  syncVacationCoverage(); renderAbsenceComposerContext();
  if (navigate) location.hash = "#vacation";
  if (scroll) requestAnimationFrame(() => $("vacationPeriodForm")?.scrollIntoView({ behavior:"smooth", block:"start" }));
  return period;
}
async function openAbsenceEditor(id, { source = "vacation" } = {}){
  const vueDomain = window.DutyLogVueDomains?.absenceTimeBank;
  if (vueDomain) return vueDomain.openAbsenceEditor(Number(id));
  if (!moduleEnabled("vacation")) {
    setSave("err", t("модуль выключен"));
    return false;
  }
  let period = (state.vacationPlanner?.absences || []).find(item => Number(item.id) === Number(id));
  if (!period) {
    await loadVacationPlanner(true);
    period = (state.vacationPlanner?.absences || []).find(item => Number(item.id) === Number(id));
  }
  if (!period) {
    setSave("err", t("отсутствие не найдено"));
    return false;
  }
  if (moduleEnabled("overtime") && typeof loadLedgerPage === "function") await loadLedgerPage(true);
  absenceComposerSource = source;
  editAbsence(period.id, { navigate:false, scroll:false });
  moveAbsenceComposerToModal();
  openAppModal("absenceComposerModal", "vacationTitle");
  await previewVacationDraft();
  return true;
}
function editAbsenceFromOccurrence(occurrence){
  openAbsenceEditor(Number(occurrence?.periodId), { source:"calendar" }).catch(console.error);
}

function readVacationDraft(){
  const coverage = $("vacationCoverage")?.value || "FULL_DAY";
  const partial = coverage === "PARTIAL";
  const singleDay = partial || coverage === "HOURS_ONLY";
  const startDate = $("vacationStart")?.value || "";
  return {
    typeId:Number($("vacationType")?.value || 0), title:$("vacationTitle")?.value?.trim() || null,
    startDate, endDate:singleDay ? startDate : ($("vacationEnd")?.value || ""),
    status:$("vacationStatus")?.value || "PLANNED", note:$("vacationNote")?.value?.trim() || null,
    coverage,
    startTime:partial ? ($("vacationStartTime")?.value || "") : null,
    endTime:partial ? ($("vacationEndTime")?.value || "") : null,
    compensationPolicy:$("vacationCompensation")?.value || defaultAbsenceCompensation(selectedAbsenceType())
  };
}
function renderVacationPreview(preview){
  const box = $("vacationPreview"); if (!box) return;
  state.vacationPreview = preview;
  renderAbsenceComposerContext(preview);
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
  box.innerHTML = `<div class="vacationPreviewHead"><b>${esc(preview.typeName)}</b><span>${esc(absenceCoverageLabel(preview.coverage))}${preview.coverage === "PARTIAL" || preview.coverage === "HOURS_ONLY" ? ` · ${esc(minutesLabel(preview.durationMinutes))}${preview.coverage === "HOURS_ONLY" ? ` · ${esc(t("Интервал не указан"))}` : ""}` : ` · ${preview.calendarDays} ${esc(t("календарных дней"))}`}</span></div>
    <div class="vacationPreviewBalance"><span>${esc(t("Запланировано"))}: <b>${esc(before)}</b></span><span>${esc(t("После сохранения"))}: <b>${esc(projected)}</b></span><span>${esc(t("Осталось"))}: <b>${esc(remaining)}</b></span></div>
    <div class="vacationPreviewSource"><span>${esc(t("Источник покрытия"))}</span><b>${esc(absenceCompensationLabel(preview.compensationPolicy))}</b></div>
    <div class="vacationPreviewSource"><span>${esc(t("Статус"))}</span><b>${esc(vacationStatusLabel(draftStatus))} · ${esc(absencePostingLabel({ status:draftStatus }))}</b></div>
    ${warnings.length ? `<div class="vacationPreviewWarnings">${warnings.map(item => `<span>${esc(item)}</span>`).join("")}</div>` : `<div class="vacationPreviewOk">✓ ${esc(t("Предпросмотр готов"))}</div>`}
    <div class="vacationPreviewDays">${(preview.items || []).map(item => `<span class="${item.action === "CONFLICT" ? "conflict" : item.shiftConflict ? "shift" : item.counted ? "counted" : "free"}" title="${esc([item.date,item.plannedShiftName].filter(Boolean).join(" · "))}">${String(item.date).slice(8,10)}</span>`).join("")}</div>`;
  renderAbsenceFifoForecast(preview);
  return !blocked;
}

async function previewVacationDraft(){
  const draft = readVacationDraft();
  if (!draft.title) { setVacationMessage(t("Причина обязательна"), true); $("vacationTitle")?.focus(); return null; }
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
    const composerWasOpen = activeAppModalId === "absenceComposerModal";
    if (composerWasOpen) closeAbsenceComposer({ keepEditor:true });
    resetVacationEditor(); await loadVacationPlanner(true); await loadMonth({ fresh:true });
    if (moduleEnabled("overtime")) { renderOvertimeControls(); await loadLedgerPage(true); }
    setVacationMessage(t("Период сохранён"));
  } catch (error) {
    const message = error.code === "VACATION_LIMIT_EXCEEDED" ? t("Лимит отпуска превышен") : error.code === "TIME_OFF_LIMIT_EXCEEDED" || error.code === "OVERTIME_BALANCE_EXCEEDED" ? t("Недостаточно часов переработки") : error.code === "ABSENCE_OVERLAP" ? t("Пересечение с другим отсутствием") : error.code === "PERIOD_CLOSED" ? `${t("Период закрыт")}. ${t("Откройте период или добавьте корректировку")}` : (error.message || t("Ошибка сохранения"));
    setVacationMessage(message, true);
  } finally { if (button) button.disabled = false; }
}
async function deleteAbsence(id){
  if (!confirm(t("Удалить этот период отсутствия?"))) return;
  try { await api.deleteAbsence(id); if (Number(state.editingAbsenceId) === Number(id)) resetVacationEditor(); await loadVacationPlanner(true); await loadMonth({ fresh:true }); if (moduleEnabled("overtime")) { renderOvertimeControls(); await loadLedgerPage(true); } setVacationMessage(t("Период удалён")); }
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
  if (["PARTIAL","HOURS_ONLY"].includes($("vacationCoverage")?.value)) return;
  const start = $("vacationStart")?.value || state.selected || todayKey();
  if ($("vacationStart")) $("vacationStart").value = start;
  if ($("vacationEnd")) $("vacationEnd").value = vacationIsoAddDays(start, Number(days) - 1);
  state.vacationPreview = null; if ($("vacationPreview")) $("vacationPreview").hidden = true;
}
function openVacationPlannerForDate(date){
  openAbsenceComposer({ date:date || state.selected || todayKey(), source:"calendar" }).catch(console.error);
}
window.__dutylogVacationReady = Promise.resolve();
function openVacationPlannerView(force = false){
  const vueDomain = window.DutyLogVueDomains?.absenceTimeBank;
  if (vueDomain) return vueDomain.refresh();
  renderVacationPlanner();
  if (!force && state.vacationPlanner) return Promise.resolve(state.vacationPlanner);
  const ready = Promise.resolve(loadVacationPlanner(force));
  window.__dutylogVacationReady = ready;
  ready.catch(console.error);
  return ready;
}

$("vacationOpenTimeBank")?.addEventListener("click", () => { location.hash = "#overtime"; if (typeof setTimeBankView === "function") setTimeBankView("overview"); });
$("absenceGuideOpen")?.addEventListener("click", () => { if (typeof openTimeBankGuide === "function") openTimeBankGuide(); });
for (const id of ["absenceScope","absenceTypeFilter","absenceStatusFilter"]) $(id)?.addEventListener("change", event => {
  const key = id === "absenceScope" ? "scope" : id === "absenceTypeFilter" ? "type" : "status";
  state.absenceFilters[key] = event.target.value;
  renderVacationPeriods();
});
$("absenceSearch")?.addEventListener("input", event => {
  clearTimeout(window.__absenceSearchTimer);
  window.__absenceSearchTimer = setTimeout(() => { state.absenceFilters.q = event.target.value.trim(); renderVacationPeriods(); }, 180);
});

$("vacationPeriodForm")?.addEventListener("submit", saveVacationPeriod);
$("vacationPreviewBtn")?.addEventListener("click", previewVacationDraft);
$("vacationEditorReset")?.addEventListener("click", () => resetVacationEditor());
$("vacationReload")?.addEventListener("click", () => loadVacationPlanner(true));
$("vacationSettingsForm")?.addEventListener("submit", saveVacationSettings);
$("vacationTypeForm")?.addEventListener("submit", createVacationType);
$("vacationPlanSelected")?.addEventListener("click", () => openVacationPlannerForDate(state.selected));
$("vacationComposerOpen")?.addEventListener("click", () => openAbsenceComposer({ date:state.selected || todayKey(), source:"vacation" }).catch(console.error));
$("absenceComposerClose")?.addEventListener("click", () => closeAbsenceComposer());
$("absenceComposerBackdrop")?.addEventListener("click", () => closeAbsenceComposer());
$("vacationCoverage")?.addEventListener("change", () => { syncVacationCoverage(); state.vacationPreview = null; if ($("vacationPreview")) $("vacationPreview").hidden = true; });
$("vacationStart")?.addEventListener("change", () => { if (["PARTIAL","HOURS_ONLY"].includes($("vacationCoverage")?.value) && $("vacationEnd")) $("vacationEnd").value = $("vacationStart").value; });
document.querySelectorAll("[data-vacation-days]").forEach(button => button.addEventListener("click", () => applyVacationDuration(button.dataset.vacationDays)));
$("vacationType")?.addEventListener("change", () => { syncVacationCompensation(); state.vacationPreview = null; renderAbsenceComposerContext(); if ($("vacationPreview")) $("vacationPreview").hidden = true; });
for (const id of ["vacationStart","vacationEnd","vacationStartTime","vacationEndTime","vacationCompensation"]) $(id)?.addEventListener("change", () => { state.vacationPreview = null; renderAbsenceComposerContext(); if ($("vacationPreview")) $("vacationPreview").hidden = true; });

resetVacationEditor();
