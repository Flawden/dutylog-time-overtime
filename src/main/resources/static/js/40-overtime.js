/*
 * 40-overtime.js — Переработки: журнал, начисления/списания, быстрые сценарии
 * Часть бывшего app.js (распил v26.1). Файлы делят ГЛОБАЛЬНУЮ область
 * видимости (это не ES-модули); порядок подключения в index.html — закон.
 * Инвариант: склейка всех js/*.js по порядку === старый app.js.
 */
/* ─── Журнал переработки и отгулов ─────────────────────────── */
function updateOvertimeBalanceLabel(){
  const acc = state.overtimeAccount || { balanceHours:0 };
  const bal = numOr0(acc.balanceHours);
  $("otBalance").textContent = `доступно ${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
  $("ledgerBalance").textContent = `${bal > 0 ? "+" : ""}${fmtHours(bal)} ч`;
}

function renderOvertimeControls(){
  if (!moduleEnabled("overtime")) { updateAccSummaries(); return; }
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
  if (!moduleEnabled("overtime")) return;
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
  if (!moduleEnabled("overtime")) {
    state.ledgerPage = { items:[], page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false };
    renderLedgerTable();
    return;
  }
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
