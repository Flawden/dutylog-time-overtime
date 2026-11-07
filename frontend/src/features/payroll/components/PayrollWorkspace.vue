<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { usePayrollStore } from "../stores/payrollStore";
import type {
  DutyLogPayrollDomain,
  PayrollCompensationComponentLine,
  PayrollCompensationTermInput,
} from "../types/domain";
import { navigateHashRoute } from "@/platform/router/hashRoute";
import Article153PaymentDetails from "./Article153PaymentDetails.vue";
import { ordinaryPremiumMessage } from "../presentation/ordinaryPremiumMessage";
import HolidayReviewCard from "./HolidayReviewCard.vue";
import RestDayElectionCard from "./RestDayElectionCard.vue";
import PricingSettingsCard from "./PricingSettingsCard.vue";
import CompensationComponentsCard from "./CompensationComponentsCard.vue";
import PayrollMonthPicker from "./PayrollMonthPicker.vue";

const shell = useShellStore(); const store = usePayrollStore();
const { activeRoute, language, modulesLoaded, modules } = storeToRefs(shell); const { period, loading, error } = storeToRefs(store);
let previousDomain: DutyLogPayrollDomain | undefined;
function defaultMonth(): string { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}`; }
const month = ref(defaultMonth()); const effectiveMonth = ref(defaultMonth());
const payMode = ref<"HOURLY"|"SALARY">("HOURLY"); const currencyCode = ref("RUB"); const compensationAmount = ref("");
const adjustmentType = ref<"ADDITION"|"DEDUCTION">("ADDITION"); const adjustmentAmount = ref("");
const adjustmentTitle = ref(""); const adjustmentNote = ref(""); const message = ref(""); const messageOk = ref(false);
const payrollEnabled = computed(() => modulesLoaded.value && modules.value.payroll !== false);
const preview = computed(() => period.value?.preview ?? null); const productionCalendar = computed(() => period.value?.productionCalendar ?? null);
const productionAffectedDays = computed(() => productionCalendar.value?.days.filter(day => day.sourceType !== "NONE") ?? []);
const effectiveCompensation = computed(() => period.value?.effectiveCompensation ?? null);
const compensationHistory = computed(() => period.value?.compensationHistory ?? []);
const currency = computed(() => preview.value?.currencyCode || effectiveCompensation.value?.currencyCode || period.value?.settings.currencyCode || "RUB");
const salaryMode = computed(() => preview.value?.payMode === "SALARY");
type PayrollTab = "overview" | "earnings" | "settings" | "history";
const activeTab = ref<PayrollTab>("overview");
type SettingsSection = "terms" | "components" | "rules" | "norm";
const settingsSection = ref<SettingsSection | null>(null);
function openSettingsSection(section: SettingsSection): void {
  settingsSection.value = section;
}
function closeSettingsSection(): void {
  settingsSection.value = null;
}
const settingsDetailTitle = computed(() => {
  const labels: Record<SettingsSection, { ru: string; en: string }> = {
    terms: { ru: "Условия оплаты", en: "Pay terms" },
    components: { ru: "Доплаты и надбавки", en: "Allowances and bonuses" },
    rules: { ru: "Правила оплаты", en: "Pay rules" },
    norm: { ru: "Норма времени", en: "Working-time norm" },
  };
  const label = settingsSection.value ? labels[settingsSection.value] : null;
  return language.value === "en" ? label?.en ?? "Settings" : label?.ru ?? "Настройки";
});
const settingsDetailDescription = computed(() => {
  const descriptions: Record<SettingsSection, { ru: string; en: string }> = {
    terms: { ru: "Оклад, ставка, валюта и история условий", en: "Salary, hourly rate, currency and pay history" },
    components: { ru: "Премии, коэффициенты и дополнительные выплаты", en: "Bonuses, coefficients and other payments" },
    rules: { ru: "Оплата ночной работы, праздников и переработок", en: "Night, holiday and overtime payment settings" },
    norm: { ru: "Производственный календарь и расчётная норма", en: "Production calendar and working-time norm" },
  };
  const description = settingsSection.value ? descriptions[settingsSection.value] : null;
  return language.value === "en" ? description?.en ?? "" : description?.ru ?? "";
});
const readableMonth = computed(() => {
  const match = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(month.value);
  if (!match) return month.value;
  const monthName = new Intl.DateTimeFormat(language.value === "en" ? "en-US" : "ru-RU", { month: "long", timeZone: "UTC" })
    .format(new Date(Date.UTC(2024, Number(match[2]) - 1, 1)));
  return `${monthName.charAt(0).toUpperCase()}${monthName.slice(1)} ${match[1]}`;
});
const payrollTabs: { key: PayrollTab; ru: string; en: string }[] = [
  { key: "overview", ru: "Обзор", en: "Overview" },
  { key: "earnings", ru: "Начисления", en: "Earnings" },
  { key: "settings", ru: "Настройки", en: "Settings" },
  { key: "history", ru: "История", en: "History" },
];
function tabLabel(key: PayrollTab): string {
  const tab = payrollTabs.find(item => item.key === key);
  return (language.value === "en" ? tab?.en : tab?.ru) || key;
}

const text = computed(() => language.value === "en" ? {
  foundation:"Native Payroll",title:"Payroll",intro:"Wages from your pay terms, recorded time and explicit overtime payments.",month:"Accounting month",reload:"Refresh",period:"Period",periodClosed:"Period is closed",periodOpen:"Period is open",frozen:"The time source is frozen",live:"Preview changes with Day Truth",integrity:"Integrity",healthy:"Ledger is healthy",unhealthy:"Ledger has issues",payable:"Payroll calculation",totalPay:"Calculated total",totalDisclaimer:"Income tax, advances and actual payouts are not calculated automatically. Manually entered deductions are included.",preview:"Preview",revision:"Revision",breakdown:"Time behind the amount",planned:"Required norm",worked:"Worked",paidAbsence:"Paid absence",unpaid:"Unpaid time",timeAdjustment:"Time adjustment",payableTime:"Canonical payable time",ordinaryBaseTime:"Ordinary base time",setup:"How are you paid?",hourly:"Hourly rate",salary:"Monthly salary",effective:"Effective from month",currencyCode:"Currency",amount:"Amount",save:"Save compensation",history:"Compensation history",remove:"Delete",salaryHint:"Salary covers the required norm according to coverage. Explicit overtime cash settlements are added separately.",hourlyHint:"Ordinary hourly base excludes banked overtime. Overtime becomes money only through an explicit cash settlement.",effectiveRate:"Effective hourly value",coverage:"Salary-covered norm",basePay:"Ordinary base pay",ordinaryPremium:"Night / holiday premium",settlement:"Overtime settlement",settlementTransactions:"Settlement transactions",settlementBase:"Settlement base",settlementPremium:"Settlement premiums",settlementTotal:"Overtime paid",settlementReady:"Settlement pricing is ready",settlementNone:"No overtime cash settlements in this payroll month",settlementProvenanceBlock:"Some settled overtime minutes have no canonical provenance, so DutyLog cannot price them automatically.",settlementRulesBlock:"Pricing rules are missing for the historical source date of part of this settlement.",settlementCurrencyBlock:"Historical settlement sources resolve to incompatible currencies.",settlementPayrollCurrencyBlock:"The overtime settlement currency does not match this payroll month's compensation currency.",settlementGenericBlock:"Overtime settlement pricing is not ready.",technicalDetails:"Technical calculation identity",fingerprint:"Pricing fingerprint",additions:"Additions",deductions:"Deductions",manual:"Manual operations",operations:"Additions and deductions",addition:"Addition",deduction:"Deduction",titlePlaceholder:"For example: bonus",notePlaceholder:"Comment",addOperation:"Add operation",snapshots:"Closed calculation",revisions:"Snapshot revisions",calculate:"Save new revision",noAdjustments:"No manual adjustments",noSnapshots:"No calculations yet",periodOpenBlock:"Close the accounting period first",integrityBlock:"Fix ledger integrity issues first",compensationBlock:"Set how you are paid first",coverageBlock:"Salary mode needs complete schedule coverage for the month",normBlock:"Salary mode needs a positive production norm",ready:"Ready to calculate",included:"Included in salary base",loading:"Loading…",openCalendar:"Open calendar",production:"Production norm",base:"Base schedule norm",adjustment:"Calendar adjustment",rules:"Calendar rules",noRules:"No production-calendar rules",coverageWarn:"Schedule coverage is incomplete; norm uses known dates only.",saved:"Compensation saved",deleted:"Compensation term deleted",adjustmentOk:"Money adjustment added",calculateOk:"Payroll revision saved",compensationComponents:"Allowances and bonuses",componentCount:"components",componentFixed:"Fixed amount",componentNominalBase:"Monthly salary",componentEarnedBase:"Earned base pay",componentEligibleBase:"Eligible earnings",componentCurrencyBlock:"A compensation component uses a currency that does not match this payroll month. Update that component's currency before calculating payroll.",componentBaseBlock:"A percentage component cannot resolve its selected base. Monthly salary as a base requires salary pay mode.",componentInvalidBlock:"A compensation component has an invalid calculation configuration. Review its formula, amount or percentage.",componentGenericBlock:"Compensation component calculation is not ready.",componentFingerprint:"Compensation component fingerprint",invalidAmount:"Enter a positive amount",
} : {
  foundation:"Native Payroll",title:"Расчёт зарплаты",intro:"Зарплата по условиям оплаты, учтённому времени и выплатам переработки.",month:"Расчётный месяц",reload:"Обновить",period:"Период",periodClosed:"Период закрыт",periodOpen:"Период открыт",frozen:"Источник времени зафиксирован",live:"Предпросмотр меняется вместе с Day Truth",integrity:"Целостность",healthy:"Журнал согласован",unhealthy:"Есть расхождения",payable:"Расчёт начислений",totalPay:"Итог расчёта",totalDisclaimer:"НДФЛ, авансы и фактические выплаты автоматически не рассчитываются. Внесённые вручную удержания учитываются.",preview:"Предпросмотр",revision:"Ревизия",breakdown:"Из какого времени получилась сумма",planned:"Обязательная норма",worked:"Фактически отработано",paidAbsence:"Оплачиваемые отсутствия",unpaid:"Неоплачиваемое время",timeAdjustment:"Корректировка времени",payableTime:"Каноническое оплачиваемое время",ordinaryBaseTime:"Время обычной оплаты",setup:"Как тебе платят?",hourly:"Почасовая ставка",salary:"Оклад",effective:"Действует с месяца",currencyCode:"Валюта",amount:"Сумма",save:"Сохранить условия",history:"История условий",remove:"Удалить",salaryHint:"Оклад покрывает обязательную норму согласно фактическому покрытию. Явные денежные выплаты переработки добавляются отдельно.",hourlyHint:"Обычная почасовая база не включает переработку, оставленную в банке. Она превращается в деньги только через явную выплату.",effectiveRate:"Расчётная стоимость часа",coverage:"Норма, покрытая окладом",basePay:"Обычная оплата",ordinaryPremium:"Ночная / праздничная доплата",settlement:"Выплата переработки",settlementTransactions:"Операций выплаты",settlementBase:"Базовая часть выплаты",settlementPremium:"Надбавки",settlementTotal:"Выплата за переработку",settlementReady:"Расчёт выплаты переработки готов",settlementNone:"В этом расчётном месяце нет денежных выплат переработки",settlementProvenanceBlock:"У части выплачиваемой переработки нет подтверждённого происхождения минут, поэтому DutyLog не может безопасно рассчитать её автоматически.",settlementRulesBlock:"Для исторической даты части выплачиваемой переработки не настроены правила оплаты.",settlementCurrencyBlock:"Исторические источники выплаты переработки рассчитаны в несовместимых валютах.",settlementPayrollCurrencyBlock:"Валюта выплаты переработки не совпадает с валютой этого расчётного месяца.",settlementGenericBlock:"Расчёт выплаты переработки пока недоступен.",technicalDetails:"Техническая идентичность расчёта",fingerprint:"Отпечаток pricing",additions:"Начисления",deductions:"Удержания",manual:"Ручные операции",operations:"Начисления и удержания",addition:"Начисление",deduction:"Удержание",titlePlaceholder:"Например: премия",notePlaceholder:"Комментарий",addOperation:"Добавить операцию",snapshots:"Закрытый расчёт",revisions:"Ревизии snapshot",calculate:"Зафиксировать новую ревизию",noAdjustments:"Нет ручных операций",noSnapshots:"Расчётов пока нет",periodOpenBlock:"Сначала закрой расчётный период",integrityBlock:"Сначала исправь расхождения журнала",compensationBlock:"Сначала укажи, как тебе платят",coverageBlock:"Укажи исходные смены и выходные на весь месяц, включая дни отсутствий. Отпуск, больничный, отсутствие без содержания и отгул учитываются отдельно от графика.",normBlock:"Для оклада нужна положительная расчётная норма месяца",ready:"Готово к фиксации",included:"Входит в базовый оклад",loading:"Загрузка…",openCalendar:"Открыть календарь",production:"Расчётная норма",base:"Базовая норма графика",adjustment:"Корректировка календаря",rules:"Правила календаря",noRules:"В этом месяце правил производственного календаря нет",coverageWarn:"Исходный график заполнен не полностью. Норма пока рассчитана только по известным датам. На днях отсутствий укажи смены или выходные по своему графику; само отсутствие сохраняется.",saved:"Условия оплаты сохранены",deleted:"Условия оплаты удалены",adjustmentOk:"Денежная операция добавлена",calculateOk:"Ревизия расчёта сохранена",compensationComponents:"Доплаты и надбавки",componentCount:"компонентов",componentFixed:"Фиксированная сумма",componentNominalBase:"Оклад",componentEarnedBase:"Фактически начисленная базовая оплата",componentEligibleBase:"Допустимые начисления",componentCurrencyBlock:"Валюта одной из доплат или надбавок не совпадает с валютой этого расчётного месяца. Исправь валюту компонента перед расчётом.",componentBaseBlock:"Для процентной доплаты недоступна выбранная база. База «Оклад» требует окладной системы оплаты.",componentInvalidBlock:"Одна из доплат или надбавок настроена некорректно. Проверь её формулу, сумму или процент.",componentGenericBlock:"Расчёт доплат и надбавок пока недоступен.",componentFingerprint:"Отпечаток компонентов",invalidAmount:"Укажи положительную сумму",
});
function minutes(value:number|null|undefined):string { const total=Math.round(Number(value??0)), sign=total<0?"−":"", safe=Math.abs(total), h=Math.floor(safe/60), m=safe%60; return language.value==="en"?`${sign}${h} h${m?` ${m} min`:""}`:`${sign}${h} ч${m?` ${m} мин`:""}`; }
function money(minor:number|null|undefined, code=currency.value):string { const value=Number(minor??0)/100; try { return new Intl.NumberFormat(language.value==="en"?"en-US":"ru-RU",{style:"currency",currency:String(code||"RUB").toUpperCase(),minimumFractionDigits:2,maximumFractionDigits:2}).format(value); } catch { return `${value.toFixed(2)} ${code}`; } }
function moneyForMinutes(value:number|null|undefined, rate:number|null|undefined):number { return Math.round((Number(value??0)*Number(rate??0))/60); }
function compensationMonthLabel(value:string|null|undefined):string { if(value==="1970-01") return language.value==="en"?"Initial conditions":"Исходные условия"; return value||"—"; }
function requestErrorMessage(caught:unknown):string { return caught instanceof Error&&caught.message?caught.message:error.value||(language.value==="en"?"Request failed":"Не удалось выполнить запрос"); }
function settlementBlockingMessage(code:string|null|undefined):string {
  switch(code){
    case"PAY_PRICING_PROVENANCE_REQUIRED":return text.value.settlementProvenanceBlock;
    case"PAY_PRICING_RULES_REQUIRED":return text.value.settlementRulesBlock;
    case"PAY_PRICING_CURRENCY_MISMATCH":return text.value.settlementCurrencyBlock;
    case"PAYROLL_SETTLEMENT_CURRENCY_MISMATCH":return text.value.settlementPayrollCurrencyBlock;
    case"PAYROLL_COMPENSATION_REQUIRED":return text.value.compensationBlock;
    case"PAYROLL_PRODUCTION_NORM_INCOMPLETE":return text.value.coverageBlock;
    case"PAYROLL_PRODUCTION_NORM_REQUIRED":return text.value.normBlock;
    default:return code?text.value.settlementGenericBlock:"";
  }
}
function componentPercent(rateBps:number|null|undefined):string {
  const value=Number(rateBps??0)/100;
  return `${Number.isInteger(value)?value:value.toFixed(2)}%`;
}
function componentBaseLabel(base:string|null|undefined):string {
  return base==="NOMINAL_SALARY"
    ?text.value.componentNominalBase
    :base==="LOCAL_ELIGIBLE_EARNINGS"
      ?text.value.componentEligibleBase
      :text.value.componentEarnedBase;
}
function componentLineDetail(line:PayrollCompensationComponentLine,code=currency.value):string {
  if(line.calculationType==="FIXED_AMOUNT"){
    return `${text.value.componentFixed} · ${money(line.configuredAmountMinor??line.amountMinor,line.configuredCurrencyCode||code)}`;
  }
  return `${componentPercent(line.rateBps)} · ${componentBaseLabel(line.calculationBase)}: ${money(line.referenceBaseMinor,code)}`;
}
function compensationComponentBlockingMessage(code:string|null|undefined):string {
  switch(code){
    case"PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH":return text.value.componentCurrencyBlock;
    case"PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE":return text.value.componentBaseBlock;
    case"PAYROLL_COMP_COMPONENT_LOCAL_BASE_INCOMPLETE":return text.value.componentBaseBlock;
    case"PAYROLL_COMP_COMPONENT_LOCAL_BASE_UNSUPPORTED":return text.value.componentInvalidBlock;
    case"PAYROLL_COMP_COMPONENT_INVALID":return text.value.componentInvalidBlock;
    case"PAYROLL_COMPENSATION_REQUIRED":return text.value.compensationBlock;
    case"PAYROLL_PRODUCTION_NORM_INCOMPLETE":return text.value.coverageBlock;
    case"PAYROLL_PRODUCTION_NORM_REQUIRED":return text.value.normBlock;
    default:return code?text.value.componentGenericBlock:"";
  }
}
const breakdown = computed(() => { const p=preview.value; if(!p)return []; const rows=[{key:"worked",label:text.value.worked,value:p.workedMinutes},{key:"vacation",label:language.value==="en"?"Paid vacation":"Оплачиваемый отпуск",value:p.vacationMinutes},{key:"sick",label:language.value==="en"?"Sick leave":"Больничный",value:p.sickMinutes},{key:"overtime",label:language.value==="en"?"Overtime time-off":"Отгул из переработок",value:p.overtimeCompensatedMinutes},{key:"adjustment",label:text.value.timeAdjustment,value:p.timeAdjustmentMinutes}].filter(i=>Number(i.value)!==0); return rows.map(i=>({...i,amount:salaryMode.value?null:moneyForMinutes(i.value,p.effectiveHourlyRateMinor)})); });
const compensationComponentBlocking = computed(() => preview.value&&!preview.value.compensationComponentCalculationReady?compensationComponentBlockingMessage(preview.value.compensationComponentCalculationBlockingReason):"");
const settlementBlocking = computed(() => preview.value&&!preview.value.settlementPricingReady?settlementBlockingMessage(preview.value.settlementPricingBlockingReason):"");
const blocking = computed(() => {
  switch(period.value?.blockingReason){
    case"PERIOD_OPEN":return text.value.periodOpenBlock;
    case"LEDGER_INTEGRITY_FAILED":return text.value.integrityBlock;
    case"PAYROLL_COMPENSATION_REQUIRED":return text.value.compensationBlock;
    case"PAYROLL_PRODUCTION_NORM_INCOMPLETE":return text.value.coverageBlock;
    case"PAYROLL_PRODUCTION_NORM_REQUIRED":return text.value.normBlock;
    case"PAYROLL_COMP_COMPONENT_CURRENCY_MISMATCH":return text.value.componentCurrencyBlock;
    case"PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE":return text.value.componentBaseBlock;
    case"PAYROLL_COMP_COMPONENT_LOCAL_BASE_INCOMPLETE":return text.value.componentBaseBlock;
    case"PAYROLL_COMP_COMPONENT_LOCAL_BASE_UNSUPPORTED":return text.value.componentInvalidBlock;
    case"PAYROLL_COMP_COMPONENT_INVALID":return text.value.componentInvalidBlock;
    default:
      if(preview.value&&!preview.value.compensationComponentCalculationReady)return compensationComponentBlocking.value||text.value.componentGenericBlock;
      if(preview.value&&!preview.value.settlementPricingReady)return settlementBlockingMessage(period.value?.blockingReason);
      if(preview.value&&!preview.value.ordinaryPremiumPricingReady)return ordinaryPremiumMessage(preview.value.ordinaryPremiumPricingBlockingReason,language.value);
      return settlementBlockingMessage(period.value?.blockingReason);
  }
});
function synchronizeInputs():void { const term=effectiveCompensation.value; effectiveMonth.value=month.value; payMode.value=term?.payMode==="SALARY"?"SALARY":"HOURLY"; currencyCode.value=term?.currencyCode||period.value?.settings.currencyCode||"RUB"; const minor=payMode.value==="SALARY"?term?.monthlySalaryMinor:term?.hourlyRateMinor; compensationAmount.value=minor? (Number(minor)/100).toFixed(2):""; }
function openCalendar():void { navigateHashRoute("calendar"); }
async function refresh():Promise<void>{ if(!payrollEnabled.value)return; try{await store.load(month.value);synchronizeInputs();}catch(caught){message.value=requestErrorMessage(caught);messageOk.value=false;} }
async function saveCompensation():Promise<void>{ const amount=Number(compensationAmount.value); if(!Number.isFinite(amount)||amount<=0){message.value=text.value.invalidAmount;messageOk.value=false;return;} const body:PayrollCompensationTermInput={payMode:payMode.value,currencyCode:currencyCode.value.trim().toUpperCase()||"RUB",hourlyRateMinor:payMode.value==="HOURLY"?Math.round(amount*100):null,monthlySalaryMinor:payMode.value==="SALARY"?Math.round(amount*100):null}; try{await store.saveCompensationTerm(effectiveMonth.value,body);synchronizeInputs();message.value=text.value.saved;messageOk.value=true;}catch(caught){message.value=requestErrorMessage(caught);messageOk.value=false;} }
async function removeCompensation(termMonth:string):Promise<void>{ try{await store.deleteCompensationTerm(termMonth);synchronizeInputs();message.value=text.value.deleted;messageOk.value=true;}catch(caught){message.value=requestErrorMessage(caught);messageOk.value=false;} }
async function addAdjustment():Promise<void>{const amount=Number(adjustmentAmount.value);if(!Number.isFinite(amount)||amount<=0){message.value=text.value.invalidAmount;messageOk.value=false;return;}const title=adjustmentTitle.value.trim();if(!title){message.value=text.value.titlePlaceholder;messageOk.value=false;return;}try{await store.addAdjustment({month:month.value,adjustmentType:adjustmentType.value,amountMinor:Math.round(amount*100),title,note:adjustmentNote.value.trim()||null});adjustmentAmount.value="";adjustmentTitle.value="";adjustmentNote.value="";message.value=text.value.adjustmentOk;messageOk.value=true;}catch(caught){message.value=requestErrorMessage(caught);messageOk.value=false;}}
async function calculate():Promise<void>{try{await store.calculate();message.value=text.value.calculateOk;messageOk.value=true;}catch(caught){message.value=requestErrorMessage(caught);messageOk.value=false;}}
async function synchronizeRoute(route:string):Promise<void>{if(route==="payroll"&&payrollEnabled.value)await refresh();}
onMounted(()=>{previousDomain=window.DutyLogVueDomains?.payroll;const domain:DutyLogPayrollDomain=Object.freeze({ready:()=>store.loaded&&!store.loading,refresh});window.DutyLogVueDomains=Object.freeze({...(window.DutyLogVueDomains??{}),payroll:domain});document.documentElement.dataset.vuePayroll="ready";void synchronizeRoute(activeRoute.value);});
watch(activeRoute,route=>{void synchronizeRoute(route);});watch([modulesLoaded,modules],()=>{if(activeRoute.value==="payroll")void refresh();},{deep:true});watch(month,()=>{effectiveMonth.value=month.value;});
onBeforeUnmount(()=>{if(previousDomain)window.DutyLogVueDomains=Object.freeze({...(window.DutyLogVueDomains??{}),payroll:previousDomain});else if(window.DutyLogVueDomains){const{payroll:_removed,...rest}=window.DutyLogVueDomains;window.DutyLogVueDomains=Object.freeze(rest);}delete document.documentElement.dataset.vuePayroll;});
</script>

<template><section v-if="activeRoute === 'payroll'" id="view-payroll" class="view payrollView" data-vue-domain-route="payroll" data-vue-domain-owner="payroll"><div class="payrollWorkspace payrollWorkspace--redesign">
<header class="payrollHero card" aria-labelledby="payrollTitle"><div><div class="eyebrow">{{ text.foundation }}</div><h2 id="payrollTitle">{{ text.title }}</h2><p>{{ text.intro }}</p></div><div class="payrollHeroControls"><PayrollMonthPicker v-model="month" :language="language" :disabled="loading" @change="refresh" /><button id="payrollReload" type="button" :disabled="loading" @click="refresh">{{ text.reload }}</button></div></header>

<nav class="payrollTabs" role="tablist" :aria-label="language === 'en' ? 'Payroll sections' : 'Разделы зарплаты'">
  <button v-for="tab in payrollTabs" :id="`payroll-tab-${tab.key}`" :key="tab.key" type="button" role="tab"
    :aria-selected="activeTab === tab.key" :aria-controls="`payroll-panel-${tab.key}`"
    :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
    {{ tabLabel(tab.key) }}
  </button>
</nav>

<section v-show="activeTab === 'overview'" id="payroll-panel-overview" role="tabpanel" aria-labelledby="payroll-tab-overview" class="payrollOverview">
  <div class="payrollOverviewStats">
    <article class="payrollStat payrollStat--total">
      <span>{{ text.totalPay }}</span>
      <strong id="payrollTotal">{{ preview ? money(preview.totalPayMinor) : '—' }}</strong>
      <small id="payrollRevisionLabel">{{ period?.latestSnapshot ? `${text.revision} ${period.latestSnapshot.revision} · ${money(period.latestSnapshot.totalPayMinor,period.latestSnapshot.currencyCode)}` : text.preview }}</small>
      <small>{{ text.totalDisclaimer }}</small>
    </article>
    <article class="payrollStat"><span>{{ text.basePay }}</span><strong>{{ preview ? money(preview.basePayMinor) : '—' }}</strong></article>
    <article class="payrollStat"><span>{{ text.compensationComponents }}</span><strong>{{ preview ? money(preview.compensationComponentEarningsMinor) : '—' }}</strong></article>
    <article class="payrollStat"><span>{{ text.worked }}</span><strong>{{ preview ? minutes(preview.workedMinutes) : '—' }} <small>/ {{ minutes(preview?.productionNormMinutes) }}</small></strong></article>
    <article class="payrollStat payrollStat--action">
      <span>{{ language === 'en' ? 'Holiday and rest-day work' : 'Работа в праздники и выходные' }}</span>
      <strong>{{ language === 'en' ? 'Review compensation' : 'Проверить компенсацию' }}</strong>
      <button type="button" @click="activeTab = 'earnings'">{{ language === 'en' ? 'See details' : 'Расшифровка' }} <span aria-hidden="true">→</span></button>
    </article>
  </div>
  <p v-if="blocking && period?.blockingReason !== 'PERIOD_OPEN'" class="payrollOverviewNotice" role="status">
    {{ language === 'en' ? 'Before saving a revision:' : 'Перед сохранением расчёта:' }} {{ blocking }}
  </p>
  <div class="payrollOverviewColumns">
    <div class="payrollOverviewLeft">
      <section class="card payrollOverviewPanel">
        <div class="payrollOverviewHeading"><h3>{{ language === 'en' ? 'Where the total comes from' : 'Из чего состоит сумма' }}</h3><button type="button" @click="activeTab = 'earnings'">{{ language === 'en' ? 'All earnings' : 'Подробнее' }} <span aria-hidden="true">→</span></button></div>
        <template v-if="preview">
          <div class="payrollOverviewBreakdown">
            <div><span>{{ text.basePay }}</span><b>{{ money(preview.basePayMinor) }}</b></div>
            <div v-for="line in preview.compensationComponentLines" :key="`overview-${line.versionId}`"><span>{{ line.displayName }}</span><b>{{ money(line.amountMinor) }}</b></div>
            <div v-if="preview.ordinaryPremiumPayMinor || !preview.ordinaryPremiumPricingReady"><span>{{ text.ordinaryPremium }}</span><b>{{ money(preview.ordinaryPremiumPayMinor) }}</b></div>
            <div><span>{{ text.settlement }}</span><b>{{ money(preview.settlementPayMinor) }}</b></div>
            <div v-if="preview.additionsMinor"><span>{{ text.additions }}</span><b>{{ money(preview.additionsMinor) }}</b></div>
            <div v-if="preview.deductionsMinor"><span>{{ text.deductions }}</span><b>−{{ money(preview.deductionsMinor) }}</b></div>
          </div>
        </template>
        <div v-else class="payrollEmpty">{{ loading ? text.loading : (error || '—') }}</div>
      </section>
      <section class="card payrollOverviewPanel">
        <div class="payrollOverviewHeading"><h3>{{ language === 'en' ? 'Time behind the calculation' : 'Время и основание расчёта' }}</h3><button type="button" @click="activeTab = 'earnings'">{{ language === 'en' ? 'Details' : 'Подробнее' }} <span aria-hidden="true">→</span></button></div>
        <div class="payrollOverviewTime">
          <div><span>{{ text.production }}</span><b>{{ preview ? minutes(preview.productionNormMinutes) : '—' }}</b></div>
          <div><span>{{ text.worked }}</span><b>{{ preview ? minutes(preview.workedMinutes) : '—' }}</b></div>
          <div><span>{{ text.unpaid }}</span><b>{{ preview ? minutes(preview.unpaidMinutes) : '—' }}</b></div>
          <div><span>{{ text.timeAdjustment }}</span><b>{{ preview ? minutes(preview.timeAdjustmentMinutes) : '—' }}</b></div>
        </div>
      </section>
    </div>
    <section class="card payrollOverviewPanel payrollOverviewAttention" aria-label="Действия по расчёту">
      <h3>{{ language === 'en' ? 'What needs attention' : 'Что требует внимания' }}</h3>
      <RestDayElectionCard :month="month" :language="language" @saved="refresh" />
      <HolidayReviewCard :month="month" :language="language" @saved="refresh" />
      <div class="payrollAttentionHistory">
        <div><strong>{{ language === 'en' ? 'Last revision' : 'Последняя ревизия' }}</strong><small>{{ period?.latestSnapshot ? `${text.revision} ${period.latestSnapshot.revision}` : text.noSnapshots }}</small></div>
        <button type="button" @click="activeTab = 'history'">{{ language === 'en' ? 'Open history' : 'Открыть историю' }} <span aria-hidden="true">→</span></button>
      </div>
    </section>
  </div>
  <div class="payrollOverviewSource">
    <div><span>{{ text.period }}</span><b id="payrollPeriodStatus" :class="period?.periodClosed ? 'ok' : 'warn'">{{ !period ? '—' : (period.periodClosed ? text.periodClosed : text.periodOpen) }}</b><small id="payrollPeriodHint">{{ period?.periodClosed ? text.frozen : text.live }}</small></div>
    <div><span>{{ text.integrity }}</span><b id="payrollIntegrityStatus" :class="period?.integrityHealthy ? 'ok' : 'bad'">{{ !period ? '—' : (period.integrityHealthy ? text.healthy : text.unhealthy) }}</b></div>
  </div>
</section>

<section v-show="activeTab === 'earnings'" id="payroll-panel-earnings" role="tabpanel" aria-labelledby="payroll-tab-earnings" class="payrollTabBody">
<section class="card payrollMoneyCard"><div class="payrollSectionHead"><div><div class="eyebrow">{{ preview?.payMode || '—' }}</div><h3>{{ text.payable }}</h3></div><span id="payrollCurrencyBadge">{{ currency }}</span></div><div class="payrollMoneySummary nativePayrollSummary"><div v-if="preview?.payMode==='SALARY'"><span>{{ text.salary }}</span><b>{{ money(preview?.monthlySalaryMinor) }}</b></div><div><span>{{ text.production }}</span><b>{{ minutes(preview?.productionNormMinutes) }}</b></div><div><span>{{ text.effectiveRate }}</span><b>{{ money(preview?.effectiveHourlyRateMinor) }} / ч</b></div><div v-if="preview?.payMode==='SALARY'"><span>{{ text.coverage }}</span><b>{{ minutes(preview?.salaryCoveredMinutes) }} / {{ minutes(preview?.productionNormMinutes) }}</b></div><div v-if="preview?.payMode==='HOURLY'"><span>{{ text.ordinaryBaseTime }}</span><b id="payrollHourlyBasePayable">{{ minutes(preview?.hourlyBasePayableMinutes) }}</b></div><div><span>{{ text.basePay }}</span><b id="payrollBasePay">{{ money(preview?.basePayMinor) }}</b></div><div><span>{{ text.compensationComponents }}</span><b id="payrollCompensationComponentsTotal">{{ money(preview?.compensationComponentEarningsMinor) }}</b></div></div><div v-if="preview?.compensationComponentLines.length" class="payrollBreakdown" id="payrollCompensationComponentBreakdown"><div v-for="line in preview.compensationComponentLines" :key="line.versionId" class="payrollBreakdownRow"><span><b>{{ line.displayName }}</b><small>{{ componentLineDetail(line) }}</small></span><strong>{{ money(line.amountMinor) }}</strong></div></div><div v-if="preview&&!preview.compensationComponentCalculationReady" id="payrollCompensationComponentStatus" class="payrollBlocking blocked">{{ compensationComponentBlocking || text.componentGenericBlock }}</div><div v-if="preview&&(!preview.ordinaryPremiumPricingReady||preview.ordinaryPremiumPricingIdentityRequired||preview.ordinaryPremiumPayMinor)" class="payrollBreakdown" id="payrollOrdinaryPremiumBreakdown"><div class="payrollBreakdownRow"><span><b>{{ text.ordinaryPremium }}</b><small>{{ minutes(preview.ordinaryPremiumMinutes) }} · {{ text.basePay }}: {{ money(preview.ordinaryPremiumReferenceBasePayMinor) }}</small></span><strong id="payrollOrdinaryPremium">{{ money(preview.ordinaryPremiumPayMinor) }}</strong></div></div><Article153PaymentDetails v-if="preview&&(preview.article153?.status!=='LEGACY_UNREVIEWED'||preview.ordinaryPremiumPricingIdentityRequired)" :summary="preview.article153" :currency="currency" :language="language" /><div v-if="preview&&!preview.ordinaryPremiumPricingReady&&preview.article153?.status!=='REVIEW_BLOCKED'" id="payrollOrdinaryPremiumPricingStatus" class="payrollBlocking blocked">{{ ordinaryPremiumMessage(preview.ordinaryPremiumPricingBlockingReason,language) }}</div><div class="payrollBreakdown payrollSettlementBreakdown" id="payrollSettlementBreakdown"><div class="payrollBreakdownRow"><span><b>{{ text.settlement }}</b><small v-if="preview?.settlementCount">{{ text.settlementTransactions }}: {{ preview.settlementCount }} · {{ minutes(preview.settlementMinutes) }}</small><small v-else>{{ text.settlementNone }}</small></span><strong id="payrollSettlementTotal">{{ money(preview?.settlementPayMinor) }}</strong></div><div v-if="preview?.settlementCount" class="payrollBreakdownRow"><span><b>{{ text.settlementBase }}</b><small>{{ minutes(preview.settlementMinutes) }}</small></span><strong id="payrollSettlementBase">{{ money(preview.settlementBasePayMinor) }}</strong></div><div v-if="preview?.settlementCount" class="payrollBreakdownRow"><span><b>{{ text.settlementPremium }}</b></span><strong id="payrollSettlementPremium">{{ money(preview.settlementPremiumPayMinor) }}</strong></div></div><div v-if="preview&&!preview.settlementPricingReady" id="payrollSettlementPricingStatus" class="payrollBlocking blocked">{{ settlementBlocking || text.settlementGenericBlock }}</div><div v-else-if="preview?.settlementCount" id="payrollSettlementPricingStatus" class="payrollBlocking ready">{{ text.settlementReady }}</div><div class="payrollMoneySummary nativePayrollSummary"><div><span>{{ text.additions }}</span><b id="payrollAdditions">{{ money(preview?.additionsMinor) }}</b></div><div><span>{{ text.deductions }}</span><b id="payrollDeductions">{{ money(preview?.deductionsMinor) }}</b></div><div class="accent"><span>{{ text.totalPay }}</span><b id="payrollGrandTotal">{{ money(preview?.totalPayMinor) }}</b></div></div><p id="payrollTotalScope" class="payrollTotalScope">{{ text.totalDisclaimer }}</p></section>
<section class="card payrollSummaryCard"><div class="payrollSectionHead"><div><div class="eyebrow">Day Truth → Payroll</div><h3>{{ text.breakdown }}</h3></div></div><div class="payrollMetricGrid"><div><span>{{ text.planned }}</span><b id="payrollPlanned">{{ minutes(preview?.plannedMinutes) }}</b></div><div><span>{{ text.worked }}</span><b id="payrollWorked">{{ minutes(preview?.workedMinutes) }}</b></div><div><span>{{ text.paidAbsence }}</span><b id="payrollPaidAbsence">{{ minutes(preview?.paidAbsenceMinutes) }}</b></div><div><span>{{ text.unpaid }}</span><b id="payrollUnpaid">{{ minutes(preview?.unpaidMinutes) }}</b></div><div><span>{{ text.timeAdjustment }}</span><b id="payrollTimeAdjustment">{{ minutes(preview?.timeAdjustmentMinutes) }}</b></div><div><span>{{ text.payableTime }}</span><b id="payrollPayable">{{ minutes(preview?.payableMinutes) }}</b></div><div v-if="preview?.payMode==='HOURLY'" class="accent"><span>{{ text.ordinaryBaseTime }}</span><b id="payrollHourlyBaseMinutes">{{ minutes(preview?.hourlyBasePayableMinutes) }}</b></div></div><div class="payrollBreakdown" id="payrollBreakdown"><div v-for="item in breakdown" :key="item.key" class="payrollBreakdownRow"><span><b>{{ item.label }}</b><small>{{ minutes(item.value) }}</small></span><strong>{{ item.amount===null?text.included:money(item.amount) }}</strong></div></div></section>
<section class="card payrollAdjustmentsCard"><div class="payrollSectionHead"><div><div class="eyebrow">{{ text.manual }}</div><h3>{{ text.operations }}</h3></div></div><form id="payrollAdjustmentForm" class="payrollAdjustmentForm" @submit.prevent="addAdjustment"><select id="payrollAdjustmentType" v-model="adjustmentType" :disabled="!period?.periodClosed||loading"><option value="ADDITION">{{ text.addition }}</option><option value="DEDUCTION">{{ text.deduction }}</option></select><input id="payrollAdjustmentAmount" v-model="adjustmentAmount" type="number" min="0.01" step="0.01" :placeholder="text.amount" required :disabled="!period?.periodClosed||loading"/><input id="payrollAdjustmentTitle" v-model="adjustmentTitle" maxlength="120" :placeholder="text.titlePlaceholder" required :disabled="!period?.periodClosed||loading"/><input id="payrollAdjustmentNote" v-model="adjustmentNote" maxlength="500" :placeholder="text.notePlaceholder" :disabled="!period?.periodClosed||loading"/><button type="submit" :disabled="!period?.periodClosed||loading">{{ text.addOperation }}</button></form><div class="payrollAdjustmentList" id="payrollAdjustmentList"><article v-for="item in period?.adjustments??[]" :key="item.id" class="payrollAdjustmentItem" :class="item.adjustmentType==='DEDUCTION'?'deduction':'addition'"><span><b>{{ item.title }}</b><small>{{ item.note || (item.adjustmentType==='DEDUCTION'?text.deduction:text.addition) }}</small></span><strong>{{ item.adjustmentType==='DEDUCTION'?'−':'+' }}{{ money(item.amountMinor) }}</strong></article><div v-if="!(period?.adjustments.length)" class="payrollEmpty">{{ text.noAdjustments }}</div></div></section>

</section>
<section v-show="activeTab === 'settings'" id="payroll-panel-settings" role="tabpanel" aria-labelledby="payroll-tab-settings" class="payrollTabBody payrollSettingsBody">
<div v-show="settingsSection === null" class="payrollSettingsTiles" :aria-label="language === 'en' ? 'Payroll settings sections' : 'Разделы настроек зарплаты'">
  <button id="payroll-settings-terms" type="button" class="payrollSettingsTile" :class="{active: settingsSection === 'terms'}"
    :aria-expanded="settingsSection === 'terms'" aria-controls="payroll-settings-panel-terms" @click="openSettingsSection('terms')">
    <span class="payrollSettingsTileIcon payrollSettingsTileIcon--terms" aria-hidden="true">▣</span>
    <span class="payrollSettingsTileText"><strong>{{ language === 'en' ? 'Pay terms' : 'Условия оплаты' }}</strong>
      <small>{{ language === 'en' ? 'Salary, hourly rate, currency and dates' : 'Оклад, ставка, валюта и период действия' }}</small>
      <em>{{ effectiveCompensation ? `${effectiveCompensation.payMode === 'SALARY' ? text.salary : text.hourly}: ${effectiveCompensation.payMode === 'SALARY' ? money(effectiveCompensation.monthlySalaryMinor, effectiveCompensation.currencyCode) : money(effectiveCompensation.hourlyRateMinor, effectiveCompensation.currencyCode)}` : (language === 'en' ? 'Not configured' : 'Не настроено') }}</em>
    </span><span aria-hidden="true" class="payrollSettingsChevron">›</span>
  </button>
  <button id="payroll-settings-components" type="button" class="payrollSettingsTile" :class="{active: settingsSection === 'components'}"
    :aria-expanded="settingsSection === 'components'" aria-controls="payroll-settings-panel-components" @click="openSettingsSection('components')">
    <span class="payrollSettingsTileIcon payrollSettingsTileIcon--components" aria-hidden="true">▤</span>
    <span class="payrollSettingsTileText"><strong>{{ text.compensationComponents }}</strong>
      <small>{{ language === 'en' ? 'Bonuses, region coefficients and allowances' : 'Премии, коэффициенты и дополнительные выплаты' }}</small>
      <em>{{ language === 'en' ? 'In current calculation' : 'В текущем расчёте' }}: {{ preview ? preview.compensationComponentLines.length : '—' }}</em>
    </span><span aria-hidden="true" class="payrollSettingsChevron">›</span>
  </button>
  <button id="payroll-settings-rules" type="button" class="payrollSettingsTile" :class="{active: settingsSection === 'rules'}"
    :aria-expanded="settingsSection === 'rules'" aria-controls="payroll-settings-panel-rules" @click="openSettingsSection('rules')">
    <span class="payrollSettingsTileIcon payrollSettingsTileIcon--rules" aria-hidden="true">☷</span>
    <span class="payrollSettingsTileText"><strong>{{ language === 'en' ? 'Pay rules' : 'Правила оплаты' }}</strong>
      <small>{{ language === 'en' ? 'Nights, holidays and cash overtime' : 'Ночная работа, праздники и выплата переработки' }}</small>
      <em>{{ language === 'en' ? 'Open configuration' : 'Открыть настройки' }}</em>
    </span><span aria-hidden="true" class="payrollSettingsChevron">›</span>
  </button>
  <button id="payroll-settings-norm" type="button" class="payrollSettingsTile" :class="{active: settingsSection === 'norm'}"
    :aria-expanded="settingsSection === 'norm'" aria-controls="payroll-settings-panel-norm" @click="openSettingsSection('norm')">
    <span class="payrollSettingsTileIcon payrollSettingsTileIcon--norm" aria-hidden="true">▦</span>
    <span class="payrollSettingsTileText"><strong>{{ language === 'en' ? 'Working-time norm' : 'Норма времени' }}</strong>
      <small>{{ language === 'en' ? 'Production calendar and required hours' : 'Производственный календарь и расчётная норма' }}</small>
      <em>{{ readableMonth }} · {{ productionCalendar ? minutes(productionCalendar.productionNormMinutes) : '—' }}</em>
    </span><span aria-hidden="true" class="payrollSettingsChevron">›</span>
  </button>
</div>

<div v-show="settingsSection !== null" class="payrollSettingsDetail" data-payroll-settings-detail>
  <header class="payrollSettingsDetailHeader">
    <button id="payroll-settings-back" type="button" class="payrollSettingsBack" @click="closeSettingsSection">
      <span aria-hidden="true">←</span> {{ language === 'en' ? 'Back to settings' : 'Назад к настройкам' }}
    </button>
    <div class="payrollSettingsDetailIntro">
      <div class="eyebrow">{{ language === 'en' ? 'Payroll settings' : 'Настройки зарплаты' }}</div>
      <h2 id="payroll-settings-detail-title">{{ settingsDetailTitle }}</h2>
      <p>{{ settingsDetailDescription }}</p>
    </div>
  </header>

<div id="payroll-settings-panel-terms" v-show="settingsSection === 'terms'" class="payrollSettingsEditor" role="region" aria-labelledby="payroll-settings-detail-title">
<section class="card payrollSettingsCard payrollCompensationCard"><div class="payrollSectionHead"><div><div class="eyebrow">Compensation</div><h3>{{ text.setup }}</h3></div></div><form id="payrollSettingsForm" class="payrollSettingsForm nativeCompensationForm" @submit.prevent="saveCompensation"><div class="compensationModeSwitch"><button type="button" :class="{active:payMode==='HOURLY'}" @click="payMode='HOURLY'">{{ text.hourly }}</button><button type="button" :class="{active:payMode==='SALARY'}" @click="payMode='SALARY'">{{ text.salary }}</button></div><label>{{ text.effective }}<input id="payrollCompensationMonth" v-model="effectiveMonth" type="month" required/></label><label>{{ text.currencyCode }}<input id="payrollCurrency" v-model="currencyCode" maxlength="3" pattern="[A-Za-z]{3}" required/></label><label>{{ payMode==='SALARY'?text.salary:text.hourly }}<input id="payrollHourlyRate" v-model="compensationAmount" type="number" min="0.01" max="10000000000" step="0.01" required/></label><button class="primary" type="submit" :disabled="loading">{{ text.save }}</button></form><p class="payrollHint">{{ payMode==='SALARY'?text.salaryHint:text.hourlyHint }}</p>
<div v-if="compensationHistory.length" class="compensationHistory"><div class="eyebrow">{{ text.history }}</div><article v-for="item in compensationHistory" :key="item.id" class="compensationHistoryRow"><span><b>{{ compensationMonthLabel(item.effectiveMonth) }} · {{ item.payMode==='SALARY'?text.salary:text.hourly }}</b><small>{{ item.payMode==='SALARY'?money(item.monthlySalaryMinor,item.currencyCode):`${money(item.hourlyRateMinor,item.currencyCode)} / ч` }}</small></span><button v-if="item.effectiveMonth !== '1970-01'" type="button" :disabled="loading" @click="removeCompensation(item.effectiveMonth)">{{ text.remove }}</button></article></div></section>
</div>
<div id="payroll-settings-panel-components" v-show="settingsSection === 'components'" class="payrollSettingsEditor" role="region" aria-labelledby="payroll-settings-detail-title">
<CompensationComponentsCard
  :language="language"
  :month="month"
  :currency-code="currency"
/>
</div>

<div id="payroll-settings-panel-rules" v-show="settingsSection === 'rules'" class="payrollSettingsEditor" role="region" aria-labelledby="payroll-settings-detail-title">
<PricingSettingsCard :language="language" />
</div>
<div id="payroll-settings-panel-norm" v-show="settingsSection === 'norm'" class="payrollSettingsEditor" role="region" aria-labelledby="payroll-settings-detail-title">
<section class="card productionCalendarCard" data-production-calendar-foundation><div class="payrollSectionHead"><div><div class="eyebrow">Production Calendar</div><h3>{{ text.production }}</h3></div><button type="button" @click="openCalendar">{{ text.openCalendar }}</button></div><div class="productionNormMetrics"><div><span>{{ text.base }}</span><b id="productionBaseNorm">{{ minutes(productionCalendar?.baseNormMinutes) }}</b></div><div><span>{{ text.adjustment }}</span><b id="productionNormAdjustment">{{ minutes(productionCalendar?.adjustmentMinutes) }}</b></div><div class="accent"><span>{{ text.production }}</span><b id="productionNorm">{{ minutes(productionCalendar?.productionNormMinutes) }}</b></div></div><p v-if="productionCalendar&&!productionCalendar.scheduleCoverageComplete" id="productionCoverageWarning" class="productionCoverageWarning">{{ text.coverageWarn }}</p><div class="productionCalendarRules" data-production-calendar-summary-only><article v-for="day in productionAffectedDays" :key="day.date" class="productionCalendarRule productionCalendarRule--summary"><span><b>{{ day.date }}</b><small>{{ day.label || day.dayKind }}</small></span><strong>{{ minutes(day.baseNormMinutes) }} → {{ minutes(day.productionNormMinutes) }}</strong></article><div v-if="!productionAffectedDays.length" class="payrollEmpty">{{ text.noRules }}</div></div></section>
</div>
</div>
</section>
<section v-show="activeTab === 'history'" id="payroll-panel-history" role="tabpanel" aria-labelledby="payroll-tab-history" class="payrollTabBody">
<section class="card payrollSnapshotCard"><div class="payrollSectionHead"><div><div class="eyebrow">{{ text.snapshots }}</div><h3>{{ text.revisions }}</h3></div></div><div class="payrollBlocking" id="payrollBlocking" :class="blocking?'blocked':'ready'">{{ blocking || text.ready }}</div><button class="primary payrollCalculate" id="payrollCalculate" type="button" :disabled="!period?.canCalculate||loading" @click="calculate">{{ text.calculate }}</button><div class="payrollSnapshotList" id="payrollSnapshotList"><article v-for="item in period?.snapshots??[]" :key="item.id" class="payrollSnapshotItem" :class="item.supersededById?'superseded':'current'"><span><b>{{ text.revision }} {{ item.revision }}</b><small>{{ item.payMode }} · {{ compensationMonthLabel(item.compensationEffectiveMonth) }} · {{ new Date(item.createdAt).toLocaleString(language==='en'?'en-US':'ru-RU') }}</small><small v-if="item.settlementCount">{{ text.settlement }} · {{ minutes(item.settlementMinutes) }} · {{ money(item.settlementPayMinor,item.currencyCode) }}</small><small v-if="item.ordinaryPremiumPricingFingerprint">{{ text.ordinaryPremium }} · {{ minutes(item.ordinaryPremiumMinutes) }} · {{ money(item.ordinaryPremiumPayMinor,item.currencyCode) }}</small><small v-if="item.compensationComponentCount">{{ text.compensationComponents }} · {{ item.compensationComponentCount }} {{ text.componentCount }} · {{ money(item.compensationComponentEarningsMinor,item.currencyCode) }}</small><small v-for="line in item.compensationComponentLines" :key="`snapshot-component-${item.id}-${line.versionId}`"><b>{{ line.displayName }}</b> · {{ componentLineDetail(line,item.currencyCode) }} → {{ money(line.amountMinor,item.currencyCode) }}</small><Article153PaymentDetails v-if="item.article153?.status==='REVIEWED'||item.ordinaryPremiumPricingFingerprint" :summary="item.article153" :currency="item.currencyCode" :language="language" /><details v-if="item.settlementPricingFingerprint||item.compensationComponentFingerprint" class="payrollSnapshotTechnical"><summary>{{ text.technicalDetails }}</summary><template v-if="item.settlementPricingFingerprint"><small>{{ text.fingerprint }}</small><code class="payrollFingerprint">{{ item.settlementPricingFingerprint }}</code></template><template v-if="item.compensationComponentFingerprint"><small>{{ text.componentFingerprint }}</small><code class="payrollFingerprint">{{ item.compensationComponentFingerprint }}</code></template></details></span><strong>{{ money(item.totalPayMinor,item.currencyCode) }}</strong></article><div v-if="!(period?.snapshots.length)" class="payrollEmpty">{{ text.noSnapshots }}</div></div></section>

</section>
<div class="appModalMessage payrollMessage" id="payrollMessage" :class="message?(messageOk?'ok':'err'):''" role="status" aria-live="polite">{{ message || (loading?text.loading:error) }}</div>
</div></section></template>

<style scoped>

/* UX-P0.2: settings catalog and the selected editor occupy separate screens. */
.payrollSettingsDetail { display: grid; gap: 16px; min-width: 0; }
.payrollSettingsDetailHeader { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 16px; padding: 18px 20px; border: 1px solid var(--payroll-ux-line); border-radius: 16px; background: var(--panel); }
.payrollSettingsBack { display: inline-flex; align-items: center; gap: 8px; flex: none; min-height: 42px; font-weight: 700; color: var(--accent); }
.payrollSettingsBack:hover { border-color: var(--accent); }
.payrollSettingsBack:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.payrollSettingsDetailIntro { display: grid; gap: 7px; min-width: 230px; flex: 1; }
.payrollSettingsDetailIntro h2 { margin: 0; font-size: clamp(1.2rem, 2vw, 1.5rem); color: var(--text); }
.payrollSettingsDetailIntro p { margin: 0; color: var(--mut); line-height: 1.45; font-size: .86rem; }
@media (max-width: 640px) {
  .payrollSettingsDetailHeader { padding: 15px; flex-direction: column; gap: 12px; }
  .payrollSettingsBack { align-self: flex-start; }
  .payrollSettingsDetailIntro { min-width: 0; }
}

/* UX-P0.1: focused, two-column settings instead of a continuous form. */
.payrollSettingsTiles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.payrollSettingsTile { display: flex; gap: 14px; align-items: flex-start; min-width: 0; min-height: 118px; text-align: left; padding: 18px; border: 1px solid var(--payroll-ux-line); border-radius: 16px; background: var(--panel); color: var(--text); }
.payrollSettingsTile:hover, .payrollSettingsTile.active { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 7%, var(--panel)); }
.payrollSettingsTile:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.payrollSettingsTileIcon { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 11px; background: color-mix(in srgb, var(--accent) 15%, var(--panel2)); color: var(--accent); font-size: 1.3rem; }
.payrollSettingsTileIcon--terms { color: #8ba7ef; background: #355a9d33; }
.payrollSettingsTileIcon--norm { color: #e6b36f; background: #b87a2130; }
.payrollSettingsTileText { flex: 1; display: grid; gap: 7px; min-width: 0; }
.payrollSettingsTileText strong { font-size: 1.03rem; }
.payrollSettingsTileText small { color: var(--mut); font-size: .78rem; line-height: 1.4; }
.payrollSettingsTileText em { width: fit-content; color: var(--accent); font-size: .75rem; font-style: normal; font-weight: 700; }
.payrollSettingsChevron { flex: none; color: var(--accent); font-size: 1.6rem; line-height: 1; }
.payrollSettingsEditor { display: grid; min-width: 0; }
.payrollSettingsEditor > .card { min-width: 0; }
@media (max-width: 640px) {
  .payrollSettingsTiles { grid-template-columns: minmax(0, 1fr); }
  .payrollSettingsTile { min-height: 100px; padding: 14px; }
}

/* UX-P0: one-glance Payroll overview, advanced controls in focused sections. */
.payrollWorkspace--redesign { --payroll-ux-line: var(--line, #303b46); gap: 18px; }
.payrollWorkspace--redesign > .payrollHero { padding: 18px 24px; }
.payrollTabs { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 6px; padding: 5px; border: 1px solid var(--payroll-ux-line); border-radius: 15px; background: var(--panel); }
.payrollTabs button { min-height: 46px; border-radius: 11px; border: 1px solid transparent; background: transparent; color: var(--mut); font-weight: 750; }
.payrollTabs button.active { border-color: color-mix(in srgb, var(--accent) 60%, var(--payroll-ux-line)); background: color-mix(in srgb, var(--accent) 16%, var(--panel)); color: var(--text); }
.payrollTabs button:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
.payrollOverview { display: grid; gap: 16px; min-width: 0; }
.payrollOverviewStats { display: grid; grid-template-columns: minmax(230px, 1.6fr) repeat(3, minmax(135px, 1fr)) minmax(170px, 1fr); gap: 10px; }
.payrollStat { min-width: 0; border: 1px solid var(--payroll-ux-line); border-radius: 16px; padding: 15px; background: var(--panel); display: flex; flex-direction: column; justify-content: center; gap: 9px; }
.payrollStat > span { color: var(--mut); font-size: .8rem; font-weight: 750; }
.payrollStat > strong { color: var(--text); font-size: clamp(1rem, 1.7vw, 1.35rem); overflow-wrap: anywhere; }
.payrollStat strong small { color: var(--mut); font-size: .77rem; font-weight: 600; }
.payrollStat > small { color: var(--mut); font-size: .72rem; line-height: 1.4; }
.payrollStat--total { border-color: color-mix(in srgb, var(--accent) 70%, var(--payroll-ux-line)); background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 19%, var(--panel)), var(--panel)); }
.payrollStat--total > strong { color: var(--accent); font-size: clamp(1.35rem, 2.2vw, 1.85rem); }
.payrollStat--action { border-color: color-mix(in srgb, #c99549 50%, var(--payroll-ux-line)); }
.payrollStat--action > strong { color: #e0ac60; font-size: .96rem; }
.payrollStat--action > button { align-self: flex-start; font-size: .76rem; padding: 5px 9px; }
.payrollOverviewNotice { margin: 0; padding: 12px 14px; border: 1px solid color-mix(in srgb, #c99549 50%, var(--payroll-ux-line)); border-radius: 13px; color: #dfb476; background: color-mix(in srgb, #c99549 10%, var(--panel)); }
.payrollOverviewColumns { display: grid; grid-template-columns: minmax(0, 1.04fr) minmax(0, 1fr); gap: 16px; min-width: 0; align-items: start; }
.payrollOverviewLeft { display: grid; gap: 16px; min-width: 0; }
.payrollOverviewPanel { padding: 18px; min-width: 0; }
.payrollOverviewPanel h3 { font-size: 1.06rem; margin: 0; }
.payrollOverviewHeading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.payrollOverviewHeading button { font-size: .79rem; flex: none; }
.payrollOverviewBreakdown { display: grid; }
.payrollOverviewBreakdown > div { display: flex; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--payroll-ux-line); padding: 11px 0; align-items: baseline; }
.payrollOverviewBreakdown > div:last-child { border-bottom: 0; padding-bottom: 0; }
.payrollOverviewBreakdown span { min-width: 0; overflow-wrap: anywhere; }
.payrollOverviewBreakdown b { white-space: nowrap; font-variant-numeric: tabular-nums; }
.payrollOverviewTime { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 8px; }
.payrollOverviewTime > div { display: grid; gap: 7px; padding: 12px 10px; border: 1px solid var(--payroll-ux-line); border-radius: 12px; background: var(--panel2); min-width: 0; }
.payrollOverviewTime span { font-size: .72rem; color: var(--mut); overflow-wrap: anywhere; }
.payrollOverviewTime b { font-size: 1.04rem; }
.payrollOverviewAttention { display: grid; gap: 13px; }
.payrollOverviewAttention :deep(.rest-election-card), .payrollOverviewAttention :deep(.holiday-review-card) { margin: 0; padding: 15px; background: var(--panel2); border: 1px solid var(--payroll-ux-line); border-radius: 14px; }
.payrollOverviewAttention :deep(.rest-election-card) h3, .payrollOverviewAttention :deep(.holiday-review-card) h3 { font-size: .95rem; line-height: 1.35; }
.payrollOverviewAttention :deep(.rest-election-card) p, .payrollOverviewAttention :deep(.holiday-review-card) p { color: var(--mut); font-size: .8rem; line-height: 1.45; }
.payrollOverviewAttention :deep(.rest-election-card) button, .payrollOverviewAttention :deep(.holiday-review-card) > button { min-height: 40px; }
.payrollAttentionHistory { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--payroll-ux-line); border-radius: 14px; background: var(--panel2); }
.payrollAttentionHistory > div { display: grid; gap: 5px; }
.payrollAttentionHistory small { color: var(--mut); }
.payrollOverviewSource { display: flex; flex-wrap: wrap; gap: 12px; }
.payrollOverviewSource > div { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; color: var(--mut); font-size: .77rem; }
.payrollOverviewSource b { color: var(--text); }
.payrollOverviewSource b.ok { color: var(--ok); }
.payrollOverviewSource b.bad { color: var(--bad); }
.payrollOverviewSource b.warn { color: var(--accent); }
.payrollTabBody { display: grid; gap: 16px; min-width: 0; }
.payrollTabBody > .payrollMoneyCard, .payrollTabBody > .payrollSettingsCard, .payrollTabBody > .payrollSummaryCard, .payrollTabBody > .payrollSnapshotCard { min-width: 0; }
@media (max-width: 1120px) {
  .payrollOverviewStats { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .payrollStat--total { grid-column: span 2; }
  .payrollStat--action { grid-column: span 2; }
  .payrollOverviewTime { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 800px) {
  .payrollOverviewColumns { grid-template-columns: 1fr; }
  .payrollOverviewStats { grid-template-columns: repeat(2, minmax(0,1fr)); }
  .payrollStat--total { grid-column: span 2; }
  .payrollStat--action { grid-column: span 2; }
}
@media (max-width: 560px) {
  .payrollWorkspace--redesign > .payrollHero { padding: 14px; }
  .payrollTabs { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .payrollTabs button { min-height: 44px; }
  .payrollStat { padding: 12px; }
  .payrollStat > strong { font-size: 1rem; }
  .payrollOverviewBreakdown > div { font-size: .86rem; }
  .payrollOverviewTime { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .payrollAttentionHistory { flex-direction: column; align-items: stretch; }
}

.payrollTotalScope { margin: .5rem 0 0; color: var(--color-text-secondary, var(--dim, #777)); font-size: .85rem; line-height: 1.4; }
.payrollFingerprint {
  display: block;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
  font-size: 0.75rem;
}
.payrollSnapshotTechnical {
  margin-top: 0.35rem;
}
</style>
