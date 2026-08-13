<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { useSettingsWorkspaceStore } from "@/features/settings-workspace/stores/settingsWorkspaceStore";
import { workspaceDefinition } from "@/features/settings-workspace/types/model";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import {
  calendarDayVisualStyle,
  calendarFactualAbsence,
  calendarImportantGlyph,
  calendarOpenTaskCount,
  calendarScheduleFree,
  dateLabel,
  dayFacts,
  durationCountdown,
  importantRelativeLabel,
  timePart,
  todayShiftProjection,
} from "../types/model";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const shell = useShellStore();
const settings = useSettingsWorkspaceStore();
const store = useCalendarTimelineStore();
const { language, modules } = storeToRefs(shell);
const { appearance } = storeToRefs(settings);
const { bundle, workDate } = storeToRefs(store);
const facts = computed(() => dayFacts(bundle.value, workDate.value));
const nearby = computed(() => Array.from({ length: 7 }, (_, index) => {
  const value = new Date(`${workDate.value}T00:00:00Z`); value.setUTCDate(value.getUTCDate() + index - 3); return value.toISOString().slice(0,10);
}));
function nearbyFacts(date: string) { return dayFacts(bundle.value, date); }
function nearbyFactualAbsence(date: string) { return calendarFactualAbsence(nearbyFacts(date)); }
function nearbyScheduleFree(date: string): boolean { return Boolean(bundle.value) && calendarScheduleFree(nearbyFacts(date)); }
function nearbyTaskCount(date: string): number { return calendarOpenTaskCount(nearbyFacts(date)); }
function nearbyImportant(date: string) { return nearbyFacts(date).important[0] ?? null; }
function nearbyMarker(date: string): string { return String(nearbyFacts(date).day?.dayEmoji ?? "").trim(); }
function nearbyStyle(date: string): Record<string, string> { return calendarDayVisualStyle(nearbyFacts(date)); }
function nearbyAriaLabel(date: string): string {
  const day = nearbyFacts(date);
  const parts = [dateLabel(date, language.value, { weekday: "long", day: "numeric", month: "long" })];
  const absence = calendarFactualAbsence(day);
  if (absence) parts.push(`${language.value === "en" ? "Absence" : "Отсутствие"}: ${absence.title || absence.typeName}`);
  else if (day.shift) parts.push(`${language.value === "en" ? "Shift" : "Смена"}: ${day.shift.name}`);
  else if (calendarScheduleFree(day)) parts.push(language.value === "en" ? "Schedule: day off" : "По графику: свободный день");
  const tasks = calendarOpenTaskCount(day);
  if (tasks) parts.push(`${language.value === "en" ? "Tasks" : "Задачи"}: ${tasks}`);
  if (day.important.length) parts.push(`${language.value === "en" ? "Important dates" : "Важные даты"}: ${day.important.length}`);
  const marker = String(day.day?.dayEmoji ?? "").trim();
  if (marker) parts.push(`${language.value === "en" ? "Day marker" : "Маркер дня"}: ${marker}`);
  return parts.join(". ");
}
const openTasks = computed(() => facts.value.tasks.filter(task => !task.done));
const upcoming = computed(() => (bundle.value?.importantDays ?? []).filter(item => item.date >= workDate.value).sort((a,b) => a.date.localeCompare(b.date)).slice(0,5));
const nowMs = ref(Date.now());
let todayClock: ReturnType<typeof setInterval> | null = null;
const shiftProjection = computed(() => todayShiftProjection(bundle.value, workDate.value, nowMs.value));
const shiftOccurrence = computed(() => shiftProjection.value?.occurrence ?? facts.value.occurrences[0] ?? null);
const shiftRange = computed(() => shiftOccurrence.value ? `${timePart(shiftOccurrence.value.displayStart)}–${timePart(shiftOccurrence.value.displayEnd)}` : "—");
const shiftTitle = computed(() => shiftProjection.value?.shift?.name ?? facts.value.shift?.name ?? (language.value === "en" ? "No shift assigned" : "Смена не назначена"));
const shiftStatus = computed(() => {
  const phase = shiftProjection.value?.phase;
  if (language.value === "en") return phase === "active" ? "in progress" : phase === "future" || phase === "next" ? "until start" : phase === "finished" ? "completed" : (facts.value.shift ? "scheduled" : "no shift");
  return phase === "active" ? "идёт" : phase === "future" || phase === "next" ? "до начала" : phase === "finished" ? "завершена" : (facts.value.shift ? "по графику" : "нет смены");
});
const shiftCountdown = computed(() => {
  const projection = shiftProjection.value;
  if (!projection) return facts.value.shift ? (language.value === "en" ? "Shift is shown in the calendar" : "Смена отражена в календаре") : (language.value === "en" ? "The day is free for planning" : "День свободен для планирования");
  if (projection.phase === "finished") return language.value === "en" ? "Shift completed" : "Смена завершена";
  const prefix = projection.phase === "active" ? (language.value === "en" ? "Ends in" : "До конца") : (language.value === "en" ? "Starts in" : "До начала");
  return `${prefix}: ${durationCountdown(projection.remainingMs, language.value)}`;
});
const shiftProgress = computed(() => shiftProjection.value?.progress ?? 0);
const shiftProgressVisible = computed(() => Boolean(shiftProjection.value));
const overtimeBalancePercent = computed(() => {
  const earned = Math.max(0, Number(bundle.value?.overtimeAccount.totalEarnedHours ?? 0));
  const balance = Math.max(0, Number(bundle.value?.overtimeAccount.balanceHours ?? 0));
  if (!(earned > 0.0001)) return 0;
  return Math.min(100, Math.max(0, (balance / earned) * 100));
});
const overtimeProgressLabel = computed(() => {
  const earned = Number(bundle.value?.overtimeAccount.totalEarnedHours ?? 0);
  const used = Number(bundle.value?.overtimeAccount.totalUsedHours ?? 0);
  const balance = Number(bundle.value?.overtimeAccount.balanceHours ?? 0);
  return language.value === "en"
    ? `Available ${balance} of ${earned} earned hours; ${used} used`
    : `Доступно ${balance} из ${earned} начисленных часов; использовано ${used}`;
});
const shiftOpenDate = computed(() => shiftProjection.value?.date ?? workDate.value);
const shiftMeta = computed(() => {
  const occurrence = shiftOccurrence.value;
  if (!occurrence) return "";
  const parts = [occurrence.displayTimezone];
  if (Number.isFinite(occurrence.netMinutes)) parts.push(language.value === "en" ? `Working time: ${(occurrence.netMinutes / 60).toFixed(1).replace(/\.0$/, "")} h` : `Рабочее время: ${(occurrence.netMinutes / 60).toFixed(1).replace(/\.0$/, "")} ч`);
  if (Number.isFinite(occurrence.breakMinutes) && occurrence.breakMinutes > 0) parts.push(language.value === "en" ? `Break: ${occurrence.breakMinutes} min` : `Обед: ${occurrence.breakMinutes} мин`);
  return parts.filter(Boolean).join(" · ");
});
function relativeImportant(date: string): string { return importantRelativeLabel(workDate.value, date, language.value); }

