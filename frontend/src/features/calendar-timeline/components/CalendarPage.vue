<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import {
  dateLabel,
  dayFacts,
  minutesOf,
  monthGridDates,
  monthStart,
  timePart,
  weekDates,
} from "../types/model";
import type { CalendarMode } from "../types/domain";

const props = defineProps<{ bridge: LegacyBridge }>();
const store = useCalendarTimelineStore();
const shell = useShellStore();
const { language } = storeToRefs(shell);
const { bundle, focusDate, mode, loading, error, workDate } = storeToRefs(store);

const gridDates = computed(() => monthGridDates(focusDate.value));
const week = computed(() => weekDates(focusDate.value));
const focusFacts = computed(() => dayFacts(bundle.value, focusDate.value));
const focusMonth = computed(() => monthStart(focusDate.value).slice(0, 7));
const monthTitle = computed(() => dateLabel(`${focusMonth.value}-01`, language.value, { month: "long" }));
const yearTitle = computed(() => focusDate.value.slice(0, 4));
const monthDates = computed(() => gridDates.value.filter(date => date.startsWith(focusMonth.value)));
const monthSummary = computed(() => ({
  shifts: monthDates.value.filter(date => dayFacts(bundle.value, date).shift).length,
  tasks: monthDates.value.reduce((sum, date) => sum + dayFacts(bundle.value, date).tasks.filter(task => !task.done).length, 0),
  absences: monthDates.value.reduce((sum, date) => sum + dayFacts(bundle.value, date).absences.length, 0),
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
  for (const { layer, entry } of focusFacts.value.layers.filter(value => value.entry.timed !== false && value.entry.displayStart)) {
    const startDate = String(entry.displayStart).slice(0, 10) || focusDate.value;
    const endDate = String(entry.displayEnd || entry.displayStart).slice(0, 10) || startDate;
    const value = segment(startDate, timePart(entry.displayStart), endDate, timePart(entry.displayEnd));
    if (value) events.push({ key: `layer-${layer.id}-${entry.sourceDate || entry.date}`, type: "layer", title: `${layer.name}: ${entry.shiftTypeName || "Смена"}`, ...value, meta: layer.timezone || "", color: layer.color, actionId: null });
  }
  const laneEnds: number[] = [];
  return events.sort((left, right) => left.start - right.start || left.end - right.end).map(event => {
    let lane = laneEnds.findIndex(value => value <= event.start);
    if (lane < 0) lane = laneEnds.length;
    laneEnds[lane] = event.end;
    return { ...event, lane };
  });
});

function cellFacts(date: string) { return dayFacts(bundle.value, date); }
function factualAbsence(date: string) { return cellFacts(date).absences.find(item => item.coverage === "FULL_DAY" && item.replacesShift) ?? null; }
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
function shiftRange(date: string): string {
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

async function chooseDate(date: string): Promise<void> {
  await store.openDate(date, "month");
  await nextTick();
  props.bridge.openCalendarDay(date);
}

async function chooseWeekDate(date: string): Promise<void> { await store.openDate(date, "week"); }
async function navigate(delta: number): Promise<void> {
  props.bridge.closeCalendarDay();
  await store.navigate(delta);
}
async function goToday(): Promise<void> {
  await store.goToday(mode.value);
  if (mode.value === "month") {
    await nextTick();
    props.bridge.openCalendarDay(store.focusDate);
  } else {
    props.bridge.closeCalendarDay();
  }
}
async function setMode(nextMode: CalendarMode): Promise<void> {
  await store.setMode(nextMode);
  if (nextMode !== "month") props.bridge.closeCalendarDay();
}
async function openDetails(): Promise<void> {
  await store.setMode("month");
  await nextTick();
  props.bridge.openCalendarDay(focusDate.value);
}

onMounted(() => {
  props.bridge.attachCalendarEditor("calendarLegacyPanelHost");
});
onBeforeUnmount(() => {
  props.bridge.parkCalendarEditor();
});
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
        <div id="calendarLayerBar" class="calendarLayerBar" :hidden="!(bundle?.calendarLayers.length)">
          <button v-for="layer in bundle?.calendarLayers ?? []" :key="layer.id" type="button" class="calendarLayerToggle" :aria-pressed="layer.visible ? 'true' : 'false'" @click="store.toggleLayer(layer.id, !layer.visible)"><i :style="{ background: layer.color }"></i>{{ layer.name }}</button>
        </div>
      </div>

      <div v-if="error" class="domain-alert domain-alert--danger" role="alert">{{ error }} <button type="button" @click="store.refresh()">Повторить</button></div>

      <section v-show="mode === 'week'" id="calendarWeekExperience" class="calendarWeekExperience" aria-label="Недельный календарь">
        <div id="calendarWeekStrip" class="calendarWeekStrip">
          <button v-for="date in week" :key="date" type="button" class="calendarWeekDay" :class="{ isSelected: date === focusDate, todayCell: date === workDate }" :data-date="date" @click="chooseWeekDate(date)"><small>{{ weekday(date) }}</small><b>{{ dayNumber(date) }}</b><span>{{ cellFacts(date).shift?.name || '—' }}</span></button>
        </div>
        <div id="calendarWeekAgenda" class="calendarWeekAgenda">
          <article v-for="date in week" :key="`agenda-${date}`"><header><b>{{ dayLabel(date) }}</b><span>{{ cellFacts(date).tasks.filter(task => !task.done).length }} задач</span></header><p v-if="cellFacts(date).shift">{{ cellFacts(date).shift?.name }}</p><p v-for="item in cellFacts(date).important" :key="item.id">★ {{ item.title }}</p><p v-for="absence in cellFacts(date).absences" :key="absence.periodId">{{ absence.title || absence.typeName }}</p></article>
        </div>
      </section>

      <section v-show="mode === 'day'" id="calendarDayExperience" class="calendarDayExperience" aria-label="Почасовой календарь дня">
        <div class="calendarDayHeader"><div><div class="eyebrow">День</div><h2 id="calendarDayTitle">{{ dateLabel(focusDate, language, { weekday:'long', day:'numeric', month:'long' }) }}</h2><div id="calendarDaySubtitle" class="calendarDaySubtitle">{{ focusFacts.shift?.name || 'Смена не назначена' }}</div></div><button id="calendarDayOpenDetails" type="button" @click="openDetails">Все детали дня</button></div>
        <div id="calendarAllDay" class="calendarAllDay" :hidden="!allDayItems.length"><div class="calendarAllDayHead">Весь день</div><div class="calendarAllDayItems"><div v-for="item in allDayItems" :key="item.key" class="calendarAllDayItem" :class="item.type" :style="{ '--event-color': item.color || 'var(--accent)' }">{{ item.title }}</div></div></div>
        <div id="calendarTimeline" class="calendarTimeline" aria-label="Почасовая шкала"><div id="calendarTimelineHours" class="calendarTimelineHours"><span v-for="index in 13" :key="index" :style="{ top: `${(((index - 1) * 2) / 24) * 100}%` }">{{ String((index - 1) * 2).padStart(2,'0') }}:00</span></div><div id="calendarTimelineCanvas" class="calendarTimelineCanvas"><button v-for="event in timelineEvents" :key="event.key" type="button" class="calendarTimelineEvent" :class="event.type" :style="eventStyle(event)" @click="openTimelineEvent(event)"><b>{{ event.title }}</b><span>{{ timelineRange(event) }}<template v-if="event.meta"> · {{ event.meta }}</template></span></button></div></div>
      </section>
    </div>

    <div v-show="mode === 'month'" id="calendarMonthExperience">
      <div id="layout" class="layout">
        <div class="card vue-calendar-month-card">
          <div class="wd"><div v-for="label in (language === 'en' ? ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'] : ['Пн','Вт','Ср','Чт','Пт','Сб','Вс'])" :key="label">{{ label }}</div></div>
          <div id="grid" class="grid" :aria-busy="loading ? 'true' : 'false'">
            <button
              v-for="date in gridDates"
              :key="date"
              type="button"
              class="cell"
              :class="{ sel: date === focusDate, todayCell: date === workDate, outside: !inFocusMonth(date), hasVacation: cellFacts(date).absences.length, hasAbsenceFact: Boolean(factualAbsence(date)) }"
              :style="factualAbsence(date) ? { '--absence-color': factualAbsence(date)?.typeColor } : undefined"
              :data-date="date"
              @click="chooseDate(date)"
            >
              <span class="num">{{ dayNumber(date) }}</span>
              <span v-if="cellFacts(date).day?.dayEmoji" class="dayEmoji">{{ cellFacts(date).day?.dayEmoji }}</span>
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
              <span v-for="item in cellFacts(date).important.slice(0,1)" :key="item.id" class="importantMark">★</span>
              <span v-if="cellFacts(date).tasks.some(task => !task.done)" class="taskMark">!</span>
              <span v-for="item in cellFacts(date).layers.slice(0,2)" :key="`${item.layer.id}-${item.entry.sourceDate}`" class="calendarLayerChip" :style="{ '--layer-color': item.layer.color }">{{ item.layer.name }}</span>
            </button>
          </div>
          <div id="summary" class="sum"><span class="lbl">Итого:</span><span>{{ monthSummary.shifts }} смен</span><span>{{ monthSummary.tasks }} задач</span><span>{{ monthSummary.absences }} отсутствий</span><span class="over">баланс {{ bundle?.overtimeAccount.balanceHours ?? 0 }} ч</span></div>
        </div>
        <div id="calendarLegacyPanelHost" class="calendar-legacy-panel-host"></div>
      </div>
    </div>
  </section>
</template>
