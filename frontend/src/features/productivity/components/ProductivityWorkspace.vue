<script setup lang="ts">
import { computed, onBeforeMount, onBeforeUnmount, onMounted, watch } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useCalendarTimelineStore } from "@/features/calendar-timeline/stores/calendarTimelineStore";
import { installProductivityBridge, useProductivityStore } from "../stores/productivityStore";
import type { DutyLogProductivityDomain } from "../types/domain";
import SelectedDayTasks from "./SelectedDayTasks.vue";
import SelectedDayNotes from "./SelectedDayNotes.vue";
import SelectedDayImportant from "./SelectedDayImportant.vue";
import TasksPage from "./TasksPage.vue";
import ImportantPage from "./ImportantPage.vue";
import TaskModalLayer from "./TaskModalLayer.vue";
import ImportantModalLayer from "./ImportantModalLayer.vue";
import "../productivity.css";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const calendar = useCalendarTimelineStore();
const store = useProductivityStore();
const { activeRoute, modules } = storeToRefs(shell);
const { focusDate } = storeToRefs(calendar);
let previousDomain: DutyLogProductivityDomain | undefined;
let restoreBridge: (() => void) | null = null;

const tasksEnabled = computed(() => modules.value.tasks !== false);
const notesEnabled = computed(() => modules.value.notes !== false);
const importantEnabled = computed(() => modules.value.important_dates !== false);

onBeforeMount(() => {
  props.bridge.retireDomainOwners("productivity");
  restoreBridge = installProductivityBridge(props.bridge);
});

async function synchronizeRoute(route: string): Promise<void> {
  if (route === "tasks" && tasksEnabled.value) {
    await Promise.all([store.loadBoard(), store.loadInbox()]);
    store.loaded = true;
  } else if (route === "important" && importantEnabled.value) {
    await store.loadImportantDays();
    store.loaded = true;
  }
}

async function synchronizeSelectedDate(date: string): Promise<void> {
  if (!tasksEnabled.value && !notesEnabled.value && !importantEnabled.value) return;
  await store.loadSelectedDate(date);
  store.loaded = true;
}

async function reconnect(): Promise<void> {
  if (!navigator.onLine) return;
  try { await store.flushOfflineQueue(); } catch { /* the queue owns retry/backoff diagnostics */ }
}

onMounted(() => {
  previousDomain = window.DutyLogVueDomains?.productivity;
  const domain: DutyLogProductivityDomain = Object.freeze({
    ready: () => store.loaded,
    refresh: async () => {
      await Promise.all([
        synchronizeSelectedDate(focusDate.value),
        tasksEnabled.value ? store.loadBoard() : Promise.resolve(),
        importantEnabled.value ? store.loadImportantDays() : Promise.resolve(),
        tasksEnabled.value ? store.loadInbox() : Promise.resolve(),
      ]);
    },
    openTaskCreate: async (date?: string) => {
      await store.openTaskCreate(date || focusDate.value);
    },
    openTaskDetails: async (id: number) => { await store.openTaskDetails(id); },
    openImportantCreate: async (date?: string) => {
      await store.openImportantCreate(date || focusDate.value);
    },
    openImportantEdit: async (id: number) => { await store.openImportantEdit(id); },
    openImportantDetails: async (id: number) => { await store.openImportantDetails(id); },
    snapshot: () => Object.freeze({
      selectedDate: store.selectedDate,
      boardTotal: Number(store.board.total || 0),
      selectedTasks: store.selectedTasks.length,
      selectedNotes: store.selectedNotes.length,
      selectedImportant: store.selectedImportant.length,
      queuedMutations: store.queuedMutations,
    }),
  });
  window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), productivity: domain });
  void synchronizeSelectedDate(focusDate.value);
  void synchronizeRoute(activeRoute.value);
  window.addEventListener("online", reconnect);
});

watch(activeRoute, route => { void synchronizeRoute(route); });
watch(focusDate, date => { void synchronizeSelectedDate(date); });
watch(modules, () => { void synchronizeSelectedDate(focusDate.value); void synchronizeRoute(activeRoute.value); }, { deep: true });

onBeforeUnmount(() => {
  window.removeEventListener("online", reconnect);
  restoreBridge?.();
  if (previousDomain) window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), productivity: previousDomain });
  else if (window.DutyLogVueDomains) { const { productivity: _removed, ...rest } = window.DutyLogVueDomains; window.DutyLogVueDomains = Object.freeze(rest); }
  delete document.documentElement.dataset.vueProductivity;
});
</script>

<template>
  <TasksPage v-show="activeRoute === 'tasks'" />
  <ImportantPage v-show="activeRoute === 'important'" />

  <Teleport v-if="tasksEnabled" to="#vueSelectedDayTasksMount"><SelectedDayTasks /></Teleport>
  <Teleport v-if="notesEnabled" to="#vueSelectedDayNotesMount"><SelectedDayNotes /></Teleport>
  <Teleport v-if="importantEnabled" to="#vueSelectedDayImportantMount"><SelectedDayImportant /></Teleport>

  <TaskModalLayer />
  <ImportantModalLayer />
</template>
