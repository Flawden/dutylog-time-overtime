<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import { calendarTimelineApi } from "../api/calendarTimelineApi";

const props = defineProps<{ date: string; overtimeEnabled: boolean }>();
const emit = defineEmits<{ refresh: []; openSection: [section: "shift"] }>();

const truth = ref<DutyLogApiSchemas.WorkdayTruth | null>(null);
const loading = ref(false);
const error = ref("");
const editor = ref<"special" | "actual" | null>(null);

const productionKind = ref<DutyLogApiSchemas.ProductionCalendarDayInput["dayKind"]>("NORMAL");
const overrideNorm = ref(false);
const requiredHours = ref("");
const payrollEffect = ref<DutyLogApiSchemas.ProductionCalendarDayInput["payrollEffect"]>("NONE");
const productionLabel = ref("");

const actualId = ref<number | null>(null);
const actualStart = ref("");
const actualEnd = ref("");
const actualNote = ref("");

function formatMinutes(value: number | null | undefined): string {
  const minutes = Math.max(0, Math.round(Number(value ?? 0)));
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return [hours ? `${hours} ч` : "", rest ? `${rest} мин` : ""].filter(Boolean).join(" ") || "0 мин";
}

function shortTime(value: string | null | undefined): string {
  return String(value ?? "").slice(0, 5);
}

function errorMessage(value: unknown): string {
  return value instanceof Error ? value.message : "Не удалось сохранить изменения дня";
}

const shiftLabel = computed(() => {
  if (!truth.value?.shiftName) return "По графику выходной";
  const range = truth.value.scheduledStartTime && truth.value.scheduledEndTime
    ? ` · ${shortTime(truth.value.scheduledStartTime)}–${shortTime(truth.value.scheduledEndTime)}` : "";
  return `${truth.value.shiftName}${range}`;
});
const normChanged = computed(() => Boolean(truth.value && truth.value.requiredNormMinutes !== truth.value.baseNormMinutes));
const productionSpecial = computed(() => truth.value?.productionCalendar.sourceType !== "NONE");
const actualLabel = computed(() => truth.value?.explicitActual
  ? `${formatMinutes(truth.value.actualMinutes)} · отмечено фактически`
  : `${formatMinutes(truth.value?.actualMinutes)} · ${truth.value?.factLabel || "по плану"}`);
const actualDeltaMinutes = computed(() => truth.value?.explicitActual
  ? Number(truth.value.actualMinutes) - Number(truth.value.requiredNormMinutes)
  : 0);
const consequenceLabel = computed(() => {
  const value = truth.value;
  if (!value) return "—";
  const pieces: string[] = [];
  if (value.absenceMinutes > 0) pieces.push(`отсутствие ${formatMinutes(value.absenceMinutes)}`);
  if (value.overtimeEarnedMinutes > 0) pieces.push(`+${formatMinutes(value.overtimeEarnedMinutes)} в банк`);
  if (value.overtimeUsedMinutes > 0) pieces.push(`−${formatMinutes(value.overtimeUsedMinutes)} из банка`);
  if (normChanged.value) pieces.push(`норма ${formatMinutes(value.baseNormMinutes)} → ${formatMinutes(value.requiredNormMinutes)}`);
  if (value.explicitActual && actualDeltaMinutes.value > 0 && value.overtimeEarnedMinutes <= 0) {
    pieces.push(`факт выше нормы на ${formatMinutes(actualDeltaMinutes.value)} · проводка переработки пока не создана автоматически`);
  }
  if (value.explicitActual && actualDeltaMinutes.value < 0 && value.absenceMinutes <= 0) {
    pieces.push(`факт ниже нормы на ${formatMinutes(Math.abs(actualDeltaMinutes.value))} · проверь отсутствие или причину`);
  }
  return pieces.join(" · ") || "Дополнительных последствий нет";
});

function productionKindLabel(kind: string): string {
  return ({ NORMAL: "Обычный день", HOLIDAY: "Праздник", TRANSFERRED_DAY_OFF: "Перенесённый выходной", TRANSFERRED_WORKDAY: "Перенесённый рабочий день", SHORTENED_DAY: "Сокращённый день" } as Record<string,string>)[kind] ?? kind;
}

function syncProductionEditor(): void {
  const day = truth.value?.productionCalendar;
  productionKind.value = day?.dayKind ?? "NORMAL";
  overrideNorm.value = day?.scheduleEffect === "NORM_OVERRIDE";
  requiredHours.value = day?.normMinutesOverride == null ? String(Number(truth.value?.requiredNormMinutes ?? 0) / 60) : String(Number(day.normMinutesOverride) / 60);
  payrollEffect.value = day?.payrollEffect ?? "NONE";
  productionLabel.value = day?.label ?? "";
}

