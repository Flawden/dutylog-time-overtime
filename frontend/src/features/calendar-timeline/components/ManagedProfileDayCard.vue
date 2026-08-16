<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import type { CalendarLayer, CalendarLayerEntry, CalendarShiftType } from "../types/domain";

const props = defineProps<{
  profile: CalendarLayer;
  date: string;
  entry: CalendarLayerEntry | null;
  shiftTypes: CalendarShiftType[];
  language: string;
}>();

const store = useCalendarTimelineStore();
const editing = ref(false);
const mode = ref<"SCHEDULE" | "OFF" | "WORK">("SCHEDULE");
const reason = ref<"TIME_OFF" | "VACATION" | "SICK" | "OTHER">("TIME_OFF");
const shiftTypeId = ref<number | null>(null);
const startTime = ref("");
const endTime = ref("");
const saving = ref(false);
const saveError = ref("");

const workingShifts = computed(() => props.shiftTypes.filter(shift =>
  Number(shift.plannedHours ?? 0) > 0 || Boolean(shift.startTime && shift.endTime)
));
const selectedShift = computed(() => workingShifts.value.find(shift => shift.id === shiftTypeId.value) ?? null);
const overrideDate = computed(() => props.entry?.sourceDate || props.date);

function reasonLabel(value: string | null | undefined): string {
  return ({
    TIME_OFF: props.language === "en" ? "Time off" : "Отгул",
    VACATION: props.language === "en" ? "Vacation" : "Отпуск",
    SICK: props.language === "en" ? "Sick leave" : "Больничный",
    OTHER: props.language === "en" ? "Other" : "Другое",
  } as Record<string, string>)[String(value ?? "OTHER")] ?? String(value ?? "");
}

const formatDate = computed(() => {
  try {
    return new Intl.DateTimeFormat(props.language === "en" ? "en-GB" : "ru-RU", {
      day: "numeric", month: "long", weekday: "long", timeZone: "UTC"
    }).format(new Date(`${props.date}T00:00:00Z`));
  } catch {
    return props.date;
  }
});

const plannedLabel = computed(() => {
  if (props.entry?.overrideKind) {
    return props.entry.plannedShiftTypeName || (props.language === "en" ? "Day off" : "Выходной");
  }
  if (!props.entry || props.entry.dayOff) return props.language === "en" ? "Day off" : "Выходной";
  return props.entry.shiftTypeName || (props.language === "en" ? "Work" : "Работает");
});

const actualLabel = computed(() => {
  if (props.entry?.overrideKind === "OFF") {
    return `${props.language === "en" ? "Not working" : "Не работает"} · ${reasonLabel(props.entry.overrideReason)}`;
  }
  if (!props.entry || props.entry.dayOff) return props.language === "en" ? "Day off" : "Выходной";
  const range = props.entry.sourceStartTime && props.entry.sourceEndTime
    ? ` · ${props.entry.sourceStartTime}–${props.entry.sourceEndTime}`
    : "";
  return `${props.entry.shiftTypeName || (props.language === "en" ? "Work" : "Работает")}${range}`;
});

function loadEditor(): void {
  saveError.value = "";
  if (props.entry?.overrideKind === "OFF") {
    mode.value = "OFF";
    reason.value = (props.entry.overrideReason || "TIME_OFF") as typeof reason.value;
  } else if (props.entry?.overrideKind === "WORK") {
    mode.value = "WORK";
  } else {
    mode.value = "SCHEDULE";
  }

  const fallback = workingShifts.value[0] ?? null;
  shiftTypeId.value = Number(props.entry?.shiftTypeId ?? fallback?.id ?? 0) || null;
  const shift = workingShifts.value.find(item => item.id === shiftTypeId.value) ?? fallback;
  startTime.value = props.entry?.sourceStartTime || shift?.startTime || "";
  endTime.value = props.entry?.sourceEndTime || shift?.endTime || "";
}

watch(
  () => [
    props.profile.id,
    props.date,
    props.entry?.overrideKind,
    props.entry?.overrideReason,
    props.entry?.shiftTypeId,
    props.entry?.sourceStartTime,
    props.entry?.sourceEndTime
  ],
  loadEditor,
  { immediate: true }
);

function selectShift(): void {
  const shift = selectedShift.value;
  startTime.value = shift?.startTime || "";
  endTime.value = shift?.endTime || "";
}

function mutationError(error: unknown, fallback: string): string {
  return store.error || (error instanceof Error && error.message ? error.message : fallback);
}

async function save(): Promise<void> {
  saving.value = true;
  saveError.value = "";
  try {
    if (mode.value === "SCHEDULE") {
      await store.resetProfileDayOverride(props.profile.id, overrideDate.value);
    } else if (mode.value === "OFF") {
      await store.saveProfileDayOverride(props.profile.id, overrideDate.value, {
        kind: "OFF",
        reason: reason.value,
      });
    } else {
      if (shiftTypeId.value == null) return;
      await store.saveProfileDayOverride(props.profile.id, overrideDate.value, {
        kind: "WORK",
        shiftTypeId: shiftTypeId.value,
        startTime: startTime.value || null,
        endTime: endTime.value || null,
      });
    }
    editing.value = false;
  } catch (error) {
    saveError.value = mutationError(error, props.language === "en" ? "Could not change this day" : "Не удалось изменить этот день");
  } finally {
    saving.value = false;
  }
}

