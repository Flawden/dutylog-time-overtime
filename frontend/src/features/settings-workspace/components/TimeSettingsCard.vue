<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";
import SettingsCard from "./SettingsCard.vue";

const props = defineProps<{ bridge: LegacyBridge; active: boolean }>();
const emit = defineEmits<{ open: [] }>();
const settings = useSettingsWorkspaceStore();
const { profile, shiftTypes, timeContext, workTimezoneHistory, legacyShiftPreview, legacyTaskDeadlinePreview } = storeToRefs(settings);

const workTimezone = ref("UTC");
const effectiveFrom = ref("");
const timezoneDraftTouched = ref(false);
const dayStart = ref("08:30");
const dayEnd = ref("17:00");
const dayBreak = ref(30);
const dayPlan = ref(8);
const nightStart = ref("20:00");
const nightEnd = ref("08:00");
const nightBreak = ref(60);
const nightPlan = ref(11);

// Preserve the curated legacy list even when the browser exposes a narrower or
// differently canonicalized Intl.supportedValuesOf("timeZone") result (for
// example Chromium builds that omit the Europe/Kyiv alias).
const TIMEZONE_FALLBACKS = [
  "UTC", "Europe/Chisinau", "Europe/Moscow", "Europe/Berlin", "Europe/Kyiv",
  "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Novosibirsk", "Asia/Irkutsk",
  "Asia/Vladivostok", "Asia/Krasnoyarsk", "Asia/Kamchatka",
  "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "Asia/Tbilisi", "Asia/Yerevan", "Asia/Almaty", "Asia/Dubai", "Asia/Tokyo",
] as const;

const language = computed(() => settings.language);
const text = computed(() => language.value === "en" ? {
  eyebrow: "Time", title: "Timezone and time format", hint: "Work timezone is effective-dated so historical local work time keeps its original meaning.", timezone: "Timezone", format: "Time format", current: "Current time", saveTimezone: "Save timezone", detect: "Detect automatically", shifts: "Shift templates", shiftsHint: "Built-in day and night shift defaults.", day: "Day", night: "Night", start: "Start", end: "End", break: "Break, min", plan: "Plan, h", saveShifts: "Save shift templates", legacyShifts: "Attach legacy shifts", legacyTasks: "Attach legacy task deadlines", migrationConfirm: "Interpret legacy local time in the selected timezone? This writes absolute instants.", saved: "saved",
} : {
  eyebrow: "Время", title: "Часовой пояс и формат времени", hint: "Рабочий часовой пояс хранится по периодам, чтобы историческое локальное время сохраняло исходный смысл.", timezone: "Часовой пояс", format: "Формат времени", current: "Текущее время", saveTimezone: "Сохранить часовой пояс", detect: "Определить автоматически", shifts: "Шаблоны смен", shiftsHint: "Параметры встроенных дневной и ночной смен.", day: "Дневная", night: "Ночная", start: "Начало", end: "Конец", break: "Обед, мин", plan: "План, ч", saveShifts: "Сохранить параметры смен", legacyShifts: "Привязать старые смены", legacyTasks: "Привязать старые задачи", migrationConfirm: "Интерпретировать старое локальное время в выбранном часовом поясе? Будут записаны абсолютные моменты времени.", saved: "сохранено",
});

const temporalText = computed(() => language.value === "en" ? {
  effectiveFrom: "Effective from",
  effectiveHint: "The selected timezone applies from this work-local date until the next history entry. Future dates are not available yet.",
  history: "Work timezone history",
  historyHint: "Each entry stays effective until the next one.",
  baseline: "Initial conditions",
  currentTerm: "current",
  noHistory: "No timezone history",
  historicalConfirm: "Changing a historical timezone can change the time binding of actual work and recalculate derived overtime for the affected period. Continue?",
} : {
  effectiveFrom: "Действует с",
  effectiveHint: "Выбранный часовой пояс действует с этой рабочей локальной даты до следующей записи в истории. Будущие даты пока недоступны.",
  history: "История рабочего часового пояса",
  historyHint: "Каждая запись действует до начала следующей.",
  baseline: "Исходные условия",
  currentTerm: "текущий",
  noHistory: "История часового пояса пока пуста",
  historicalConfirm: "Изменение исторического часового пояса может изменить привязку фактической работы по времени и пересчитать производную переработку за затронутый период. Продолжить?",
});

