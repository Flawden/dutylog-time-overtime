<script setup lang="ts">
import { defineAsyncComponent, onBeforeMount, onBeforeUnmount, onMounted, watch } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { installCalendarTimelineOfflineSource, useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import type { CalendarDaySection, CalendarMode, DutyLogCalendarTimelineDomain } from "../types/domain";
import AsyncWorkspaceLoading from "@/shared/ui/AsyncWorkspaceLoading.vue";
const CalendarPage = defineAsyncComponent({ loader: () => import("./CalendarPage.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
const TodayPage = defineAsyncComponent({ loader: () => import("./TodayPage.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
import "../calendar-timeline.css";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const store = useCalendarTimelineStore();
const { activeRoute } = storeToRefs(shell);
let previousDomain: DutyLogCalendarTimelineDomain | undefined;
let restoreOfflineSource: (() => void) | null = null;
const domain: DutyLogCalendarTimelineDomain = Object.freeze({
  ready: () => store.loaded,
  refresh: () => store.refresh(),
  openDate: async (date: string, mode?: CalendarMode) => { navigateHashRoute("calendar"); await store.openDate(date, mode); },
  openDay: async (date: string, section?: CalendarDaySection | null) => { navigateHashRoute("calendar"); await store.openDayPanel(date, section ?? null); },
  closeDay: () => store.closeDayPanel(),
  snapshot: () => store.bundle ? Object.freeze({ focusDate: store.focusDate, mode: store.mode, from: store.bundle.from, to: store.bundle.to }) : null,
});

onBeforeMount(() => {
  previousDomain = window.DutyLogVueDomains?.calendarTimeline;
  restoreOfflineSource = installCalendarTimelineOfflineSource(async (focusDate: string) => props.bridge.offlineCalendarSnapshot(focusDate));
  window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), calendarTimeline: domain });
  props.bridge.retireDomainOwners("calendar-timeline");
});

async function synchronize(route: string): Promise<void> {
  if (route !== "today" && route !== "calendar") return;
  // Today is a dashboard freshness boundary: callers can mutate tasks, notes,
  // important days and overtime while another route is open. A route entry
  // must therefore refresh the authoritative bundle instead of reusing a
  // previously loaded boot snapshot. Calendar itself keeps its focused range.
  if (route === "today") await store.refresh(true);
  else await store.ensureLoaded();
}

onMounted(() => {
  void synchronize(activeRoute.value);
});
watch(activeRoute, route => {
  if (route !== "calendar" && store.dayPanelOpen) store.closeDayPanel();
  void synchronize(route);
});
onBeforeUnmount(() => {
  restoreOfflineSource?.();
  restoreOfflineSource = null;
  if (previousDomain) window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), calendarTimeline: previousDomain });
  else if (window.DutyLogVueDomains) { const { calendarTimeline: _removed, ...rest } = window.DutyLogVueDomains; window.DutyLogVueDomains = Object.freeze(rest); }
  delete document.documentElement.dataset.vueCalendarTimeline;
  delete document.documentElement.dataset.vueCalendarSelectedDay;
});
</script>
<template><TodayPage v-if="activeRoute === 'today'" /><CalendarPage v-else-if="activeRoute === 'calendar'" :bridge="bridge" /></template>