function applyKindDefaults(): void {
  const base = Number(truth.value?.baseNormMinutes ?? 0);
  switch (productionKind.value) {
    case "HOLIDAY":
      overrideNorm.value = true; requiredHours.value = "0"; payrollEffect.value = "HOLIDAY"; break;
    case "TRANSFERRED_DAY_OFF":
      overrideNorm.value = true; requiredHours.value = "0"; payrollEffect.value = "NONE"; break;
    case "TRANSFERRED_WORKDAY":
      overrideNorm.value = true; requiredHours.value = base > 0 ? String(base / 60) : "8"; payrollEffect.value = "NONE"; break;
    case "SHORTENED_DAY":
      overrideNorm.value = true; requiredHours.value = base > 0 ? String(base / 60) : ""; payrollEffect.value = "NONE"; break;
    default:
      overrideNorm.value = false; requiredHours.value = ""; payrollEffect.value = "NONE";
  }
}

function resetActualEditor(item: DutyLogApiSchemas.ActualWorkInterval | null = null): void {
  actualId.value = item?.id ?? null;
  actualStart.value = shortTime(item?.startTime) || shortTime(truth.value?.scheduledStartTime) || "08:00";
  actualEnd.value = shortTime(item?.endTime) || shortTime(truth.value?.scheduledEndTime) || "17:00";
  actualNote.value = item?.note ?? "";
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    truth.value = await calendarTimelineApi.workdayTruth(props.date);
    syncProductionEditor();
    if (editor.value === "actual" && actualId.value != null) {
      const current = truth.value.actualWork.find(item => Number(item.id) === Number(actualId.value)) ?? null;
      resetActualEditor(current);
    }
  } catch (caught) {
    error.value = errorMessage(caught);
  } finally {
    loading.value = false;
  }
}

async function refreshAll(): Promise<void> {
  await load();
  emit("refresh");
}

async function saveSpecialDay(): Promise<void> {
  let normMinutesOverride: number | null = null;
  if (overrideNorm.value) {
    const hours = Number(requiredHours.value);
    if (!Number.isFinite(hours) || hours < 0 || hours > 24) {
      error.value = "Укажи обязательную норму дня от 0 до 24 часов";
      return;
    }
    normMinutesOverride = Math.round(hours * 60);
  }
  loading.value = true;
  error.value = "";
  try {
    await calendarTimelineApi.saveProductionDay(props.date, {
      dayKind: productionKind.value,
      scheduleEffect: overrideNorm.value ? "NORM_OVERRIDE" : "NONE",
      normMinutesOverride,
      payrollEffect: payrollEffect.value,
      label: productionLabel.value.trim() || null,
    });
    editor.value = null;
    await refreshAll();
  } catch (caught) {
    error.value = errorMessage(caught);
  } finally {
    loading.value = false;
  }
}

async function deleteSpecialDay(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    await calendarTimelineApi.deleteProductionDay(props.date);
    editor.value = null;
    await refreshAll();
  } catch (caught) {
    error.value = errorMessage(caught);
  } finally {
    loading.value = false;
  }
}

async function saveActual(): Promise<void> {
  if (!actualStart.value || !actualEnd.value) {
    error.value = "Укажи начало и конец фактической работы";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const body: DutyLogApiSchemas.ActualWorkIntervalInput = {
      workDate: props.date,
      startTime: actualStart.value,
      endTime: actualEnd.value,
      note: actualNote.value.trim() || null,
    };
    if (actualId.value == null) await calendarTimelineApi.createActualWork(body);
    else await calendarTimelineApi.updateActualWork(actualId.value, body);
    editor.value = null;
    resetActualEditor(null);
    await refreshAll();
  } catch (caught) {
    error.value = errorMessage(caught);
  } finally {
    loading.value = false;
  }
}

async function deleteActual(id: number): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    await calendarTimelineApi.deleteActualWork(id);
    editor.value = null;
    resetActualEditor(null);
    await refreshAll();
  } catch (caught) {
    error.value = errorMessage(caught);
  } finally {
    loading.value = false;
  }
}

async function deleteSelectedActual(): Promise<void> {
  if (actualId.value == null) return;
  await deleteActual(actualId.value);
}

async function openAbsence(): Promise<void> {
  await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceComposer({ date: props.date, source: "calendar" });
}

