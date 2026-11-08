<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import { createVacationPayApi } from "./vacationPayApi";
import {
  formatVacationMoney,
  isEligibleVacation,
  localIsoDate,
  parsePositiveMinorAmount,
  provenVacationGross,
  type VacationPayAbsenceSummary,
  type VacationPayReceipt,
  type VacationPayReceiptHistory,
} from "./vacationPayModel";

const props = defineProps<{ language: string }>();
const api = createVacationPayApi();
const loading = ref(false);
const saving = ref(false);
const historyLoading = ref(false);
const historyError = ref("");
const error = ref("");
const feedback = ref("");
const absences = ref<DutyLogApiSchemas.AbsencePeriod[]>([]);
const receipts = ref<VacationPayReceipt[]>([]);
const summaries = ref<VacationPayAbsenceSummary[]>([]);
const histories = ref<VacationPayReceiptHistory[]>([]);
const selectedId = ref<number | null>(null);
const receivedDate = ref(localIsoDate(new Date()));
const amount = ref("");
const currency = ref("RUB");
const today = localIsoDate(new Date());
let historyRequest = 0;

const en = computed(() => props.language === "en");
const busy = computed(() => loading.value || saving.value);
const eligibleIds = computed(() => new Set(absences.value.filter(isEligibleVacation).map(item => item.id)));
const choices = computed(() => {
  const byId = new Map<number, { id: number; from: string; to: string; title: string; eligible: boolean }>();
  for (const absence of absences.value) {
    if (!isEligibleVacation(absence)) continue;
    byId.set(absence.id, {
      id: absence.id, from: absence.startDate, to: absence.endDate,
      title: absence.title || (en.value ? "Paid annual leave" : "Оплачиваемый отпуск"), eligible: true,
    });
  }
  for (const entry of summaries.value) {
    if (!byId.has(entry.absencePeriodId)) byId.set(entry.absencePeriodId, {
      id: entry.absencePeriodId, from: entry.vacationFrom, to: entry.vacationTo,
      title: en.value ? "Historical vacation" : "Отпуск из истории", eligible: false,
    });
  }
  for (const entry of receipts.value) {
    if (!byId.has(entry.absencePeriodId)) byId.set(entry.absencePeriodId, {
      id: entry.absencePeriodId, from: entry.vacationFrom, to: entry.vacationTo,
      title: en.value ? "Historical vacation" : "Отпуск из истории", eligible: false,
    });
  }
  return [...byId.values()].sort((a, b) => b.from.localeCompare(a.from) || b.id - a.id);
});
const selected = computed(() => choices.value.find(item => item.id === selectedId.value) ?? null);
const selectedSummary = computed(() => summaries.value.find(item => item.absencePeriodId === selectedId.value) ?? null);
const calculatedGross = computed(() => provenVacationGross(selectedSummary.value));
const canRegister = computed(() => selectedId.value !== null && eligibleIds.value.has(selectedId.value));
const selectedReceipts = computed(() => receipts.value.filter(item => item.absencePeriodId === selectedId.value));
const statusLabel = (status: string) => ({
  ACTIVE: en.value ? "Active" : "Действует",
  VOIDED: en.value ? "Voided" : "Аннулирован",
  REFUNDED: en.value ? "Fully refunded" : "Полностью возвращён",
  PARTIALLY_REFUNDED: en.value ? "Partially refunded" : "Частичный возврат",
}[status] ?? status);
function money(minor: string | number, code: string): string {
  return formatVacationMoney(minor, code, props.language);
}
function dateLabel(value: string): string {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return parts ? `${parts[3]}.${parts[2]}.${parts[1]}` : value;
}
function failure(caught: unknown): string {
  return caught instanceof Error && caught.message ? caught.message : (en.value ? "Request failed" : "Ошибка запроса");
}
async function loadHistories(): Promise<void> {
  const request = ++historyRequest;
  histories.value = [];
  historyLoading.value = false;
  historyError.value = "";
  const relevant = selectedReceipts.value;
  if (!relevant.length) return;
  historyLoading.value = true;
  try {
    const response = await Promise.all(relevant.map(item => api.history(item.id)));
    if (request === historyRequest) histories.value = response.filter((item): item is VacationPayReceiptHistory => item !== null);
  } catch (caught) {
    if (request === historyRequest) historyError.value = failure(caught);
  } finally {
    if (request === historyRequest) historyLoading.value = false;
  }
}
async function refresh(): Promise<void> {
  if (busy.value) return;
  loading.value = true;
  error.value = "";
  try {
    const [planner, allReceipts, allSummaries] = await Promise.all([
      api.planner(), api.receipts(), api.summary(),
    ]);
    absences.value = planner?.absences ?? [];
    receipts.value = allReceipts ?? [];
    summaries.value = allSummaries ?? [];
    if (!choices.value.some(item => item.id === selectedId.value)) selectedId.value = choices.value[0]?.id ?? null;
    await loadHistories();
  } catch (caught) {
    error.value = failure(caught);
  } finally {
    loading.value = false;
  }
}
async function register(): Promise<void> {
  if (busy.value || !canRegister.value || selectedId.value === null) return;
  feedback.value = "";
  error.value = "";
  const minor = parsePositiveMinorAmount(amount.value);
  const code = currency.value.trim().toUpperCase();
  if (minor === null) { error.value = en.value ? "Enter a positive amount with at most two decimals." : "Введи положительную сумму, максимум два знака после запятой."; return; }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(receivedDate.value) || receivedDate.value > today) {
    error.value = en.value ? "Choose an actual receipt date, not a future date." : "Укажи дату фактического поступления, не будущую.";
    return;
  }
  if (!/^[A-Z]{3}$/.test(code)) { error.value = en.value ? "Currency must have three Latin letters." : "Валюта — три латинские буквы (например, RUB)."; return; }
  saving.value = true;
  try {
    await api.register({ absencePeriodId: selectedId.value, receivedDate: receivedDate.value, actualReceivedAmountMinor: minor, currencyCode: code });
    amount.value = "";
    feedback.value = en.value ? "Bank transfer saved. The original entry cannot be edited." : "Перевод сохранён. Исходную запись нельзя изменять.";
  } catch (caught) {
    error.value = `${failure(caught)} ${en.value ? "If the connection failed, refresh before retrying to avoid duplicates." : "При сбое сети сначала обнови журнал, чтобы не внести перевод дважды."}`;
  } finally {
    saving.value = false;
  }
  if (feedback.value) await refresh();
}
function choose(): void { error.value = ""; void loadHistories(); }
onMounted(() => { void refresh(); });
</script>

