<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { usePayrollStore } from "../stores/payrollStore";
import type { DutyLogPayrollDomain, ProductionCalendarDayInput } from "../types/domain";

const shell = useShellStore();
const store = usePayrollStore();
const { activeRoute, language, modulesLoaded, modules } = storeToRefs(shell);
const { period, loading, error } = storeToRefs(store);
let previousDomain: DutyLogPayrollDomain | undefined;

function defaultMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

const month = ref(defaultMonth());
const currencyCode = ref("RUB");
const hourlyRate = ref("0.00");
const adjustmentType = ref<"ADDITION" | "DEDUCTION">("ADDITION");
const adjustmentAmount = ref("");
const adjustmentTitle = ref("");
const adjustmentNote = ref("");
const message = ref("");
const messageOk = ref(false);
const productionDate = ref(`${month.value}-01`);
const productionKind = ref<ProductionCalendarDayInput["dayKind"]>("HOLIDAY");
const productionScheduleEffect = ref<ProductionCalendarDayInput["scheduleEffect"]>("NORM_OVERRIDE");
const productionNormHours = ref("0");
const productionPayrollEffect = ref<ProductionCalendarDayInput["payrollEffect"]>("HOLIDAY");
const productionLabel = ref("");

const payrollEnabled = computed(() => modulesLoaded.value && modules.value.payroll !== false);
const preview = computed(() => period.value?.preview ?? null);
const productionCalendar = computed(() => period.value?.productionCalendar ?? null);
const productionAffectedDays = computed(() => productionCalendar.value?.days.filter(day => day.sourceType !== "NONE") ?? []);
const selectedProductionDay = computed(() => productionCalendar.value?.days.find(day => day.date === productionDate.value) ?? null);
const currency = computed(() => preview.value?.currencyCode || period.value?.settings.currencyCode || "RUB");

const text = computed(() => language.value === "en" ? {
  foundation: "Payroll Foundation", title: "Payroll calculation", intro: "DutyLog uses only approved facts from a closed period. Every calculation is stored as an immutable revision.", month: "Accounting month", reload: "Refresh", period: "Period", periodClosed: "Period is closed", periodOpen: "Period is open", frozen: "The time source is frozen", live: "Preview changes with the calendar", integrity: "Integrity", healthy: "Ledger is healthy", unhealthy: "Ledger has issues", integrityHint: "Calculation is blocked when integrity checks fail", payable: "Payable", preview: "Preview calculation", revision: "Revision", breakdownEyebrow: "Time breakdown", breakdownTitle: "What makes up the amount", planned: "Scheduled", worked: "Worked", paidAbsence: "Paid absence", unpaid: "Unpaid time", timeAdjustment: "Time adjustment", payableTime: "Payable time", rules: "Calculation rules", rateCurrency: "Rate and currency", currencyCode: "Currency code", hourlyRate: "Hourly rate", saveRules: "Save rules", moneyHint: "Money is stored in minor currency units. This foundation uses one hourly rate without taxes or coefficients.", basePay: "Base amount", additions: "Additions", deductions: "Deductions", manual: "Manual operations", operations: "Additions and deductions", addition: "Addition", deduction: "Deduction", amount: "Amount", titlePlaceholder: "For example: bonus", notePlaceholder: "Comment", addOperation: "Add operation", snapshots: "Closed calculation", revisions: "Snapshot revisions", calculate: "Save new revision", noPaid: "No paid time", noAdjustments: "No manual adjustments", noSnapshots: "No calculations yet", saveRulesOk: "Payroll rules saved", adjustmentOk: "Money adjustment added", calculateOk: "Payroll revision saved", invalidRate: "Invalid hourly rate", invalidAmount: "Enter a positive amount", periodOpenBlock: "Close the accounting period first", integrityBlock: "Fix ledger integrity issues first", rateBlock: "Set the hourly rate first", ready: "Ready to calculate", perHour: "/ h", loading: "Loading…",
} : {
  foundation: "Payroll Foundation", title: "Расчёт зарплаты", intro: "DutyLog берёт только утверждённый факт из закрытого периода. Каждый расчёт сохраняется отдельной неизменяемой ревизией.", month: "Расчётный месяц", reload: "Обновить", period: "Период", periodClosed: "Период закрыт", periodOpen: "Период открыт", frozen: "Источник времени зафиксирован", live: "Предпросмотр меняется вместе с календарём", integrity: "Целостность", healthy: "Журнал согласован", unhealthy: "Есть расхождения", integrityHint: "Расчёт блокируется при расхождениях", payable: "К выплате", preview: "Предварительный расчёт", revision: "Ревизия", breakdownEyebrow: "Расшифровка времени", breakdownTitle: "Из чего сложилась сумма", planned: "По графику", worked: "Фактически отработано", paidAbsence: "Оплачиваемые отсутствия", unpaid: "Неоплачиваемое время", timeAdjustment: "Корректировка времени", payableTime: "Оплачиваемое время", rules: "Правила расчёта", rateCurrency: "Ставка и валюта", currencyCode: "Код валюты", hourlyRate: "Ставка за час", saveRules: "Сохранить правила", moneyHint: "Денежные значения хранятся в минимальных единицах валюты. Первый релиз использует одну почасовую ставку без налогов и коэффициентов.", basePay: "Базовая сумма", additions: "Начисления", deductions: "Удержания", manual: "Ручные операции", operations: "Начисления и удержания", addition: "Начисление", deduction: "Удержание", amount: "Сумма", titlePlaceholder: "Например: премия", notePlaceholder: "Комментарий", addOperation: "Добавить операцию", snapshots: "Закрытый расчёт", revisions: "Ревизии snapshot", calculate: "Зафиксировать новую ревизию", noPaid: "Нет оплачиваемого времени", noAdjustments: "Нет ручных операций", noSnapshots: "Расчётов пока нет", saveRulesOk: "Правила расчёта сохранены", adjustmentOk: "Денежная операция добавлена", calculateOk: "Ревизия расчёта сохранена", invalidRate: "Некорректная ставка", invalidAmount: "Укажи положительную сумму", periodOpenBlock: "Сначала закрой расчётный период", integrityBlock: "Сначала исправь расхождения журнала", rateBlock: "Сначала укажи почасовую ставку", ready: "Готово к фиксации", perHour: "/ ч", loading: "Загрузка…",
});