function openActual(item: DutyLogApiSchemas.ActualWorkInterval | null = null): void {
  resetActualEditor(item);
  editor.value = "actual";
}

watch(() => props.date, () => { editor.value = null; actualId.value = null; void load(); });
onMounted(() => void load());
</script>

<template>
  <section class="nativeWorkdayCard" data-native-workday-truth>
    <header>
      <div><small>Рабочий день</small><h3>Что происходит {{ date.slice(8,10) }} числа</h3></div>
      <span v-if="loading" class="nativeWorkdayBusy">обновляю…</span>
    </header>

    <div v-if="error" class="nativeWorkdayError" role="alert">{{ error }}</div>

    <div class="nativeWorkdayTruthGrid">
      <button type="button" class="nativeTruthCell" @click="emit('openSection', 'shift')">
        <small>По графику</small><b>{{ shiftLabel }}</b><span>{{ formatMinutes(truth?.baseNormMinutes) }}</span>
      </button>
      <button type="button" class="nativeTruthCell" :class="{ changed: normChanged || productionSpecial }" @click="editor = editor === 'special' ? null : 'special'">
        <small>Обязательная норма</small><b>{{ formatMinutes(truth?.requiredNormMinutes) }}</b><span>{{ productionSpecial ? productionKindLabel(truth?.productionCalendar.dayKind || 'NORMAL') : 'Обычный день' }}</span>
      </button>
      <button v-if="overtimeEnabled" type="button" class="nativeTruthCell" :class="{ changed: truth?.explicitActual }" @click="openActual(truth?.actualWork?.[0] ?? null)">
        <small>Фактически</small><b>{{ actualLabel }}</b><span>{{ truth?.explicitActual ? 'явный факт' : 'выведено автоматически' }}</span>
      </button>
    </div>

    <div class="nativeWorkdayConsequences"><small>DutyLog уже учёл</small><b>{{ consequenceLabel }}</b></div>

    <div class="nativeWorkdayActions">
      <button type="button" @click="emit('openSection', 'shift')">Изменить смену</button>
      <button v-if="overtimeEnabled" type="button" @click="openActual(null)">Фактическая работа</button>
      <button type="button" @click="openAbsence">Отсутствовать</button>
      <button type="button" :class="{ active: editor === 'special' }" @click="editor = editor === 'special' ? null : 'special'">Особый день</button>
    </div>

    <form v-if="editor === 'special'" class="nativeWorkdayEditor" data-native-special-day-editor @submit.prevent="saveSpecialDay">
      <div class="nativeEditorHead"><b>Особый день</b><span>Дата уже выбрана: {{ date }}</span></div>
      <label>Что за день
        <select v-model="productionKind" @change="applyKindDefaults">
          <option value="NORMAL">Обычный</option>
          <option value="HOLIDAY">Праздник</option>
          <option value="TRANSFERRED_DAY_OFF">Перенесённый выходной</option>
          <option value="TRANSFERRED_WORKDAY">Перенесённый рабочий день</option>
          <option value="SHORTENED_DAY">Сокращённый день</option>
        </select>
      </label>
      <label class="nativeCheck"><input v-model="overrideNorm" type="checkbox"/> Изменить обязательную норму этого дня</label>
      <label v-if="overrideNorm">Обязательная норма, ч
        <input v-model="requiredHours" type="number" min="0" max="24" step="0.25" inputmode="decimal"/>
      </label>
      <label>Как считать работу в этот день
        <select v-model="payrollEffect"><option value="NONE">Обычно</option><option value="HOLIDAY">По праздничным правилам</option></select>
      </label>
      <label>Причина / название
        <input v-model="productionLabel" maxlength="120" placeholder="Например: предпраздничный день"/>
      </label>
      <div class="nativeEditorActions">
        <button class="primary" type="submit" :disabled="loading">Сохранить</button>
        <button v-if="truth?.productionCalendar.localOverride" type="button" :disabled="loading" @click="deleteSpecialDay">Удалить правило</button>
        <button type="button" @click="editor = null">Закрыть</button>
      </div>
      <p>Особый день меняет обязательную норму и/или категорию оплаты. Он не создаёт отгул и не двигает часы банка сам по себе.</p>
    </form>

    <div v-if="editor === 'actual' && overtimeEnabled" class="nativeWorkdayEditor" data-native-actual-work-editor>
      <div class="nativeEditorHead"><b>Фактическая работа</b><span>Вводи реальность — расчёт использует её вместо плана.</span></div>
      <div v-if="truth?.actualWork.length" class="nativeActualList">
        <button v-for="item in truth.actualWork" :key="item.id" type="button" :class="{ active: actualId === item.id }" @click="openActual(item)">
          <b>{{ shortTime(item.startTime) }}–{{ shortTime(item.endTime) }}</b><span>{{ formatMinutes(item.workedMinutes) }}{{ item.note ? ` · ${item.note}` : '' }}</span>
        </button>
      </div>
      <form class="nativeActualForm" @submit.prevent="saveActual">
        <label>Начало<input v-model="actualStart" type="time" required/></label>
        <label>Конец<input v-model="actualEnd" type="time" required/></label>
        <label class="wide">Причина / комментарий<input v-model="actualNote" maxlength="500" placeholder="Например: задержался по работе"/></label>
        <div class="nativeEditorActions wide">
          <button class="primary" type="submit" :disabled="loading">{{ actualId == null ? 'Записать факт' : 'Сохранить факт' }}</button>
          <button v-if="actualId != null" type="button" :disabled="loading" @click="deleteSelectedActual">Удалить факт</button>
          <button type="button" @click="editor = null">Закрыть</button>
        </div>
      </form>
      <p>Пока явный факт влияет на Time/Payroll read model. Автоматическое создание проводки переработки будет следующим интеграционным шагом, чтобы не дублировать существующие credits.</p>
    </div>
  </section>
