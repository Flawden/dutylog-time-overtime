<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from "vue";
import { storeToRefs } from "pinia";
import UiButton from "@/shared/ui/UiButton.vue";
import UiModal from "@/shared/overlays/UiModal.vue";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import { compensationContext, fifoForecast, formatMinutes } from "../types/model";

const store = useAbsenceTimeBankStore();
const {
  absenceModalOpen,
  absenceDraft,
  planner,
  account,
  absencePreview,
  previewLoading,
  mutationPending,
  conflict,
  error,
} = storeToRefs(store);
let previewTimer: number | null = null;

const editing = computed(() => Boolean(absenceDraft.value.id));
const title = computed(() => editing.value ? "Редактировать отсутствие" : "Оформить отсутствие");
const previewDuration = computed(() => formatMinutes(absencePreview.value?.durationMinutes ?? 0));
const previewBalance = computed(() => formatMinutes(absencePreview.value?.timeOffRemainingAfter ?? planner.value?.summary.timeOffRemainingMinutes));
const contextMessage = computed(() => compensationContext(absenceDraft.value.compensationPolicy, planner.value));
const forecast = computed(() => fifoForecast(
  account.value ?? { totalEarnedHours: 0, totalUsedHours: 0, balanceHours: 0, credits: [], usages: [] },
  Number(absencePreview.value?.durationMinutes ?? 0),
  absenceDraft.value.id,
));

watch(() => [
  absenceDraft.value.typeId,
  absenceDraft.value.startDate,
  absenceDraft.value.endDate,
  absenceDraft.value.coverage,
  absenceDraft.value.startTime,
  absenceDraft.value.endTime,
  absenceDraft.value.compensationPolicy,
], () => {
  if (!absenceModalOpen.value) return;
  if (absenceDraft.value.coverage === "PARTIAL") store.syncAbsenceCoverage();
  if (previewTimer !== null) globalThis.clearTimeout(previewTimer);
  previewTimer = globalThis.setTimeout(() => { void store.previewAbsence(); }, 260);
});

onBeforeUnmount(() => {
  if (previewTimer !== null) globalThis.clearTimeout(previewTimer);
});

function typeChanged(): void {
  store.syncCompensationFromType();
}
</script>

