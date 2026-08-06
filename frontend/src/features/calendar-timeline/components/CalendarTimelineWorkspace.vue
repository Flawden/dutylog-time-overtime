<script setup lang="ts">
import { nextTick, onBeforeMount, onBeforeUnmount, onMounted, watch } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import type { CalendarMode, DutyLogCalendarTimelineDomain } from "../types/domain";
import CalendarPage from "./CalendarPage.vue";
import TodayPage from "./TodayPage.vue";
import "../calendar-timeline.css";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const store = useCalendarTimelineStore();
const { activeRoute } = storeToRefs(shell);
let previousDomain: DutyLogCalendarTimelineDomain | undefined;

onBeforeMount(() => props.bridge.retireDomainOwners("calendar-timeline"));

async function synchronize(route: string): Promise<void> {
  if (route !== "today" && route !== "calendar") return;
  if (route === "today") await store.ensureTodayLoaded();
  else await store.ensureLoaded();
  if (route === "calendar") await nextTick(() => props.bridge.attachCalendarEditor("calendarLegacyPanelHost"));
}

onMounted(() => {
  previousDomain = window.DutyLogVueDomains?.calendarTimeline;
  const domain: DutyLogCalendarTimelineDomain = Object.freeze({
    ready: () => store.loaded,
    refresh: () => store.refresh(),
    openDate: async (date: string, mode?: CalendarMode) => { props.bridge.navigate("calendar"); await store.openDate(date, mode); },
    snapshot: () => store.bundle ? Object.freeze({ focusDate: store.focusDate, mode: store.mode, from: store.bundle.from, to: store.bundle.to }) : null,
  });
  window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), calendarTimeline: domain });
  void synchronize(activeRoute.value);
});
watch(activeRoute, route => { void synchronize(route); });
onBeforeUnmount(() => {
  if (previousDomain) window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), calendarTimeline: previousDomain });
  else if (window.DutyLogVueDomains) { const { calendarTimeline: _removed, ...rest } = window.DutyLogVueDomains; window.DutyLogVueDomains = Object.freeze(rest); }
  delete document.documentElement.dataset.vueCalendarTimeline;
});
</script>
<template><TodayPage v-if="activeRoute === 'today'" :bridge="bridge" /><CalendarPage v-else-if="activeRoute === 'calendar'" :bridge="bridge" /></template>
