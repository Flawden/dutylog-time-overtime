<script setup lang="ts">
import { computed, onBeforeMount, onBeforeUnmount, onMounted, watch } from "vue";
import { storeToRefs } from "pinia";
import { OFFLINE_SYNC_COMPLETE_EVENT, type LegacyBridge } from "@/platform/bridge/legacyBridge";
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
const { activeRoute, modules, modulesLoaded, onboardingCompleted, online } = storeToRefs(shell);
const { focusDate, dayPanelOpen } = storeToRefs(calendar);
let previousDomain: DutyLogProductivityDomain | undefined;
let restoreBridge: (() => void) | null = null;

// Online boot waits for the authoritative first-run profile. Offline reloads may
// render the authenticated dataLayer snapshot once its cached module map is restored.
const productivityReadable = computed(() => modulesLoaded.value && (onboardingCompleted.value || !online.value));
const tasksEnabled = computed(() => productivityReadable.value && modules.value.tasks !== false);
const notesEnabled = computed(() => productivityReadable.value && modules.value.notes !== false);
const importantEnabled = computed(() => productivityReadable.value && modules.value.important_dates !== false);
const hiddenModuleSummary = computed(() => shell.language === "en" ? "Hidden by module" : "Скрыто модулем");
const tasksSummary = computed(() => !productivityReadable.value ? "" : (tasksEnabled.value ? (store.selectedTasks.length ? String(store.selectedTasks.length) : "") : hiddenModuleSummary.value));
const notesSummary = computed(() => !productivityReadable.value ? "" : (notesEnabled.value ? (store.selectedNotes.length ? String(store.selectedNotes.length) : "") : hiddenModuleSummary.value));
const importantSummary = computed(() => !productivityReadable.value ? "" : (importantEnabled.value ? (store.selectedImportant.length ? String(store.selectedImportant.length) : "") : hiddenModuleSummary.value));

const domain: DutyLogProductivityDomain = Object.freeze({
  ready: () => productivityReadable.value && store.loaded,
  refresh: async () => { await store.refreshAll(focusDate.value); },
  openTaskCreate: async (date?: string, text = "", sourceInboxId: number | null = null) => {
    await store.openTaskCreate(date || focusDate.value, text, sourceInboxId);
  },
  openTaskDetails: async (id: number) => { await store.openTaskDetails(id); },
  openNoteCreate: async (date?: string, content = "") => {
    const targetDate = date || focusDate.value;
    await calendar.openDayPanel(targetDate, "notes");
    props.bridge.navigate("calendar");
    await store.createNote(targetDate, content);
  },
  openImportantCreate: async (date?: string, title = "") => {
    await store.openImportantCreate(date || focusDate.value, title);
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

onBeforeMount(() => {
  previousDomain = window.DutyLogVueDomains?.productivity;
  restoreBridge = installProductivityBridge(props.bridge);
  window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), productivity: domain });
  props.bridge.retireDomainOwners("productivity");
});

async function synchronizeRoute(route: string): Promise<void> {
  if (!productivityReadable.value) return;
  if (route === "tasks" && tasksEnabled.value) {
    await Promise.all([store.loadBoard(), store.loadInbox()]);
  } else if (route === "important" && importantEnabled.value) {
    await store.loadImportantDays();
  }
}

async function synchronizeSelectedDate(date: string): Promise<void> {
  if (!productivityReadable.value) return;
  if (!tasksEnabled.value && !notesEnabled.value && !importantEnabled.value) return;
  await store.loadSelectedDate(date);
}

async function synchronizeProductivity(): Promise<void> {
  if (!productivityReadable.value) {
    store.loaded = false;
    return;
  }
  await store.refreshAll(focusDate.value);
}

async function refreshAfterOfflineSync(): Promise<void> {
  if (!navigator.onLine) return;
  // The legacy dataLayer is the single queue/sync owner during migration.
  // Refresh Vue only after that owner publishes completion; do not start a
  // second reconnect flush from ProductivityWorkspace.
  store.synchronizeQueuedCount();
  try { await store.refreshAll(focusDate.value); } catch { /* queue diagnostics remain authoritative */ }
}
function handleOfflineSyncComplete(): void { void refreshAfterOfflineSync(); }

onMounted(() => {
  void synchronizeProductivity();
  window.addEventListener(OFFLINE_SYNC_COMPLETE_EVENT, handleOfflineSyncComplete);
});

watch(activeRoute, route => { void synchronizeRoute(route); });
watch(focusDate, date => { void synchronizeSelectedDate(date); });
watch([modulesLoaded, onboardingCompleted, modules], () => { void synchronizeProductivity(); }, { deep: true });

onBeforeUnmount(() => {
  window.removeEventListener(OFFLINE_SYNC_COMPLETE_EVENT, handleOfflineSyncComplete);
  restoreBridge?.();
  if (previousDomain) window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), productivity: previousDomain });
  else if (window.DutyLogVueDomains) { const { productivity: _removed, ...rest } = window.DutyLogVueDomains; window.DutyLogVueDomains = Object.freeze(rest); }
  delete document.documentElement.dataset.vueProductivity;
});
</script>

<template>
  <Teleport defer v-if="activeRoute === 'calendar' && dayPanelOpen" to="#sumTasks"><span data-vue-productivity-summary="tasks">{{ tasksSummary }}</span></Teleport>
  <Teleport defer v-if="activeRoute === 'calendar' && dayPanelOpen" to="#sumNote"><span data-vue-productivity-summary="notes">{{ notesSummary }}</span></Teleport>
  <Teleport defer v-if="activeRoute === 'calendar' && dayPanelOpen" to="#sumImp"><span data-vue-productivity-summary="important">{{ importantSummary }}</span></Teleport>

  <TasksPage v-show="activeRoute === 'tasks'" />
  <ImportantPage v-show="activeRoute === 'important'" />

  <Teleport defer v-if="activeRoute === 'calendar' && dayPanelOpen && tasksEnabled" to="#vueSelectedDayTasksMount"><SelectedDayTasks /></Teleport>
  <Teleport defer v-if="activeRoute === 'calendar' && dayPanelOpen && notesEnabled" to="#vueSelectedDayNotesMount"><SelectedDayNotes /></Teleport>
  <Teleport defer v-if="activeRoute === 'calendar' && dayPanelOpen && importantEnabled" to="#vueSelectedDayImportantMount"><SelectedDayImportant /></Teleport>

  <TaskModalLayer />
  <ImportantModalLayer />
</template>