const timeZones = computed(() => {
  let supported: string[] = [];
  try {
    const intl = Intl as typeof Intl & { supportedValuesOf?: (key: "timeZone") => string[] };
    supported = intl.supportedValuesOf?.("timeZone") ?? [];
  } catch { supported = []; }
  const current = workTimezone.value || profile.value?.workTimezone || "UTC";
  return [...new Set([current, ...TIMEZONE_FALLBACKS, ...supported])].sort((a, b) => a.localeCompare(b));
});
const nowLabel = computed(() => {
  const zone = workTimezone.value || "UTC";
  try {
    return new Intl.DateTimeFormat(language.value === "en" ? "en-US" : "ru-RU", {
      timeZone: zone, dateStyle: "medium", timeStyle: "medium", hour12: false,
    }).format(new Date());
  } catch { return timeContext.value?.workLocalDateTime ?? "—"; }
});
const currentWorkDate = computed(() =>
  workTimezoneHistory.value?.currentDate || timeContext.value?.workDate || ""
);

const currentTimezone = computed(() =>
  workTimezoneHistory.value?.currentTimezone
  || profile.value?.workTimezone
  || timeContext.value?.workTimezone
  || "UTC"
);

const historyTerms = computed(() =>
  [...(workTimezoneHistory.value?.terms ?? [])]
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))
);

function timezoneAt(date: string): string {
  if (!date) return currentTimezone.value;
  return historyTerms.value.find(term => term.effectiveFrom <= date)?.timezone
    || currentTimezone.value;
}

const currentTermEffectiveFrom = computed(() => {
  const date = currentWorkDate.value;
  if (!date) return "";
  return historyTerms.value.find(term => term.effectiveFrom <= date)?.effectiveFrom || "";
});

const effectiveDateValid = computed(() =>
  Boolean(effectiveFrom.value)
  && effectiveFrom.value >= "1970-01-02"
  && (!currentWorkDate.value || effectiveFrom.value <= currentWorkDate.value)
);

const historicalSelection = computed(() =>
  Boolean(effectiveFrom.value)
  && Boolean(currentWorkDate.value)
  && effectiveFrom.value < currentWorkDate.value
);

const timezoneDirty = computed(() =>
  effectiveDateValid.value
  && workTimezone.value !== timezoneAt(effectiveFrom.value)
);

const status = computed(() => {
  if (!effectiveDateValid.value) {
    return language.value === "en"
      ? "choose an effective date"
      : "укажите дату действия";
  }
  if (timezoneDirty.value) {
    return language.value === "en" ? "not saved" : "не сохранено";
  }
  return settings.timeMessage
    || `${text.value.saved} · ${workTimezone.value} · ${effectiveFrom.value}`;
});

function formatHistoryDate(date: string): string {
  if (!date) return "—";
  const parts = date.split("-").map(Number);
  if (parts.length !== 3 || parts.some(value => !Number.isFinite(value))) return date;

  const [year, month, day] = parts;
  if (year === undefined || month === undefined || day === undefined) return date;

  try {
    return new Intl.DateTimeFormat(
      language.value === "en" ? "en-US" : "ru-RU",
      { year: "numeric", month: "short", day: "2-digit" },
    ).format(new Date(year, month - 1, day, 12, 0, 0));
  } catch {
    return date;
  }
}

