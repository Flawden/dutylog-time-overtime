<script setup lang="ts">
import { computed } from "vue";
import type { CalendarLayer, CalendarRangeBundle, SharedAvailabilityWindow } from "../types/domain";
import { sharedAvailabilityForDate } from "../types/model";

const props = defineProps<{
  bundle: CalendarRangeBundle | null;
  date: string;
  profile: CalendarLayer;
  language: string;
}>();

const availability = computed(() => sharedAvailabilityForDate(props.bundle, props.date, String(props.profile.id)));

function durationLabel(window: SharedAvailabilityWindow): string {
  const hours = Math.floor(window.durationMinutes / 60);
  const minutes = window.durationMinutes % 60;
  const parts: string[] = [];
  if (hours) parts.push(`${hours} ${props.language === "en" ? "h" : "ч"}`);
  if (minutes) parts.push(`${minutes} ${props.language === "en" ? "min" : "мин"}`);
  return parts.join(" ") || (props.language === "en" ? "0 min" : "0 мин");
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
    <header>
      <div>
        <small>{{ language === 'en' ? 'Work availability' : 'Рабочая доступность' }}</small>
        <h3>{{ language === 'en' ? `Free together · Me + ${profile.name}` : `Вместе свободны · Я + ${profile.name}` }}</h3>
      </div>
      <span class="sharedAvailabilityBadge">{{ date }}</span>
    </header>

    <div v-if="!availability.precise" class="sharedAvailabilityUnknown" data-shared-availability-unknown>
      <b>{{ language === 'en' ? 'Exact time is unknown' : 'Точное время неизвестно' }}</b>
      <span>{{ unknownText }}</span>
    </div>

    <div v-else-if="availability.allDayFree" class="sharedAvailabilityHero" data-shared-availability-all-day>
      <b>{{ language === 'en' ? 'Free together all day' : 'Свободны вместе весь день' }}</b>
      <span>00:00–24:00</span>
    </div>

    <div v-else-if="availability.noSharedFreeTime" class="sharedAvailabilityHero" data-shared-availability-none>
      <b>{{ language === 'en' ? 'No shared free window' : 'Общего свободного окна нет' }}</b>
      <span>{{ language === 'en' ? 'Work schedules cover the whole day.' : 'Рабочие графики перекрывают весь день.' }}</span>
    </div>

    <div v-else class="sharedAvailabilityWindows" data-shared-availability-windows>
      <div v-for="window in availability.freeWindows" :key="`${window.startMinute}-${window.endMinute}`" class="sharedAvailabilityWindow">
        <b>{{ window.startTime }}–{{ window.endTime }}</b>
        <span>{{ durationLabel(window) }}</span>
      </div>
    </div>

    <footer>
      {{ language === 'en'
        ? 'Only work schedules and work absences are included. Tasks, notes and personal events are not treated as busy time.'
        : 'Учитываем только рабочие графики и рабочие отсутствия. Задачи, заметки и личные события не считаются занятостью.' }}
    </footer>
  </section>
</template>

<style scoped>
.sharedAvailabilityCard{margin:12px 18px 0;padding:14px 16px;border:1px solid var(--border);border-radius:16px;background:var(--panel);display:grid;gap:12px}
.sharedAvailabilityCard header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.sharedAvailabilityCard header small{display:block;color:var(--muted);font-weight:700}
.sharedAvailabilityCard h3{margin:2px 0 0;font-size:1rem}
.sharedAvailabilityBadge{flex:0 0 auto;padding:5px 8px;border-radius:999px;background:var(--panelAlt,var(--panel));color:var(--muted);font-size:.76rem;font-weight:700}
.sharedAvailabilityWindows{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px}
.sharedAvailabilityWindow,.sharedAvailabilityHero,.sharedAvailabilityUnknown{padding:11px 12px;border-radius:12px;background:var(--panelAlt,var(--panel));display:grid;gap:3px}
.sharedAvailabilityWindow b,.sharedAvailabilityHero b{font-size:1.02rem}
.sharedAvailabilityWindow span,.sharedAvailabilityHero span,.sharedAvailabilityUnknown span{color:var(--muted);font-size:.82rem}
.sharedAvailabilityUnknown{border:1px dashed var(--border)}
.sharedAvailabilityCard footer{color:var(--muted);font-size:.75rem;line-height:1.35}
@media (max-width:640px){.sharedAvailabilityCard{margin-inline:0}.sharedAvailabilityCard header{align-items:flex-start}.sharedAvailabilityWindows{grid-template-columns:1fr 1fr}}
@media (max-width:420px){.sharedAvailabilityWindows{grid-template-columns:1fr}}
</style>