onMounted(() => { todayClock = setInterval(() => { nowMs.value = Date.now(); }, 30_000); });
onUnmounted(() => { if (todayClock !== null) clearInterval(todayClock); });

type TodayWidget = "shift" | "overtime" | "tasks" | "important";
const todayWidgets = computed<TodayWidget[]>(() => {
  const configured = workspaceDefinition(appearance.value.themeConfig).todayWidgets.filter((id): id is TodayWidget => ["shift", "overtime", "tasks", "important"].includes(id));
  const requested = configured.includes("shift") ? configured : ["shift" as TodayWidget, ...configured];
  return requested.filter(id => {
    if (id === "shift") return true;
    if (id === "overtime") return modules.value.overtime !== false;
    if (id === "tasks") return modules.value.tasks !== false;
    return modules.value.important_dates !== false;
  });
});

async function openCalendar(date = workDate.value): Promise<void> { navigateHashRoute("calendar"); await store.openDate(date, "day"); }
function openTask(): void { void window.DutyLogVueDomains?.productivity?.openTaskCreate(workDate.value); }
function openTaskDetails(id: number): void { void window.DutyLogVueDomains?.productivity?.openTaskDetails(id); }
function openImportantDetails(id: number): void { void window.DutyLogVueDomains?.productivity?.openImportantDetails(id); }
async function openAbsence(): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceComposer({ date: workDate.value, source: "today" }); }
function openMore(): void { window.DutyLogVueDomains?.productivity?.openQuickActions(workDate.value); }
async function openNote(): Promise<void> { await window.DutyLogVueDomains?.productivity?.openNoteCreate(workDate.value); }
</script>

