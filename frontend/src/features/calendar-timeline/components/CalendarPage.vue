<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import {
  calendarDayVisualStyle,
  calendarFactualAbsence,
  calendarImportantGlyph,
  calendarOpenTaskCount,
  calendarScheduleFree,
  dateLabel,
  dayFacts,
  dayFactsForProfile,
  profileEntryForDate,
  profileLayer,
  minutesOf,
  monthGridDates,
  monthStart,
  timePart,
  weekDates,
} from "../types/model";
import type { CalendarMode } from "../types/domain";
import ManagedProfileDayCard from "./ManagedProfileDayCard.vue";
import SharedAvailabilityCard from "./SharedAvailabilityCard.vue";
import SelectedDayPanel from "./SelectedDayPanel.vue";

const props = defineProps<{ bridge: LegacyBridge }>();
const store = useCalendarTimelineStore();
const shell = useShellStore();
const { language } = storeToRefs(shell);
const { bundle, focusDate, mode, loading, error, workDate, dayPanelOpen, activeProfileId } = storeToRefs(store);

const gridDates = computed(() => monthGridDates(focusDate.value));
const week = computed(() => weekDates(focusDate.value));
const selectedProfile = computed(() => profileLayer(bundle.value, activeProfileId.value));
const peopleProfiles = computed(() => (bundle.value?.calendarLayers ?? []).filter(layer => layer.visible));
const viewingSelf = computed(() => activeProfileId.value === "self");
const focusFacts = computed(() => dayFactsForProfile(bundle.value, focusDate.value, activeProfileId.value));
const focusProfileEntry = computed(() => profileEntryForDate(bundle.value, activeProfileId.value, focusDate.value));
const focusMonth = computed(() => monthStart(focusDate.value).slice(0, 7));
const monthTitle = computed(() => dateLabel(`${focusMonth.value}-01`, language.value, { month: "long" }));
const yearTitle = computed(() => focusDate.value.slice(0, 4));
const monthDates = computed(() => gridDates.value.filter(date => date.startsWith(focusMonth.value)));
const monthSummary = computed(() => ({
  shifts: monthDates.value.filter(date => dayFactsForProfile(bundle.value, date, activeProfileId.value).shift).length,
  tasks: monthDates.value.reduce((sum, date) => sum + dayFactsForProfile(bundle.value, date, activeProfileId.value).tasks.filter(task => !task.done).length, 0),
  absences: monthDates.value.reduce((sum, date) => sum + dayFactsForProfile(bundle.value, date, activeProfileId.value).absences.length, 0),
}));

const allDayItems = computed(() => [
  ...focusFacts.value.important.filter(item => item.allDay !== false).map(item => ({ key: `important-${item.id}`, type: "important", title: item.title, color: item.color })),
  ...focusFacts.value.absences.filter(item => item.coverage !== "PARTIAL" && item.coverage !== "HOURS_ONLY").map(item => ({ key: `absence-${item.periodId}`, type: "vacation", title: item.title || item.typeName, color: item.typeColor })),
  ...focusFacts.value.tasks.filter(item => item.allDay !== false && !item.scheduledStartTime).map(item => ({ key: `task-${item.id}`, type: "task", title: item.text, color: null })),
  ...(focusFacts.value.day?.notes?.length || focusFacts.value.day?.note ? [{ key: "notes", type: "note", title: `Заметки: ${focusFacts.value.day?.notes?.length || 1}`, color: null }] : []),
]);

