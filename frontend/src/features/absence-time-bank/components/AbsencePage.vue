<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import UiButton from "@/shared/ui/UiButton.vue";
import UiCard from "@/shared/ui/UiCard.vue";
import UiEmptyState from "@/shared/ui/UiEmptyState.vue";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import { absenceDisplayTitle, formatMinutes } from "../types/model";
import type { AbsencePeriod } from "../types/domain";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const store = useAbsenceTimeBankStore();
const { planner, filteredAbsences, loading, error, conflict, periodFilter, search } = storeToRefs(store);
const summary = computed(() => planner.value?.summary);

function statusLabel(status: string | undefined): string {
  return ({ DRAFT:"Черновик", PLANNED:"Запланировано", SUBMITTED:"На согласовании", APPROVED:"Подтверждено", REJECTED:"Отклонено", CANCELLED:"Отменено", COMPLETED:"Завершено" } as Record<string,string>)[String(status)] ?? String(status ?? "—");
}

function periodRange(start: string, end: string): string {
  return start === end ? start : `${start} — ${end}`;
}

async function openTimeBank(period: AbsencePeriod | null = null): Promise<void> {
  navigateHashRoute("overtime");
  await store.openTimeBankUsage(period ? Number(period.id) : null);
}
</script>

<template>
  <main class="domain-workspace" data-vue-domain-route="vacation" data-vue-domain-owner="absence-time-bank">
    <header class="domain-hero">
      <div>
        <p class="domain-eyebrow">Vue domain · единый источник истины</p>
        <h1>Отсутствия</h1>
        <p>Оформляйте отпуск, отгул, больничный и отсутствие без оплаты. Балансы, пересечения и компенсацию проверяет Spring Boot.</p>
      </div>
      <UiButton id="vacationComposerOpen" variant="primary" @click="store.openAbsenceComposer()">＋ Оформить отсутствие</UiButton>
    </header>

    <div v-if="error" class="domain-alert domain-alert--danger" role="alert">{{ error }} <button type="button" @click="store.refresh()">Повторить</button></div>
    <div v-if="conflict" class="domain-alert domain-alert--warning" role="alert">{{ conflict }}</div>

    <section class="domain-metrics" aria-label="Баланс отсутствий">
      <UiCard><small>Доступно</small><strong id="vacationAvailable">{{ summary?.availableDays ?? 0 }} дн.</strong><span>норма и перенос</span></UiCard>
      <UiCard><small>Запланировано</small><strong id="vacationPlanned">{{ summary?.plannedDays ?? 0 }} дн.</strong><span>текущий рабочий год</span></UiCard>
      <UiCard><small>Осталось</small><strong id="vacationRemaining">{{ summary?.remainingDays ?? 0 }} дн.</strong><span>{{ summary?.countMode === 'WEEKDAYS' ? 'рабочие дни' : 'календарные дни' }}</span></UiCard>
      <UiCard class="domain-metric--accent"><small>Свободно в банке</small><strong id="timeOffRemaining">{{ formatMinutes(summary?.timeOffRemainingMinutes) }}</strong><span>после резервов</span><UiButton id="vacationOpenTimeBank" size="sm" @click="openTimeBank()">Детализация →</UiButton></UiCard>
    </section>

    <section class="domain-panel">
      <header class="domain-panel__header">
        <div><p class="domain-eyebrow">Журнал</p><h2>Все периоды отсутствия</h2></div>
        <div class="domain-toolbar">
          <input v-model="search" type="search" placeholder="Поиск по причине или дате" aria-label="Поиск отсутствий" />
          <select v-model="periodFilter" aria-label="Фильтр отсутствий">
            <option value="all">Все</option>
            <option value="active">Активные</option>
            <option value="history">История</option>
          </select>
          <UiButton variant="ghost" :disabled="loading" @click="store.refresh()">Обновить</UiButton>
        </div>
      </header>

      <div id="vacationPeriodList" class="absence-list" aria-live="polite">
        <UiEmptyState v-if="!loading && !filteredAbsences.length" title="Пока нет отсутствий" description="Создайте первый период через единый Vue-конструктор.">
          <template #actions><UiButton variant="primary" @click="store.openAbsenceComposer()">Оформить отсутствие</UiButton></template>
        </UiEmptyState>
        <article v-for="period in filteredAbsences" :key="period.id" class="absence-row vacationPeriodCard" :data-absence-id="period.id">
          <span class="absence-row__color" :style="{ background: period.typeColor || 'var(--accent)' }" aria-hidden="true"></span>
          <div class="absence-row__main">
            <div><strong>{{ absenceDisplayTitle(period) }}</strong><span class="domain-status">{{ statusLabel(period.status) }}</span></div>
            <p>{{ periodRange(period.startDate, period.endDate) }} · {{ period.typeName }}</p>
            <small>
              {{ period.coverage === 'PARTIAL' ? `${period.startTime ?? '—'}–${period.endTime ?? '—'}` : `${period.countedDays ?? period.calendarDays ?? 0} дн.` }}
              · {{ formatMinutes(period.compensatedMinutes ?? period.chargedMinutes) }}
              <template v-if="period.compensationPolicy === 'OVERTIME_BANK'"> · FIFO из банка</template>
            </small>
          </div>
          <div class="absence-row__actions">
            <UiButton v-if="period.linkedOvertimeUsageId" size="sm" :data-bank-absence="period.id" @click="openTimeBank(period)">Посмотреть списание</UiButton>
            <UiButton size="sm" :data-edit-absence="period.id" @click="store.openAbsenceEditor(Number(period.id))">Изменить</UiButton>
            <UiButton size="sm" variant="danger" :data-delete-absence-row="period.id" @click="store.deleteAbsence(Number(period.id))">Удалить</UiButton>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>