<template>
  <section class="vpLedger" aria-labelledby="vacationPayLedgerTitle">
    <header class="vpLedgerHeader card">
      <div>
        <div class="eyebrow">{{ en ? 'Vacation transfers' : 'Фактические поступления' }}</div>
        <h3 id="vacationPayLedgerTitle">{{ en ? 'Vacation pay' : 'Отпускные' }}</h3>
        <p>{{ en ? 'Bank credits after withholding, separately from the calculated gross amount.' : 'Деньги, реально поступившие после удержаний. Расчётная сумма до удержаний учитывается отдельно.' }}</p>
      </div>
      <button type="button" :disabled="busy" @click="refresh">{{ en ? 'Refresh' : 'Обновить' }}</button>
    </header>

    <div v-if="loading && !choices.length" class="card vpEmpty" role="status">{{ en ? 'Loading vacation ledger…' : 'Загружаем журнал отпускных…' }}</div>
    <template v-else>
      <section class="card vpSelector">
        <label for="vpAbsenceSelect">{{ en ? 'Vacation' : 'Выбери отпуск' }}</label>
        <select id="vpAbsenceSelect" v-model.number="selectedId" :disabled="busy || !choices.length" @change="choose">
          <option v-if="!choices.length" :value="null">{{ en ? 'No eligible vacations' : 'Нет подтверждённых отпусков' }}</option>
          <option v-for="item in choices" :key="item.id" :value="item.id">
            {{ dateLabel(item.from) }} — {{ dateLabel(item.to) }} · {{ item.title }}{{ item.eligible ? '' : (en ? ' (history)' : ' (история)') }}
          </option>
        </select>
        <p v-if="!choices.length" class="vpHint">{{ en ? 'Approve a paid annual vacation in Absences first.' : 'Сначала подтверди ежегодный оплачиваемый отпуск в разделе «Отсутствия».' }}</p>
        <p v-else-if="selected && !selected.eligible" class="vpHint">{{ en ? 'Historical entries can be viewed, but this vacation is not currently eligible for new registrations.' : 'Исторические записи доступны для просмотра, но новые переводы по этому отпуску сейчас регистрировать нельзя.' }}</p>
      </section>

      <section v-if="selected" class="vpTotals card" aria-label="Vacation payment summary">
        <div class="vpSectionTitle">
          <h4>{{ en ? 'Received and retained' : 'Получено и осталось' }}</h4>
          <small>{{ en ? 'From immutable ledger entries' : 'По неизменяемой истории операций' }}</small>
        </div>
        <div v-if="selectedSummary?.currencyTotals.length" class="vpTotalGrid">
          <article v-for="total in selectedSummary.currencyTotals" :key="total.currencyCode" class="vpTotalItem">
            <small>{{ total.currencyCode }} · {{ en ? 'Retained' : 'Осталось после возвратов' }}</small>
            <strong>{{ money(total.netRetainedAmountMinor, total.currencyCode) }}</strong>
            <span>{{ en ? 'Received' : 'Поступило' }}: {{ money(total.receivedBeforeRefundsAmountMinor, total.currencyCode) }}</span>
            <span>{{ en ? 'Refunded' : 'Возвращено' }}: {{ money(total.refundedAmountMinor, total.currencyCode) }}</span>
          </article>
        </div>
        <div v-else class="vpHint">{{ en ? 'No transfers registered for this vacation.' : 'По этому отпуску пока нет зарегистрированных переводов.' }}</div>
        <p v-if="calculatedGross" class="vpGrossEvidence">
          <span>{{ en ? 'Calculated gross vacation pay (before withholding)' : 'Расчётные отпускные до удержаний' }}</span>
          <strong>{{ money(calculatedGross.amountMinor, calculatedGross.currencyCode) }}</strong>
        </p>
        <p class="vpDisclaimer">{{ en ? 'Calculated gross pay and actual net transfers cannot be compared as an underpayment without withholding data.' : 'Расчётные отпускные до удержаний и фактические поступления после удержаний нельзя напрямую сравнивать как недоплату.' }}</p>
        <p v-if="selectedSummary?.calculationSnapshot" class="vpHint">{{ en ? 'Calculation evidence' : 'Расчётные данные' }}: {{ selectedSummary.calculationSnapshot.status }}<template v-if="selectedSummary.calculationSnapshot.blockingReason"> · {{ selectedSummary.calculationSnapshot.blockingReason }}</template></p>
      </section>

      <div v-if="selected" class="vpColumns">
        <section class="card vpForm">
          <h4>{{ en ? 'Record bank transfer' : 'Записать поступление' }}</h4>
          <p class="vpHint">{{ en ? 'Enter the actual amount credited to your account. Each bank transfer is a separate entry.' : 'Укажи сумму, которая действительно пришла на счёт. Каждый перевод — отдельная запись.' }}</p>
          <form @submit.prevent="register">
            <label for="vpReceivedDate">{{ en ? 'Received on' : 'Дата поступления' }}</label>
            <input id="vpReceivedDate" v-model="receivedDate" type="date" :max="today" :disabled="busy || !canRegister" required />
            <label for="vpAmount">{{ en ? 'Net amount' : 'Полученная сумма (после удержаний)' }}</label>
            <input id="vpAmount" v-model="amount" inputmode="decimal" placeholder="48000,00" :disabled="busy || !canRegister" required />
            <label for="vpCurrency">{{ en ? 'Currency' : 'Валюта' }}</label>
            <input id="vpCurrency" v-model="currency" maxlength="3" autocomplete="off" placeholder="RUB" :disabled="busy || !canRegister" required />
            <button type="submit" class="primary" :disabled="busy || !canRegister">{{ saving ? (en ? 'Saving…' : 'Сохраняем…') : (en ? 'Record transfer' : 'Сохранить перевод') }}</button>
          </form>
          <p class="vpHint">{{ en ? 'This first version records actual money without requesting a new pay calculation.' : 'Эта версия записывает факт получения денег, не требуя нового расчёта отпускных.' }}</p>
        </section>

        <section class="card vpHistory">
          <div class="vpSectionTitle"><h4>{{ en ? 'Transfer history' : 'История переводов' }}</h4><small>{{ selectedReceipts.length }}</small></div>
          <div v-if="historyLoading" class="vpHint" role="status">{{ en ? 'Loading history…' : 'Загружаем историю…' }}</div>
          <div v-else-if="historyError" class="vpError" role="alert">{{ en ? 'Could not load transfer history. Refresh to try again.' : 'Не удалось загрузить историю переводов. Нажми «Обновить», чтобы повторить.' }} {{ historyError }}</div>
          <div v-else-if="!selectedReceipts.length" class="vpHint">{{ en ? 'No recorded transfers yet.' : 'Переводов пока нет.' }}</div>
          <div v-else-if="!histories.length" class="vpHint">{{ en ? 'Transfer history is unavailable.' : 'История переводов недоступна.' }}</div>
          <div v-else class="vpHistoryList">
            <article v-for="item in histories" :key="item.receipt.id" class="vpHistoryItem">
              <div class="vpHistoryTop">
                <div><strong>{{ money(item.receipt.actualReceivedAmountMinor, item.receipt.currencyCode) }}</strong><small>{{ dateLabel(item.receipt.receivedDate) }} · #{{ item.receipt.id }}</small></div>
                <span class="vpStatus" :class="{ 'vpStatus--void': item.status === 'VOIDED' }">{{ statusLabel(item.status) }}</span>
              </div>
              <p v-if="item.status !== 'VOIDED' && item.status !== 'ACTIVE'" class="vpHint">{{ en ? 'Remaining' : 'Осталось' }}: {{ money(item.remainingAmountMinor, item.receipt.currencyCode) }}</p>
              <div v-for="event in item.adjustments" :key="event.id" class="vpAdjustment">
                <small>{{ event.adjustmentType === 'VOID' ? (en ? 'Cancelled' : 'Аннулирование') : (en ? 'Refund' : 'Возврат денег') }} · {{ dateLabel(event.eventDate) }}</small>
                <span v-if="event.adjustmentType === 'REFUND'">−{{ money(event.amountMinor, item.receipt.currencyCode) }}</span>
                <p>{{ event.reason }}</p>
              </div>
            </article>
          </div>
        </section>
      </div>
    </template>
    <p v-if="feedback" class="vpFeedback" role="status" aria-live="polite">{{ feedback }}</p>
    <p v-if="error" class="vpError" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.vpLedger { display: grid; gap: 16px; min-width: 0; }