async function reset(): Promise<void> {
  saving.value = true;
  saveError.value = "";
  try {
    await store.resetProfileDayOverride(props.profile.id, overrideDate.value);
    editing.value = false;
  } catch (error) {
    saveError.value = mutationError(error, props.language === "en" ? "Could not restore the schedule" : "Не удалось вернуть день к графику");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="managedProfileDayCard" data-managed-profile-day :data-date="date">
    <header>
      <div>
        <small>{{ profile.name }}</small>
        <h3>{{ formatDate }}</h3>
      </div>
      <button type="button" data-profile-override-edit @click="editing = !editing">
        {{ editing ? (language === 'en' ? 'Close' : 'Закрыть') : (language === 'en' ? 'Change day' : 'Изменить день') }}
      </button>
    </header>

    <div class="managedProfileDayFacts">
      <div><span>{{ language === 'en' ? 'Scheduled' : 'По графику' }}</span><b>{{ plannedLabel }}</b></div>
      <div><span>{{ language === 'en' ? 'Actual' : 'Фактически' }}</span><b>{{ actualLabel }}</b></div>
    </div>

    <form v-if="editing" class="managedProfileDayEditor" @submit.prevent="save">
      <label>
        {{ language === 'en' ? 'Day state' : 'Состояние дня' }}
        <select v-model="mode" data-profile-override-kind>
          <option value="SCHEDULE">{{ language === 'en' ? 'Follow schedule' : 'По графику' }}</option>
          <option value="OFF">{{ language === 'en' ? 'Not working' : 'Не работает' }}</option>
          <option value="WORK">{{ language === 'en' ? 'Working' : 'Работает' }}</option>
        </select>
      </label>

      <label v-if="mode === 'OFF'">
        {{ language === 'en' ? 'Reason' : 'Причина' }}
        <select v-model="reason" data-profile-override-reason>
          <option value="TIME_OFF">{{ language === 'en' ? 'Time off' : 'Отгул' }}</option>
          <option value="VACATION">{{ language === 'en' ? 'Vacation' : 'Отпуск' }}</option>
          <option value="SICK">{{ language === 'en' ? 'Sick leave' : 'Больничный' }}</option>
          <option value="OTHER">{{ language === 'en' ? 'Other' : 'Другое' }}</option>
        </select>
      </label>

      <template v-if="mode === 'WORK'">
        <label>
          {{ language === 'en' ? 'Shift' : 'Смена' }}
          <select v-model.number="shiftTypeId" data-profile-override-shift @change="selectShift">
            <option v-for="shift in workingShifts" :key="shift.id" :value="shift.id">{{ shift.name }}</option>
          </select>
        </label>
        <div class="managedProfileTimeRow">
          <label>{{ language === 'en' ? 'From' : 'С' }} <input v-model="startTime" type="time" data-profile-override-start /></label>
          <label>{{ language === 'en' ? 'To' : 'До' }} <input v-model="endTime" type="time" data-profile-override-end /></label>
        </div>
      </template>

      <p v-if="saveError" class="managedProfileDayError" role="alert" data-profile-override-error>{{ saveError }}</p>

      <div class="managedProfileDayActions">
        <button class="primary" type="submit" :disabled="saving" data-profile-override-save>
          {{ saving ? '…' : (language === 'en' ? 'Save' : 'Сохранить') }}
        </button>
        <button v-if="entry?.overrideKind" type="button" :disabled="saving" data-profile-override-reset @click="reset">
          {{ language === 'en' ? 'Restore schedule' : 'Вернуть по графику' }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.managedProfileDayCard{margin:12px 18px 0;padding:14px 16px;border:1px solid var(--border);border-radius:16px;background:var(--panel);display:grid;gap:12px}
.managedProfileDayCard header{display:flex;align-items:center;justify-content:space-between;gap:12px}
.managedProfileDayCard header small{display:block;color:var(--muted);font-weight:700}
.managedProfileDayCard h3{margin:2px 0 0;font-size:1rem;text-transform:capitalize}
.managedProfileDayCard button,.managedProfileDayCard select,.managedProfileDayCard input{min-height:38px;border:1px solid var(--border);border-radius:10px;background:var(--panel);color:var(--text);padding:0 10px}
.managedProfileDayFacts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.managedProfileDayFacts>div{padding:10px;border-radius:12px;background:var(--panelAlt,var(--panel));display:grid;gap:3px}
.managedProfileDayFacts span,.managedProfileDayEditor label{color:var(--muted);font-size:.78rem}
.managedProfileDayEditor{display:grid;gap:10px}
.managedProfileDayEditor>label{display:grid;gap:5px}
.managedProfileTimeRow{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.managedProfileTimeRow label{display:grid;gap:5px}
.managedProfileDayError{margin:0;padding:9px 10px;border:1px solid color-mix(in srgb,#ff8a5b 55%,var(--border));border-radius:10px;background:color-mix(in srgb,#ff8a5b 10%,var(--panel));color:#ffad8c;font-size:.82rem;font-weight:700}
.managedProfileDayActions{display:flex;gap:8px;flex-wrap:wrap}
@media (max-width:640px){.managedProfileDayFacts,.managedProfileTimeRow{grid-template-columns:1fr}.managedProfileDayCard{margin-inline:0}.managedProfileDayCard header{align-items:flex-start}}
</style>
