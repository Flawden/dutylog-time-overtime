<script setup lang="ts">
import { onBeforeUnmount, onMounted } from "vue";
import AppShell from "@/app/AppShell.vue";
import AppErrorBoundary from "@/shared/errors/AppErrorBoundary.vue";
import { updateFrontendRoute } from "@/platform/diagnostics/frontendDiagnostics";
import { guardHashRoute, navigateHashRoute, readHashRoute, subscribeHashRoute } from "@/platform/router/hashRoute";
import { useShellStore } from "@/app/shellStore";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { usePlatformStore } from "@/platform/stores/platformStore";

const props = defineProps<{ bridge: LegacyBridge }>();
const platform = usePlatformStore();
const shell = useShellStore();
let unsubscribeLegacy: (() => void) | null = null;
let unsubscribeRoute: (() => void) | null = null;

function synchronizeRoute(): void {
  const requested = readHashRoute();
  const route = guardHashRoute(requested, {
    profileLoaded: shell.profileLoaded,
    admin: shell.admin,
    modulesLoaded: shell.modulesLoaded,
    modules: shell.modules,
  });
  shell.synchronizeRoute(route.rawRoute);
  document.body.dataset.view = route.activeRoute;
  updateFrontendRoute(route.rawRoute);
  if (route.rawRoute !== requested.rawRoute) navigateHashRoute(route.rawRoute);
}

onMounted(() => {
  platform.markMounted(props.bridge.connected());
  shell.synchronize(props.bridge.snapshot());
  synchronizeRoute();
  unsubscribeLegacy = props.bridge.subscribe(snapshot => {
    shell.synchronize(snapshot);
    synchronizeRoute();
  });
  unsubscribeRoute = subscribeHashRoute(() => synchronizeRoute());
});

onBeforeUnmount(() => {
  unsubscribeLegacy?.();
  unsubscribeRoute?.();
});
</script>

<template>
  <AppErrorBoundary>
    <AppShell :bridge="bridge" />
  </AppErrorBoundary>
</template>