function findBuiltIn(kind: "day" | "night") {
  const russian = kind === "day" ? "Дневная" : "Ночная";
  const english = kind === "day" ? /day/i : /night/i;
  return shiftTypes.value.find(item => item.builtin && item.name === russian)
    ?? shiftTypes.value.find(item => item.builtin && english.test(item.name));
}
function syncDraft(): void {
  if (
    currentWorkDate.value
    && (!effectiveFrom.value || effectiveFrom.value > currentWorkDate.value)
  ) {
    // A timezone move can shift the canonical work date backwards across
    // midnight. Never retain an effective date that became future-dated.
    effectiveFrom.value = currentWorkDate.value;
  }
  if (!timezoneDraftTouched.value) {
    workTimezone.value = timezoneAt(effectiveFrom.value || currentWorkDate.value);
  }
  const day = findBuiltIn("day");
  const night = findBuiltIn("night");
  if (day) {
    dayStart.value = day.startTime || "08:30";
    dayEnd.value = day.endTime || "17:00";
    dayBreak.value = Number(day.breakMinutes ?? 30);
    dayPlan.value = Number(day.plannedHours ?? day.hours ?? 8);
  }
  if (night) {
    nightStart.value = night.startTime || "20:00";
    nightEnd.value = night.endTime || "08:00";
    nightBreak.value = Number(night.breakMinutes ?? 60);
    nightPlan.value = Number(night.plannedHours ?? night.hours ?? 11);
  }
}
watch([profile, shiftTypes, timeContext, workTimezoneHistory], syncDraft, { immediate: true, deep: true });

watch(effectiveFrom, date => {
  if (!date || !effectiveDateValid.value) return;
  timezoneDraftTouched.value = false;
  workTimezone.value = timezoneAt(date);
});

function markTimezoneDraftTouched(): void {
  timezoneDraftTouched.value = true;
}
function detectBrowserTimezone(): void {
  timezoneDraftTouched.value = true;
  try { workTimezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; }
  catch { workTimezone.value = "UTC"; }
}
async function saveTimezone(): Promise<void> {
  if (!effectiveDateValid.value || !timezoneDirty.value) return;
  if (
    historicalSelection.value
    && !window.confirm(temporalText.value.historicalConfirm)
  ) {
    return;
  }
  await settings.saveTimezoneFrom(
    workTimezone.value,
    effectiveFrom.value,
    props.bridge,
  );
  timezoneDraftTouched.value = false;
  syncDraft();
}
async function saveShifts(): Promise<void> {
  await settings.saveBuiltInShiftDefaults({
    dayStart: dayStart.value, dayEnd: dayEnd.value, dayBreakMinutes: dayBreak.value, dayPlannedHours: dayPlan.value,
    nightStart: nightStart.value, nightEnd: nightEnd.value, nightBreakMinutes: nightBreak.value, nightPlannedHours: nightPlan.value,
  });
}
async function migrateShifts(): Promise<void> {
  if (!window.confirm(text.value.migrationConfirm)) return;
  await settings.migrateLegacyShifts(workTimezone.value);
}
async function migrateTasks(): Promise<void> {
  if (!window.confirm(text.value.migrationConfirm)) return;
  await settings.migrateLegacyTaskDeadlines(workTimezone.value);
}
</script>

