<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { usePayrollStore } from "../stores/payrollStore";
import type { DutyLogPayrollDomain } from "../types/domain";
import { navigateHashRoute } from "@/platform/router/hashRoute";

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

const payrollEnabled = computed(() => modulesLoaded.value && modules.value.payroll !== false);
const preview = computed(() => period.value?.preview ?? null);
const productionCalendar = computed(() => period.value?.productionCalendar ?? null);
const productionAffectedDays = computed(() => productionCalendar.value?.days.filter(day => day.sourceType !== "NONE") ?? []);
const currency = computed(() => preview.value?.currencyCode || period.value?.settings.currencyCode || "RUB");

const text = computed(() => language.value === "en" ? {
  foundation: "Payroll Foundation", title: "Payroll calculation", intro: "DutyLog uses only approved facts from a closed period. Every calculation is stored as an immutable revision.", month: "Accounting month", reload: "Refresh", period: "Period", periodClosed: "Period is closed", periodOpen: "Period is open", frozen: "The time source is frozen", live: "Preview changes with the calendar", integrity: "Integrity", healthy: "Ledger is healthy", unhealthy: "Ledger has issues", integrityHint: "Calculation is blocked when integrity checks fail", payable: "Payable", preview: "Preview calculation", revision: "Revision", breakdownEyebrow: "Time breakdown", breakdownTitle: "What makes up the amount", planned: "Scheduled", worked: "Worked", paidAbsence: "Paid absence", unpaid: "Unpaid time", timeAdjustment: "Time adjustment", payableTime: "Payable time", rules: "Calculation rules", rateCurrency: "Rate and currency", currencyCode: "Currency code", hourlyRate: "Hourly rate", saveRules: "Save rules", moneyHint: "Money is stored in minor currency units. This foundation uses one hourly rate without taxes or coefficients.", basePay: "Base amount", additions: "Additions", deductions: "Deductions", manual: "Manual operations", operations: "Additions and deductions", addition: "Addition", deduction: "Deduction", amount: "Amount", titlePlaceholder: "For example: bonus", notePlaceholder: "Comment", addOperation: "Add operation", snapshots: "Closed calculation", revisions: "Snapshot revisions", calculate: "Save new revision", noPaid: "No paid time", noAdjustments: "No manual adjustments", noSnapshots: "No calculations yet", saveRulesOk: "Payroll rules saved", adjustmentOk: "Money adjustment added", calculateOk: "Payroll revision saved", invalidRate: "Invalid hourly rate", invalidAmount: "Enter a positive amount", periodOpenBlock: "Close the accounting period first", integrityBlock: "Fix ledger integrity issues first", rateBlock: "Set the hourly rate first", ready: "Ready to calculate", perHour: "/ h", loading: "Loading…",
} : {
  foundation: "Payroll Foundation", title: "Расчёт зарплаты", intro: "DutyLog берёт только утверждённый факт из закрытого периода. Каждый расчёт сохраняется отдельной неизменяемой ревизией.", month: "Расчётный месяц", reload: "Обновить", period: "Период", periodClosed: "Период закрыт", periodOpen: "Период открыт", frozen: "Источник времени зафиксирован", live: "Предпросмотр меняется вместе с календарём", integrity: "Целостность", healthy: "Журнал согласован", unhealthy: "Есть расхождения", integrityHint: "Расчёт блокируется при расхождениях", payable: "К выплате", preview: "Предварительный расчёт", revision: "Ревизия", breakdownEyebrow: "Расшифровка времени", breakdownTitle: "Из чего сложилась сумма", planned: "Обязательная норма", worked: "Фактически отработано", paidAbsence: "Оплачиваемые отсутствия", unpaid: "Неоплачиваемое время", timeAdjustment: "Корректировка времени", payableTime: "Оплачиваемое время", rules: "Правила расчёта", rateCurrency: "Ставка и валюта", currencyCode: "Код валюты", hourlyRate: "Ставка за час", saveRules: "Сохранить правила", moneyHint: "Денежные значения хранятся в минимальных единицах валюты. Первый релиз использует одну почасовую ставку без налогов и коэффициентов.", basePay: "Базовая сумма", additions: "Начисления", deductions: "Удержания", manual: "Ручные операции", operations: "Начисления и удержания", addition: "Начисление", deduction: "Удержание", amount: "Сумма", titlePlaceholder: "Например: премия", notePlaceholder: "Комментарий", addOperation: "Добавить операцию", snapshots: "Закрытый расчёт", revisions: "Ревизии snapshot", calculate: "Зафиксировать новую ревизию", noPaid: "Нет оплачиваемого времени", noAdjustments: "Нет ручных операций", noSnapshots: "Расчётов пока нет", saveRulesOk: "Правила расчёта сохранены", adjustmentOk: "Денежная операция добавлена", calculateOk: "Ревизия расчёта сохранена", invalidRate: "Некорректная ставка", invalidAmount: "Укажи положительную сумму", periodOpenBlock: "Сначала закрой расчётный период", integrityBlock: "Сначала исправь расхождения журнала", rateBlock: "Сначала укажи почасовую ставку", ready: "Готово к фиксации", perHour: "/ ч", loading: "Загрузка…",
});

const productionText = computed(() => language.value === "en" ? {
  eyebrow: "Production Calendar", title: "Required work norm", intro: "The calendar is now edited from the actual day. Payroll only explains the monthly result.", base: "Base schedule norm", adjustment: "Calendar adjustment", production: "Production norm", coverageWarn: "Schedule coverage is incomplete; the norm is calculated only from known dated schedule entries.", affected: "Calendar rules", noRules: "No production-calendar rules for this month", custom: "local", baseSource: "base", openCalendar: "Open calendar",
} : {
  eyebrow: "Производственный календарь", title: "Обязательная норма", intro: "Теперь особые дни редактируются там, где они происходят — в календаре. Payroll только объясняет итог месяца.", base: "Базовая норма графика", adjustment: "Корректировка календаря", production: "Расчётная норма", coverageWarn: "График заполнен не на весь месяц: норма считается только по известным календарным дням.", affected: "Правила календаря", noRules: "В этом месяце правил производственного календаря пока нет", custom: "локально", baseSource: "база", openCalendar: "Открыть календарь",
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

function openCalendar(): void { navigateHashRoute("calendar"); }

async function refresh(): Promise<void> {
  if (!payrollEnabled.value) return;
  try {
    await store.load(month.value);
    synchronizeInputs();
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
        <div class="productionCalendarSummaryOnly" data-production-calendar-summary-only>
          <div class="productionCalendarRules">
            <div class="payrollSectionHead compact"><div><div class="eyebrow">{{ productionText.affected }}</div></div><b>{{ productionAffectedDays.length }}</b></div>
            <article v-for="day in productionAffectedDays" :key="day.date" class="productionCalendarRule productionCalendarRule--summary">
              <span><b>{{ new Date(`${day.date}T12:00:00`).toLocaleDateString(language === 'en' ? 'en-US' : 'ru-RU', { day:'numeric', month:'short' }) }}</b><small>{{ day.label || (day.localOverride ? productionText.custom : productionText.baseSource) }}</small></span>
              <strong>{{ minutes(day.baseNormMinutes) }} → {{ minutes(day.productionNormMinutes) }}</strong>
            </article>
            <div v-if="!productionAffectedDays.length" class="payrollEmpty">{{ productionText.noRules }}</div>
          </div>
          <div class="productionCalendarNativeHint"><p>{{ productionText.intro }}</p><button type="button" @click="openCalendar">{{ productionText.openCalendar }}</button></div>
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
