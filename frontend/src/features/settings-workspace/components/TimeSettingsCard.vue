<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";
import SettingsCard from "./SettingsCard.vue";

const props = defineProps<{ bridge: LegacyBridge; active: boolean }>();
const emit = defineEmits<{ open: [] }>();
const settings = useSettingsWorkspaceStore();
const { profile, shiftTypes, timeContext, legacyShiftPreview, legacyTaskDeadlinePreview } = storeToRefs(settings);

const workTimezone = ref("UTC");
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
  eyebrow: "Time", title: "Timezone and time format", hint: "One IANA timezone is used for calendar, shifts, reminders and absolute intervals.", timezone: "Timezone", format: "Time format", current: "Current time", saveTimezone: "Save timezone", detect: "Detect automatically", shifts: "Shift templates", shiftsHint: "Built-in day and night shift defaults.", day: "Day", night: "Night", start: "Start", end: "End", break: "Break, min", plan: "Plan, h", saveShifts: "Save shift templates", legacyShifts: "Attach legacy shifts", legacyTasks: "Attach legacy task deadlines", migrationConfirm: "Interpret legacy local time in the selected timezone? This writes absolute instants.", saved: "saved",
} : {
  eyebrow: "Время", title: "Часовой пояс и формат времени", hint: "Один IANA-часовой пояс используется для календаря, смен, напоминаний и абсолютных интервалов.", timezone: "Часовой пояс", format: "Формат времени", current: "Текущее время", saveTimezone: "Сохранить часовой пояс", detect: "Определить автоматически", shifts: "Шаблоны смен", shiftsHint: "Параметры встроенных дневной и ночной смен.", day: "Дневная", night: "Ночная", start: "Начало", end: "Конец", break: "Обед, мин", plan: "План, ч", saveShifts: "Сохранить параметры смен", legacyShifts: "Привязать старые смены", legacyTasks: "Привязать старые задачи", migrationConfirm: "Интерпретировать старое локальное время в выбранном часовом поясе? Будут записаны абсолютные моменты времени.", saved: "сохранено",
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
const timezoneDirty = computed(() => workTimezone.value !== (profile.value?.workTimezone || timeContext.value?.workTimezone || "UTC"));
const status = computed(() => timezoneDirty.value
  ? (language.value === "en" ? "not saved" : "не сохранено")
  : settings.timeMessage || `${text.value.saved} · ${workTimezone.value}`);

function findBuiltIn(kind: "day" | "night") {
  const russian = kind === "day" ? "Дневная" : "Ночная";
  const english = kind === "day" ? /day/i : /night/i;
  return shiftTypes.value.find(item => item.builtin && item.name === russian)
    ?? shiftTypes.value.find(item => item.builtin && english.test(item.name));
}
function syncDraft(): void {
  workTimezone.value = profile.value?.workTimezone || timeContext.value?.workTimezone || "UTC";
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
watch([profile, shiftTypes, timeContext], syncDraft, { immediate: true, deep: true });

function detectBrowserTimezone(): void {
  try { workTimezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; }
  catch { workTimezone.value = "UTC"; }
}
async function saveTimezone(): Promise<void> { await settings.saveTimezone(workTimezone.value, props.bridge); }
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
        <select id="workTimezone" v-model="workTimezone" aria-describedby="timeZoneHelp"><option v-for="zone in timeZones" :key="zone" :value="zone">{{ zone }}</option></select>
      </label>
      <input id="displayTimezone" type="hidden" :value="workTimezone" />
      <label>{{ text.format }}<select id="timeFormatPref" value="24h"><option value="24h">24 часа</option></select></label>
    </div>
    <div id="timeNowBox" class="timeNowBox"><div><span>{{ text.current }}:</span> <b>{{ nowLabel }}</b> <code>{{ workTimezone }}</code></div></div>
    <div class="timeSettingsActions timezoneActions">
      <button id="timeSaveTimezone" class="primary" type="button" @click="saveTimezone">{{ text.saveTimezone }}</button>
      <button id="timeDetectBrowser" type="button" @click="detectBrowserTimezone">{{ text.detect }}</button>
      <button v-if="(legacyShiftPreview?.legacyCount ?? 0) > 0" id="legacyShiftOpen" type="button" @click="migrateShifts">⚠ {{ text.legacyShifts }} ({{ legacyShiftPreview?.legacyCount }})</button>
      <button v-if="(legacyTaskDeadlinePreview?.legacyCount ?? 0) > 0" id="legacyTaskDeadlineOpen" type="button" @click="migrateTasks">⚠ {{ text.legacyTasks }} ({{ legacyTaskDeadlinePreview?.legacyCount }})</button>
    </div>
    <div id="timeZoneHelp" class="wideHint">IANA: Europe/Chisinau, Europe/Berlin, Asia/Yekaterinburg. {{ text.hint }}</div>

    <div class="timeDefaults timeSettingsSubcard">
      <div class="timeDefaultsHead"><div><b>{{ text.shifts }}</b><span>{{ text.shiftsHint }}</span></div></div>
      <div id="shiftTemplateZoneHint" class="wideHint">{{ workTimezone }}</div>
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
