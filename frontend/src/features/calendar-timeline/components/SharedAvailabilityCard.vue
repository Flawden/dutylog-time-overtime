<script setup lang="ts">
import { computed } from "vue";
import type { CalendarLayer, CalendarRangeBundle, SharedAvailabilityWindow } from "../types/domain";
import { sharedAvailabilityForDate } from "../types/model";

const props = defineProps<{
  bundle: CalendarRangeBundle | null;
  date: string;
  profile: CalendarLayer;
  language: string;
  highlightSharedWork: boolean;
}>();

const emit = defineEmits<{ (event: "toggle-shared-work"): void }>();
const availability = computed(() => sharedAvailabilityForDate(props.bundle, props.date, String(props.profile.id)));

function durationMinutesLabel(minutesValue: number): string {
  const minutes = Math.max(0, Math.round(minutesValue));
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const parts: string[] = [];
  if (hours) parts.push(`${hours} ${props.language === "en" ? "h" : "ч"}`);
  if (rest) parts.push(`${rest} ${props.language === "en" ? "min" : "мин"}`);
  return parts.join(" ") || (props.language === "en" ? "0 min" : "0 мин");
}

const totalFreeMinutes = computed(() => availability.value?.freeWindows.reduce((sum, item) => sum + item.durationMinutes, 0) ?? 0);
const totalSharedWorkMinutes = computed(() => availability.value?.sharedBusyWindows.reduce((sum, item) => sum + item.durationMinutes, 0) ?? 0);

const humanDate = computed(() => {
  try {
    return new Intl.DateTimeFormat(props.language === "en" ? "en-GB" : "ru-RU", {
      day: "numeric", month: "long", timeZone: "UTC",
    }).format(new Date(`${props.date}T00:00:00Z`));
  } catch {
    return props.date;
  }
});

function windowLabel(window: SharedAvailabilityWindow): string {
  if (window.startMinute === 0 && window.endMinute === 1440) return props.language === "en" ? "All day" : "Весь день";
  if (window.startMinute === 0) return props.language === "en" ? `Until ${window.endTime}` : `До ${window.endTime}`;
  if (window.endMinute === 1440) return props.language === "en" ? `After ${window.startTime}` : `После ${window.startTime}`;
  return `${window.startTime}–${window.endTime}`;
}

function segmentStyle(window: SharedAvailabilityWindow): Record<string, string> {
  return {
    left: `${(window.startMinute / 1440) * 100}%`,
    width: `${Math.max(0.35, (window.durationMinutes / 1440) * 100)}%`,
  };
}

const unknownText = computed(() => {
  if (availability.value?.unknownReason === "SELF_UNTIMED_WORK") {
    return props.language === "en"
      ? "Your work shift has no exact time, so a precise shared window cannot be calculated."
      : "У твоей рабочей смены не задано точное время, поэтому общее окно нельзя посчитать точно.";
  }
  return props.language === "en"
    ? `${props.profile.name}'s work shift has no exact time, so a precise shared window cannot be calculated.`
    : `У смены ${props.profile.name} не задано точное время, поэтому общее окно нельзя посчитать точно.`;
});
</script>

