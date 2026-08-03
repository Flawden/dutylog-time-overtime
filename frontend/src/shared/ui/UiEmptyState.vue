<script setup lang="ts">
import AppIcon from "./AppIcon.vue";

defineProps<{ title: string; description: string }>();
</script>

<template>
  <div class="ui-empty-state">
    <div class="ui-empty-state__icon" aria-hidden="true"><AppIcon name="info" /></div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <div v-if="$slots.actions" class="ui-empty-state__actions"><slot name="actions" /></div>
  </div>
</template>
