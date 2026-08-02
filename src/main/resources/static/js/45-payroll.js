/*
 * 45-payroll.js — Payroll Foundation
 *
 * First transparent money layer. The browser only renders backend projections;
 * it never reinterprets calendar events or creates authoritative totals itself.
 */

"use strict";

Object.assign(I18N_EN, {
  "Зарплата":"Payroll",
  "Расчёт зарплаты":"Payroll calculation",
  "Период открыт":"Period is open",
  "Период закрыт":"Period is closed",
  "Журнал согласован":"Ledger is healthy",
  "Есть расхождения":"Ledger has issues",
  "Предварительный расчёт":"Preview calculation",
  "Ревизия":"Revision",
  "Сначала закрой расчётный период":"Close the accounting period first",
  "Сначала укажи почасовую ставку":"Set the hourly rate first",
  "Сначала исправь расхождения журнала":"Fix ledger integrity issues first",
  "Правила расчёта сохранены":"Payroll rules saved",
  "Денежная операция добавлена":"Money adjustment added",
  "Ревизия расчёта сохранена":"Payroll revision saved",
  "Нет ручных операций":"No manual adjustments",
  "Расчётов пока нет":"No calculations yet"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

window.__dutylogPayrollReady = Promise.resolve();

function payrollDefaultMonth(){
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function payrollMonth(){
  return $("payrollMonth")?.value || payrollDefaultMonth();
}

function payrollMinutes(value){
  const minutes = Number(value || 0);
  const sign = minutes < 0 ? "−" : "";
  const safe = Math.abs(Math.round(minutes));
  const hours = Math.floor(safe / 60);
  const rest = safe % 60;
  return `${sign}${hours} ч${rest ? ` ${rest} мин` : ""}`;
}

function payrollMoney(minor, currency = state.payrollPeriod?.settings?.currencyCode || "RUB"){
  const value = Number(minor || 0) / 100;
  try {
    return new Intl.NumberFormat(state.language === "en" ? "en-US" : "ru-RU", {
      style:"currency", currency:String(currency || "RUB").toUpperCase(),
      minimumFractionDigits:2, maximumFractionDigits:2
    }).format(value);
  } catch (_) {
    return `${value.toFixed(2)} ${String(currency || "RUB").toUpperCase()}`;
  }
}

function payrollRateMoney(minor, currency){
  return `${payrollMoney(minor, currency)} / ч`;
}

function payrollMessage(kind, text){
  const box = $("payrollMessage");
  if (!box) return;
  box.className = `appModalMessage payrollMessage ${kind || ""}`.trim();
  box.textContent = text || "";
}

function payrollBlockingLabel(code){
  if (code === "PERIOD_OPEN") return t("Сначала закрой расчётный период");
  if (code === "LEDGER_INTEGRITY_FAILED") return t("Сначала исправь расхождения журнала");
  if (code === "PAYROLL_RATE_REQUIRED") return t("Сначала укажи почасовую ставку");
  return "";
}

function payrollMoneyForMinutes(minutes, rateMinor){
  return Math.round((Number(minutes || 0) * Number(rateMinor || 0)) / 60);
}

function renderPayrollBreakdown(data){
  const box = $("payrollBreakdown");
  if (!box) return;
  const preview = data?.preview;
  if (!preview) { box.innerHTML = ""; return; }
  const currency = preview.currencyCode;
  const rate = preview.hourlyRateMinor;
  const lines = [
    ["Фактически отработано", preview.workedMinutes, payrollMoneyForMinutes(preview.workedMinutes, rate), "worked"],
    ["Оплачиваемый отпуск", preview.vacationMinutes, payrollMoneyForMinutes(preview.vacationMinutes, rate), "vacation"],
    ["Больничный", preview.sickMinutes, payrollMoneyForMinutes(preview.sickMinutes, rate), "sick"],
    ["Отгул из переработок", preview.overtimeCompensatedMinutes, payrollMoneyForMinutes(preview.overtimeCompensatedMinutes, rate), "overtime"],
    ["Корректировка времени", preview.timeAdjustmentMinutes, payrollMoneyForMinutes(preview.timeAdjustmentMinutes, rate), "adjustment"]
  ].filter(line => Number(line[1]) !== 0);
  box.innerHTML = lines.length ? lines.map(([label, minutes, amount, kind]) => `
    <div class="payrollBreakdownRow ${esc(kind)}">
      <span><b>${esc(t(label))}</b><small>${esc(payrollMinutes(minutes))}</small></span>
      <strong>${esc(payrollMoney(amount, currency))}</strong>
    </div>`).join("") : `<div class="payrollEmpty">${esc(t("Нет оплачиваемого времени"))}</div>`;
}

function renderPayrollAdjustments(data){
  const list = $("payrollAdjustmentList");
  if (!list) return;
  const currency = data?.settings?.currencyCode || "RUB";
  const items = data?.adjustments || [];
  list.innerHTML = items.length ? items.map(item => `
    <article class="payrollAdjustmentItem ${item.adjustmentType === "DEDUCTION" ? "deduction" : "addition"}">
      <span><b>${esc(item.title)}</b><small>${esc(item.note || (item.adjustmentType === "DEDUCTION" ? "Удержание" : "Начисление"))}</small></span>
      <strong>${item.adjustmentType === "DEDUCTION" ? "−" : "+"}${esc(payrollMoney(item.amountMinor, currency))}</strong>
    </article>`).join("") : `<div class="payrollEmpty">${esc(t("Нет ручных операций"))}</div>`;
}

function renderPayrollSnapshots(data){
  const list = $("payrollSnapshotList");
  if (!list) return;
  const items = data?.snapshots || [];
  list.innerHTML = items.length ? items.map(item => `
    <article class="payrollSnapshotItem ${item.supersededById ? "superseded" : "current"}">
      <span><b>${esc(t("Ревизия"))} ${item.revision}</b><small>${esc(new Date(item.createdAt).toLocaleString())} · ${esc(item.calculationHash.slice(0, 10))}</small></span>
      <strong>${esc(payrollMoney(item.totalPayMinor, item.currencyCode))}</strong>
    </article>`).join("") : `<div class="payrollEmpty">${esc(t("Расчётов пока нет"))}</div>`;
}

function renderPayroll(){
  const data = state.payrollPeriod;
  if (!data) return;
  const preview = data.preview;
  const currency = preview?.currencyCode || data.settings?.currencyCode || "RUB";

  if ($("payrollPeriodStatus")) $("payrollPeriodStatus").textContent = data.periodClosed ? t("Период закрыт") : t("Период открыт");
  if ($("payrollPeriodStatus")) $("payrollPeriodStatus").className = data.periodClosed ? "ok" : "warn";
  if ($("payrollPeriodHint")) $("payrollPeriodHint").textContent = data.periodClosed ? "Источник времени зафиксирован" : "Предпросмотр меняется вместе с календарём";
  if ($("payrollIntegrityStatus")) $("payrollIntegrityStatus").textContent = data.integrityHealthy ? t("Журнал согласован") : t("Есть расхождения");
  if ($("payrollIntegrityStatus")) $("payrollIntegrityStatus").className = data.integrityHealthy ? "ok" : "bad";
  if ($("payrollTotal")) $("payrollTotal").textContent = payrollMoney(preview?.totalPayMinor, currency);
  if ($("payrollRevisionLabel")) $("payrollRevisionLabel").textContent = data.latestSnapshot
    ? `${t("Ревизия")} ${data.latestSnapshot.revision} · ${payrollMoney(data.latestSnapshot.totalPayMinor, data.latestSnapshot.currencyCode)}`
    : t("Предварительный расчёт");
  if ($("payrollCurrencyBadge")) $("payrollCurrencyBadge").textContent = currency;

  if ($("payrollPlanned")) $("payrollPlanned").textContent = payrollMinutes(preview?.plannedMinutes);
  if ($("payrollWorked")) $("payrollWorked").textContent = payrollMinutes(preview?.workedMinutes);
  if ($("payrollPaidAbsence")) $("payrollPaidAbsence").textContent = payrollMinutes(preview?.paidAbsenceMinutes);
  if ($("payrollUnpaid")) $("payrollUnpaid").textContent = payrollMinutes(preview?.unpaidMinutes);
  if ($("payrollTimeAdjustment")) $("payrollTimeAdjustment").textContent = payrollMinutes(preview?.timeAdjustmentMinutes);
  if ($("payrollPayable")) $("payrollPayable").textContent = payrollMinutes(preview?.payableMinutes);
  if ($("payrollBasePay")) $("payrollBasePay").textContent = payrollMoney(preview?.basePayMinor, currency);
  if ($("payrollAdditions")) $("payrollAdditions").textContent = payrollMoney(preview?.additionsMinor, currency);
  if ($("payrollDeductions")) $("payrollDeductions").textContent = payrollMoney(preview?.deductionsMinor, currency);

  if ($("payrollCurrency") && document.activeElement !== $("payrollCurrency")) $("payrollCurrency").value = data.settings?.currencyCode || "RUB";
  if ($("payrollHourlyRate") && document.activeElement !== $("payrollHourlyRate")) $("payrollHourlyRate").value = ((data.settings?.hourlyRateMinor || 0) / 100).toFixed(2);

  const blocking = payrollBlockingLabel(data.blockingReason);
  if ($("payrollBlocking")) {
    $("payrollBlocking").textContent = blocking || `Готово к фиксации · ${payrollRateMoney(preview?.hourlyRateMinor, currency)}`;
    $("payrollBlocking").className = `payrollBlocking ${blocking ? "blocked" : "ready"}`;
  }
  if ($("payrollCalculate")) $("payrollCalculate").disabled = !data.canCalculate || state.payrollLoading;
  const adjustmentDisabled = !data.periodClosed || state.payrollLoading;
  $("payrollAdjustmentForm")?.querySelectorAll("input, select, button").forEach(el => el.disabled = adjustmentDisabled);

  renderPayrollBreakdown(data);
  renderPayrollAdjustments(data);
  renderPayrollSnapshots(data);
}

async function loadPayroll(force = false){
  if (!moduleEnabled("payroll")) { state.payrollPeriod = null; return null; }
  const month = payrollMonth();
  if (!force && state.payrollPeriod?.month === month) { renderPayroll(); return state.payrollPeriod; }
  state.payrollLoading = true;
  renderPayroll();
  try {
    state.payrollPeriod = await api.payrollPeriod(month);
    renderPayroll();
    return state.payrollPeriod;
  } catch (error) {
    payrollMessage("err", error.message);
    throw error;
  } finally {
    state.payrollLoading = false;
    renderPayroll();
  }
}

function openPayrollView(force = false){
  if ($("payrollMonth") && !$("payrollMonth").value) $("payrollMonth").value = payrollDefaultMonth();
  const run = Promise.resolve(loadPayroll(force));
  window.__dutylogPayrollReady = run;
  return run;
}

async function savePayrollSettings(event){
  event?.preventDefault();
  const currencyCode = String($("payrollCurrency")?.value || "RUB").trim().toUpperCase();
  const rate = Number($("payrollHourlyRate")?.value || 0);
  if (!Number.isFinite(rate) || rate < 0) return payrollMessage("err", "Некорректная ставка");
  try {
    await api.updatePayrollSettings({ currencyCode, hourlyRateMinor:Math.round(rate * 100) });
    await loadPayroll(true);
    payrollMessage("ok", t("Правила расчёта сохранены"));
  } catch (error) { payrollMessage("err", error.message); }
}

async function savePayrollAdjustment(event){
  event?.preventDefault();
  const amount = Number($("payrollAdjustmentAmount")?.value || 0);
  if (!Number.isFinite(amount) || amount <= 0) return payrollMessage("err", "Укажи положительную сумму");
  const body = {
    month:payrollMonth(),
    adjustmentType:$("payrollAdjustmentType")?.value || "ADDITION",
    amountMinor:Math.round(amount * 100),
    title:$("payrollAdjustmentTitle")?.value?.trim(),
    note:$("payrollAdjustmentNote")?.value?.trim() || null
  };
  try {
    await api.addPayrollAdjustment(body);
    if ($("payrollAdjustmentAmount")) $("payrollAdjustmentAmount").value = "";
    if ($("payrollAdjustmentTitle")) $("payrollAdjustmentTitle").value = "";
    if ($("payrollAdjustmentNote")) $("payrollAdjustmentNote").value = "";
    await loadPayroll(true);
    payrollMessage("ok", t("Денежная операция добавлена"));
  } catch (error) { payrollMessage("err", error.message); }
}

async function calculatePayroll(){
  state.payrollLoading = true;
  renderPayroll();
  try {
    await api.calculatePayroll(payrollMonth());
    await loadPayroll(true);
    payrollMessage("ok", t("Ревизия расчёта сохранена"));
  } catch (error) { payrollMessage("err", error.message); }
  finally { state.payrollLoading = false; renderPayroll(); }
}

$("payrollMonth")?.addEventListener("change", () => openPayrollView(true));
$("payrollReload")?.addEventListener("click", () => openPayrollView(true));
$("payrollSettingsForm")?.addEventListener("submit", savePayrollSettings);
$("payrollAdjustmentForm")?.addEventListener("submit", savePayrollAdjustment);
$("payrollCalculate")?.addEventListener("click", calculatePayroll);
