<script setup lang="ts">
import { storeToRefs } from "pinia";
import { computed, onBeforeUnmount, watch } from "vue";
import UiButton from "@/shared/ui/UiButton.vue";
import UiModal from "@/shared/overlays/UiModal.vue";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import { formatMinutes, scenarioDescription } from "../types/model";

const store = useAbsenceTimeBankStore();
const {
  creditModalOpen,
  creditDraft,
  creditPreview,
  creditPreviewLoading,
  mutationPending,
  error,
  conflict,
  scenarios,
  scenarioManagerMode,
  scenarioDraft,
} = storeToRefs(store);
const editing = computed(() => Boolean(creditDraft.value.id));
let previewTimer: number | null = null;

watch(() => [
  creditDraft.value.startDateTime,
  creditDraft.value.endDateTime,
  creditDraft.value.breakMinutes,
  creditDraft.value.plannedHours,
], () => {
  if (!creditModalOpen.value || scenarioManagerMode.value !== "closed") return;
  store.updateCreditHoursFromDraft();
  if (!creditDraft.value.startDateTime || !creditDraft.value.endDateTime) return;
  if (previewTimer !== null) globalThis.clearTimeout(previewTimer);
  previewTimer = globalThis.setTimeout(() => { void store.previewCredit(); }, 280);
});

onBeforeUnmount(() => {
  if (previewTimer !== null) globalThis.clearTimeout(previewTimer);
});

async function scenarioSelected(event: Event): Promise<void> {
  const value = Number((event.target as HTMLSelectElement).value);
  if (value) await store.applyScenario(value);
}
</script>