const timelineEvents = computed(() => {
  type TimelineEvent = { key: string; type: string; title: string; start: number; end: number; meta: string; color: string; actionId: number | null };
  const events: TimelineEvent[] = [];
  const segment = (startDate: string, startTime: string | null | undefined, endDate: string, endTime: string | null | undefined): { start: number; end: number } | null => {
    const date = focusDate.value;
    if (date < startDate || date > endDate) return null;
    if (date === endDate && endDate > startDate && minutesOf(endTime, 0) === 0) return null;
    const start = date === startDate ? minutesOf(startTime, 0) : 0;
    let end = date === endDate ? minutesOf(endTime, Math.min(1440, start + 60)) : 1440;
    if (end <= start) end = Math.min(1440, start + 60);
    return { start, end };
  };
  for (const occurrence of focusFacts.value.occurrences) {
    const startDate = occurrence.displayStart.slice(0, 10);
    const endDate = occurrence.displayEnd.slice(0, 10);
    const value = segment(startDate, timePart(occurrence.displayStart), endDate, timePart(occurrence.displayEnd));
    if (value) events.push({ key: `shift-${occurrence.dayEntryId ?? occurrence.startInstant}`, type: "shift", title: focusFacts.value.shift?.name || "Смена", ...value, meta: `${timePart(occurrence.displayStart)}–${timePart(occurrence.displayEnd)}`, color: focusFacts.value.shift?.color || "var(--accent)", actionId: null });
  }
  for (const absence of focusFacts.value.absences.filter(value => value.coverage === "PARTIAL" && value.startTime)) {
    const value = segment(absence.date, absence.startTime, absence.date, absence.endTime);
    if (value) events.push({ key: `absence-${absence.periodId}`, type: "vacation", title: absence.title || absence.typeName, ...value, meta: `${absence.startTime || ""}–${absence.endTime || ""}`, color: absence.typeColor, actionId: absence.periodId });
  }
  for (const item of focusFacts.value.important.filter(value => value.allDay === false && value.startTime)) {
    const startDate = item.startDate || item.date;
    const endDate = item.endDate || startDate;
    const value = segment(startDate, item.startTime, endDate, item.endTime);
    if (value) events.push({ key: `important-${item.id}`, type: "important", title: item.title, ...value, meta: item.place || "", color: item.color || "#f5b841", actionId: item.id });
  }
  for (const task of focusFacts.value.tasks.filter(value => value.scheduledStartTime)) {
    const startDate = task.scheduledStartDate || task.date;
    const endDate = task.scheduledEndDate || startDate;
    const value = segment(startDate, task.scheduledStartTime, endDate, task.scheduledEndTime);
    if (value) events.push({ key: `task-${task.id}`, type: "task", title: task.text, ...value, meta: task.priority, color: "#7b8ce0", actionId: task.id });
  }
  for (const reminder of focusFacts.value.reminders.filter(value => String(value.type).toUpperCase() !== "IMPORTANT_DAY")) {
    const reminderTime = timePart(reminder.displayAt || reminder.remindAt);
    if (!reminderTime) continue;
    const start = minutesOf(reminderTime, 9 * 60);
    events.push({ key: `reminder-${reminder.id}`, type: "reminder", title: reminder.title, start, end: Math.min(1440, start + 30), meta: reminder.details || "", color: "var(--accent)", actionId: null });
  }
  if (!viewingSelf.value) {
    const layer = selectedProfile.value;
    const entry = profileEntryForDate(bundle.value, activeProfileId.value, focusDate.value);
    if (layer && entry?.timed !== false && entry?.displayStart) {
      const startDate = String(entry.displayStart).slice(0, 10) || focusDate.value;
      const endDate = String(entry.displayEnd || entry.displayStart).slice(0, 10) || startDate;
      const value = segment(startDate, timePart(entry.displayStart), endDate, timePart(entry.displayEnd));
      if (value) events.push({ key: `profile-${layer.id}-${entry.sourceDate || entry.date}`, type: "layer", title: entry.shiftTypeName || "Смена", ...value, meta: layer.timezone || "", color: entry.shiftColor || layer.color, actionId: null });
    }
  }
  const laneEnds: number[] = [];
  return events.sort((left, right) => left.start - right.start || left.end - right.end).map(event => {
    let lane = laneEnds.findIndex(value => value <= event.start);
    if (lane < 0) lane = laneEnds.length;
    laneEnds[lane] = event.end;
    return { ...event, lane };
  });
});