<template>
  <SettingsCard id="timeSettingsCard" section="time" :active="active" :eyebrow="text.eyebrow" :title="text.title" :hint="text.hint" @open="emit('open')">
    <template #status><div id="timeSettingsStatus" class="status" :class="{ ok: settings.timeMessageOk }">{{ status }}</div></template>
    <div class="timeSettingsGrid timeZoneSettingsGrid singleTimezoneGrid">
      <label>{{ text.timezone }}
        <select id="workTimezone" v-model="workTimezone" aria-describedby="timeZoneHelp" @change="markTimezoneDraftTouched"><option v-for="zone in timeZones" :key="zone" :value="zone">{{ zone }}</option></select>
      </label>
      <input id="displayTimezone" type="hidden" :value="workTimezone" />
      <label>{{ temporalText.effectiveFrom }}
        <input
          id="workTimezoneEffectiveFrom"
          v-model="effectiveFrom"
          type="date"
          min="1970-01-02"
          :max="currentWorkDate"
          aria-describedby="timeZoneHelp"
          required
        />
      </label>
      <label>{{ text.format }}<select id="timeFormatPref" value="24h"><option value="24h">24 часа</option></select></label>
    </div>
    <div id="timeNowBox" class="timeNowBox"><div><span>{{ text.current }}:</span> <b>{{ nowLabel }}</b> <code>{{ workTimezone }}</code></div></div>
    <div class="timeSettingsActions timezoneActions">
      <button id="timeSaveTimezone" class="primary" type="button" :disabled="!timezoneDirty" @click="saveTimezone">{{ text.saveTimezone }}</button>
      <button id="timeDetectBrowser" type="button" @click="detectBrowserTimezone">{{ text.detect }}</button>
      <button v-if="(legacyShiftPreview?.legacyCount ?? 0) > 0" id="legacyShiftOpen" type="button" @click="migrateShifts">⚠ {{ text.legacyShifts }} ({{ legacyShiftPreview?.legacyCount }})</button>
      <button v-if="(legacyTaskDeadlinePreview?.legacyCount ?? 0) > 0" id="legacyTaskDeadlineOpen" type="button" @click="migrateTasks">⚠ {{ text.legacyTasks }} ({{ legacyTaskDeadlinePreview?.legacyCount }})</button>
    </div>
    <div id="timeZoneHelp" class="wideHint">{{ temporalText.effectiveHint }} IANA: Europe/Chisinau, Europe/Berlin, Asia/Yekaterinburg.</div>

    <section id="workTimezoneHistory" class="timeDefaults timeSettingsSubcard">
      <div class="timeDefaultsHead">
        <div>
          <b>{{ temporalText.history }}</b>
          <span>{{ temporalText.historyHint }}</span>
        </div>
      </div>
      <div v-if="historyTerms.length">
        <div
          v-for="term in historyTerms"
          :key="term.effectiveFrom"
          class="wideHint"
        >
          <strong>{{ term.baseline ? temporalText.baseline : formatHistoryDate(term.effectiveFrom) }}</strong>
          · <code>{{ term.timezone }}</code>
          <span v-if="term.effectiveFrom === currentTermEffectiveFrom">
            · {{ temporalText.currentTerm }}
          </span>
        </div>
      </div>
      <div v-else class="wideHint">{{ temporalText.noHistory }}</div>
    </section>

    <div class="timeDefaults timeSettingsSubcard">
      <div class="timeDefaultsHead"><div><b>{{ text.shifts }}</b><span>{{ text.shiftsHint }}</span></div></div>
      <div id="shiftTemplateZoneHint" class="wideHint">{{ currentTimezone }}</div>
      <div class="timeDefaultRow">
        <b>{{ text.day }}</b>
        <label>{{ text.start }} <input id="defDayStart" v-model="dayStart" type="time" /></label>
        <label>{{ text.end }} <input id="defDayEnd" v-model="dayEnd" type="time" /></label>
        <label>{{ text.break }} <input id="defDayBreak" v-model.number="dayBreak" max="1440" min="0" step="5" type="number" /></label>
        <label>{{ text.plan }} <input id="defDayPlan" v-model.number="dayPlan" max="24" min="0" step="0.25" type="number" /></label>
      </div>
      <div class="timeDefaultRow">
        <b>{{ text.night }}</b>
        <label>{{ text.start }} <input id="defNightStart" v-model="nightStart" type="time" /></label>
        <label>{{ text.end }} <input id="defNightEnd" v-model="nightEnd" type="time" /></label>
        <label>{{ text.break }} <input id="defNightBreak" v-model.number="nightBreak" max="1440" min="0" step="5" type="number" /></label>
        <label>{{ text.plan }} <input id="defNightPlan" v-model.number="nightPlan" max="24" min="0" step="0.25" type="number" /></label>
      </div>
      <div class="timeSettingsActions shiftDefaultsActions"><button id="timeApplyBuiltins" class="primary" type="button" @click="saveShifts">{{ text.saveShifts }}</button></div>
    </div>
    <div class="profileMsg" :class="{ ok: settings.timeMessageOk }">{{ settings.timeMessage }}</div>
  </SettingsCard>
</template>
