<script setup lang="ts">
import { storeToRefs } from "pinia";
import AppIcon from "@/shared/ui/AppIcon.vue";
import { useShellStore } from "@/app/shellStore";

const shell = useShellStore();
const { toasts } = storeToRefs(shell);
</script>

<template>
  <div class="ui-toast-region" role="status" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="ui-toast">
      <div v-for="toast in toasts" :key="toast.id" class="ui-toast" :class="`ui-toast--${toast.tone}`">
        <AppIcon :name="toast.tone === 'success' ? 'check' : 'info'" />
        <span>{{ toast.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>
