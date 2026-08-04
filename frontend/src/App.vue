<script setup lang="ts">
import { onBeforeUnmount, onMounted } from "vue";
import AppShell from "@/app/AppShell.vue";
import AppErrorBoundary from "@/shared/errors/AppErrorBoundary.vue";
import { updateFrontendRoute } from "@/platform/diagnostics/frontendDiagnostics";
import { useShellStore } from "@/app/shellStore";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { usePlatformStore } from "@/platform/stores/platformStore";

const props = defineProps<{ bridge: LegacyBridge }>();
const platform = usePlatformStore();
const shell = useShellStore();
let unsubscribe: (() => void) | null = null;

onMounted(() => {
  platform.markMounted(props.bridge.connected());
  const initial = props.bridge.snapshot();
  shell.synchronize(initial);
  if (initial) updateFrontendRoute(initial.route);
  unsubscribe = props.bridge.subscribe(snapshot => {
    shell.synchronize(snapshot);
    updateFrontendRoute(snapshot.route);
  });
});

onBeforeUnmount(() => unsubscribe?.());
</script>

<template>
  <AppErrorBoundary>
    <AppShell :bridge="bridge" />
  </AppErrorBoundary>
</template>