.vpLedger .card { min-width: 0; padding: 20px; }
.vpLedger h3, .vpLedger h4 { margin: 0; }
.vpLedger h3 { font-size: 1.5rem; }
.vpLedger h4 { font-size: 1.06rem; }
.vpLedger p { margin: 0; line-height: 1.5; }
.vpLedgerHeader { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 15px; }
.vpLedgerHeader > div { display: grid; gap: 8px; max-width: 700px; }
.vpLedgerHeader p, .vpHint, .vpDisclaimer { color: var(--mut); font-size: .86rem; }
.vpLedgerHeader button { flex: none; }
.vpSelector { display: grid; gap: 8px; }
.vpSelector select, .vpForm input { width: 100%; min-height: 42px; border: 1px solid var(--line); background: var(--panel2); color: var(--text); border-radius: 9px; padding: 8px 12px; }
.vpSelector label, .vpForm label { font-weight: 650; font-size: .86rem; }
.vpSectionTitle { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.vpSectionTitle small { color: var(--mut); }
.vpTotalGrid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.vpTotalItem { display: grid; gap: 6px; background: var(--panel2); border: 1px solid var(--line); border-radius: 12px; padding: 15px; }
.vpTotalItem strong { font-size: 1.25rem; }
.vpTotalItem small, .vpTotalItem span { color: var(--mut); font-size: .82rem; }
.vpGrossEvidence { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 8px; margin-top: 14px !important; padding: 12px; background: var(--panel2); border: 1px solid var(--line); border-radius: 10px; }
.vpGrossEvidence span { color: var(--mut); font-size: .86rem; }
.vpDisclaimer { margin-top: 14px !important; }
.vpColumns { display: grid; grid-template-columns: minmax(270px, .9fr) minmax(0, 1.3fr); gap: 16px; }
.vpForm, .vpHistory { display: grid; align-content: start; gap: 12px; }
.vpForm form { display: grid; gap: 9px; }
.vpForm form button { margin-top: 8px; min-height: 44px; }
.vpHistoryList { display: grid; gap: 10px; }
.vpHistoryItem { padding: 14px; background: var(--panel2); border: 1px solid var(--line); border-radius: 12px; min-width: 0; }
.vpHistoryTop { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
.vpHistoryTop > div { display: grid; gap: 5px; min-width: 0; }
.vpHistoryTop small { color: var(--mut); }
.vpStatus { font-size: .76rem; font-weight: 700; color: var(--ok); white-space: nowrap; }
.vpStatus--void { color: var(--bad); }
.vpAdjustment { display: grid; gap: 4px; padding: 10px 0 0; margin-top: 10px; border-top: 1px solid var(--line); }
.vpAdjustment p { font-size: .82rem; overflow-wrap: anywhere; }
.vpAdjustment small { color: var(--mut); }
.vpFeedback { color: var(--ok); }
.vpError { color: var(--bad); overflow-wrap: anywhere; }
@media (max-width: 700px) { .vpColumns { grid-template-columns: 1fr; } .vpLedger .card { padding: 15px; } }
</style>