function cellFacts(date: string) { return dayFactsForProfile(bundle.value, date, activeProfileId.value); }
function openTaskCount(date: string): number { return calendarOpenTaskCount(cellFacts(date)); }
function importantMarker(date: string) { return cellFacts(date).important[0] ?? null; }
function importantMarkerGlyph(date: string): string { return calendarImportantGlyph(importantMarker(date)); }
function isScheduleFreeDay(date: string): boolean {
  return Boolean(bundle.value) && calendarScheduleFree(cellFacts(date));
}
function isWeekend(date: string): boolean {
  const value = new Date(`${date}T00:00:00Z`).getUTCDay();
  return value === 0 || value === 6;
}
function cellStyle(date: string): Record<string, string> { return calendarDayVisualStyle(cellFacts(date)); }
function cellAriaLabel(date: string): string {
  const facts = cellFacts(date);
  const parts = [dateLabel(date, language.value, { weekday: "long", day: "numeric", month: "long" })];
  const shiftName = facts.shift?.name;
  if (shiftName) parts.push(`${language.value === "en" ? "Shift" : "Смена"}: ${shiftName}`);
  else if (calendarScheduleFree(facts)) parts.push(language.value === "en" ? "Schedule: day off" : "По графику: свободный день");
  if (facts.absences.length) parts.push(`${language.value === "en" ? "Absences" : "Отсутствия"}: ${facts.absences.length}`);
  const openTasks = calendarOpenTaskCount(facts);
  if (openTasks) parts.push(`${language.value === "en" ? "Tasks" : "Задачи"}: ${openTasks}`);
  if (facts.important.length) parts.push(`${language.value === "en" ? "Important dates" : "Важные даты"}: ${facts.important.length}`);
  const dayMarker = String(facts.day?.dayEmoji ?? "").trim();
  if (dayMarker) parts.push(`${language.value === "en" ? "Day marker" : "Маркер дня"}: ${dayMarker}`);
  return parts.join(". ");
}
function factualAbsence(date: string) { return calendarFactualAbsence(cellFacts(date)); }
function partialAbsences(date: string) { return cellFacts(date).absences.filter(item => item.coverage === "PARTIAL" || item.coverage === "HOURS_ONLY"); }
function secondaryAbsences(date: string) {
  const factual = factualAbsence(date);
  return cellFacts(date).absences.filter(item => item !== factual && item.coverage !== "PARTIAL" && item.coverage !== "HOURS_ONLY");
}
function absenceTitle(item: { title?: string | null; typeName: string } | null | undefined): string { return String(item?.title ?? "").trim() || item?.typeName || (language.value === "en" ? "Absence" : "Отсутствие"); }
function absenceGlyph(item: { systemCode?: string | null } | null | undefined): string {
  return ({ VACATION:"☀", TIME_OFF:"◷", SICK:"✚", UNPAID:"○", OTHER:"◆" } as Record<string,string>)[String(item?.systemCode ?? "").toUpperCase()] ?? "●";
}
function absenceSystemClass(item: { systemCode?: string | null } | null | undefined): string {
  const code = String(item?.systemCode ?? "OTHER").toLowerCase().replace(/[^a-z0-9_-]/g, "");
  return `absence-${code || "other"}`;
}
function profileOverrideReason(date: string): string {
  if (viewingSelf.value) return "";
  const entry = profileEntryForDate(bundle.value, activeProfileId.value, date);
  if (entry?.overrideKind !== "OFF") return "";
  return ({
    TIME_OFF: language.value === "en" ? "Time off" : "Отгул",
    VACATION: language.value === "en" ? "Vacation" : "Отпуск",
    SICK: language.value === "en" ? "Sick leave" : "Больничный",
    OTHER: language.value === "en" ? "Other" : "Другое",
  } as Record<string, string>)[String(entry.overrideReason ?? "OTHER")] ?? "";
}
function shiftRange(date: string): string {
  if (!viewingSelf.value) {
    const entry = profileEntryForDate(bundle.value, activeProfileId.value, date);
    return entry?.displayStart && entry?.displayEnd ? `${timePart(entry.displayStart)}–${timePart(entry.displayEnd)}` : "";
  }
  const occurrence = cellFacts(date).occurrences[0];
  return occurrence ? `${timePart(occurrence.displayStart)}–${timePart(occurrence.displayEnd)}` : "";
}
function inFocusMonth(date: string): boolean { return date.startsWith(focusMonth.value); }
function dayNumber(date: string): string { return String(Number(date.slice(8, 10))); }
function weekday(date: string): string { return dateLabel(date, language.value, { weekday: "short" }); }
function dayLabel(date: string): string { return dateLabel(date, language.value, { day: "numeric", month: "short" }); }
function eventStyle(event: { start: number; end: number; lane: number; color: string }) {
  const start = Math.max(0, Math.min(1440, event.start));
  const duration = Math.max(30, Math.min(1440, event.end) - start);
  return {
    top: `${(start / 1440) * 100}%`,
    height: `${Math.max(2.8, (duration / 1440) * 100)}%`,
    "--start": String(start / 14.4),
    "--duration": String(Math.max(2.8, duration / 14.4)),
    "--lane": String(Math.min(event.lane, 3)),
    "--event-color": event.color,
  };
}
function timelineRange(event: { start: number; end: number }): string {
  const label = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`.replace(/^24:/, "24:");
  return `${label(event.start)}–${label(event.end)}`;
}
async function openTimelineEvent(event: { type: string; actionId: number | null }): Promise<void> {
  if (event.type === "task" && event.actionId != null) await window.DutyLogVueDomains?.productivity?.openTaskDetails(event.actionId);
  else if (event.type === "important" && event.actionId != null) await window.DutyLogVueDomains?.productivity?.openImportantDetails(event.actionId);
  else if (event.type === "vacation" && event.actionId != null) await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceEditor(event.actionId);
}

async function chooseDate(date: string): Promise<void> { if (viewingSelf.value) await store.openDayPanel(date); else await store.openDate(date, "month"); }
async function chooseWeekDate(date: string): Promise<void> { store.closeDayPanel(); await store.openDate(date, "week"); }
async function navigate(delta: number): Promise<void> {
  store.closeDayPanel();
  await store.navigate(delta);
}
async function goToday(): Promise<void> {
  await store.goToday(mode.value);
  if (mode.value === "month" && viewingSelf.value) await store.openDayPanel(store.focusDate);
  else store.closeDayPanel();
}
async function setMode(nextMode: CalendarMode): Promise<void> {
  await store.setMode(nextMode);
  if (nextMode !== "month") store.closeDayPanel();
}
async function openDetails(): Promise<void> { if (viewingSelf.value) await store.openDayPanel(focusDate.value); }
</script>

<template>
  <section id="view-calendar" class="view vue-calendar-view" data-vue-domain-route="calendar" data-vue-domain-owner="calendar-timeline">
    <header class="vue-calendar-toolbar">
      <div class="vue-calendar-title"><small>Vue Calendar · canonical server range</small><h1><span id="monthName">{{ monthTitle }}</span> <span id="yearName">{{ yearTitle }}</span></h1></div>
      <div class="vue-calendar-nav">
        <button id="prev" type="button" aria-label="Предыдущий период" @click="navigate(-1)">←</button>
        <button id="todayBtn" type="button" :hidden="focusDate === workDate" @click="goToday">Сегодня</button>
        <button id="next" type="button" aria-label="Следующий период" @click="navigate(1)">→</button>
      </div>
    </header>

    <div class="calendarExperience" id="calendarExperience">
      <div class="calendarExperienceBar">
        <div class="calendarModeSwitch" role="group" aria-label="Масштаб календаря">
          <button v-for="value in (['month','week','day'] as CalendarMode[])" :key="value" type="button" :data-calendar-mode="value" :aria-pressed="mode === value ? 'true' : 'false'" @click="setMode(value)">{{ value === 'month' ? 'Месяц' : value === 'week' ? 'Неделя' : 'День' }}</button>
        </div>
        <div id="calendarLoadStatus" class="calendarLoadStatus" role="status" aria-live="polite" :hidden="!loading"><span aria-hidden="true"></span><b>Обновляю календарь…</b></div>
        <div id="calendarFocusLabel" class="calendarFocusLabel">{{ dateLabel(focusDate, language, { day:'numeric', month:'long', year:'numeric' }) }}</div>
        <div id="calendarProfileBar" class="calendarProfileBar" role="group" :aria-label="language === 'en' ? 'Calendar person' : 'Чей календарь'">
          <button type="button" class="calendarProfileToggle" :aria-pressed="viewingSelf ? 'true' : 'false'" @click="store.selectProfile('self')"><i class="selfProfileDot"></i>{{ language === 'en' ? 'Me' : 'Я' }}</button>
          <button v-for="profile in peopleProfiles" :key="profile.id" type="button" class="calendarProfileToggle" :aria-pressed="activeProfileId === String(profile.id) ? 'true' : 'false'" @click="store.selectProfile(String(profile.id))"><i :style="{ background: profile.color }"></i>{{ profile.name }}</button>
        </div>
      </div>

      <div v-if="error" class="domain-alert domain-alert--danger" role="alert">{{ error }} <button type="button" @click="store.refresh()">Повторить</button></div>

      <SharedAvailabilityCard
        v-if="!viewingSelf && selectedProfile"
        :bundle="bundle"
        :date="focusDate"
        :profile="selectedProfile"
        :language="language"
      />

      <section v-show="mode === 'week'" id="calendarWeekExperience" class="calendarWeekExperience" aria-label="Недельный календарь">
        <div id="calendarWeekStrip" class="calendarWeekStrip">
          <button v-for="date in week" :key="date" type="button" class="calendarWeekDay" :class="{ isSelected: date === focusDate, todayCell: date === workDate, hasShift: Boolean(cellFacts(date).shift), isScheduleFree: isScheduleFreeDay(date), hasAbsenceFact: Boolean(factualAbsence(date)), isWeekend: isWeekend(date) }" :style="cellStyle(date)" :data-date="date" :aria-label="cellAriaLabel(date)" @click="chooseWeekDate(date)">
            <small>{{ weekday(date) }}</small><b>{{ dayNumber(date) }}</b>
            <span v-if="cellFacts(date).shift">{{ cellFacts(date).shift?.name }}</span>
            <span v-else-if="isScheduleFreeDay(date)" class="calendarWeekPalm" aria-hidden="true">
              <svg viewBox="0 0 64 64" focusable="false"><path d="M31 55c1-12 2-24 1-36M31 22c-8-9-16-9-23-5 8 0 14 4 19 11M33 21c7-10 15-11 23-7-8 1-14 6-18 13M32 19c-2-8-7-13-14-15 5 5 8 11 9 18M33 19c3-8 8-13 15-15-5 5-8 11-10 18"/></svg>
            </span>
            <span v-else>{{ language === 'en' ? 'Absence' : 'Отсутствие' }}</span>
          </button>
        </div>
        <div id="calendarWeekAgenda" class="calendarWeekAgenda">
          <article v-for="date in week" :key="`agenda-${date}`" :class="{ hasShift: Boolean(cellFacts(date).shift), isScheduleFree: isScheduleFreeDay(date), hasAbsenceFact: Boolean(factualAbsence(date)) }" :style="cellStyle(date)"><header><b>{{ dayLabel(date) }}</b><span v-if="openTaskCount(date)" class="calendarWeekTaskCount">{{ openTaskCount(date) }}</span></header><p v-if="cellFacts(date).shift" class="calendarWeekShiftLabel">{{ cellFacts(date).shift?.name }}</p><p v-else-if="isScheduleFreeDay(date)" class="calendarWeekFreeLabel"><span class="calendarWeekPalm" aria-hidden="true"><svg viewBox="0 0 64 64" focusable="false"><path d="M31 55c1-12 2-24 1-36M31 22c-8-9-16-9-23-5 8 0 14 4 19 11M33 21c7-10 15-11 23-7-8 1-14 6-18 13M32 19c-2-8-7-13-14-15 5 5 8 11 9 18M33 19c3-8 8-13 15-15-5 5-8 11-10 18"/></svg></span></p><p v-for="item in cellFacts(date).important" :key="item.id">{{ item.icon || '★' }} {{ item.title }}</p><p v-for="absence in cellFacts(date).absences" :key="absence.periodId">{{ absence.title || absence.typeName }}</p></article>
        </div>
      </section>

      <section v-show="mode === 'day'" id="calendarDayExperience" class="calendarDayExperience" aria-label="Почасовой календарь дня">
        <div class="calendarDayHeader"><div><div class="eyebrow">День</div><h2 id="calendarDayTitle">{{ dateLabel(focusDate, language, { weekday:'long', day:'numeric', month:'long' }) }}</h2><div id="calendarDaySubtitle" class="calendarDaySubtitle">{{ focusFacts.shift?.name || (language === 'en' ? 'Day off' : 'Свободный день') }}</div></div><button v-if="viewingSelf" id="calendarDayOpenDetails" type="button" @click="openDetails">Все детали дня</button><span v-else class="calendarProfileReadOnly">{{ language === 'en' ? 'Read-only schedule' : 'График только для просмотра' }}</span></div>
        <div id="calendarAllDay" class="calendarAllDay" :hidden="!allDayItems.length"><div class="calendarAllDayHead">Весь день</div><div class="calendarAllDayItems"><div v-for="item in allDayItems" :key="item.key" class="calendarAllDayItem" :class="item.type" :style="{ '--event-color': item.color || 'var(--accent)' }">{{ item.title }}</div></div></div>
        <div id="calendarTimeline" class="calendarTimeline" aria-label="Почасовая шкала"><div id="calendarTimelineHours" class="calendarTimelineHours"><span v-for="index in 13" :key="index" :style="{ top: `${(((index - 1) * 2) / 24) * 100}%` }">{{ String((index - 1) * 2).padStart(2,'0') }}:00</span></div><div id="calendarTimelineCanvas" class="calendarTimelineCanvas"><button v-for="event in timelineEvents" :key="event.key" type="button" class="calendarTimelineEvent" :class="event.type" :style="eventStyle(event)" @click="openTimelineEvent(event)"><b>{{ event.title }}</b><span>{{ timelineRange(event) }}<template v-if="event.meta"> · {{ event.meta }}</template></span></button></div></div>
      </section>
    </div>

    <div v-show="mode === 'month'" id="calendarMonthExperience">
      <div id="layout" class="layout" :class="{ 'with-panel': dayPanelOpen }">
        <div class="card vue-calendar-month-card">
          <div class="wd"><div v-for="(label, index) in (language === 'en' ? ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'] : ['Пн','Вт','Ср','Чт','Пт','Сб','Вс'])" :key="label" :class="{ we: index > 4 }">{{ label }}</div></div>
          <div id="grid" class="grid" :aria-busy="loading ? 'true' : 'false'">
            <button
              v-for="date in gridDates"
              :key="date"
              type="button"
              class="cell"
              :class="{ sel: date === focusDate, todayCell: date === workDate, outside: !inFocusMonth(date), hasShift: Boolean(cellFacts(date).shift), hasVacation: cellFacts(date).absences.length, hasAbsenceFact: Boolean(factualAbsence(date)), isScheduleFree: inFocusMonth(date) && isScheduleFreeDay(date), isWeekend: isWeekend(date) }"
              :style="cellStyle(date)"
              :aria-label="cellAriaLabel(date)"
              :data-date="date"
              @click="chooseDate(date)"
            >
              <span class="num calendarCellDateZone">{{ dayNumber(date) }}</span>
              <span v-if="importantMarker(date)" class="importantMark calendarCellImportantZone" :style="{ '--important-color': importantMarker(date)?.color || 'var(--accent)' }" :title="importantMarker(date)?.title || ''"><span>{{ importantMarkerGlyph(date) }}</span><small v-if="cellFacts(date).important.length > 1">+{{ cellFacts(date).important.length - 1 }}</small></span>
              <span v-if="cellFacts(date).day?.dayEmoji" class="dayEmoji calendarCellMarkerZone" :title="language === 'en' ? 'Day marker' : 'Маркер дня'">{{ cellFacts(date).day?.dayEmoji }}</span>
              <span v-if="openTaskCount(date)" class="taskMark calendarCellTaskZone" :aria-label="`${language === 'en' ? 'Open tasks' : 'Открытых задач'}: ${openTaskCount(date)}`">{{ openTaskCount(date) }}</span>
              <span v-if="inFocusMonth(date) && isScheduleFreeDay(date)" class="calendarDayOffWatermark" aria-hidden="true">
                <svg viewBox="0 0 64 64" focusable="false"><path d="M31 55c1-12 2-24 1-36M31 22c-8-9-16-9-23-5 8 0 14 4 19 11M33 21c7-10 15-11 23-7-8 1-14 6-18 13M32 19c-2-8-7-13-14-15 5 5 8 11 9 18M33 19c3-8 8-13 15-15-5 5-8 11-10 18"/></svg>
              </span>
              <span v-if="profileOverrideReason(date)" class="absenceFact absence-time_off" data-profile-override-off>◷ {{ profileOverrideReason(date) }}</span>
              <template v-if="factualAbsence(date)">
                <span
                  class="absenceFact"
                  :class="absenceSystemClass(factualAbsence(date))"
                  :style="{ color: factualAbsence(date)?.typeColor, '--absence-color': factualAbsence(date)?.typeColor }"
                  :data-absence-status="String(factualAbsence(date)?.status ?? 'PLANNED').toLowerCase()"
                >{{ absenceGlyph(factualAbsence(date)) }} {{ absenceTitle(factualAbsence(date)) }}</span>
                <span v-if="cellFacts(date).shift" class="plannedShiftGhost">{{ language === 'en' ? 'Planned' : 'По графику' }}: {{ cellFacts(date).shift?.name }}</span>
              </template>
              <span v-else-if="cellFacts(date).shift" class="shift" :style="{ color: cellFacts(date).shift?.color }">{{ cellFacts(date).shift?.name }}<small v-if="shiftRange(date)" class="shiftClock">{{ shiftRange(date) }}</small></span>
              <span
                v-for="absence in partialAbsences(date).slice(0,2)"
                :key="`partial-${absence.periodId}`"
                class="partialAbsenceBar"
                :class="absenceSystemClass(absence)"
                :style="{ '--absence-color': absence.typeColor }"
                :data-absence-status="String(absence.status ?? 'PLANNED').toLowerCase()"
              >{{ absenceGlyph(absence) }} {{ absence.startTime || '—' }}–{{ absence.endTime || '—' }} · {{ absenceTitle(absence) }}</span>
              <span v-if="cellFacts(date).day?.notes?.length || cellFacts(date).day?.note" class="ear"></span>
              <span v-if="(cellFacts(date).day?.notes?.length ?? 0) > 1" class="noteCountBadge">{{ cellFacts(date).day?.notes?.length }}</span>
              <span v-if="secondaryAbsences(date).length" class="vacationMark" :style="{ background: secondaryAbsences(date)[0]?.typeColor }">●</span>
            </button>
          </div>
          <div id="summary" class="sum"><span class="lbl">{{ selectedProfile?.name || (language === 'en' ? 'Me' : 'Я') }}:</span><span>{{ monthSummary.shifts }} смен</span><template v-if="viewingSelf"><span>{{ monthSummary.tasks }} задач</span><span>{{ monthSummary.absences }} отсутствий</span><span class="over">баланс {{ bundle?.overtimeAccount.balanceHours ?? 0 }} ч</span></template><span v-else class="calendarProfileReadOnly">{{ selectedProfile?.timezone }}</span></div>
        </div>
        <SelectedDayPanel v-if="dayPanelOpen && viewingSelf" :bridge="bridge" />
      </div>
      <ManagedProfileDayCard
        v-if="!viewingSelf && selectedProfile && selectedProfile.scheduleEditable !== false"
        :profile="selectedProfile"
        :date="focusDate"
        :entry="focusProfileEntry"
        :shift-types="bundle?.shiftTypes ?? []"
        :language="language"
      />
    </div>
  </section>
</template>