<template>
  <UiModal
    :open="absenceModalOpen"
    :title="title"
    description="Backend проверит баланс, пересечения, статус и FIFO до сохранения."
    @close="store.closeAbsenceComposer()"
  >
    <div id="absenceComposerModal" class="domain-modal-body" data-vue-absence-composer>
      <h3 id="vacationEditorTitle" class="domain-visually-hidden">{{ title }}</h3>
      <div v-if="conflict" class="domain-alert domain-alert--warning" role="alert">{{ conflict }}</div>
      <div v-if="error" class="domain-alert domain-alert--danger" role="alert">{{ error }}</div>
      <form class="domain-form" @submit.prevent="store.saveAbsence()">
        <label>Тип
          <select id="vacationType" v-model.number="absenceDraft.typeId" required @change="typeChanged">
            <option v-for="type in planner?.types ?? []" :key="type.id" :value="type.id">{{ type.name }}</option>
          </select>
        </label>
        <label>Статус
          <select id="vacationStatus" v-model="absenceDraft.status">
            <option value="DRAFT">Черновик</option>
            <option value="PLANNED">Запланировано</option>
            <option value="SUBMITTED">На согласовании</option>
            <option value="APPROVED">Подтверждено</option>
            <option value="REJECTED">Отклонено</option>
            <option value="CANCELLED">Отменено</option>
            <option value="COMPLETED">Завершено</option>
          </select>
        </label>
        <label class="domain-form__wide">Причина
          <input id="vacationTitle" v-model="absenceDraft.title" maxlength="120" required placeholder="Например: основной отпуск" />
        </label>
        <label>Начало
          <input id="vacationStart" v-model="absenceDraft.startDate" type="date" required />
        </label>
        <label>Окончание
          <input id="vacationEnd" v-model="absenceDraft.endDate" type="date" required :disabled="absenceDraft.coverage === 'PARTIAL'" />
        </label>
        <div v-if="absenceDraft.coverage !== 'PARTIAL'" class="domain-duration-presets domain-form__wide" aria-label="Быстрый выбор длительности">
          <button v-for="days in planner?.durationPresets ?? []" :key="days" type="button" :data-vacation-days="days" @click="store.setAbsenceDuration(Number(days))">{{ days }} дней</button>
        </div>
        <label>Формат
          <select id="vacationCoverage" v-model="absenceDraft.coverage" @change="store.syncAbsenceCoverage()">
            <option value="FULL_DAY">Полный день</option>
            <option value="PARTIAL">Часть дня</option>
            <option v-if="absenceDraft.coverage === 'HOURS_ONLY'" value="HOURS_ONLY">Объём часов</option>
          </select>
        </label>
        <label>Источник покрытия
          <select id="vacationCompensation" v-model="absenceDraft.compensationPolicy">
            <option value="VACATION_ALLOWANCE">Отпускные дни</option>
            <option value="OVERTIME_BANK">Банк переработок</option>
            <option value="SICK_PAY">Больничный</option>
            <option value="UNPAID">Без оплаты</option>
            <option value="NONE">Без компенсации</option>
          </select>
        </label>
        <div v-if="absenceDraft.coverage === 'PARTIAL'" id="vacationPartialTimes" class="domain-form__times domain-form__wide">
          <label>С
            <input id="vacationStartTime" v-model="absenceDraft.startTime" type="time" required />
          </label>
          <label>До
            <input id="vacationEndTime" v-model="absenceDraft.endTime" type="time" required />
          </label>
        </div>
        <label class="domain-form__wide">Комментарий
          <textarea v-model="absenceDraft.note" maxlength="1000" rows="3"></textarea>
        </label>
      </form>

      <section id="absenceComposerContext" class="domain-context-card" aria-live="polite">
        <strong>{{ contextMessage }}</strong>
      </section>

      <section id="vacationPreview" class="domain-preview-card" aria-live="polite">
        <div><small>Период</small><strong>{{ previewLoading ? 'Проверяем…' : `${absencePreview?.calendarDays ?? 0} дн.` }}</strong></div>
        <div><small>Учитывается</small><strong>{{ absencePreview?.countedDays ?? 0 }} дн.</strong></div>
        <div><small>Объём</small><strong>{{ previewDuration }}</strong></div>
        <div><small>Остаток</small><strong>{{ previewBalance }}</strong></div>
        <p v-if="Number(absencePreview?.shiftConflictCount ?? 0) > 0" class="domain-preview-card__warning">Есть пересечения со сменами: {{ absencePreview?.shiftConflictCount }}</p>
        <p v-if="Number(absencePreview?.absenceConflictCount ?? 0) > 0" class="domain-preview-card__warning">Есть пересечения с отсутствиями: {{ absencePreview?.absenceConflictCount }}</p>
      </section>

      <section v-if="absenceDraft.compensationPolicy === 'OVERTIME_BANK'" id="absenceFifoForecast" class="domain-fifo-forecast">
        <h3>FIFO-прогноз</h3>
        <p v-if="!forecast.requestedMinutes">Укажите длительность отсутствия — DutyLog покажет источники списания.</p>
        <div v-for="item in forecast.allocations" :key="`${item.creditId}-${item.minutes}`" class="fifo-forecast-row">
          <span>{{ item.workedDate }} · {{ item.reason }}</span><b>−{{ formatMinutes(item.minutes) }}</b>
        </div>
        <p v-if="forecast.shortageMinutes" class="domain-preview-card__warning">Недостаточно свободного времени: {{ formatMinutes(forecast.shortageMinutes) }}</p>
        <p v-else-if="forecast.requestedMinutes">После этого останется {{ formatMinutes(forecast.freeAfterMinutes) }}</p>
      </section>
    </div>
    <template #footer>
      <UiButton id="absenceComposerClose" variant="ghost" :disabled="mutationPending" @click="store.closeAbsenceComposer()">Отмена</UiButton>
      <UiButton id="vacationPreviewBtn" variant="secondary" :disabled="previewLoading || mutationPending" @click="store.previewAbsence()">Проверить</UiButton>
      <UiButton id="vacationSaveBtn" variant="primary" :disabled="mutationPending" @click="store.saveAbsence()">
        {{ mutationPending ? 'Сохраняем…' : 'Сохранить' }}
      </UiButton>
    </template>
  </UiModal>
</template>
