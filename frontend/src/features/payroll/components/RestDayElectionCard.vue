<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { DutyLogApiSchemas as S } from "@/generated/dutylog-api";
import { createPayrollApi } from "../api/payrollApi";

const props = defineProps<{ month: string; language: string }>();
const emit = defineEmits<{ saved: [] }>();
const api = createPayrollApi();
const english = computed(() => props.language === "en");
const opened = ref(false);
const busy = ref(false);
const message = ref("");
const workspace = ref<S.Article153ElectionWorkspace | null>(null);
let sequence = 0;

async function load() {
  const ticket = ++sequence;
  const currentMonth = props.month;
  busy.value = true;
  message.value = "";
  workspace.value = null;
  try {
    const data = await api.restDayElections(currentMonth);
    if (ticket === sequence && currentMonth === props.month) workspace.value = data;
  } catch (error) {
    if (ticket === sequence) message.value = error instanceof Error ? error.message : String(error);
  } finally {
    if (ticket === sequence) busy.value = false;
  }
}

async function elect(choice: S.Article153ElectionChoice) {
  if (busy.value || choice.state !== "NONE" || !choice.sourceFingerprint) return;
  const currentMonth = props.month;
  if (!globalThis.confirm(english.value
    ? "Choose another rest day for this work event? Corrections require separate review."
    : "Выбрать другой день отдыха за эту работу? Исправление выбора потребует отдельной проверки.")) return;
  const ticket = ++sequence;
  busy.value = true;
  message.value = "";
  try {
    const body: S.Article153ElectionRequest = {
      workDate: choice.workDate,
      sourceKind: choice.sourceKind,
      sourceEntityId: choice.sourceEntityId,
      expectedSourceFingerprint: choice.sourceFingerprint,
    };
    await api.electRestDay(currentMonth, body);
    if (ticket !== sequence || currentMonth !== props.month) return;
    await load();
    if (currentMonth !== props.month || !workspace.value) return;
    message.value = english.value
      ? "Rest-day election saved. Recalculate the month to create a new payroll revision."
      : "Выбор другого дня отдыха сохранён. Пересчитай месяц для новой ревизии зарплаты.";
    emit("saved");
  } catch (error) {
    if (ticket === sequence) message.value = error instanceof Error ? error.message : String(error);
  } finally {
    if (ticket === sequence) busy.value = false;
  }
}

function toggle() {
  opened.value = !opened.value;
  if (opened.value) void load();
  else { ++sequence; busy.value = false; workspace.value = null; message.value = ""; }
}
watch(() => props.month, () => { ++sequence; busy.value = false; if (opened.value) void load(); });
</script>

<template>
  <section class="card rest-election-card" data-testid="article153-rest-election">
    <h3>{{ english ? 'Compensation for holiday / rest-day work' : 'Компенсация работы в праздник или выходной' }}</h3>
    <p>{{ english
      ? 'Choose another rest day for one verified work event. This is not an overtime-bank withdrawal and does not schedule the rest day itself.'
      : 'Выбери другой день отдыха за конкретную подтверждённую работу. Это не списание из банка переработок и не назначение даты отдыха.' }}</p>
    <button type="button" :disabled="busy" :aria-expanded="opened" @click="toggle">
      {{ opened ? (english ? 'Collapse' : 'Свернуть') : (english ? 'Show eligible work' : 'Показать подходящую работу') }}
    </button>
    <template v-if="opened">
      <p v-if="busy" role="status">{{ english ? 'Loading / saving…' : 'Загрузка / сохранение…' }}</p>
      <p v-if="message" role="status">{{ message }}</p>
      <template v-if="workspace">
        <p v-if="!workspace.ready" class="rest-election-warning" data-testid="article153-rest-blocked">
          {{ english ? 'Source evidence is incomplete. Choices are disabled.' : 'Основания расчёта не подтверждены. Выбор недоступен.' }}
          <small v-for="(reason, index) in workspace.blockers" :key="index">{{ reason }}</small>
        </p>
        <p v-else-if="!workspace.choices.length">
          {{ english ? 'No qualifying events in this month.' : 'За этот месяц подходящих событий работы не найдено.' }}
        </p>
        <ul v-else class="rest-election-list">
          <li v-for="choice in workspace.choices" :key="`${choice.workDate}-${choice.sourceKind}-${choice.sourceEntityId}`"
            class="rest-election-row" :data-state="choice.state">
            <div>
              <strong>{{ choice.workDate }}</strong>
              <small>{{ choice.sourceKind === 'EXPLICIT' ? (english ? 'Recorded work' : 'Фактическая работа') : (english ? 'Work from shift plan' : 'Работа по графику') }} · {{ Math.floor(choice.qualifiedMinutes / 60) }} {{ english ? 'h' : 'ч' }} {{ choice.qualifiedMinutes % 60 }} {{ english ? 'min' : 'мин' }}</small>
              <small>{{ english ? 'Work event' : 'Событие работы' }} № {{ choice.sourceEntityId }}</small>
              <small v-if="choice.state === 'ELECTED'">{{ english ? 'Another rest day selected' : 'Выбран другой день отдыха' }}</small>
              <small v-else-if="choice.state === 'NONE'">{{ english ? 'Another rest day not selected' : 'Другой день отдыха пока не выбран' }}</small>
              <small v-else>{{ english ? 'Needs review' : 'Требуется проверка' }}: {{ choice.blockingReason }}</small>
            </div>
            <button v-if="choice.state === 'NONE'" type="button"
              :disabled="busy || !choice.sourceFingerprint" @click="elect(choice)">
              {{ english ? 'Choose another rest day' : 'Выбрать другой день отдыха' }}
            </button>
          </li>
        </ul>
      </template>
      <button v-if="workspace" type="button" :disabled="busy" @click="load">{{ english ? 'Refresh events' : 'Обновить события' }}</button>
    </template>
  </section>
</template>

<style scoped>
.rest-election-card { display: grid; gap: .75rem; }
.rest-election-card p { margin: 0; }
.rest-election-warning { display: grid; gap: .25rem; }
.rest-election-warning small { overflow-wrap: anywhere; }
.rest-election-list { list-style: none; padding: 0; margin: 0; display: grid; gap: .5rem; }
.rest-election-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: .75rem; padding: .75rem; border: 1px solid var(--border-color, #888); border-radius: .6rem; }
.rest-election-row > div { display: grid; gap: .2rem; }
.rest-election-row small { display: block; }
</style>
