<script setup lang="ts">
import { onBeforeUnmount, onMounted } from "vue";
import AppShell from "@/app/AppShell.vue";
import { useShellStore } from "@/app/shellStore";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { usePlatformStore } from "@/platform/stores/platformStore";

const props = defineProps<{ bridge: LegacyBridge }>();
const platform = usePlatformStore();
const shell = useShellStore();
let unsubscribe: (() => void) | null = null;

onMounted(() => {
  platform.markMounted(props.bridge.connected());
  shell.synchronize(props.bridge.snapshot());
  unsubscribe = props.bridge.subscribe(snapshot => shell.synchronize(snapshot));
});

onBeforeUnmount(() => unsubscribe?.());
</script>

<template><AppShell :bridge="bridge" /></template>