<template>
  <section id="view-today" class="view todayView vue-today-view" data-vue-domain-route="today" data-vue-domain-owner="calendar-timeline">
    <div class="todayDashboard">
      <section class="card todayHero"><div class="todayHeroHead"><div><div class="eyebrow">Сегодня</div><h2 id="todayDateTitle">{{ dateLabel(workDate, language, { weekday:'long', day:'numeric', month:'long' }) }}</h2><div id="todayDateSubtitle" class="todayDateSubtitle">{{ dateLabel(workDate, language, { year:'numeric' }) }}</div></div><button id="todayOpenCalendar" class="todayCalendarButton" type="button" @click="openCalendar()">Открыть календарь</button></div><div id="todayDateStrip" class="todayDateStrip" aria-label="Ближайшие дни"><button v-for="date in nearby" :key="date" type="button" class="todayDateChip" :class="{ active: date === workDate, isToday: date === workDate, hasShift: Boolean(nearbyFacts(date).shift), isScheduleFree: nearbyScheduleFree(date), hasAbsenceFact: Boolean(nearbyFactualAbsence(date)) }" :style="nearbyStyle(date)" :data-date="date" :aria-label="nearbyAriaLabel(date)" @click="openCalendar(date)"><small>{{ dateLabel(date, language, { weekday:'short' }) }}</small><b>{{ Number(date.slice(8,10)) }}</b><span v-if="nearbyFactualAbsence(date)" class="todayDateAbsenceLabel">{{ nearbyFactualAbsence(date)?.title || nearbyFactualAbsence(date)?.typeName }}</span><span v-else-if="nearbyFacts(date).shift" class="todayDateShiftLabel">{{ nearbyFacts(date).shift?.name }}</span><span v-else-if="nearbyScheduleFree(date)" class="todayDatePalm" aria-hidden="true"><svg viewBox="0 0 64 64" focusable="false"><path d="M31 55c1-12 2-24 1-36M31 22c-8-9-16-9-23-5 8 0 14 4 19 11M33 21c7-10 15-11 23-7-8 1-14 6-18 13M32 19c-2-8-7-13-14-15 5 5 8 11 9 18M33 19c3-8 8-13 15-15-5 5-8 11-10 18"/></svg></span><span v-if="nearbyImportant(date)" class="todayDateImportantGlyph" aria-hidden="true">{{ calendarImportantGlyph(nearbyImportant(date)) }}</span><span v-if="nearbyMarker(date)" class="todayDateMarkerGlyph" aria-hidden="true">{{ nearbyMarker(date) }}</span><span v-if="nearbyTaskCount(date)" class="todayDateTaskCount" aria-hidden="true">{{ nearbyTaskCount(date) }}</span></button></div></section>
      <section class="todayQuickActions" aria-label="Быстрые действия"><button id="todayQuickTask" type="button" @click="openTask"><span>✓</span><b>Новая задача</b><small>на сегодня</small></button><button id="todayQuickNote" type="button" @click="openNote"><span>▤</span><b>Новая заметка</b><small>в выбранный день</small></button><button id="todayQuickAbsence" type="button" @click="openAbsence"><span>☂</span><b>Оформить отсутствие</b><small>отпуск, отгул, больничный</small></button><button id="todayQuickMore" type="button" @click="openMore"><span>⋯</span><b>Быстро добавить</b><small>все действия</small></button></section>
      <div class="todayDashboardGrid">
        <template v-for="widget in todayWidgets" :key="widget">
          <section v-if="widget === 'shift'" id="todayShiftCard" class="card todayShiftCard" :data-shift-state="shiftProjection?.phase || (facts.shift ? 'scheduled' : 'empty')"><div class="todayCardHead"><span class="todayCardIcon">◷</span><div class="todayCardHeading"><small>Смена</small><h3 id="todayShiftTitle">{{ shiftTitle }}</h3></div><span id="todayShiftStatus" class="todayStatusPill">{{ shiftStatus }}</span></div><div id="todayShiftTime" class="todayShiftTime">{{ shiftRange }}</div><div id="todayShiftDateRange" hidden></div><div id="todayShiftMeta" class="todayShiftMeta">{{ shiftMeta }}</div><div v-show="shiftProgressVisible" id="todayShiftProgressWrap" class="todayProgress" role="progressbar" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="shiftProgress"><span id="todayShiftProgress" :style="{ width: `${shiftProgress}%` }"></span></div><div class="todayShiftFooter"><span id="todayShiftCountdown">{{ shiftCountdown }}</span><button id="todayOpenShiftDay" type="button" @click="openCalendar(shiftOpenDate)">Открыть день</button></div></section>
          <section v-else-if="widget === 'overtime'" id="todayOvertimeCard" class="card todayOvertimeCard"><div class="todayCardHead"><span class="todayCardIcon">◴</span><div class="todayCardHeading"><small>Учёт времени</small><h3 id="todayOvertimeTitle">Переработки</h3></div><button id="todayOpenOvertime" class="todayCardLink" type="button" @click="navigateHashRoute('overtime')">Журнал</button></div><div class="todayBalanceRow"><span>Доступно</span><b id="todayOvertimeBalance">{{ bundle?.overtimeAccount.balanceHours ?? 0 }} ч</b></div><div class="todayBalanceTrack" role="progressbar" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="Math.round(overtimeBalancePercent)" :aria-label="overtimeProgressLabel"><span id="todayOvertimeProgress" :style="{ width: `${overtimeBalancePercent}%` }" :data-balance-percent="overtimeBalancePercent.toFixed(1)"></span></div><div class="todayMetricGrid"><div><small>Начислено</small><b id="todayOvertimeEarned">{{ bundle?.overtimeAccount.totalEarnedHours ?? 0 }} ч</b></div><div><small>Использовано</small><b id="todayOvertimeUsed">{{ bundle?.overtimeAccount.totalUsedHours ?? 0 }} ч</b></div></div></section>
          <section v-else-if="widget === 'tasks'" id="todayTasksCard" class="card todayTasksCard"><div class="todayCardHead"><span class="todayCardIcon">✓</span><div class="todayCardHeading"><small>План дня</small><h3 id="todayTasksTitle">Задачи</h3></div><span id="todayTaskCount" class="todayCountPill">{{ openTasks.length }}</span></div><div id="todayTaskList" class="todayTaskList"><button v-for="task in openTasks.slice(0,5)" :key="task.id" type="button" class="todayTaskMain" @click="openTaskDetails(task.id)"><b>{{ task.text }}</b><small>{{ task.dueTime || task.priority }}</small></button><p v-if="!openTasks.length">На сегодня открытых задач нет.</p></div><button id="todayOpenTasks" class="todayWideLink" type="button" @click="navigateHashRoute('tasks')">Показать все задачи →</button></section>
          <section v-else id="todayUpcomingCard" class="card todayUpcomingCard"><div class="todayCardHead"><span class="todayCardIcon">★</span><div class="todayCardHeading"><small>Не пропустить</small><h3 id="todayUpcomingTitle">Ближайшие даты</h3></div><button id="todayOpenImportant" class="todayCardLink" type="button" @click="navigateHashRoute('important')">Все даты</button></div><div id="todayUpcomingList" class="todayUpcomingList"><button v-for="item in upcoming" :key="`${item.id}-${item.date}`" type="button" class="todayUpcomingRow" @click="openImportantDetails(item.id)"><span class="todayUpcomingDot" :style="{ '--event-color': item.color || 'var(--accent)' }"></span><span><b>{{ item.icon || '★' }} {{ item.title }}</b><small>{{ dateLabel(item.date, language, { day:'numeric', month:'short' }) }}</small></span><strong>{{ relativeImportant(item.date) }}</strong></button><p v-if="!upcoming.length">Ближайших дат нет.</p></div></section>
        </template>
      </div>
    </div>
  </section>
</template>
