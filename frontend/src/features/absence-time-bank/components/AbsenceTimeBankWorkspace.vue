<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from "vue";
import "../absence-time-bank.css";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import type { AbsenceComposerOpenOptions, DutyLogAbsenceTimeBankDomain } from "../types/domain";
import AbsenceComposer from "./AbsenceComposer.vue";
import CreditEditor from "./CreditEditor.vue";
import AbsencePage from "./AbsencePage.vue";
import TimeBankPage from "./TimeBankPage.vue";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const store = useAbsenceTimeBankStore();
const { activeRoute } = storeToRefs(shell);
let previousDomain: DutyLogAbsenceTimeBankDomain | undefined;

async function synchronizeRoute(route: string): Promise<void> {
  if (route !== "vacation" && route !== "overtime") return;
  await store.ensureLoaded();
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
  <AbsenceComposer />
  <CreditEditor />
</template>