<template>
  <section v-if="availability" class="sharedAvailabilityCard" data-shared-availability :data-date="date" :data-profile-id="profile.id">
    <header class="sharedAvailabilityHead">
      <div>
        <h3>{{ language === 'en' ? 'Free together' : 'Вместе свободны' }}</h3>
        <span>{{ language === 'en' ? `Me + ${profile.name}` : `Я + ${profile.name}` }} · {{ humanDate }}</span>
      </div>
      <button
        type="button"
        class="sharedWorkToggle"
        :class="{ active: highlightSharedWork }"
        :aria-pressed="highlightSharedWork ? 'true' : 'false'"
        data-shared-work-toggle
        @click="emit('toggle-shared-work')"
      >
        <i aria-hidden="true"></i>
        {{ highlightSharedWork
          ? (language === 'en' ? 'Hide shared shifts' : 'Скрыть совместные смены')
          : (language === 'en' ? 'Show shared shifts' : 'Совместные смены') }}
      </button>
    </header>

    <div v-if="!availability.precise" class="sharedAvailabilityUnknown" data-shared-availability-unknown>
      <b>{{ language === 'en' ? 'Exact time is unknown' : 'Точное время неизвестно' }}</b>
      <span>{{ unknownText }}</span>
    </div>

    <template v-else>
      <div class="sharedAvailabilityHero" :class="{ allDay: availability.allDayFree, none: availability.noSharedFreeTime }">
        <b v-if="availability.allDayFree">{{ language === 'en' ? 'Free together all day' : 'Свободны вместе весь день' }}</b>
        <b v-else-if="availability.noSharedFreeTime">{{ language === 'en' ? 'No shared free time today' : 'Сегодня общего свободного времени нет' }}</b>
        <template v-else>
          <strong>{{ durationMinutesLabel(totalFreeMinutes) }}</strong>
          <span>{{ language === 'en' ? 'free together' : 'вместе свободны' }}</span>
        </template>
      </div>

      <div v-if="availability.freeWindows.length && !availability.allDayFree" class="sharedAvailabilityWindows" data-shared-availability-windows>
        <div v-for="window in availability.freeWindows" :key="`${window.startMinute}-${window.endMinute}`" class="sharedAvailabilityWindow">
          <b>{{ windowLabel(window) }}</b>
          <span>{{ durationMinutesLabel(window.durationMinutes) }}</span>
        </div>
      </div>

      <div class="sharedAvailabilityTimeline" aria-label="24 hour work availability">
        <div class="sharedAvailabilityTrack">
          <span
            v-for="window in availability.freeWindows"
            :key="`free-${window.startMinute}-${window.endMinute}`"
            class="sharedAvailabilitySegment free"
            :style="segmentStyle(window)"
            :title="`${windowLabel(window)} · ${durationMinutesLabel(window.durationMinutes)}`"
          ></span>
          <template v-if="highlightSharedWork">
            <span
              v-for="window in availability.sharedBusyWindows"
              :key="`shared-${window.startMinute}-${window.endMinute}`"
              class="sharedAvailabilitySegment sharedWork"
              :style="segmentStyle(window)"
              :title="`${language === 'en' ? 'Working together' : 'Работаем вместе'} ${window.startTime}–${window.endTime}`"
            ></span>
          </template>
        </div>
        <div class="sharedAvailabilityTicks" aria-hidden="true"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></div>
      </div>

      <div v-if="highlightSharedWork" class="sharedWorkSummary" data-shared-work-summary>
        <template v-if="availability.sharedBusyWindows.length">
          <b>{{ language === 'en' ? 'Working together' : 'Работаем вместе' }} · {{ durationMinutesLabel(totalSharedWorkMinutes) }}</b>
          <span>{{ availability.sharedBusyWindows.map(item => `${item.startTime}–${item.endTime}`).join(' · ') }}</span>
        </template>
        <template v-else>
          <b>{{ language === 'en' ? 'No shared shift today' : 'В этот день вместе не работаем' }}</b>
          <span>{{ language === 'en' ? 'Other dates in the calendar can still be highlighted.' : 'Совместные смены на других датах всё равно будут подсвечены в календаре.' }}</span>
        </template>
      </div>
    </template>

    <details class="sharedAvailabilityInfo">
      <summary>{{ language === 'en' ? 'Work schedules only' : 'Только рабочие графики' }} <span aria-hidden="true">ⓘ</span></summary>
      <p>{{ language === 'en'
        ? 'Work shifts and work absences are included. Tasks, notes and personal events are not treated as busy time.'
        : 'Учитываются смены и рабочие отсутствия. Задачи, заметки и личные события не считаются занятостью.' }}</p>
    </details>
  </section>
</template>

