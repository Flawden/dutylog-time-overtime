<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, onMounted, watch } from "vue";
import "../absence-time-bank.css";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import type { AbsenceComposerOpenOptions, DutyLogAbsenceTimeBankDomain } from "../types/domain";
import AsyncWorkspaceLoading from "@/shared/ui/AsyncWorkspaceLoading.vue";
const AbsenceComposer = defineAsyncComponent({ loader: () => import("./AbsenceComposer.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
const CreditEditor = defineAsyncComponent({ loader: () => import("./CreditEditor.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
const SettlementEditor = defineAsyncComponent({ loader: () => import("./SettlementEditor.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
const AbsencePage = defineAsyncComponent({ loader: () => import("./AbsencePage.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
const TimeBankPage = defineAsyncComponent({ loader: () => import("./TimeBankPage.vue"), loadingComponent: AsyncWorkspaceLoading, delay: 120 });
import { navigateHashRoute } from "@/platform/router/hashRoute";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const store = useAbsenceTimeBankStore();
const { activeRoute } = storeToRefs(shell);
const { absenceModalOpen, creditModalOpen, settlementModalOpen } = storeToRefs(store);
let previousDomain: DutyLogAbsenceTimeBankDomain | undefined;

async function synchronizeRoute(route: string): Promise<void> {
  if (route !== "vacation" && route !== "overtime") return;
  // Entering a Vue-owned planner/time-bank route is a freshness boundary.
  // The legacy router used to force a fresh read on every entry; keep that
  // behavior in the actual domain owner instead of depending on applyRoute().
  await store.refresh();
}

onMounted(() => {
  previousDomain = window.DutyLogVueDomains?.absenceTimeBank;
  const domain: DutyLogAbsenceTimeBankDomain = Object.freeze({
    ready: () => store.loaded,
    refresh: () => store.refresh(),
    openAbsenceComposer: async (options?: AbsenceComposerOpenOptions) => {
      if (options?.source === "vacation") navigateHashRoute("vacation");
      if (options?.source === "time-bank") navigateHashRoute("overtime");
      await store.openAbsenceComposer(options);
    },
    openAbsenceEditor: async (id: number) => {
      navigateHashRoute("vacation");
      await store.openAbsenceEditor(id);
    },
    openCreditEditor: async (date?: string | null) => {
      await store.openCreditEditor(date);
    },
    editCredit: async (id: number) => {
      await store.ensureLoaded();
      store.editCredit(id);
    },
    openSettlementEditor: async (id?: number | null, date?: string | null) => {
      await store.openSettlementEditor(id ?? null, date ?? null);
    },
    openTimeBankUsage: async (absenceId?: number | null) => {
      navigateHashRoute("overtime");
      await store.openTimeBankUsage(absenceId ?? null);
    },
  });
  window.DutyLogVueDomains = Object.freeze({
    ...(window.DutyLogVueDomains ?? {}),
    absenceTimeBank: domain,
  });
  void synchronizeRoute(activeRoute.value);
});

watch(activeRoute, route => { void synchronizeRoute(route); });

onBeforeUnmount(() => {
  if (previousDomain) {
    window.DutyLogVueDomains = Object.freeze({ ...(window.DutyLogVueDomains ?? {}), absenceTimeBank: previousDomain });
  } else if (window.DutyLogVueDomains) {
    const { absenceTimeBank: _removed, ...rest } = window.DutyLogVueDomains;
    window.DutyLogVueDomains = Object.freeze(rest);
  }
  delete document.documentElement.dataset.vueAbsenceTimeBank;
});
</script>

<template>
  <AbsencePage v-if="activeRoute === 'vacation'" />
  <TimeBankPage v-else-if="activeRoute === 'overtime'" />
  <AbsenceComposer v-if="absenceModalOpen" />
  <CreditEditor v-if="creditModalOpen" />
  <SettlementEditor v-if="settlementModalOpen" />
</template>
