<script setup lang="ts">
withDefaults(defineProps<{ as?: "section" | "article" | "div"; padded?: boolean }>(), {
  as: "section",
  padded: true,
});
</script>

<template>
  <component :is="as" class="ui-card" :class="{ 'ui-card--padded': padded }"><slot /></component>
</template>