</template>

<style scoped>
.nativeWorkdayCard{border:1px solid color-mix(in srgb,var(--accent) 24%,var(--border));border-radius:18px;padding:14px;background:color-mix(in srgb,var(--surface, #151922) 96%,var(--accent) 4%);display:grid;gap:12px}.nativeWorkdayCard header{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.nativeWorkdayCard header small,.nativeTruthCell small,.nativeWorkdayConsequences small{display:block;opacity:.68;font-size:.76rem}.nativeWorkdayCard h3{margin:2px 0 0;font-size:1rem}.nativeWorkdayBusy{font-size:.78rem;opacity:.7}.nativeWorkdayError{padding:9px 10px;border-radius:10px;background:color-mix(in srgb,#e45151 15%,transparent);color:#ffb8b8}.nativeWorkdayTruthGrid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.nativeTruthCell{text-align:left;border:1px solid var(--border);border-radius:12px;padding:10px;background:color-mix(in srgb,var(--surface, #151922) 92%,white 2%);display:grid;gap:3px;min-width:0}.nativeTruthCell.changed{border-color:color-mix(in srgb,var(--accent) 55%,var(--border));background:color-mix(in srgb,var(--accent) 10%,var(--surface, #151922))}.nativeTruthCell b{white-space:normal}.nativeTruthCell span{font-size:.78rem;opacity:.72}.nativeWorkdayConsequences{padding:9px 10px;border-radius:11px;background:color-mix(in srgb,var(--accent) 7%,transparent);display:grid;gap:2px}.nativeWorkdayActions{display:flex;flex-wrap:wrap;gap:7px}.nativeWorkdayActions button,.nativeEditorActions button{border-radius:10px;padding:8px 10px}.nativeWorkdayActions button.active{border-color:var(--accent)}.nativeWorkdayEditor{border-top:1px solid var(--border);padding-top:12px;display:grid;gap:10px}.nativeEditorHead{display:flex;justify-content:space-between;gap:12px;align-items:center}.nativeEditorHead span,.nativeWorkdayEditor p{font-size:.78rem;opacity:.7;margin:0}.nativeWorkdayEditor label{display:grid;gap:5px;font-size:.82rem}.nativeWorkdayEditor input,.nativeWorkdayEditor select{width:100%}.nativeCheck{display:flex!important;grid-template-columns:auto 1fr!important;align-items:center}.nativeCheck input{width:auto}.nativeEditorActions{display:flex;flex-wrap:wrap;gap:7px}.nativeActualList{display:grid;gap:6px}.nativeActualList button{text-align:left;display:flex;justify-content:space-between;gap:12px;border-radius:10px;padding:8px}.nativeActualList button.active{border-color:var(--accent)}.nativeActualForm{display:grid;grid-template-columns:1fr 1fr;gap:9px}.nativeActualForm .wide{grid-column:1/-1}@media(max-width:760px){.nativeWorkdayTruthGrid{grid-template-columns:1fr}.nativeActualForm{grid-template-columns:1fr}.nativeActualForm .wide{grid-column:auto}.nativeEditorHead{align-items:flex-start;flex-direction:column}}
</style>