const productionText = computed(() => language.value === "en" ? {
  eyebrow: "Production Calendar", title: "Required work norm", intro: "Production Calendar changes the required monthly norm independently from absences and payroll classification.", base: "Base schedule norm", adjustment: "Calendar adjustment", production: "Production norm", coverageWarn: "Schedule coverage is incomplete; the norm is calculated only from known dated schedule entries.", affected: "Calendar rules", noRules: "No production-calendar rules for this month", date: "Date", kind: "Day kind", normal: "Normal", holiday: "Holiday", transferredOff: "Transferred day off", transferredWork: "Transferred workday", shortened: "Shortened day", normEffect: "Norm effect", keepNorm: "Keep base norm", overrideNorm: "Set required norm", normHours: "Required hours", payEffect: "Payroll classification", normalPay: "Normal", holidayPay: "Holiday work", label: "Label", save: "Save day rule", remove: "Remove local override", saved: "Production calendar updated", removed: "Local production-calendar override removed", invalidNorm: "Enter required hours from 0 to 24", custom: "local", baseSource: "base", moneyLater: "Payroll classification is stored independently; v27.45.0 does not change the money formula yet.",
} : {
  eyebrow: "Производственный календарь", title: "Обязательная норма", intro: "Производственный календарь меняет обязательную норму месяца независимо от отсутствий и категории оплаты.", base: "Базовая норма графика", adjustment: "Корректировка календаря", production: "Расчётная норма", coverageWarn: "График заполнен не на весь месяц: норма считается только по известным календарным дням.", affected: "Правила календаря", noRules: "В этом месяце правил производственного календаря пока нет", date: "Дата", kind: "Тип дня", normal: "Обычный", holiday: "Праздник", transferredOff: "Перенесённый выходной", transferredWork: "Перенесённый рабочий день", shortened: "Сокращённый день", normEffect: "Влияние на норму", keepNorm: "Не менять базовую норму", overrideNorm: "Задать обязательную норму", normHours: "Норма дня, ч", payEffect: "Категория оплаты", normalPay: "Обычная", holidayPay: "Праздничная работа", label: "Название / причина", save: "Сохранить правило", remove: "Удалить локальное правило", saved: "Производственный календарь обновлён", removed: "Локальное правило удалено", invalidNorm: "Укажи норму дня от 0 до 24 часов", custom: "локально", baseSource: "база", moneyLater: "Категория оплаты хранится отдельно; v27.45.0 пока не меняет денежную формулу Payroll.",
});

