<script setup lang="ts">
import { computed, nextTick, watch, type ComponentPublicInstance } from "vue";
import { storeToRefs } from "pinia";
import UiButton from "@/shared/ui/UiButton.vue";
import UiCard from "@/shared/ui/UiCard.vue";
import UiEmptyState from "@/shared/ui/UiEmptyState.vue";
import UiModal from "@/shared/overlays/UiModal.vue";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import { navigateHashRoute } from "@/platform/router/hashRoute";
import {
  dayCreditTotals,
  fifoForecast,
  fifoOpenCredits,
  formatHours,
  formatMinutes,
  formatSignedHours,
  ledgerChartColumns,
  oldestCreditRemainingMinutes,
  reservedUsageMinutes,
  uniqueSourceCredits,
  usageIsAbsenceOwned,
  usageRatio,
} from "../types/model";
import type { OvertimeCredit, OvertimeUsage } from "../types/domain";

const store = useAbsenceTimeBankStore();
const {
  account,
  compensation,
  integrity,
  actualWork,
  timeBankTab,
  rangeMode,
  loading,
  error,
  conflict,
  guideOpen,
  fifoForecastHours,
  focusAbsenceUsageId,
} = storeToRefs(store);

const emptyAccount = { totalEarnedHours: 0, totalUsedHours: 0, balanceHours: 0, credits: [], usages: [] };
const safeAccount = computed(() => account.value ?? emptyAccount);
const fifoCredits = computed(() => fifoOpenCredits(safeAccount.value));
const reservedMinutes = computed(() => reservedUsageMinutes(safeAccount.value));
const postedMinutes = computed(() => (safeAccount.value.usages ?? [])
  .filter(item => !item.reserved)
  .reduce((sum, item) => sum + Number(item.minutes ?? 0), 0));
const ratio = computed(() => usageRatio(safeAccount.value));
const oldestRemaining = computed(() => oldestCreditRemainingMinutes(safeAccount.value));
const chartColumns = computed(() => ledgerChartColumns(safeAccount.value, rangeMode.value));
const sourceCredits = computed(() => uniqueSourceCredits(safeAccount.value.credits));
const totalsByDay = computed(() => dayCreditTotals(safeAccount.value.credits));
const forecast = computed(() => fifoForecast(safeAccount.value, Math.round(Math.max(0, Number(fifoForecastHours.value || 0)) * 60)));
const maxChartHours = computed(() => Math.max(1, ...chartColumns.value.flatMap(column => [column.earnedHours, column.usedHours])));
const periodLabel = computed(() => rangeMode.value === "year" ? "Год" : "Месяц");
const integrityHealthy = computed(() => integrity.value?.healthy !== false);
const currentPeriod = computed(() => integrity.value?.periods?.find(item => String(item.month ?? "") === new Date().toISOString().slice(0, 7)) ?? null);

const usageRows = new Map<number, HTMLElement>();

const tabs = [
  { value: "overview" as const, label: "Сводка" },
  { value: "credits" as const, label: "Начисления" },
  { value: "usage" as const, label: "Использование" },
  { value: "fifo" as const, label: "FIFO" },
];

function creditUsages(credit: OvertimeCredit): OvertimeUsage[] {
  const id = Number(credit.id);
  return safeAccount.value.usages.filter(usage => (usage.allocations ?? []).some(allocation => Number(allocation.creditId) === id));
}

function isLastCreditOfDay(index: number): boolean {
  const rows = safeAccount.value.credits;
  return index === rows.length - 1 || rows[index + 1]?.workedDate !== rows[index]?.workedDate;
}

async function openAbsence(id: number | null | undefined): Promise<void> {
  if (!id) return;
  navigateHashRoute("vacation");
  await store.openAbsenceEditor(Number(id));
}

async function selectPeriod(mode: "month" | "year"): Promise<void> {
  await store.setRangeMode(mode);
}

function submitForecast(): void {
  fifoForecastHours.value = Math.max(0, Number(fifoForecastHours.value || 0));
}

function captureUsageRow(
  absenceId: number | null | undefined,
  target: Element | ComponentPublicInstance | null,
): void {
  if (!absenceId) return;
  const key = Number(absenceId);
  if (target instanceof HTMLElement) usageRows.set(key, target);
  else usageRows.delete(key);
}