<style scoped>
.sharedAvailabilityCard{--shared-work-color:#ff8a5b;margin:10px 18px 0;padding:14px 16px;border:1px solid var(--border);border-radius:16px;background:var(--panel);display:grid;gap:10px}
.sharedAvailabilityHead{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.sharedAvailabilityHead h3{margin:0;font-size:1.08rem}
.sharedAvailabilityHead span{display:block;margin-top:3px;color:var(--muted);font-size:.78rem}
.sharedWorkToggle{min-height:36px;border:1px solid var(--border);border-radius:999px;background:var(--panelAlt,var(--panel));color:var(--text);padding:0 11px;display:inline-flex;align-items:center;gap:7px;font-size:.78rem;font-weight:700;cursor:pointer;white-space:nowrap}
.sharedWorkToggle i{width:9px;height:9px;border-radius:999px;background:var(--shared-work-color);box-shadow:0 0 0 3px color-mix(in srgb,var(--shared-work-color) 15%,transparent)}
.sharedWorkToggle.active{border-color:var(--shared-work-color);background:color-mix(in srgb,var(--shared-work-color) 12%,var(--panel))}
.sharedAvailabilityHero{display:flex;align-items:baseline;gap:7px;min-height:38px}
.sharedAvailabilityHero strong{font-size:clamp(1.45rem,4vw,2rem);line-height:1}
.sharedAvailabilityHero>span{color:var(--muted);font-weight:700}
.sharedAvailabilityHero> b{font-size:1.08rem}
.sharedAvailabilityHero.allDay b{color:var(--accent)}
.sharedAvailabilityHero.none b{color:var(--muted)}
.sharedAvailabilityWindows{display:flex;gap:8px;flex-wrap:wrap}
.sharedAvailabilityWindow{min-width:132px;padding:8px 10px;border:1px solid color-mix(in srgb,var(--accent) 20%,var(--border));border-radius:12px;background:color-mix(in srgb,var(--accent) 7%,var(--panelAlt,var(--panel)));display:grid;gap:2px}
.sharedAvailabilityWindow b{font-size:.92rem}
.sharedAvailabilityWindow span{color:var(--muted);font-size:.76rem}
.sharedAvailabilityTimeline{display:grid;gap:4px}
.sharedAvailabilityTrack{position:relative;height:11px;border-radius:999px;overflow:hidden;background:color-mix(in srgb,var(--text) 9%,var(--panelAlt,var(--panel)))}
.sharedAvailabilitySegment{position:absolute;top:0;bottom:0;border-radius:999px}
.sharedAvailabilitySegment.free{background:color-mix(in srgb,var(--accent) 72%,var(--panel))}
.sharedAvailabilitySegment.sharedWork{background:var(--shared-work-color);z-index:2;box-shadow:0 0 8px color-mix(in srgb,var(--shared-work-color) 48%,transparent)}
.sharedAvailabilityTicks{display:flex;justify-content:space-between;color:var(--muted);font-size:.64rem;font-variant-numeric:tabular-nums}
.sharedWorkSummary{padding:8px 10px;border-left:3px solid var(--shared-work-color);border-radius:9px;background:color-mix(in srgb,var(--shared-work-color) 8%,var(--panelAlt,var(--panel)));display:grid;gap:2px}
.sharedWorkSummary b{font-size:.84rem}.sharedWorkSummary span{color:var(--muted);font-size:.74rem}
.sharedAvailabilityUnknown{padding:10px 11px;border:1px dashed var(--border);border-radius:12px;display:grid;gap:3px}.sharedAvailabilityUnknown span{color:var(--muted);font-size:.8rem}
.sharedAvailabilityInfo{color:var(--muted);font-size:.74rem}.sharedAvailabilityInfo summary{cursor:pointer;display:inline-flex;gap:5px;align-items:center;font-weight:700}.sharedAvailabilityInfo p{margin:6px 0 0;line-height:1.4;max-width:760px}
@media (max-width:640px){.sharedAvailabilityCard{margin-inline:0;padding:12px}.sharedAvailabilityHead{display:grid}.sharedWorkToggle{justify-self:start}.sharedAvailabilityWindow{flex:1 1 calc(50% - 4px);min-width:120px}}
@media (max-width:390px){.sharedAvailabilityWindow{flex-basis:100%}}
</style>