function minutes(value: number | null | undefined): string {
  const total = Math.round(Number(value ?? 0));
  const sign = total < 0 ? "−" : "";
  const safe = Math.abs(total);
  const hours = Math.floor(safe / 60);
  const rest = safe % 60;
  if (language.value === "en") return `${sign}${hours} h${rest ? ` ${rest} min` : ""}`;
  return `${sign}${hours} ч${rest ? ` ${rest} мин` : ""}`;
}

function money(minor: number | null | undefined, code = currency.value): string {
  const value = Number(minor ?? 0) / 100;
  try {
    return new Intl.NumberFormat(language.value === "en" ? "en-US" : "ru-RU", {
      style: "currency", currency: String(code || "RUB").toUpperCase(), minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${value.toFixed(2)} ${String(code || "RUB").toUpperCase()}`;
  }
}

function moneyForMinutes(value: number | null | undefined, rate: number | null | undefined): number {
  return Math.round((Number(value ?? 0) * Number(rate ?? 0)) / 60);
}

function requestErrorMessage(caught: unknown): string {
  if (caught instanceof Error && caught.message) return caught.message;
  return error.value || (language.value === "en" ? "Request failed" : "Не удалось выполнить запрос");
}

const breakdown = computed(() => {
  const current = preview.value;
  if (!current) return [];
  return [
    { key: "worked", label: text.value.worked, value: current.workedMinutes },
    { key: "vacation", label: language.value === "en" ? "Paid vacation" : "Оплачиваемый отпуск", value: current.vacationMinutes },
    { key: "sick", label: language.value === "en" ? "Sick leave" : "Больничный", value: current.sickMinutes },
    { key: "overtime", label: language.value === "en" ? "Overtime time-off" : "Отгул из переработок", value: current.overtimeCompensatedMinutes },
    { key: "adjustment", label: text.value.timeAdjustment, value: current.timeAdjustmentMinutes },
  ].filter(item => Number(item.value) !== 0).map(item => ({ ...item, amount: moneyForMinutes(item.value, current.hourlyRateMinor) }));
});

const blocking = computed(() => {
  switch (period.value?.blockingReason) {
    case "PERIOD_OPEN": return text.value.periodOpenBlock;
    case "LEDGER_INTEGRITY_FAILED": return text.value.integrityBlock;
    case "PAYROLL_RATE_REQUIRED": return text.value.rateBlock;
    default: return "";
  }
});

function synchronizeInputs(): void {
  const settings = period.value?.settings;
  if (!settings) return;
  currencyCode.value = settings.currencyCode || "RUB";
  hourlyRate.value = (Number(settings.hourlyRateMinor ?? 0) / 100).toFixed(2);
}

function synchronizeProductionEditor(): void {
  const day = productionCalendar.value?.days.find(item => item.date === productionDate.value);
  if (!day || day.sourceType === "NONE") {
    productionKind.value = "NORMAL";
    productionScheduleEffect.value = "NONE";
    productionNormHours.value = "";
    productionPayrollEffect.value = "NONE";
    productionLabel.value = "";
    return;
  }
  productionKind.value = day.dayKind;
  productionScheduleEffect.value = day.scheduleEffect;
  productionNormHours.value = day.normMinutesOverride == null ? "" : String(Number(day.normMinutesOverride) / 60);
  productionPayrollEffect.value = day.payrollEffect;
  productionLabel.value = day.label ?? "";
}

function onProductionKindChange(): void {
  const baseMinutes = Number(selectedProductionDay.value?.baseNormMinutes ?? 0);
  switch (productionKind.value) {
    case "HOLIDAY":
      productionScheduleEffect.value = "NORM_OVERRIDE"; productionNormHours.value = "0"; productionPayrollEffect.value = "HOLIDAY"; break;
    case "TRANSFERRED_DAY_OFF":
      productionScheduleEffect.value = "NORM_OVERRIDE"; productionNormHours.value = "0"; productionPayrollEffect.value = "NONE"; break;
    case "TRANSFERRED_WORKDAY":
    case "SHORTENED_DAY":
      productionScheduleEffect.value = "NORM_OVERRIDE"; productionNormHours.value = baseMinutes > 0 ? String(baseMinutes / 60) : ""; productionPayrollEffect.value = "NONE"; break;
    default:
      productionScheduleEffect.value = "NONE"; productionNormHours.value = ""; productionPayrollEffect.value = "NONE";
  }
}

function productionKindLabel(kind: string): string {
  switch (kind) {
    case "HOLIDAY": return productionText.value.holiday;
    case "TRANSFERRED_DAY_OFF": return productionText.value.transferredOff;
    case "TRANSFERRED_WORKDAY": return productionText.value.transferredWork;
    case "SHORTENED_DAY": return productionText.value.shortened;
    default: return productionText.value.normal;
  }
}

function selectProductionDay(date: string): void {
  productionDate.value = date;
  synchronizeProductionEditor();
}

async function saveProductionDay(): Promise<void> {
  let normMinutesOverride: number | null = null;
  if (productionScheduleEffect.value === "NORM_OVERRIDE") {
    const hours = Number(productionNormHours.value);
    if (!Number.isFinite(hours) || hours < 0 || hours > 24) {
      message.value = productionText.value.invalidNorm; messageOk.value = false; return;
    }
    normMinutesOverride = Math.round(hours * 60);
  }
  try {
    await store.saveProductionDay(productionDate.value, {
      dayKind: productionKind.value, scheduleEffect: productionScheduleEffect.value, normMinutesOverride,
      payrollEffect: productionPayrollEffect.value, label: productionLabel.value.trim() || null,
    });
    synchronizeProductionEditor();
    message.value = productionText.value.saved; messageOk.value = true;
  } catch (caught) { message.value = requestErrorMessage(caught); messageOk.value = false; }
}

async function removeProductionDay(): Promise<void> {
  try {
    await store.deleteProductionDay(productionDate.value);
    synchronizeProductionEditor();
    message.value = productionText.value.removed; messageOk.value = true;
  } catch (caught) { message.value = requestErrorMessage(caught); messageOk.value = false; }
}

async function refresh(): Promise<void> {
  if (!payrollEnabled.value) return;
  try {
    await store.load(month.value);
    if (!productionDate.value.startsWith(`${month.value}-`)) productionDate.value = `${month.value}-01`;
    synchronizeInputs();
    synchronizeProductionEditor();
  } catch {
    message.value = error.value;
    messageOk.value = false;
  }
}

async function saveSettings(): Promise<void> {
  const rate = Number(hourlyRate.value);
  if (!Number.isFinite(rate) || rate < 0) { message.value = text.value.invalidRate; messageOk.value = false; return; }
  try {
    await store.saveSettings({ currencyCode: currencyCode.value.trim().toUpperCase() || "RUB", hourlyRateMinor: Math.round(rate * 100) });
    synchronizeInputs();
    message.value = text.value.saveRulesOk; messageOk.value = true;
  } catch { message.value = error.value; messageOk.value = false; }
}

async function addAdjustment(): Promise<void> {
  const amount = Number(adjustmentAmount.value);
  if (!Number.isFinite(amount) || amount <= 0) { message.value = text.value.invalidAmount; messageOk.value = false; return; }
  const title = adjustmentTitle.value.trim();
  if (!title) { message.value = text.value.titlePlaceholder; messageOk.value = false; return; }
  try {
    await store.addAdjustment({ month: month.value, adjustmentType: adjustmentType.value, amountMinor: Math.round(amount * 100), title, note: adjustmentNote.value.trim() || null });
    adjustmentAmount.value = ""; adjustmentTitle.value = ""; adjustmentNote.value = "";
    message.value = text.value.adjustmentOk; messageOk.value = true;
  } catch { message.value = error.value; messageOk.value = false; }
}

async function calculate(): Promise<void> {
  try { await store.calculate(); message.value = text.value.calculateOk; messageOk.value = true; }
  catch { message.value = error.value; messageOk.value = false; }
}

async function synchronizeRoute(route: string): Promise<void> {
  if (route === "payroll" && payrollEnabled.value) await refresh();
}

onMounted(() => {
  previousDomain = window.DutyLogVueDomains?.payroll;
  const domain: DutyLogPayrollDomain = Object.freeze({ ready: () => store.loaded && !store.loading, refresh });
  window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), payroll: domain });
  document.documentElement.dataset.vuePayroll = "ready";
  void synchronizeRoute(activeRoute.value);
});

watch(activeRoute, route => { void synchronizeRoute(route); });
watch([modulesLoaded, modules], () => { if (activeRoute.value === "payroll") void refresh(); }, { deep: true });
watch(productionDate, () => synchronizeProductionEditor());

onBeforeUnmount(() => {
  if (previousDomain) window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), payroll: previousDomain });
  else if (window.DutyLogVueDomains) { const { payroll: _removed, ...rest } = window.DutyLogVueDomains; window.DutyLogVueDomains = Object.freeze(rest); }
  delete document.documentElement.dataset.vuePayroll;
});
</script>

<template>
  <section v-if="activeRoute === 'payroll'" id="view-payroll" class="view payrollView" data-vue-domain-route="payroll" data-vue-domain-owner="payroll">
    <div class="payrollWorkspace">
      <header class="payrollHero card" aria-labelledby="payrollTitle">
        <div><div class="eyebrow">{{ text.foundation }}</div><h2 id="payrollTitle">{{ text.title }}</h2><p>{{ text.intro }}</p></div>
        <div class="payrollHeroControls"><label>{{ text.month }}<input id="payrollMonth" v-model="month" type="month" @change="refresh"/></label><button id="payrollReload" type="button" :disabled="loading" @click="refresh">{{ text.reload }}</button></div>
      </header>

      <section class="payrollStatusRow" aria-label="Статус источника расчёта">
        <div class="payrollStatusCard"><span>{{ text.period }}</span><b id="payrollPeriodStatus" :class="period?.periodClosed ? 'ok' : 'warn'">{{ period?.periodClosed ? text.periodClosed : text.periodOpen }}</b><small id="payrollPeriodHint">{{ period?.periodClosed ? text.frozen : text.live }}</small></div>
        <div class="payrollStatusCard"><span>{{ text.integrity }}</span><b id="payrollIntegrityStatus" :class="period?.integrityHealthy ? 'ok' : 'bad'">{{ period?.integrityHealthy ? text.healthy : text.unhealthy }}</b><small>{{ text.integrityHint }}</small></div>
        <div class="payrollStatusCard total"><span>{{ text.payable }}</span><b id="payrollTotal">{{ money(preview?.totalPayMinor) }}</b><small id="payrollRevisionLabel">{{ period?.latestSnapshot ? `${text.revision} ${period.latestSnapshot.revision} · ${money(period.latestSnapshot.totalPayMinor, period.latestSnapshot.currencyCode)}` : text.preview }}</small></div>
      </section>

      <section class="card productionCalendarCard" data-production-calendar-foundation>
        <div class="payrollSectionHead">
          <div><div class="eyebrow">{{ productionText.eyebrow }}</div><h3>{{ productionText.title }}</h3><p class="productionCalendarIntro">{{ productionText.intro }}</p></div>
        </div>
        <div class="productionNormMetrics">
          <div><span>{{ productionText.base }}</span><b id="productionBaseNorm">{{ minutes(productionCalendar?.baseNormMinutes) }}</b></div>
          <div><span>{{ productionText.adjustment }}</span><b id="productionNormAdjustment">{{ minutes(productionCalendar?.adjustmentMinutes) }}</b></div>
          <div class="accent"><span>{{ productionText.production }}</span><b id="productionNorm">{{ minutes(productionCalendar?.productionNormMinutes) }}</b></div>
        </div>
        <p v-if="productionCalendar && !productionCalendar.scheduleCoverageComplete" class="productionCoverageWarning" id="productionCoverageWarning">{{ productionText.coverageWarn }} · {{ productionCalendar.scheduleCoverageDays }}/{{ productionCalendar.days.length }}</p>
        <div class="productionCalendarGrid">
          <form class="productionCalendarForm" id="productionCalendarForm" @submit.prevent="saveProductionDay">
            <label>{{ productionText.date }}<input id="productionCalendarDate" v-model="productionDate" type="date" required/></label>
            <label>{{ productionText.kind }}
              <select id="productionCalendarKind" v-model="productionKind" @change="onProductionKindChange">
                <option value="NORMAL">{{ productionText.normal }}</option><option value="HOLIDAY">{{ productionText.holiday }}</option><option value="TRANSFERRED_DAY_OFF">{{ productionText.transferredOff }}</option><option value="TRANSFERRED_WORKDAY">{{ productionText.transferredWork }}</option><option value="SHORTENED_DAY">{{ productionText.shortened }}</option>
              </select>
            </label>
            <label>{{ productionText.normEffect }}
              <select id="productionCalendarScheduleEffect" v-model="productionScheduleEffect">
                <option value="NONE">{{ productionText.keepNorm }}</option><option value="NORM_OVERRIDE">{{ productionText.overrideNorm }}</option>
              </select>
            </label>
            <label v-if="productionScheduleEffect === 'NORM_OVERRIDE'">{{ productionText.normHours }}<input id="productionCalendarNormHours" v-model="productionNormHours" type="number" min="0" max="24" step="0.25" inputmode="decimal" required/></label>
            <label>{{ productionText.payEffect }}
              <select id="productionCalendarPayrollEffect" v-model="productionPayrollEffect"><option value="NONE">{{ productionText.normalPay }}</option><option value="HOLIDAY">{{ productionText.holidayPay }}</option></select>
            </label>
            <label class="productionCalendarLabel">{{ productionText.label }}<input id="productionCalendarLabel" v-model="productionLabel" maxlength="120"/></label>
            <div class="productionCalendarActions"><button class="primary" type="submit" :disabled="loading">{{ productionText.save }}</button><button type="button" :disabled="loading || !selectedProductionDay?.localOverride" @click="removeProductionDay">{{ productionText.remove }}</button></div>
            <p class="payrollHint">{{ productionText.moneyLater }}</p>
          </form>
          <div class="productionCalendarRules">
            <div class="payrollSectionHead compact"><div><div class="eyebrow">{{ productionText.affected }}</div></div><b>{{ productionAffectedDays.length }}</b></div>
            <button v-for="day in productionAffectedDays" :key="day.date" type="button" class="productionCalendarRule" :class="{ active: day.date === productionDate }" @click="selectProductionDay(day.date)">
              <span><b>{{ new Date(`${day.date}T12:00:00`).toLocaleDateString(language === 'en' ? 'en-US' : 'ru-RU', { day:'numeric', month:'short' }) }} · {{ productionKindLabel(day.dayKind) }}</b><small>{{ day.label || (day.localOverride ? productionText.custom : productionText.baseSource) }}</small></span>
              <strong>{{ minutes(day.baseNormMinutes) }} → {{ minutes(day.productionNormMinutes) }}</strong>
            </button>
            <div v-if="!productionAffectedDays.length" class="payrollEmpty">{{ productionText.noRules }}</div>
          </div>
        </div>
      </section>

      <div class="payrollGrid">
        <section class="card payrollSummaryCard">
          <div class="payrollSectionHead"><div><div class="eyebrow">{{ text.breakdownEyebrow }}</div><h3>{{ text.breakdownTitle }}</h3></div><span id="payrollCurrencyBadge">{{ currency }}</span></div>
          <div class="payrollMetricGrid">
            <div><span>{{ text.planned }}</span><b id="payrollPlanned">{{ minutes(preview?.plannedMinutes) }}</b></div><div><span>{{ text.worked }}</span><b id="payrollWorked">{{ minutes(preview?.workedMinutes) }}</b></div><div><span>{{ text.paidAbsence }}</span><b id="payrollPaidAbsence">{{ minutes(preview?.paidAbsenceMinutes) }}</b></div><div><span>{{ text.unpaid }}</span><b id="payrollUnpaid">{{ minutes(preview?.unpaidMinutes) }}</b></div><div><span>{{ text.timeAdjustment }}</span><b id="payrollTimeAdjustment">{{ minutes(preview?.timeAdjustmentMinutes) }}</b></div><div class="accent"><span>{{ text.payableTime }}</span><b id="payrollPayable">{{ minutes(preview?.payableMinutes) }}</b></div>
          </div>
          <div class="payrollBreakdown" id="payrollBreakdown"><div v-for="item in breakdown" :key="item.key" class="payrollBreakdownRow" :class="item.key"><span><b>{{ item.label }}</b><small>{{ minutes(item.value) }}</small></span><strong>{{ money(item.amount) }}</strong></div><div v-if="!breakdown.length" class="payrollEmpty">{{ text.noPaid }}</div></div>
        </section>

        <section class="card payrollSettingsCard">
          <div class="payrollSectionHead"><div><div class="eyebrow">{{ text.rules }}</div><h3>{{ text.rateCurrency }}</h3></div></div>
          <form id="payrollSettingsForm" class="payrollSettingsForm" @submit.prevent="saveSettings"><label>{{ text.currencyCode }}<input id="payrollCurrency" v-model="currencyCode" maxlength="3" pattern="[A-Za-z]{3}" required/></label><label>{{ text.hourlyRate }}<input id="payrollHourlyRate" v-model="hourlyRate" type="number" min="0" max="10000000" step="0.01" required/></label><button class="primary" type="submit" :disabled="loading">{{ text.saveRules }}</button></form>
          <p class="payrollHint">{{ text.moneyHint }}</p><div class="payrollMoneySummary"><div><span>{{ text.basePay }}</span><b id="payrollBasePay">{{ money(preview?.basePayMinor) }}</b></div><div><span>{{ text.additions }}</span><b id="payrollAdditions">{{ money(preview?.additionsMinor) }}</b></div><div><span>{{ text.deductions }}</span><b id="payrollDeductions">{{ money(preview?.deductionsMinor) }}</b></div></div>
        </section>
      </div>

      <div class="payrollGrid payrollLowerGrid">
        <section class="card payrollAdjustmentsCard"><div class="payrollSectionHead"><div><div class="eyebrow">{{ text.manual }}</div><h3>{{ text.operations }}</h3></div></div><form id="payrollAdjustmentForm" class="payrollAdjustmentForm" @submit.prevent="addAdjustment"><select id="payrollAdjustmentType" v-model="adjustmentType" :disabled="!period?.periodClosed || loading"><option value="ADDITION">{{ text.addition }}</option><option value="DEDUCTION">{{ text.deduction }}</option></select><input id="payrollAdjustmentAmount" v-model="adjustmentAmount" type="number" min="0.01" max="10000000000" step="0.01" :placeholder="text.amount" required :disabled="!period?.periodClosed || loading"/><input id="payrollAdjustmentTitle" v-model="adjustmentTitle" maxlength="120" :placeholder="text.titlePlaceholder" required :disabled="!period?.periodClosed || loading"/><input id="payrollAdjustmentNote" v-model="adjustmentNote" maxlength="500" :placeholder="text.notePlaceholder" :disabled="!period?.periodClosed || loading"/><button type="submit" :disabled="!period?.periodClosed || loading">{{ text.addOperation }}</button></form><div class="payrollAdjustmentList" id="payrollAdjustmentList"><article v-for="item in period?.adjustments ?? []" :key="item.id" class="payrollAdjustmentItem" :class="item.adjustmentType === 'DEDUCTION' ? 'deduction' : 'addition'"><span><b>{{ item.title }}</b><small>{{ item.note || (item.adjustmentType === 'DEDUCTION' ? text.deduction : text.addition) }}</small></span><strong>{{ item.adjustmentType === 'DEDUCTION' ? '−' : '+' }}{{ money(item.amountMinor) }}</strong></article><div v-if="!(period?.adjustments.length)" class="payrollEmpty">{{ text.noAdjustments }}</div></div></section>

        <section class="card payrollSnapshotCard"><div class="payrollSectionHead"><div><div class="eyebrow">{{ text.snapshots }}</div><h3>{{ text.revisions }}</h3></div></div><div class="payrollBlocking" id="payrollBlocking" :class="blocking ? 'blocked' : 'ready'">{{ blocking || `${text.ready} · ${money(preview?.hourlyRateMinor, currency)} ${text.perHour}` }}</div><button class="primary payrollCalculate" id="payrollCalculate" type="button" :disabled="!period?.canCalculate || loading" @click="calculate">{{ text.calculate }}</button><div class="payrollSnapshotList" id="payrollSnapshotList"><article v-for="item in period?.snapshots ?? []" :key="item.id" class="payrollSnapshotItem" :class="item.supersededById ? 'superseded' : 'current'"><span><b>{{ text.revision }} {{ item.revision }}</b><small>{{ new Date(item.createdAt).toLocaleString(language === 'en' ? 'en-US' : 'ru-RU') }} · {{ item.calculationHash.slice(0,10) }}</small></span><strong>{{ money(item.totalPayMinor, item.currencyCode) }}</strong></article><div v-if="!(period?.snapshots.length)" class="payrollEmpty">{{ text.noSnapshots }}</div></div></section>
      </div>
      <div class="appModalMessage payrollMessage" id="payrollMessage" :class="message ? (messageOk ? 'ok' : 'err') : ''" role="status" aria-live="polite">{{ message || (loading ? text.loading : error) }}</div>
    </div>
  </section>
</template>
