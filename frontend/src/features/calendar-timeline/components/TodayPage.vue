<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import { dateLabel, dayFacts, timePart } from "../types/model";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const store = useCalendarTimelineStore();
const { language } = storeToRefs(shell);
const { bundle, workDate } = storeToRefs(store);
const facts = computed(() => dayFacts(bundle.value, workDate.value));
const nearby = computed(() => Array.from({ length: 7 }, (_, index) => {
  const value = new Date(`${workDate.value}T00:00:00Z`); value.setUTCDate(value.getUTCDate() + index - 3); return value.toISOString().slice(0,10);
}));
const openTasks = computed(() => facts.value.tasks.filter(task => !task.done));
const upcoming = computed(() => (bundle.value?.importantDays ?? []).filter(item => item.date >= workDate.value).sort((a,b) => a.date.localeCompare(b.date)).slice(0,5));
const shiftRange = computed(() => { const occurrence = facts.value.occurrences[0]; return occurrence ? `${timePart(occurrence.displayStart)}–${timePart(occurrence.displayEnd)}` : "—"; });

async function openCalendar(date = workDate.value): Promise<void> { props.bridge.navigate("calendar"); await store.openDate(date, "day"); }
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
      <section class="card todayHero"><div class="todayHeroHead"><div><div class="eyebrow">Сегодня</div><h2 id="todayDateTitle">{{ dateLabel(workDate, language, { weekday:'long', day:'numeric', month:'long' }) }}</h2><div id="todayDateSubtitle" class="todayDateSubtitle">{{ dateLabel(workDate, language, { year:'numeric' }) }}</div></div><button id="todayOpenCalendar" class="todayCalendarButton" type="button" @click="openCalendar()">Открыть календарь</button></div><div id="todayDateStrip" class="todayDateStrip" aria-label="Ближайшие дни"><button v-for="date in nearby" :key="date" type="button" class="todayDateChip" :class="{ active: date === workDate }" @click="openCalendar(date)"><small>{{ dateLabel(date, language, { weekday:'short' }) }}</small><b>{{ Number(date.slice(8,10)) }}</b></button></div></section>
      <section class="todayQuickActions" aria-label="Быстрые действия"><button id="todayQuickTask" type="button" @click="openTask"><span>✓</span><b>Новая задача</b><small>на сегодня</small></button><button id="todayQuickNote" type="button" @click="openNote"><span>▤</span><b>Новая заметка</b><small>в выбранный день</small></button><button id="todayQuickAbsence" type="button" @click="openAbsence"><span>☂</span><b>Оформить отсутствие</b><small>отпуск, отгул, больничный</small></button><button id="todayQuickMore" type="button" @click="openMore"><span>⋯</span><b>Быстро добавить</b><small>все действия</small></button></section>
      <div class="todayDashboardGrid">
        <section id="todayShiftCard" class="card todayShiftCard"><div class="todayCardHead"><span class="todayCardIcon">◷</span><div class="todayCardHeading"><small>Смена</small><h3 id="todayShiftTitle">{{ facts.shift?.name || 'Смена не назначена' }}</h3></div><span id="todayShiftStatus" class="todayStatusPill">{{ facts.shift ? 'по графику' : 'нет смены' }}</span></div><div id="todayShiftTime" class="todayShiftTime">{{ shiftRange }}</div><div id="todayShiftDateRange" hidden></div><div id="todayShiftMeta" class="todayShiftMeta">{{ facts.occurrences[0]?.displayTimezone || '' }}</div><div id="todayShiftProgressWrap" class="todayProgress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><span id="todayShiftProgress"></span></div><div class="todayShiftFooter"><span id="todayShiftCountdown">{{ facts.shift ? 'Смена отражена в календаре' : 'День свободен для планирования' }}</span><button id="todayOpenShiftDay" type="button" @click="openCalendar()">Открыть день</button></div></section>
        <section id="todayOvertimeCard" class="card todayOvertimeCard"><div class="todayCardHead"><span class="todayCardIcon">◴</span><div class="todayCardHeading"><small>Учёт времени</small><h3 id="todayOvertimeTitle">Переработки</h3></div><button id="todayOpenOvertime" class="todayCardLink" type="button" @click="props.bridge.navigate('overtime')">Журнал</button></div><div class="todayBalanceRow"><span>Доступно</span><b id="todayOvertimeBalance">{{ bundle?.overtimeAccount.balanceHours ?? 0 }} ч</b></div><div class="todayBalanceTrack"><span id="todayOvertimeProgress"></span></div><div class="todayMetricGrid"><div><small>Начислено</small><b id="todayOvertimeEarned">{{ bundle?.overtimeAccount.totalEarnedHours ?? 0 }} ч</b></div><div><small>Использовано</small><b id="todayOvertimeUsed">{{ bundle?.overtimeAccount.totalUsedHours ?? 0 }} ч</b></div></div></section>
        <section id="todayTasksCard" class="card todayTasksCard"><div class="todayCardHead"><span class="todayCardIcon">✓</span><div class="todayCardHeading"><small>План дня</small><h3 id="todayTasksTitle">Задачи</h3></div><span id="todayTaskCount" class="todayCountPill">{{ openTasks.length }}</span></div><div id="todayTaskList" class="todayTaskList"><button v-for="task in openTasks.slice(0,5)" :key="task.id" type="button" class="todayTaskMain" @click="openTaskDetails(task.id)"><b>{{ task.text }}</b><small>{{ task.dueTime || task.priority }}</small></button><p v-if="!openTasks.length">На сегодня открытых задач нет.</p></div><button id="todayOpenTasks" class="todayWideLink" type="button" @click="props.bridge.navigate('tasks')">Показать все задачи →</button></section>
        <section id="todayUpcomingCard" class="card todayUpcomingCard"><div class="todayCardHead"><span class="todayCardIcon">★</span><div class="todayCardHeading"><small>Не пропустить</small><h3 id="todayUpcomingTitle">Ближайшие даты</h3></div><button id="todayOpenImportant" class="todayCardLink" type="button" @click="props.bridge.navigate('important')">Все даты</button></div><div id="todayUpcomingList" class="todayUpcomingList"><button v-for="item in upcoming" :key="item.id" type="button" class="todayUpcomingRow" @click="openImportantDetails(item.id)"><span class="todayUpcomingDot" :style="{ '--event-color': item.color || 'var(--accent)' }"></span><span><b>{{ item.icon || '★' }} {{ item.title }}</b><small>{{ dateLabel(item.date, language, { day:'numeric', month:'short' }) }}</small></span></button><p v-if="!upcoming.length">Ближайших дат нет.</p></div></section>
      </div>
    </div>
  </section>
</template>