<template>
  <UiModal
    :open="creditModalOpen"
    :title="editing ? 'Редактировать начисление' : 'Добавить переработку'"
    description="Интервал и часы проверяет backend; существующие FIFO-списания не теряются."
    @close="store.closeCreditEditor()"
  >
    <div id="overtimeCreditModal" class="domain-modal-body" data-vue-credit-editor>
      <h3 id="overtimeCreditTitle">{{ editing ? 'Редактировать начисление' : 'Добавить переработку' }}</h3>
      <div v-if="conflict" class="domain-alert domain-alert--warning" role="alert">{{ conflict }}</div>
      <div v-if="error" class="domain-alert domain-alert--danger" role="alert">{{ error }}</div>

      <template v-if="scenarioManagerMode === 'closed'">
        <form id="overtimeCreditForm" class="domain-form" @submit.prevent="store.saveCredit()">
          <label>Дата
            <input id="creditDate" v-model="creditDraft.date" type="date" required />
          </label>
          <label>Часы вручную
            <input id="creditHours" v-model.number="creditDraft.hours" type="number" min="0" max="100" step="0.01" :readonly="Boolean(creditDraft.startDateTime && creditDraft.endDateTime)" />
          </label>
          <label>Начало интервала
            <input id="creditStart" v-model="creditDraft.startDateTime" type="datetime-local" />
          </label>
          <label>Конец интервала
            <input id="creditEnd" v-model="creditDraft.endDateTime" type="datetime-local" />
          </label>
          <label>Перерыв, минут
            <input id="creditBreak" v-model.number="creditDraft.breakMinutes" type="number" min="0" max="1440" />
          </label>
          <label>Плановые часы
            <input id="creditPlanned" v-model.number="creditDraft.plannedHours" type="number" min="0" max="100" step="0.01" />
          </label>
          <div class="domain-form__wide domain-inline-actions">
            <UiButton id="creditTimeByShift" size="sm" @click="store.fillCreditFromShift()">Взять интервал смены</UiButton>
            <span id="creditCalcHint">{{ creditPreviewLoading ? 'Проверяем…' : creditPreview ? `Начислится ${formatMinutes(creditPreview.creditedMinutes)}` : 'Можно указать часы вручную' }}</span>
          </div>
          <label class="domain-form__wide">Сценарий
            <div class="domain-scenario-picker">
              <select id="creditScenarioSelect" aria-label="Сценарий переработки" @change="scenarioSelected">
                <option value="">Не выбран</option>
                <option v-for="scenario in scenarios" :key="scenario.id" :value="scenario.id">{{ scenario.name }} — {{ scenarioDescription(scenario) }}</option>
              </select>
              <UiButton id="creditScenarioManage" size="sm" aria-label="Управление сценариями" @click="store.openScenarioManager()">⚙</UiButton>
            </div>
          </label>
          <div class="domain-form__wide">
            <UiButton id="creditScenarioSaveCurrent" size="sm" @click="store.openScenarioDraftFromCredit()">Сохранить текущие значения как сценарий</UiButton>
          </div>
          <label class="domain-form__wide">Причина
            <textarea id="creditReason" v-model="creditDraft.reason" maxlength="1000" rows="3"></textarea>
          </label>
        </form>
      </template>

      <section v-else id="scenarioManagerView" class="scenario-manager">
        <template v-if="scenarioManagerMode === 'list'">
          <div id="scenarioManagerListPane">
            <header class="domain-panel__header"><div><p class="domain-eyebrow">Повторяемые интервалы</p><h3>Сценарии переработок</h3></div><UiButton size="sm" @click="store.openNewScenarioFromManager()">＋ Новый</UiButton></header>
            <div id="scenarioManagerList" class="scenario-manager__list">
              <p v-if="!scenarios.length">Сценарии пока не созданы.</p>
              <article v-for="scenario in scenarios" :key="scenario.id" :data-scenario-row="scenario.id" class="scenario-manager__row">
                <button type="button" :data-scenario-edit="scenario.id" @click="store.editScenario(Number(scenario.id))">
                  <strong>{{ scenario.name }}</strong><span>{{ scenario.description || scenarioDescription(scenario) }}</span>
                </button>
                <div><UiButton size="sm" :data-scenario-edit="scenario.id" @click="store.editScenario(Number(scenario.id))">Ред.</UiButton><UiButton size="sm" variant="danger" :data-scenario-delete="scenario.id" @click="store.deleteScenario(Number(scenario.id))">Удалить</UiButton></div>
              </article>
            </div>
            <UiButton id="scenarioManagerBack" variant="ghost" @click="store.closeScenarioManager()">Назад к начислению</UiButton>
          </div>
        </template>
        <form v-else id="scenarioManagerForm" class="domain-form" @submit.prevent="store.saveScenario()">
          <label class="domain-form__wide">Название
            <input id="scName" v-model="scenarioDraft.name" maxlength="80" required />
          </label>
          <label>Начало
            <select v-model="scenarioDraft.startMode"><option value="SHIFT_END">От конца смены</option><option value="SHIFT_START">От начала смены</option></select>
          </label>
          <label>Окончание
            <select v-model="scenarioDraft.endMode"><option value="ADD_MINUTES">Через N минут</option><option value="SHIFT_END">Конец смены</option><option value="FIXED_TIME">Фиксированное время</option></select>
          </label>
          <label v-if="scenarioDraft.endMode === 'ADD_MINUTES'">Смещение, минут
            <input v-model.number="scenarioDraft.endOffsetMinutes" type="number" min="0" max="4320" />
          </label>
          <label v-if="scenarioDraft.endMode === 'FIXED_TIME'">Время
            <input v-model="scenarioDraft.endFixedTime" type="time" />
          </label>
          <label v-if="scenarioDraft.endMode === 'FIXED_TIME'">Смещение дня
            <input v-model.number="scenarioDraft.endDayOffset" type="number" min="-2" max="2" />
          </label>
          <label>Перерыв
            <select v-model="scenarioDraft.breakMode"><option value="ZERO">Без перерыва</option><option value="SHIFT">Как у смены</option><option value="CUSTOM">Свой</option></select>
          </label>
          <label v-if="scenarioDraft.breakMode === 'CUSTOM'">Перерыв, минут
            <input v-model.number="scenarioDraft.customBreakMinutes" type="number" min="0" max="1440" />
          </label>
          <label>План
            <select v-model="scenarioDraft.plannedMode"><option value="ZERO">Ноль</option><option value="SHIFT">План смены</option><option value="CUSTOM">Свой</option></select>
          </label>
          <label v-if="scenarioDraft.plannedMode === 'CUSTOM'">Плановые часы
            <input v-model.number="scenarioDraft.customPlannedHours" type="number" min="0" max="100" step="0.25" />
          </label>
          <label class="domain-form__wide">Описание
            <textarea v-model="scenarioDraft.description" maxlength="300" rows="2"></textarea>
          </label>
          <label class="domain-form__wide">Причина по умолчанию
            <input v-model="scenarioDraft.reasonTemplate" maxlength="300" />
          </label>
          <div class="domain-form__wide domain-inline-actions"><UiButton variant="ghost" @click="store.cancelScenarioForm()">Отмена</UiButton><UiButton id="scSave" variant="primary" type="submit" :disabled="mutationPending">Сохранить сценарий</UiButton></div>
        </form>
      </section>
    </div>
    <template #footer>
      <template v-if="scenarioManagerMode === 'closed'">
        <UiButton id="creditCancel" variant="ghost" :disabled="mutationPending" @click="store.closeCreditEditor()">Отмена</UiButton>
        <UiButton v-if="editing && creditDraft.id" variant="danger" :disabled="mutationPending" @click="store.deleteCredit(Number(creditDraft.id))">Удалить</UiButton>
        <UiButton id="creditAdd" variant="primary" :disabled="mutationPending" @click="store.saveCredit()">
          {{ mutationPending ? 'Сохраняем…' : 'Сохранить' }}
        </UiButton>
      </template>
    </template>
  </UiModal>
</template>
