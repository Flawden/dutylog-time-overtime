/*
 * 40-overtime.js — Overtime: ledger, credits, usages and quick scenarios
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

Object.assign(I18N_EN, {
  "Добавить переработку":"Add overtime", "Редактировать переработку":"Edit overtime",
  "Добавить списание":"Add time off", "Редактировать списание":"Edit time off",
  "+ Начислить":"+ Earn", "− Списать":"− Use", "+ Добавить переработку":"+ Add overtime",
  "− Добавить списание":"− Add time off", "Не выбран":"Not selected", "Сценарий":"Scenario",
  "Доступно до списания":"Available before use", "Останется после списания":"Remaining after use",
  "списать по норме смены":"use shift norm",
  "Расчёт":"Calculation", "Итого, ч":"Total, h", "Количество часов":"Hours",
  "Управление сценариями":"Manage scenarios", "Управление сценариями…":"Manage scenarios…",
  "Сохранить текущие значения как сценарий":"Save current values as a scenario",
  "Сценарии переработок":"Overtime scenarios", "К переработке":"Back to overtime",
  "Новый сценарий":"New scenario", "Редактировать сценарий":"Edit scenario",
  "Сохранить сценарий":"Save scenario", "Назад к списку":"Back to list",
  "Сценарий сохранён":"Scenario saved", "Сценарий обновлён":"Scenario updated",
  "Для сохранения сценария укажите начало и конец":"Set start and end before saving a scenario",
  "Для сохранения сценария итог переработки должен быть больше 0":"The overtime total must be greater than 0 before saving a scenario",
  "Для сохранения из формы выберите день со сменой":"Choose a day with a shift before saving from the form",
  "Начало формы должно совпадать с началом или концом смены":"The form start must match the shift start or end",
  "Интервал сценария не может быть длиннее 72 часов":"A scenario interval cannot exceed 72 hours",
  "Сценарии пока не созданы.":"No scenarios yet.", "Управление":"Management",
  "Управление происходит в этом же окне. Введённая переработка не потеряется.":"Management stays in the same window. Your overtime draft will not be lost.",
  "Поля заполнены из текущей переработки. Дайте сценарию понятное название и сохраните.":"The fields were filled from the current overtime entry. Give the scenario a clear name and save it.",
  "Настройте, как сценарий будет заполнять форму переработки.":"Configure how the scenario fills the overtime form.",
  "из формы":"from form", "Сохранено из формы":"Saved from form",
  "укажи фиксированное время конца":"set a fixed end time",
  "точный интервал":"exact interval", "реконструировано":"reconstructed",
  "без точного времени":"no exact time", "старые записи":"legacy entries",
  "нет старых записей для миграции":"no legacy entries to migrate",
  "Привязать старые записи":"Attach legacy entries",
  "проекция":"projection", "итого за день":"day total",
  "за два дня до":"two days before", "предыдущий день":"previous day", "тот же день":"same day",
  "следующий день":"next day", "через день":"day after next",
  "канонический расчёт":"canonical calculation"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

/* ─── Журнал переработки и отгулов ─────────────────────────── */
function updateOvertimeBalanceLabel(){
  const acc = state.overtimeAccount || { balanceHours:0 };
  const bal = numOr0(acc.balanceHours);
  if ($("otBalance")) $("otBalance").textContent = `${t("доступно")} ${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
  if ($("ledgerBalance")) $("ledgerBalance").textContent = `${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
  updateUsageBalancePreview();
}

function renderOvertimeControls(){
  if (!moduleEnabled("overtime")) { updateAccSummaries(); return; }
  updateOvertimeBalanceLabel();
  const legacyButton = $("ledgerMigrateLegacy");
  if (legacyButton) {
    const count = (state.overtimeAccount?.credits || []).filter(c => c.legacyTimezoneRequired).length;
    legacyButton.hidden = count === 0;
    legacyButton.textContent = `⚠ ${t("Привязать старые записи")}${count ? ` (${count})` : ""}`;
  }
  renderOvertimeDayDetails();
  renderQuickScenarios();
  updateAccSummaries();
}

function toDateTimeLocal(value){
  return value ? String(value).slice(0, 16) : "";
}

function overtimeDefaultDate(date = null){
  return date || state.selected || todayKey();
}

function resetOvertimeForms(k = state.selected){
  state.editingCreditId = null;
  state.editingUsageId = null;
  state.activeScenarioId = null;
  state.editingCreditLegacyTimeRange = null;
  const date = overtimeDefaultDate(k);
  if ($("creditDate")) $("creditDate").value = date;
  if ($("creditStart")) $("creditStart").value = "";
  if ($("creditEnd")) $("creditEnd").value = "";
  if ($("creditBreak")) $("creditBreak").value = "0";
  if ($("creditPlanned")) $("creditPlanned").value = "0";
  if ($("creditHours")) $("creditHours").value = "0";
  if ($("creditReason")) $("creditReason").value = "";
  if ($("creditCalcHint")) $("creditCalcHint").textContent = t("можно вручную");
  if ($("creditScenarioSelect")) $("creditScenarioSelect").value = "";
  if ($("creditEditNotice")) { $("creditEditNotice").hidden = true; $("creditEditNotice").textContent = ""; }
  if ($("creditDelete")) $("creditDelete").hidden = true;
  if ($("creditAdd")) $("creditAdd").textContent = t("Начислить");
  if ($("overtimeCreditTitle")) $("overtimeCreditTitle").textContent = t("Добавить переработку");

  if ($("usageDate")) $("usageDate").value = date;
  if ($("usageHours")) $("usageHours").value = "0";
  if ($("usageReason")) $("usageReason").value = "";
  if ($("usageEditNotice")) { $("usageEditNotice").hidden = true; $("usageEditNotice").textContent = ""; }
  if ($("usageDelete")) $("usageDelete").hidden = true;
  if ($("usageAdd")) $("usageAdd").textContent = t("Списать");
  if ($("overtimeUsageTitle")) $("overtimeUsageTitle").textContent = t("Добавить списание");
  renderQuickScenarios();
  updateUsageBalancePreview();
}

function openOvertimeCreditModal(date = null){
  resetOvertimeForms(overtimeDefaultDate(date));
  showCreditEditorView({ focus:false });
  renderQuickScenarios();
  updateOvertimeCalcPreview();
  openAppModal("overtimeCreditModal", "creditScenarioSelect");
}
function closeOvertimeCreditModal(){
  const date = $("creditDate")?.value || state.selected || todayKey();
  showCreditEditorView({ focus:false });
  closeAppModal("overtimeCreditModal");
  resetOvertimeForms(date);
  renderLedgerTable();
}
function openOvertimeUsageModal(date = null){
  resetOvertimeForms(overtimeDefaultDate(date));
  updateUsageBalancePreview();
  openAppModal("overtimeUsageModal", "usageHours");
}
function closeOvertimeUsageModal(){
  const date = $("usageDate")?.value || state.selected || todayKey();
  closeAppModal("overtimeUsageModal");
  resetOvertimeForms(date);
  renderLedgerTable();
}
function cancelCreditEdit(){ closeOvertimeCreditModal(); }
function cancelUsageEdit(){ closeOvertimeUsageModal(); }

function updateUsageBalancePreview(){
  if (!$("usageBalanceBefore") || !$("usageBalanceAfter")) return;
  const balance = numOr0(state.overtimeAccount?.balanceHours);
  const original = state.editingUsageId ? numOr0(findUsageById(state.editingUsageId)?.hours) : 0;
  const available = balance + original;
  const requested = readHoursInput("usageHours");
  const after = Number.isFinite(requested) ? available - requested : available;
  $("usageBalanceBefore").textContent = `${fmtHours(available)} ч`;
  $("usageBalanceAfter").textContent = `${fmtHours(after)} ч`;
  $("usageBalanceAfter").classList.toggle("negative", after < -0.0001);
}

function readHoursInput(id){
  const el = $(id);
  const raw = String(el?.value || "").trim().replace(",", ".");
  if (!raw) return 0;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : NaN;
}

function readIntInput(id){
  const el = $(id);
  const raw = String(el?.value || "").trim().replace(",", ".");
  if (!raw) return 0;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? Math.round(n) : NaN;
}

function overtimeProjection(credit){
  const p = credit?.projection || {};
  return {
    sourceWorkedDate: p.sourceWorkedDate || credit?.workedDate || "",
    sourceTimeRange: p.sourceTimeRange || credit?.timeRange || "",
    partIndex: Math.max(1, Number(p.partIndex) || 1),
    partCount: Math.max(1, Number(p.partCount) || 1),
    dayRowIndex: Math.max(1, Number(p.dayRowIndex) || 1),
    dayRowCount: Math.max(1, Number(p.dayRowCount) || 1),
    dayEarnedHours: numOr0(p.dayEarnedHours ?? credit?.hours),
    dayUsedHours: numOr0(p.dayUsedHours ?? credit?.usedHours),
    dayRemainingHours: numOr0(p.dayRemainingHours ?? credit?.remainingHours),
    sourceCreditHours: numOr0(p.sourceCreditHours ?? credit?.hours),
    sourceUsedHours: numOr0(p.sourceUsedHours ?? credit?.usedHours),
    sourceRemainingHours: numOr0(p.sourceRemainingHours ?? credit?.remainingHours),
    exact: p.exact === true,
  };
}

function overtimeProjectionBadge(credit){
  const p = overtimeProjection(credit);
  if (p.partCount <= 1) return "";
  return `<span class="overtimeProjectionBadge">${esc(t("проекция"))} ${p.partIndex}/${p.partCount}</span>`;
}

function overtimeDaySummaryHtml(credit){
  const p = overtimeProjection(credit);
  if (p.dayRowIndex !== 1) return "";
  return `<div class="overtimeDaySummary">${esc(t("итого за день"))}: <b>+${fmtHours(p.dayEarnedHours)} ч</b>${p.dayUsedHours > 0.0001 ? ` · ${esc(t("использовано"))}: ${fmtHours(p.dayUsedHours)} ч` : ""} · ${esc(t("остаток"))}: ${fmtHours(p.dayRemainingHours)} ч</div>`;
}

function overtimeCreditDisplayRange(credit){
  if (!credit) return "";
  if (credit.displayStart && credit.displayEnd) return displayDateTimeRange(credit.displayStart, credit.displayEnd);
  return credit.timeRange || "";
}

function overtimeCreditTimeHtml(credit){
  const display = overtimeCreditDisplayRange(credit) || "—";
  const absolute = !!(credit?.startInstant && credit?.endInstant);
  const p = overtimeProjection(credit);
  const differentZone = absolute
    && credit.sourceTimezone
    && credit.displayTimezone
    && credit.sourceTimezone !== credit.displayTimezone;
  const projectionBadge = overtimeProjectionBadge(credit);
  const sourceRange = p.sourceTimeRange || credit.timeRange || displayDateTimeRange(credit.startDateTime, credit.endDateTime) || "—";
  const sourceDate = p.sourceWorkedDate && p.sourceWorkedDate !== credit.workedDate
    ? `${formatDateHuman(p.sourceWorkedDate)} · `
    : "";
  if (!differentZone && p.partCount <= 1) {
    return `<div class="ledgerTimePrimary">${esc(display)}</div>${absolute && credit.displayTimezone ? `<div class="ledgerTimeZone">${esc(credit.displayTimezone)}</div>` : ""}`;
  }
  return `
    <div class="ledgerTimePrimary">${esc(display)} ${projectionBadge}</div>
    ${credit.displayTimezone ? `<div class="ledgerTimeZone">${esc(credit.displayTimezone)}</div>` : ""}
    <div class="ledgerTimeSecondary">${esc(sourceDate + sourceRange)}${credit.sourceTimezone ? ` · ${esc(credit.sourceTimezone)}` : ""}</div>
  `;
}

function allocationRangeLabels(allocation){
  if (!allocation?.exact || !allocation.displayStart || !allocation.displayEnd) return [];
  const start = String(allocation.displayStart).slice(0, 16);
  const end = String(allocation.displayEnd).slice(0, 16);
  const startDate = start.slice(0, 10), endDate = end.slice(0, 10);
  const startTime = start.slice(11, 16), endTime = end.slice(11, 16);
  if (!startDate || !endDate || startDate === endDate) return [displayDateTimeRange(start, end)];

  const labels = [`${formatDateHuman(startDate)} ${startTime}–24:00`];
  const cursor = new Date(`${startDate}T00:00:00Z`);
  cursor.setUTCDate(cursor.getUTCDate() + 1);
  while (cursor.toISOString().slice(0, 10) < endDate) {
    labels.push(`${formatDateHuman(cursor.toISOString().slice(0, 10))} 00:00–24:00`);
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  if (endTime !== "00:00") labels.push(`${formatDateHuman(endDate)} 00:00–${endTime}`);
  return labels;
}

function safeAllocationRangeLabels(allocation){
  try {
    return allocationRangeLabels(allocation);
  } catch (err) {
    console.error("Failed to render overtime allocation", allocation, err);
    return [];
  }
}

function allocationRangeLabel(allocation){
  return safeAllocationRangeLabels(allocation).join(" · ");
}

function allocationDetailHtml(allocation){
  const ranges = safeAllocationRangeLabels(allocation);
  if (!ranges.length) {
    const failed = allocation?.exact ? ` <span class="allocationRenderWarning">⚠ ${esc(t("интервал не отображён"))}</span>` : "";
    return `<span class="allocationLegacy">${esc(t("без точного времени"))}</span>${failed}`;
  }
  const reconstruction = allocation.reconstructed ? ` <span class="allocationReconstructed">${esc(t("реконструировано"))}</span>` : "";
  return `<span class="allocationRange">${ranges.map(esc).join("<br>")}</span>${reconstruction}`;
}

function renderOvertimeDayDetails(){
  if (!moduleEnabled("overtime")) return;
  const k = state.selected;
  const el = $("otDayDetails");
  if (!el) return;
  el.innerHTML = "";
  if (!k) {
    el.innerHTML = `<span class="emptyLine">${esc(t("Сначала выберите день в календаре."))}</span>`;
    return;
  }
  const credits = creditsOf(k);
  const usages = usagesOf(k);
  if (!credits.length && !usages.length) {
    el.innerHTML = `<span class="emptyLine">${esc(t("На этот день в журнале переработок записей нет. Начисления не сгорают при переходе между месяцами."))}</span>`;
    return;
  }
  for (const c of credits) {
    const row = document.createElement("div");
    row.className = "overtimeDayEntry credit";
    const text = document.createElement("div");
    const shownRange = overtimeCreditDisplayRange(c);
    const projection = overtimeProjection(c);
    const daySummary = projection.dayRowIndex === 1 && projection.dayRowCount > 1
      ? `<small><b>${esc(t("итого за день"))}: +${fmtHours(projection.dayEarnedHours)} ч</b></small>`
      : "";
    text.innerHTML = `<b>+${fmtHours(c.hours)} ч</b><span>${esc(shownRange)}${c.reason ? `${shownRange ? " · " : ""}${esc(c.reason)}` : ""} ${overtimeProjectionBadge(c)}</span>${c.sourceTimezone && c.displayTimezone && (c.sourceTimezone !== c.displayTimezone || projection.partCount > 1) ? `<small>${esc(projection.sourceTimeRange || c.timeRange || "")} · ${esc(c.sourceTimezone)}</small>` : ""}${daySummary}<small>${esc(t("остаток"))}: ${fmtHours(c.remainingHours)} ч</small>`;
    const edit = document.createElement("button");
    edit.type = "button"; edit.textContent = t("ред.");
    edit.addEventListener("click", () => startEditOvertimeCredit(c.id));
    row.append(text, edit); el.appendChild(row);
  }
  for (const u of usages) {
    const row = document.createElement("div");
    row.className = "overtimeDayEntry usage";
    const text = document.createElement("div");
    const exactRows = (u.allocations || []).flatMap(a => safeAllocationRangeLabels(a));
    text.innerHTML = `<b>−${fmtHours(u.hours)} ч</b><span>${esc(u.reason || t("списание"))}</span>${exactRows.length ? `<small>${exactRows.map(esc).join("<br>")}</small>` : ""}`;
    const edit = document.createElement("button");
    edit.type = "button"; edit.textContent = t("ред.");
    edit.addEventListener("click", () => startEditOvertimeUsage(u.id));
    row.append(text, edit); el.appendChild(row);
  }
}

let overtimePreviewTimer = null;
let overtimePreviewGeneration = 0;

function overtimePreviewPayload(){
  const startDateTime = $("creditStart")?.value || "";
  const endDateTime = $("creditEnd")?.value || "";
  if (!startDateTime && !endDateTime) {
    $("creditCalcHint").textContent = t("можно вручную");
    return null;
  }
  if (!startDateTime || !endDateTime) {
    $("creditCalcHint").textContent = t("нужны начало и конец");
    return null;
  }
  const breakMinutes = readIntInput("creditBreak");
  const plannedHours = readHoursInput("creditPlanned");
  if (!Number.isFinite(breakMinutes) || !Number.isFinite(plannedHours)) {
    $("creditCalcHint").textContent = t("проверь обед/план");
    return null;
  }
  return {
    date: $("creditDate")?.value || startDateTime.slice(0, 10),
    timeRange: null,
    startDateTime,
    endDateTime,
    breakMinutes,
    plannedHours,
    hours: null,
    reason: $("creditReason")?.value?.trim() || null,
  };
}

async function runCanonicalOvertimePreview(generation){
  const payload = overtimePreviewPayload();
  if (!payload) return null;
  try {
    const preview = await api.previewOvertimeCredit(payload);
    if (generation !== overtimePreviewGeneration) return null;
    $("creditHours").value = fmtHours(preview.creditedHours);
    const splitHint = payload.startDateTime.slice(0, 10) !== payload.endDateTime.slice(0, 10)
      ? `; ${t("сервер разобьёт по датам")}`
      : "";
    $("creditCalcHint").textContent = `${t("канонический расчёт")}: ${fmtHours(preview.elapsedHours)}ч - ${preview.breakMinutes}м${preview.plannedHours ? " - " + fmtHours(preview.plannedHours) + "ч плана" : ""} = ${fmtHours(preview.creditedHours)}ч · ${preview.sourceTimezone}${splitHint}`;
    return preview;
  } catch (err) {
    if (generation !== overtimePreviewGeneration) return null;
    $("creditCalcHint").textContent = err?.message || t("не удалось рассчитать");
    return null;
  }
}

function updateOvertimeCalcPreview({ immediate = false } = {}){
  overtimePreviewGeneration += 1;
  const generation = overtimePreviewGeneration;
  if (overtimePreviewTimer) clearTimeout(overtimePreviewTimer);
  const payload = overtimePreviewPayload();
  if (!payload) return;
  if (immediate) {
    runCanonicalOvertimePreview(generation);
    return;
  }
  overtimePreviewTimer = setTimeout(() => runCanonicalOvertimePreview(generation), 180);
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
  return $("creditDate")?.value || state.selected || todayKey();
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
  state.activeScenarioId = null;
  if ($("creditScenarioSelect")) $("creditScenarioSelect").value = "";
}

function highlightScenario(id){
  state.activeScenarioId = id || null;
  if ($("creditScenarioSelect")) $("creditScenarioSelect").value = id ? String(id) : "";
}

function formatDateHuman(k){
  if (!k) return "—";
  const [y, m, d] = k.split("-").map(Number);
  if (!y || !m || !d) return k;
  return `${pad(d)}.${pad(m)}.${y}`;
}

function quickScenarioRequirements(){
  const date = $("creditDate")?.value || state.selected;
  const st = date ? stOf(date) : null;
  return {
    date,
    hasSelected: !!date,
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

function sortedQuickScenarios(){
  return (state.quickScenarios || []).slice().sort((a,b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0));
}

function renderQuickScenarios(){
  const select = $("creditScenarioSelect");
  const scenarios = sortedQuickScenarios();
  if (select) {
    const current = String(state.activeScenarioId || select.value || "");
    select.innerHTML = "";
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = t("Не выбран");
    select.appendChild(placeholder);

    const scenarioGroup = document.createElement("optgroup");
    scenarioGroup.label = t("Сценарии переработок");
    if (scenarios.length) {
      for (const sc of scenarios) {
        const option = document.createElement("option");
        option.value = String(sc.id);
        option.textContent = `${sc.name || t("Сценарий")} — ${scenarioHumanDescription(sc)}`;
        option.disabled = !scenarioAvailable(sc);
        scenarioGroup.appendChild(option);
      }
    } else {
      const empty = document.createElement("option");
      empty.disabled = true;
      empty.textContent = t("Сценарии пока не созданы.");
      scenarioGroup.appendChild(empty);
    }
    select.appendChild(scenarioGroup);

    const manageGroup = document.createElement("optgroup");
    manageGroup.label = t("Управление");
    const manage = document.createElement("option");
    manage.value = "__manage__";
    manage.textContent = t("Управление сценариями…");
    manageGroup.appendChild(manage);
    select.appendChild(manageGroup);

    if (current && [...select.options].some(option => option.value === current && !option.disabled)) select.value = current;
    else { select.value = ""; state.activeScenarioId = null; }
  }
  renderScenarioManagerList();
}

function renderScenarioManagerList(){
  const list = $("scenarioManagerList");
  if (!list) return;
  const scenarios = sortedQuickScenarios();
  if (!scenarios.length) {
    list.innerHTML = `<span class="emptyLine">${esc(t("Сценарии пока не созданы."))}</span>`;
    return;
  }
  list.innerHTML = scenarios.map(sc => `
    <div class="scenarioManagerRow" data-scenario-row="${sc.id}">
      <button class="scenarioManagerMain" type="button" data-scenario-edit="${sc.id}">
        <b>${esc(sc.name || t("Сценарий"))}</b>
        <span>${esc(sc.description || scenarioHumanDescription(sc))}</span>
        <small>${esc(scenarioHumanDescription(sc))}</small>
      </button>
      <div class="scenarioManagerRowActions">
        <button type="button" data-scenario-edit="${sc.id}">${esc(t("ред."))}</button>
        <button class="dangerGhost" type="button" data-scenario-delete="${sc.id}">${esc(t("удалить"))}</button>
      </div>
    </div>`).join("");
}

function scenarioDayOffset(sc){
  const raw = Number(sc?.endDayOffset ?? (sc?.endNextDay ? 1 : 0));
  return Number.isFinite(raw) ? Math.max(-2, Math.min(2, Math.trunc(raw))) : 0;
}
function scenarioDayOffsetLabel(offset){
  return t(offset === -2 ? "за два дня до" : offset === -1 ? "предыдущий день" : offset === 1 ? "следующий день" : offset === 2 ? "через день" : "тот же день");
}
function scenarioHumanDescription(sc){
  const start = sc.startMode === "SHIFT_START" ? t("от начала смены") : t("от конца смены");
  let end = "";
  if (sc.endMode === "ADD_MINUTES") end = `+${sc.endOffsetMinutes || 0} ${state.language === "en" ? "min" : "мин"}`;
  else if (sc.endMode === "FIXED_TIME") end = `${sc.endFixedTime || "--:--"} · ${scenarioDayOffsetLabel(scenarioDayOffset(sc))}`;
  else end = t("до конца смены");
  return `${start} → ${end}`;
}

function renderQuickScenarioContext(){ renderQuickScenarios(); }

function fillCreditScenario({start, end, breakMinutes = 0, plannedHours = 0, reason = "", hint = "сценарий"}){
  if (!start || !end) return setSave("err", "не получилось собрать интервал сценария");
  $("creditDate").value = start.slice(0, 10);
  $("creditStart").value = start;
  $("creditEnd").value = end;
  $("creditBreak").value = String(breakMinutes || 0);
  $("creditPlanned").value = fmtHours(plannedHours || 0);
  if (reason && !$("creditReason").value.trim()) $("creditReason").value = reason;
  updateOvertimeCalcPreview();
  setSave("saved", hint);
}

function currentSelectedShiftOrError(){
  const date = selectedCreditBaseDate();
  const st = stOf(date);
  if (!date) { setSave("err", t("укажи дату переработки")); return null; }
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
  if (sc.endMode === "ADD_MINUTES") return addMinutesToLocalInput(start, Number(sc.endOffsetMinutes || 0));
  if (sc.endMode === "FIXED_TIME") {
    if (!sc.endFixedTime) return null;
    return setTimeOnDate(dateKeyOffset(base, scenarioDayOffset(sc)), sc.endFixedTime);
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
  highlightScenario(sc.id);
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

function setScenarioManagerHeader(title, hint){
  if ($("overtimeCreditTitle")) $("overtimeCreditTitle").textContent = t(title);
  if ($("overtimeCreditHint")) $("overtimeCreditHint").textContent = t(hint);
}

function showCreditEditorView({ focus = true } = {}){
  if ($("scenarioManagerView")) $("scenarioManagerView").hidden = true;
  if ($("overtimeCreditForm")) $("overtimeCreditForm").hidden = false;
  const editing = !!state.editingCreditId;
  setScenarioManagerHeader(
    editing ? "Редактировать переработку" : "Добавить переработку",
    "Дата берётся из выбранного дня календаря. Сценарий только заполняет поля — перед сохранением их можно изменить."
  );
  renderQuickScenarios();
  if (focus) requestAnimationFrame(() => $("creditScenarioSelect")?.focus());
}

function showScenarioList(){
  state.editingScenarioId = null;
  state.scenarioEditorOrigin = null;
  if ($("scenarioManagerForm")) $("scenarioManagerForm").hidden = true;
  if ($("scenarioManagerListPane")) $("scenarioManagerListPane").hidden = false;
  if ($("scenarioManagerMessage")) { $("scenarioManagerMessage").hidden = true; $("scenarioManagerMessage").textContent = ""; }
  renderScenarioManagerList();
  requestAnimationFrame(() => $("scenarioManagerAdd")?.focus());
}

function openScenarioManager({ scenario = null, draft = null, origin = "manager" } = {}){
  if (!moduleEnabled("scenarios")) return setSave("err", t("модуль отключён"));
  if ($("overtimeCreditForm")) $("overtimeCreditForm").hidden = true;
  if ($("scenarioManagerView")) $("scenarioManagerView").hidden = false;
  setScenarioManagerHeader("Сценарии переработок", "Управление происходит в этом же окне. Введённая переработка не потеряется.");
  if (scenario || draft) openScenarioEditor(scenario, draft, origin);
  else showScenarioList();
}

function updateScenarioEditorVisibility(){
  const endMode = $("scEndMode")?.value || "ADD_MINUTES";
  if ($("scEndOffsetField")) $("scEndOffsetField").hidden = endMode !== "ADD_MINUTES";
  if ($("scEndFixedField")) $("scEndFixedField").hidden = endMode !== "FIXED_TIME";
  if ($("scEndDayOffsetField")) $("scEndDayOffsetField").hidden = endMode !== "FIXED_TIME";
  if ($("scBreakCustomField")) $("scBreakCustomField").hidden = $("scBreakMode")?.value !== "CUSTOM";
  if ($("scPlannedCustomField")) $("scPlannedCustomField").hidden = $("scPlannedMode")?.value !== "CUSTOM";
}

function resetScenarioEditor(draft = null){
  const values = draft || {};
  $("scName").value = values.name || "";
  $("scGroup").value = values.groupLabel || "";
  $("scDesc").value = values.description || "";
  $("scStartMode").value = values.startMode || "SHIFT_END";
  $("scEndMode").value = values.endMode || "ADD_MINUTES";
  $("scEndOffset").value = String(values.endOffsetMinutes ?? 120);
  $("scEndFixed").value = values.endFixedTime || "08:00";
  $("scEndDayOffset").value = String(values.endDayOffset ?? (values.endNextDay ? 1 : 0));
  $("scBreakMode").value = values.breakMode || "ZERO";
  $("scBreakCustom").value = String(values.customBreakMinutes ?? 0);
  $("scPlannedMode").value = values.plannedMode || "ZERO";
  $("scPlannedCustom").value = String(values.customPlannedHours ?? 0);
  $("scReason").value = values.reasonTemplate || "";
  updateScenarioEditorVisibility();
}

function openScenarioEditor(scenario = null, draft = null, origin = "manager"){
  state.editingScenarioId = scenario ? Number(scenario.id) : null;
  state.scenarioEditorOrigin = origin;
  if ($("scenarioManagerListPane")) $("scenarioManagerListPane").hidden = true;
  if ($("scenarioManagerForm")) $("scenarioManagerForm").hidden = false;
  resetScenarioEditor(scenario || draft || null);
  if ($("scenarioEditorTitle")) $("scenarioEditorTitle").textContent = t(scenario ? "Редактировать сценарий" : "Новый сценарий");
  if ($("scenarioEditorHint")) $("scenarioEditorHint").textContent = origin === "credit-draft"
    ? t("Поля заполнены из текущей переработки. Дайте сценарию понятное название и сохраните.")
    : t("Настройте, как сценарий будет заполнять форму переработки.");
  if ($("scDelete")) $("scDelete").hidden = !scenario;
  if ($("scSave")) $("scSave").textContent = t(scenario ? "Сохранить" : "Сохранить сценарий");
  if ($("scenarioManagerMessage")) { $("scenarioManagerMessage").hidden = true; $("scenarioManagerMessage").textContent = ""; }
  requestAnimationFrame(() => $("scName")?.focus());
}

function cancelScenarioEditor(){
  if (state.scenarioEditorOrigin === "credit-draft") showCreditEditorView();
  else showScenarioList();
}

function buildScenarioPayload(){
  const name = $("scName").value.trim();
  if (!name) { setSave("err", t("назови сценарий")); $("scName").focus(); return null; }
  if ($("scEndMode").value === "FIXED_TIME" && !$("scEndFixed").value) {
    setSave("err", t("укажи фиксированное время конца"));
    $("scEndFixed").focus();
    return null;
  }
  return {
    name,
    groupLabel: $("scGroup").value.trim() || null,
    description: $("scDesc").value.trim() || null,
    startMode: $("scStartMode").value,
    endMode: $("scEndMode").value,
    endOffsetMinutes: Number($("scEndOffset").value || 0),
    endFixedTime: $("scEndFixed").value || null,
    endNextDay: Number($("scEndDayOffset").value || 0) > 0,
    endDayOffset: Number($("scEndDayOffset").value || 0),
    breakMode: $("scBreakMode").value,
    customBreakMinutes: Number($("scBreakCustom").value || 0),
    plannedMode: $("scPlannedMode").value,
    customPlannedHours: Number($("scPlannedCustom").value || 0),
    reasonTemplate: $("scReason").value.trim() || name,
    sortOrder: state.editingScenarioId
      ? ((state.quickScenarios || []).find(item => Number(item.id) === Number(state.editingScenarioId))?.sortOrder ?? 100)
      : 100 + (state.quickScenarios || []).length * 10
  };
}

async function saveQuickScenario(){
  const payload = buildScenarioPayload();
  if (!payload) return;
  const editingId = state.editingScenarioId;
  const origin = state.scenarioEditorOrigin;
  setSave("saving");
  try {
    const saved = editingId
      ? await api.updateQuickScenario(editingId, payload)
      : await api.createQuickScenario(payload);
    state.quickScenarios = await api.quickScenarios();
    state.activeScenarioId = saved.id;
    renderQuickScenarios();
    if (origin === "credit-draft") {
      showCreditEditorView();
      highlightScenario(saved.id);
    } else {
      showScenarioList();
    }
    setSave("saved", t(editingId ? "Сценарий обновлён" : "Сценарий сохранён"));
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
    renderQuickScenarios();
    showScenarioList();
    setSave("saved");
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function sameLocalMinute(a, b){ return !!a && !!b && String(a).slice(0,16) === String(b).slice(0,16); }

function overtimeIntervalFromInputs(){
  const startValue = $("creditStart")?.value || "";
  const endValue = $("creditEnd")?.value || "";
  if (!startValue && !endValue) return null;
  if (!startValue || !endValue) return { incomplete:true, startValue, endValue };
  return {
    incomplete:false,
    startValue,
    endValue,
    breakMinutes: readIntInput("creditBreak"),
    plannedHours: readHoursInput("creditPlanned"),
    hours: readHoursInput("creditHours"),
    timeRange: displayDateTimeRange(startValue, endValue),
  };
}
function localInputWallMinute(value){
  const match = String(value || "").match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!match) return NaN;
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5])) / 60000;
}

function scenarioDraftFromCreditForm(){
  const base = selectedCreditBaseDate();
  const st = base ? stOf(base) : null;
  if (!st || !st.startTime || !st.endTime) {
    setSave("err", t("Для сохранения из формы выберите день со сменой"));
    return null;
  }
  const calc = overtimeIntervalFromInputs();
  if (!calc?.startValue || !calc?.endValue || calc.incomplete) {
    const hasCompleteInterval = !!($('creditStart')?.value && $('creditEnd')?.value);
    setSave("err", t(hasCompleteInterval
      ? "Для сохранения сценария итог переработки должен быть больше 0"
      : "Для сохранения сценария укажите начало и конец"));
    return null;
  }
  const shiftStart = setTimeOnDate(base, st.startTime);
  const shiftEnd = setTimeOnDate(shiftEndDateKey(base, st), st.endTime);
  let startMode = null;
  if (sameLocalMinute(calc.startValue, shiftStart)) startMode = "SHIFT_START";
  else if (sameLocalMinute(calc.startValue, shiftEnd)) startMode = "SHIFT_END";
  if (!startMode) {
    setSave("err", t("Начало формы должно совпадать с началом или концом смены"));
    return null;
  }

  const durationMinutes = Math.round(localInputWallMinute(calc.endValue) - localInputWallMinute(calc.startValue));
  if (!Number.isFinite(durationMinutes) || durationMinutes <= 0 || durationMinutes > 4320) {
    setSave("err", t("Интервал сценария не может быть длиннее 72 часов"));
    return null;
  }
  const endMode = startMode === "SHIFT_START" && sameLocalMinute(calc.endValue, shiftEnd) ? "SHIFT_END" : "ADD_MINUTES";
  const breakMinutes = readIntInput("creditBreak");
  const plannedHours = readHoursInput("creditPlanned");
  const shiftBreak = numOr0(st.breakMinutes);
  const shiftPlan = shiftPlannedHours(st);
  const breakMode = breakMinutes === 0 ? "ZERO" : (Math.abs(breakMinutes - shiftBreak) < 0.001 ? "SHIFT" : "CUSTOM");
  const plannedMode = plannedHours === 0 ? "ZERO" : (Math.abs(plannedHours - shiftPlan) < 0.001 ? "SHIFT" : "CUSTOM");
  return {
    name: "",
    groupLabel: t("из формы"),
    description: `${t("Сохранено из формы")}: ${calc.timeRange}`,
    startMode,
    endMode,
    endOffsetMinutes: endMode === "ADD_MINUTES" ? durationMinutes : 0,
    endFixedTime: "08:00",
    endNextDay: false,
    endDayOffset: 0,
    breakMode,
    customBreakMinutes: breakMode === "CUSTOM" ? breakMinutes : 0,
    plannedMode,
    customPlannedHours: plannedMode === "CUSTOM" ? plannedHours : 0,
    reasonTemplate: $("creditReason").value.trim() || null
  };
}

function openScenarioDraftFromCredit(){
  const draft = scenarioDraftFromCreditForm();
  if (draft) openScenarioManager({ draft, origin:"credit-draft" });
}

function buildCreditPayload(){
  const calc = overtimeIntervalFromInputs();
  if (calc?.incomplete) {
    setSave("err", t("для автоподсчёта нужны и начало, и конец"));
    return null;
  }
  const manualDate = $("creditDate")?.value || state.selected;
  if (!calc && !manualDate) {
    setSave("err", t("укажи дату переработки"));
    return null;
  }
  const manualHours = readHoursInput("creditHours");
  if (!calc && (!Number.isFinite(manualHours) || manualHours <= 0)) {
    setSave("err", t("укажи часы переработки больше 0"));
    return null;
  }
  if (calc && (!Number.isFinite(calc.breakMinutes) || !Number.isFinite(calc.plannedHours))) {
    setSave("err", t("проверь обед/план"));
    return null;
  }
  const payload = {
    date: calc ? calc.startValue.slice(0, 10) : manualDate,
    timeRange: calc ? calc.timeRange : (state.editingCreditLegacyTimeRange || null),
    hours: calc ? null : manualHours,
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
    closeAppModal("overtimeCreditModal");
    resetOvertimeForms(payload.date);
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
  const projection = overtimeProjection(c);
  resetOvertimeForms(projection.sourceWorkedDate || c.workedDate);
  state.editingCreditId = Number(id);
  state.editingUsageId = null;
  $("creditDate").value = projection.sourceWorkedDate || c.workedDate;
  state.editingCreditLegacyTimeRange = c.calculated ? null : (c.timeRange || null);
  $("creditStart").value = toDateTimeLocal(c.startDateTime);
  $("creditEnd").value = toDateTimeLocal(c.endDateTime);
  $("creditBreak").value = String(c.breakMinutes || 0);
  $("creditPlanned").value = fmtHours(c.plannedHours || 0);
  $("creditHours").value = fmtHours(projection.sourceCreditHours);
  $("creditReason").value = c.reason || "";
  $("creditAdd").textContent = t("Сохранить");
  $("creditDelete").hidden = projection.sourceUsedHours > 0.0001;
  $("creditEditNotice").hidden = false;
  const sourceZoneHint = c.sourceTimezone ? ` ${t("Исходный часовой пояс")}: ${c.sourceTimezone}.` : "";
  $("creditEditNotice").textContent = `${t("Редактируется существующее начисление. Изменение периода может пересобрать строки начислений.")}${sourceZoneHint}`;
  showCreditEditorView({ focus:false });
  $("overtimeCreditTitle").textContent = t("Редактировать переработку");
  renderQuickScenarios();
  updateOvertimeCalcPreview();
  renderLedgerTable();
  openAppModal("overtimeCreditModal", "creditReason");
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
    closeAppModal("overtimeUsageModal");
    resetOvertimeForms(date);
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
  resetOvertimeForms(u.usageDate);
  state.editingCreditId = null;
  state.editingUsageId = Number(id);
  $("usageDate").value = u.usageDate;
  $("usageHours").value = fmtHours(u.hours);
  $("usageReason").value = u.reason || "";
  $("usageAdd").textContent = t("Сохранить");
  $("usageDelete").hidden = false;
  $("usageEditNotice").hidden = false;
  $("usageEditNotice").textContent = t("Редактируется существующее списание. FIFO-распределение будет рассчитано заново.");
  $("overtimeUsageTitle").textContent = t("Редактировать списание");
  updateUsageBalancePreview();
  renderLedgerTable();
  openAppModal("overtimeUsageModal", "usageHours");
}

async function removeOvertimeCredit(id){
  const credit = (state.overtimeAccount?.credits || []).find(c => Number(c.id) === Number(id)) || (state.ledgerPage?.items || []).find(c => Number(c.id) === Number(id));
  const projection = overtimeProjection(credit);
  const label = credit ? `${projection.sourceWorkedDate || credit.workedDate} ${projection.sourceTimeRange || credit.timeRange || ""} +${fmtHours(projection.sourceCreditHours)} ч` : `#${id}`;
  if (!confirm(`Удалить начисление переработки ${label}?\n\nЭто действие нельзя отменить.`)) return;
  setSave("saving");
  try {
    state.overtimeAccount = await api.deleteOvertimeCredit(id);
    if (state.editingCreditId === Number(id)) {
      closeAppModal("overtimeCreditModal");
      resetOvertimeForms(state.selected);
    }
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
  const usage = findUsageById(id);
  const label = usage ? `${usage.usageDate} -${fmtHours(usage.hours)} ч${usage.reason ? " — " + usage.reason : ""}` : `#${id}`;
  const partCount = Math.max(1, Number(usage?.allocations?.length || 0));
  const partsText = state.language === "en"
    ? `${partCount} FIFO interval part${partCount === 1 ? "" : "s"}`
    : `${partCount} ${partCount === 1 ? "часть" : (partCount >= 2 && partCount <= 4 ? "части" : "частей")} FIFO-интервала`;
  const message = state.language === "en"
    ? `Delete the entire time-off ${label}?

${partsText} will be removed. Overtime credits will remain and the minutes will return to their balance.`
    : `Удалить весь отгул ${label}?

Будут удалены ${partsText}. Начисления переработки останутся, а минуты вернутся в их остаток.`;
  if (!confirm(message)) return;
  setSave("saving");
  try {
    state.overtimeAccount = await api.deleteOvertimeUsage(id);
    if (state.editingUsageId === Number(id)) {
      closeAppModal("overtimeUsageModal");
      resetOvertimeForms(state.selected);
    }
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
  const p = overtimeProjection(c);
  return `${c.workedDate} ${p.sourceWorkedDate} ${c.timeRange || ""} ${p.sourceTimeRange} ${c.displayStart || ""} ${c.displayEnd || ""} ${c.sourceTimezone || ""} ${c.displayTimezone || ""} ${c.hours} ${p.dayEarnedHours} ${c.reason || ""} ${usages}`.toLowerCase();
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
  syncLedgerFilterInputs();

  if (state.ui?.loadingLedger) {
    if (statsEl) statsEl.innerHTML = "";
    $("ledgerPager").innerHTML = "";
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 8;
    td.className = "emptyTableCell";
    td.innerHTML = `<div class="loadingState" role="status" aria-live="polite"><span>${htmlSafe(t("Загружаю переработки…"))}</span><i></i><i></i><i></i></div>`;
    tr.appendChild(td);
    tbody.replaceChildren(tr);
    return;
  }

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
    td.className = "emptyTableCell";
    td.innerHTML = emptyStateHtml({
      icon:"+",
      title:"Данных пока нет",
      text:"Добавь запись из панели выбранного дня в календаре.",
      variant:"board"
    });
    tr.appendChild(td);
    tbody.replaceChildren(tr);
    return;
  }

  if (!credits.length) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = 8;
    td.className = "emptyTableCell";
    td.innerHTML = emptyStateHtml({
      icon:"⌕",
      title:"Ничего не найдено",
      text:"Попробуй сбросить фильтры или выбрать другой период.",
      variant:"board"
    });
    tr.appendChild(td);
    tbody.replaceChildren(tr);
    return;
  }

  const fragment = document.createDocumentFragment();
  try {
  for (const c of credits) {
    const tr = document.createElement("tr");
    tr.dataset.creditId = String(c.id);
    const usageIds = (c.usages || []).map(u => Number(u.usageId));
    const editingCredit = Number(state.editingCreditId) === Number(c.id);
    const editingUsage = state.editingUsageId != null && usageIds.includes(Number(state.editingUsageId));
    if (editingUsage) tr.dataset.usageRowId = String(state.editingUsageId);
    tr.classList.toggle("ledgerEditingRow", editingCredit || editingUsage);
    const status = creditStatus(c);
    const usedText = (c.usages || []).length
      ? (c.usages || []).map(u => {
          const fullUsage = findUsageById(u.usageId);
          const accountParts = fullUsage?.allocations || [];
          const responsePartCount = Math.max(0, Number(u.allocationPartCount) || 0);
          const partCount = Math.max(responsePartCount, accountParts.length);
          const responsePartIndex = Math.max(0, Number(u.allocationPartIndex) || 0);
          const accountPartIndex = accountParts.findIndex(part => Number(part.creditId) === Number(c.id)) + 1;
          const partIndex = responsePartIndex || accountPartIndex || 1;
          const partLabel = partCount > 1
            ? `<span class="allocationPartBadge">${state.language === "en" ? "part" : "часть"} ${Math.min(partIndex, partCount)}/${partCount}</span>`
            : "";
          return `
          <div class="ledgerUsageItem">
            <span class="ledgerUsageText">
              <span>${esc(u.usageDate)}: ${fmtHours(u.hours)} ${state.language === "en" ? "h" : "ч"}${u.reason ? " — " + esc(u.reason) : ""} ${partLabel}</span>
              ${allocationDetailHtml(u)}
            </span>
            <span class="ledgerUsageActions" aria-label="${esc(t("Действия списания"))}">
              <button type="button" data-edit-usage="${u.usageId}" title="${esc(t("Редактировать весь отгул"))}">${esc(t("ред. отгул"))}</button>
              <button type="button" data-del-usage="${u.usageId}" title="${esc(t("Удалить весь отгул"))}">${esc(t("удалить весь отгул"))}</button>
            </span>
          </div>`;
        }).join("")
      : `<span class="small">${esc(t("не списывалось"))}</span>`;
    const projection = overtimeProjection(c);
    const legacyBadge = c.legacyTimezoneRequired ? ` · <span class="legacyTimezoneBadge">⚠ ${esc(t("без точного времени"))}</span>` : "";
    const calcInfo = c.calculated ? `<div class="small">${esc(t("обед"))}: ${c.breakMinutes || 0} ${state.language === "en" ? "min" : "мин"}${numOr0(c.plannedHours) ? ` · ${esc(t("план"))}: ${fmtHours(c.plannedHours)} ${state.language === "en" ? "h" : "ч"}` : ""}${legacyBadge}</div>` : legacyBadge;
    const deleteBtn = projection.sourceUsedHours <= 0.0001
      ? `<button type="button" data-del-credit="${c.id}" title="${esc(t("Удалить переработку"))}">${esc(t("удалить"))}</button>`
      : `<span class="small" title="${esc(t("Сначала удали списания, которые используют это начисление"))}">${esc(t("сначала списания"))}</span>`;
    tr.innerHTML = `
      <td class="mono">${esc(c.workedDate)}${overtimeDaySummaryHtml(c)}<div class="ledgerStatus ${status}">${statusLabel(status)}</div></td>
      <td>${overtimeCreditTimeHtml(c)}${calcInfo}</td>
      <td class="numc">+${fmtHours(c.hours)} ч</td>
      <td class="reason">${esc(c.reason || "—")}</td>
      <td class="numc used">${fmtHours(c.usedHours)} ч</td>
      <td class="small">${usedText}</td>
      <td class="numc remain">${fmtHours(c.remainingHours)} ч</td>
      <td class="ledgerRowActions"><button type="button" data-edit-credit="${c.id}" title="${esc(t("Редактировать переработку"))}">${esc(t("ред."))}</button>${deleteBtn}</td>
    `;
    fragment.appendChild(tr);
  }
  } catch (err) {
    console.error("Failed to render overtime ledger atomically", err);
    setSave("err", `${t("Не удалось отрисовать журнал переработок")}: ${err.message}`);
    return;
  }
  fragment.querySelectorAll("[data-edit-credit]").forEach(btn => {
    btn.addEventListener("click", () => startEditOvertimeCredit(Number(btn.dataset.editCredit)));
  });
  fragment.querySelectorAll("[data-del-credit]").forEach(btn => {
    btn.addEventListener("click", () => removeOvertimeCredit(Number(btn.dataset.delCredit)));
  });
  fragment.querySelectorAll("[data-edit-usage]").forEach(btn => {
    btn.addEventListener("click", () => startEditOvertimeUsage(Number(btn.dataset.editUsage)));
  });
  fragment.querySelectorAll("[data-del-usage]").forEach(btn => {
    btn.addEventListener("click", () => removeOvertimeUsage(Number(btn.dataset.delUsage)));
  });
  tbody.replaceChildren(fragment);
}

async function loadLedgerPage(silent = true){
  if (!moduleEnabled("overtime")) {
    state.ledgerPage = { items:[], page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false };
    renderLedgerTable();
    return;
  }
  try {
    state.ui.loadingLedger = true;
    if (!silent) renderLedgerTable();
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
  } finally {
    state.ui.loadingLedger = false;
    renderLedgerTable();
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

function legacyMigrationSelectedIds(){
  return [...document.querySelectorAll('[data-legacy-credit-id]:checked')]
    .map(input => Number(input.dataset.legacyCreditId))
    .filter(Number.isFinite);
}

function renderLegacyMigrationPreview(preview){
  state.legacyOvertimePreview = preview || { credits:[], migratableCount:0, blockedCount:0 };
  const list = $("legacyOvertimeList");
  const status = $("legacyOvertimeStatus");
  if (!list || !status) return;
  const rows = preview?.credits || [];
  status.textContent = rows.length
    ? `${preview.migratableCount} ${state.language === "en" ? "ready" : "можно привязать"}${preview.blockedCount ? ` · ${preview.blockedCount} ${state.language === "en" ? "blocked" : "без точного интервала"}` : ""}`
    : t("нет старых записей для миграции");
  list.innerHTML = rows.map(row => `
    <label class="legacyMigrationItem ${row.migratable ? "" : "blocked"}">
      <input type="checkbox" data-legacy-credit-id="${row.id}" ${row.migratable ? "checked" : "disabled"}/>
      <span>
        <b>${esc(row.workedDate)} · +${fmtHours(row.hours)} ${state.language === "en" ? "h" : "ч"}</b>
        <span>${esc(row.timeRange || `${row.startDateTime || "?"} — ${row.endDateTime || "?"}`)}${row.reason ? ` · ${esc(row.reason)}` : ""}</span>
        ${row.migratable ? `<small>${esc(row.creditedStart || "")} — ${esc(row.creditedEnd || "")} · ${esc(row.sourceTimezone || "")}</small>` : `<small class="errorText">${esc(row.blockedReason || t("без точного времени"))}</small>`}
      </span>
    </label>`).join("");
  $("legacyOvertimeApply").disabled = preview?.migratableCount < 1;
}

async function refreshLegacyMigrationPreview(){
  const zone = $("legacyOvertimeTimezone")?.value || state.timeSettings?.workTimezone || browserTimeZone();
  const status = $("legacyOvertimeStatus");
  if (status) status.textContent = state.language === "en" ? "Loading…" : "Загрузка…";
  try {
    const preview = await api.previewLegacyOvertime({ creditIds:[], sourceTimezone:zone });
    renderLegacyMigrationPreview(preview);
  } catch (err) {
    console.error(err);
    if (status) status.textContent = err.message;
  }
}

async function openLegacyOvertimeMigration(){
  const zone = state.timeSettings?.workTimezone || state.profile?.workTimezone || browserTimeZone();
  if (typeof populateTimeZoneSelect === "function") populateTimeZoneSelect("legacyOvertimeTimezone", zone);
  if ($("legacyOvertimeTimezone")) $("legacyOvertimeTimezone").value = zone;
  openAppModal("legacyOvertimeModal", "legacyOvertimeTimezone");
  await refreshLegacyMigrationPreview();
}

function closeLegacyOvertimeMigration(){
  closeAppModal("legacyOvertimeModal");
}

async function applyLegacyOvertimeMigration(){
  const creditIds = legacyMigrationSelectedIds();
  if (!creditIds.length) return setSave("err", state.language === "en" ? "Select at least one entry" : "Выбери хотя бы одну запись");
  const sourceTimezone = $("legacyOvertimeTimezone")?.value || state.timeSettings?.workTimezone || browserTimeZone();
  if (!confirm(state.language === "en"
      ? `Attach ${creditIds.length} entries to ${sourceTimezone}? Hours and balance will not change.`
      : `Привязать ${creditIds.length} записей к ${sourceTimezone}? Часы и баланс не изменятся.`)) return;
  setSave("saving");
  try {
    const result = await api.migrateLegacyOvertime({ creditIds, sourceTimezone });
    state.overtimeAccount = result.account;
    closeLegacyOvertimeMigration();
    renderOvertimeControls();
    renderCalendar();
    await loadLedgerPage(true);
    setSave("saved", `${state.language === "en" ? "Migrated" : "Привязано"}: ${result.migratedCount}`);
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
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

$("dayAddCredit")?.addEventListener("click", () => openOvertimeCreditModal(state.selected));
$("dayAddUsage")?.addEventListener("click", () => openOvertimeUsageModal(state.selected));
$("ledgerAddCredit")?.addEventListener("click", () => openOvertimeCreditModal(state.selected || todayKey()));
$("ledgerAddUsage")?.addEventListener("click", () => openOvertimeUsageModal(state.selected || todayKey()));
$("ledgerMigrateLegacy")?.addEventListener("click", openLegacyOvertimeMigration);
$("legacyOvertimeClose")?.addEventListener("click", closeLegacyOvertimeMigration);
$("legacyOvertimeCancel")?.addEventListener("click", closeLegacyOvertimeMigration);
$("legacyOvertimeBackdrop")?.addEventListener("click", closeLegacyOvertimeMigration);
$("legacyOvertimePreview")?.addEventListener("click", refreshLegacyMigrationPreview);
$("legacyOvertimeTimezone")?.addEventListener("change", refreshLegacyMigrationPreview);
$("legacyOvertimeSelectAll")?.addEventListener("click", () => document.querySelectorAll('[data-legacy-credit-id]:not(:disabled)').forEach(input => { input.checked = true; }));
$("legacyOvertimeApply")?.addEventListener("click", applyLegacyOvertimeMigration);

$("overtimeCreditForm")?.addEventListener("submit", event => { event.preventDefault(); addOvertimeCredit(); });
$("overtimeUsageForm")?.addEventListener("submit", event => { event.preventDefault(); addOvertimeUsage(); });
$("creditCancel")?.addEventListener("click", cancelCreditEdit);
$("usageCancel")?.addEventListener("click", cancelUsageEdit);
$("overtimeCreditClose")?.addEventListener("click", closeOvertimeCreditModal);
$("overtimeCreditBackdrop")?.addEventListener("click", closeOvertimeCreditModal);
$("overtimeUsageClose")?.addEventListener("click", closeOvertimeUsageModal);
$("overtimeUsageBackdrop")?.addEventListener("click", closeOvertimeUsageModal);
$("creditDelete")?.addEventListener("click", () => state.editingCreditId && removeOvertimeCredit(state.editingCreditId));
$("usageDelete")?.addEventListener("click", () => state.editingUsageId && removeOvertimeUsage(state.editingUsageId));

$("creditNightShiftPreset")?.addEventListener("click", setNightShiftPreset);
$("creditNightPreset")?.addEventListener("click", setNightOvertimePreset);
$("creditScenarioSelect")?.addEventListener("change", event => {
  if (event.target.value === "__manage__") {
    event.target.value = state.activeScenarioId ? String(state.activeScenarioId) : "";
    openScenarioManager();
    return;
  }
  const id = Number(event.target.value);
  if (!id) { clearScenarioHighlight(); return; }
  const sc = (state.quickScenarios || []).find(item => Number(item.id) === id);
  if (sc) applyQuickScenario(sc);
});
$("creditScenarioManage")?.addEventListener("click", () => openScenarioManager());
$("creditScenarioSaveCurrent")?.addEventListener("click", openScenarioDraftFromCredit);
$("scenarioManagerBack")?.addEventListener("click", () => showCreditEditorView());
$("scenarioManagerAdd")?.addEventListener("click", () => openScenarioEditor(null, null, "manager"));
$("scenarioEditorBack")?.addEventListener("click", cancelScenarioEditor);
$("scCancelEdit")?.addEventListener("click", cancelScenarioEditor);
$("scenarioManagerForm")?.addEventListener("submit", event => { event.preventDefault(); saveQuickScenario(); });
for (const id of ["scEndMode", "scBreakMode", "scPlannedMode"]) $(id)?.addEventListener("change", updateScenarioEditorVisibility);
$("scDelete")?.addEventListener("click", () => state.editingScenarioId && deleteQuickScenario(state.editingScenarioId));
$("scenarioManagerList")?.addEventListener("click", event => {
  const edit = event.target.closest("[data-scenario-edit]");
  if (edit) {
    const scenario = (state.quickScenarios || []).find(item => Number(item.id) === Number(edit.dataset.scenarioEdit));
    if (scenario) openScenarioEditor(scenario, null, "manager");
    return;
  }
  const del = event.target.closest("[data-scenario-delete]");
  if (del) deleteQuickScenario(Number(del.dataset.scenarioDelete));
});
$("creditPlanByShift")?.addEventListener("click", () => {
  const st = currentSelectedShiftOrError();
  if (!st) return;
  $("creditPlanned").value = fmtHours(shiftPlannedHours(st));
  updateOvertimeCalcPreview();
});
$("creditTimeByShift")?.addEventListener("click", () => {
  const st = currentSelectedShiftOrError();
  if (!st) return;
  const base = selectedCreditBaseDate();
  $("creditDate").value = base;
  $("creditStart").value = setTimeOnDate(base, st.startTime);
  const endDate = st.endTime <= st.startTime ? dateKeyOffset(base, 1) : base;
  $("creditEnd").value = setTimeOnDate(endDate, st.endTime);
  $("creditBreak").value = String(st.breakMinutes || 0);
  $("creditPlanned").value = fmtHours(shiftPlannedHours(st));
  updateOvertimeCalcPreview();
});
for (const id of ["creditDate", "creditStart", "creditEnd", "creditBreak", "creditPlanned"]) {
  $(id)?.addEventListener("input", () => { updateOvertimeCalcPreview(); if (id === "creditDate") renderQuickScenarios(); });
}
$("usageHours")?.addEventListener("input", updateUsageBalancePreview);
$("usageByShift")?.addEventListener("click", () => {
  const date = $("usageDate")?.value || state.selected;
  const st = date ? stOf(date) : null;
  if (!st) return setSave("err", t("на выбранном дне нет смены"));
  $("usageHours").value = fmtHours(shiftPlannedHours(st));
  updateUsageBalancePreview();
});
for (const id of ["creditDate", "creditStart", "creditEnd", "creditBreak", "creditPlanned", "creditHours", "creditReason", "usageDate", "usageHours", "usageReason"]) {
  $(id)?.addEventListener("keydown", event => {
    if (event.key !== "Enter" || event.shiftKey || event.target.tagName === "TEXTAREA") return;
    event.preventDefault();
    if (id.startsWith("credit")) addOvertimeCredit();
    else addOvertimeUsage();
  });
}