watch([timeBankTab, focusAbsenceUsageId], async ([tab, id]) => {
  if (tab !== "usage" || !id) return;
  await nextTick();
  usageRows.get(Number(id))?.scrollIntoView({ block: "center", behavior: "smooth" });
});
</script>

<template>
  <main class="domain-workspace" data-vue-domain-route="overtime" data-vue-domain-owner="absence-time-bank">
    <header class="domain-hero">
      <div>
        <p class="domain-eyebrow">Vue domain · FIFO без дублирования</p>
        <h1>Банк переработок</h1>
        <p>Начисления остаются неизменными источниками. Отгулы оформляются как отсутствия, а банк показывает резерв, списание и происхождение каждой минуты.</p>
      </div>
      <div class="domain-hero__actions">
        <UiButton id="timeBankGuideOpen" variant="ghost" @click="store.openGuide()">Как работает банк</UiButton>
        <UiButton id="ledgerAddUsage" @click="store.openAbsenceComposer({ systemCode: 'TIME_OFF', source: 'time-bank' })">＋ Оформить отгул</UiButton>
        <UiButton id="ledgerAddCredit" variant="primary" @click="store.openCreditEditor()">＋ Добавить переработку</UiButton>
      </div>
    </header>

    <div v-if="error" class="domain-alert domain-alert--danger" role="alert">{{ error }} <button type="button" @click="store.refresh()">Повторить</button></div>
    <div v-if="conflict" class="domain-alert domain-alert--warning" role="alert">{{ conflict }}</div>

    <section class="domain-metrics domain-metrics--bank" aria-label="Баланс банка переработок">
      <UiCard><small>Начислено</small><strong id="ledgerEarned">{{ formatSignedHours(account?.totalEarnedHours) }}</strong><span>все источники</span></UiCard>
      <UiCard><small>Использовано</small><strong id="ledgerUsed">{{ formatSignedHours(account?.totalUsedHours, true) }}</strong><span id="timeCompUsed">{{ formatMinutes(compensation?.overtimeUsedMinutes) }}</span></UiCard>
      <UiCard><small>Зарезервировано</small><strong id="ledgerReserved">{{ formatMinutes(reservedMinutes) }}</strong><span>будущие отгулы</span></UiCard>
      <UiCard class="domain-metric--accent"><small>Свободный остаток</small><strong id="ledgerBalance">{{ formatSignedHours(account?.balanceHours) }}</strong><span id="ledgerBalanceCaption">после резервов · зарезервировано {{ formatMinutes(reservedMinutes) }}</span></UiCard>
    </section>

    <nav class="domain-tabs" role="tablist" aria-label="Разделы банка">
      <button
        v-for="tab in tabs"
        :id="`timeBankTab${tab.value === 'usage' ? 'Usage' : tab.value === 'credits' ? 'Credits' : tab.value === 'fifo' ? 'Fifo' : 'Overview'}`"
        :key="tab.value"
        type="button"
        role="tab"
        :aria-selected="timeBankTab === tab.value"
        :class="{ 'is-active': timeBankTab === tab.value, active: timeBankTab === tab.value }"
        @click="timeBankTab = tab.value"
      >{{ tab.label }}</button>
    </nav>

    <div class="ledger-insights ledger-insights--summary" data-time-bank-insights>
      <div><small>Доля использования</small><strong id="ledgerUsageRatio">{{ ratio }}%</strong><span class="ledger-progress"><i :style="{ width: `${Math.min(100, ratio)}%` }"></i></span></div>
      <div><small>Следующим спишется</small><strong id="ledgerOldestCredit">{{ formatMinutes(oldestRemaining) }}</strong><span>{{ fifoCredits[0]?.reason || 'Нет открытых начислений' }}</span></div>
    </div>

    <section v-if="timeBankTab === 'overview'" class="domain-panel" data-time-bank-section="overview">
      <header class="domain-panel__header">
        <div><p class="domain-eyebrow">Plan → fact → compensation</p><h2>Сводка периода</h2></div>
        <UiButton variant="ghost" :disabled="loading" @click="store.refresh()">Обновить</UiButton>
      </header>
      <div id="timeCompensationCard" class="compensation-card">
        <div><small>План</small><strong>{{ formatMinutes(compensation?.plannedMinutes) }}</strong></div>
        <div><small>Факт</small><strong>{{ formatMinutes(compensation?.workedMinutes) }}</strong></div>
        <div><small>Компенсировано</small><strong>{{ formatMinutes(compensation?.compensatedMinutes) }}</strong></div>
        <div><small>Без оплаты</small><strong id="timeCompUnpaid">{{ formatMinutes(compensation?.unpaidMinutes) }}</strong></div>
      </div>
      <div id="timeCompDays" class="compensation-days">
        <p><b>Списано из банка переработок:</b> {{ formatMinutes(compensation?.overtimeUsedMinutes) }}</p>
        <p><b>Неоплачиваемое время:</b> {{ formatMinutes(compensation?.unpaidMinutes) }}</p>
        <p><b>Проведено:</b> {{ formatMinutes(postedMinutes) }} · <b>Резерв:</b> {{ formatMinutes(reservedMinutes) }}</p>
      </div>

      <div class="domain-overview-grid">
        <article id="ledgerIntegrityCard" class="integrity-card" :data-integrity="integrityHealthy ? 'healthy' : 'broken'">
          <header><div><p class="domain-eyebrow">Accounting boundary</p><h3>Целостность учёта</h3></div><span id="ledgerIntegrityStatus" :class="{ error: !integrityHealthy }">{{ integrityHealthy ? '✓ Все операции согласованы' : '! Обнаружено расхождение' }}</span></header>
          <div class="integrity-metrics">
            <span>Зарезервировано <b>{{ formatMinutes(integrity?.reservedMinutes) }}</b></span>
            <span>Проведено <b>{{ formatMinutes(integrity?.postedMinutes) }}</b></span>
            <span>Возвращено <b>{{ formatMinutes(integrity?.reversedMinutes) }}</b></span>
          </div>
          <p>{{ currentPeriod?.status === 'CLOSED' ? 'Период закрыт' : 'Период открыт' }} · {{ integrity?.from }} — {{ integrity?.to }}</p>
          <div v-if="!integrityHealthy" id="ledgerIntegrityIssues" class="domain-alert domain-alert--warning">
            <p v-for="issue in integrity?.issues ?? []" :key="`${issue.code}-${issue.message}`"><b>{{ issue.code }}</b> — {{ issue.message }}</p>
          </div>
        </article>

        <article class="actual-work-card">
          <header><p class="domain-eyebrow">Explicit fact</p><h3>Фактическая работа</h3></header>
          <div id="actualWorkList" class="actual-work-list">
            <p v-if="!actualWork.length" class="domain-muted">Плановая смена используется как факт.</p>
            <div v-for="item in actualWork" :key="item.id" class="actual-work-item">
              <strong>{{ item.workDate }} · {{ item.startTime }}–{{ item.endTime }}</strong>
              <span>{{ formatMinutes(item.workedMinutes) }}{{ item.note ? ` · ${item.note}` : '' }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section v-else-if="timeBankTab === 'credits'" class="domain-panel" data-time-bank-section="credits">
      <header class="domain-panel__header">
        <div><p class="domain-eyebrow">Источники и динамика</p><h2>Начисления переработки</h2></div>
        <div class="domain-period-switch" aria-label="Период графика">
          <button id="ledgerThisMonth" type="button" :aria-pressed="rangeMode === 'month' ? 'true' : 'false'" @click="selectPeriod('month')">Месяц</button>
          <button id="ledgerThisYear" type="button" :aria-pressed="rangeMode === 'year' ? 'true' : 'false'" @click="selectPeriod('year')">Год</button>
        </div>
      </header>

      <div class="ledger-insights ledger-insights--period">
        <div><small>Период</small><strong id="ledgerPeriodLabel">{{ periodLabel }}</strong><span>{{ rangeMode === 'year' ? 'по месяцам' : 'по дням' }}</span></div>
      </div>

      <div id="ledgerChart" class="ledger-chart" role="img" :aria-label="`Начисления и списания: ${periodLabel}`">
        <div v-if="!chartColumns.length" class="domain-muted">В выбранном периоде нет операций.</div>
        <div v-for="column in chartColumns" :key="column.key" class="overtimeChartColumn" :data-series-key="column.key" :title="column.title">
          <div class="overtimeChartColumn__bars">
            <i class="earned" :style="{ height: `${Math.max(4, (column.earnedHours / maxChartHours) * 100)}%` }"></i>
            <i class="used" :style="{ height: `${Math.max(4, (column.usedHours / maxChartHours) * 100)}%` }"></i>
          </div>
          <small>{{ column.key.slice(rangeMode === 'year' ? 5 : 8) }}</small>
        </div>
      </div>

      <div class="ledgerTableWrap domain-table-wrap">
        <table class="domain-table">
          <thead><tr><th>Дата</th><th>Интервал</th><th>Причина</th><th>Начислено</th><th>Использовано</th><th>Остаток</th><th></th></tr></thead>
          <tbody id="ledgerRows">
            <template v-for="(credit, index) in account?.credits ?? []" :key="`${credit.id}-${credit.workedDate}-${credit.projection?.partIndex ?? index}`">
              <tr :data-credit-id="credit.id">
                <td>{{ credit.workedDate }}</td>
                <td>{{ credit.displayStart && credit.displayEnd ? `${credit.displayStart} — ${credit.displayEnd}` : credit.timeRange || 'вручную' }}</td>
                <td>{{ credit.reason || 'Переработка' }}</td>
                <td>{{ formatSignedHours(credit.hours) }}</td>
                <td>{{ formatSignedHours(credit.usedHours, true) }}</td>
                <td>{{ formatHours(credit.remainingHours) }}</td>
                <td><div class="domain-row-actions"><UiButton size="sm" :data-edit-credit="credit.id" @click="store.editCredit(Number(credit.id))">Изменить</UiButton><UiButton size="sm" variant="danger" @click="store.deleteCredit(Number(credit.id))">Удалить</UiButton></div></td>
              </tr>
              <tr v-if="isLastCreditOfDay(index)" class="ledger-day-total">
                <td colspan="3">итого за день</td>
                <td>{{ formatSignedHours(totalsByDay.get(credit.workedDate)?.earned) }}</td>
                <td>{{ formatSignedHours(totalsByDay.get(credit.workedDate)?.used, true) }}</td>
                <td>{{ formatHours(totalsByDay.get(credit.workedDate)?.remaining) }}</td><td></td>
              </tr>
            </template>
          </tbody>
        </table>
        <UiEmptyState v-if="!(account?.credits.length)" title="Начислений пока нет" description="Добавьте первую переработку." />
      </div>

      <div id="ledgerCards" class="domain-mobile-cards">
        <details v-for="credit in sourceCredits" :key="credit.id" class="overtimeLedgerCard">
          <summary><span><strong>{{ credit.workedDate }}</strong><small>{{ credit.reason || credit.timeRange || 'Переработка' }}</small></span><b>{{ formatSignedHours(credit.projection?.sourceRemainingHours ?? credit.remainingHours) }}</b></summary>
          <div class="overtime-ledger-card__body">
            <p>Начислено {{ formatSignedHours(credit.projection?.sourceCreditHours ?? credit.hours) }} · использовано {{ formatSignedHours(credit.usedHours, true) }}</p>
            <div v-for="usage in creditUsages(credit)" :key="usage.id" class="overtime-ledger-card__usage">{{ usage.reason || 'Отгул' }} · {{ formatMinutes(usage.minutes) }}</div>
            <UiButton size="sm" :data-edit-credit="credit.id" @click="store.editCredit(Number(credit.id))">Изменить начисление</UiButton>
          </div>
        </details>
      </div>
    </section>

    <section v-else-if="timeBankTab === 'usage'" class="domain-panel" data-time-bank-section="usage">
      <header class="domain-panel__header"><div><p class="domain-eyebrow">Отгулы</p><h2>Использование банка</h2></div></header>
      <div id="ledgerUsageList" class="usage-list">
        <UiEmptyState v-if="!(account?.usages.length)" title="Списаний пока нет" description="Отгул создаётся через единый конструктор отсутствий." />
        <article
          v-for="usage in account?.usages ?? []"
          :key="usage.id"
          class="timeBankUsageCard"
          :class="{ 'is-focused': Number(usage.sourceAbsenceId ?? 0) === focusAbsenceUsageId }"
          :data-usage-id="usage.id"
          :data-source-absence-id="usage.sourceAbsenceId ?? undefined"
          :ref="element => captureUsageRow(usage.sourceAbsenceId, element)"
        >
          <div class="usage-card__head"><div><strong>{{ usage.reason || 'Отгул' }}</strong><p>{{ usage.usageDate }} · {{ formatHours(usage.hours) }}</p></div><span class="domain-status">{{ usage.reserved ? 'Зарезервировано' : 'Проведено' }}</span></div>
          <p v-if="usageIsAbsenceOwned(usage)" class="usage-card__owner">Управляется отсутствием</p>
          <div class="usage-card__allocations">
            <div v-for="allocation in usage.allocations ?? []" :key="`${usage.id}-${allocation.creditId}-${allocation.minutes}`" class="timeBankAllocationRow">
              <span>{{ allocation.workedDate }} · {{ allocation.displayStart && allocation.displayEnd ? `${allocation.displayStart}–${allocation.displayEnd}` : allocation.timeRange || 'источник' }}</span><b>{{ formatMinutes(allocation.minutes) }}</b>
            </div>
          </div>
          <UiButton v-if="usageIsAbsenceOwned(usage)" size="sm" :data-open-absence="usage.sourceAbsenceId" @click="openAbsence(usage.sourceAbsenceId)">Открыть отсутствие</UiButton>
        </article>
      </div>
    </section>

    <section v-else class="domain-panel" data-time-bank-section="fifo">
      <header class="domain-panel__header"><div><p class="domain-eyebrow">Очередь источников</p><h2>FIFO</h2></div></header>
      <div id="ledgerFifoQueue" class="fifo-list">
        <UiEmptyState v-if="!fifoCredits.length" title="Свободных начислений нет" description="Новые списания появятся после начисления переработки." />
        <article v-for="(credit, index) in fifoCredits" :key="credit.id" class="fifo-row">
          <span>{{ index + 1 }}</span><div><strong>{{ credit.workedDate }}</strong><p>{{ credit.reason || credit.timeRange || 'Переработка' }}</p></div><b>{{ formatHours(credit.projection?.sourceRemainingHours ?? credit.remainingHours) }}</b>
        </article>
      </div>
      <form id="fifoForecastForm" class="fifo-forecast-form" @submit.prevent="submitForecast">
        <label>Будущий отгул, часов <input id="fifoForecastHours" v-model.number="fifoForecastHours" type="number" min="0" max="1000" step="0.25" /></label>
        <UiButton type="submit">Рассчитать</UiButton>
      </form>
      <div id="ledgerFifoForecast" class="domain-fifo-forecast" aria-live="polite">
        <div class="fifo-forecast-head"><strong>Будет списано {{ formatMinutes(forecast.allocatedMinutes) }}</strong><span>После этого останется {{ formatMinutes(forecast.freeAfterMinutes) }}</span></div>
        <div v-for="item in forecast.allocations" :key="`${item.creditId}-${item.minutes}`" class="fifo-forecast-row"><span>{{ item.workedDate }} · {{ item.reason }}</span><b>−{{ formatMinutes(item.minutes) }}</b></div>
        <p v-if="forecast.shortageMinutes" class="domain-preview-card__warning">Недостаточно свободных часов: {{ formatMinutes(forecast.shortageMinutes) }}</p>
        <p v-else>FIFO-порядок рассчитан по текущим свободным остаткам.</p>
      </div>
    </section>

    <UiModal :open="guideOpen" title="Как работает банк переработок" description="Короткая карта единого учёта времени." @close="store.closeGuide()">
      <div id="timeBankGuideModal" class="time-bank-guide">
        <section><b>1. Начисление</b><p>Каждая переработка остаётся отдельным неизменным источником времени.</p></section>
        <section><b>2. Отсутствие</b><p>Отгул создаётся в конструкторе отсутствий. Там находится его статус и жизненный цикл.</p></section>
        <section><b>3. FIFO</b><p>DutyLog резервирует и списывает сначала самый старый свободный остаток, сохраняя происхождение каждой минуты.</p></section>
        <section><b>4. Две стороны одной операции</b><p>Из банка можно открыть отсутствие, а из журнала отсутствий — связанное списание.</p></section>
      </div>
      <template #footer><UiButton id="timeBankGuideDone" variant="primary" @click="store.closeGuide()">Понятно</UiButton></template>
    </UiModal>
  </main>
</template>
