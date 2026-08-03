<script setup lang="ts">
withDefaults(defineProps<{ tone?: "neutral" | "success" | "warning" | "danger" | "accent" }>(), {
  tone: "neutral",
});
</script>

<template><span class="ui-badge" :class="`ui-badge--${tone}`"><slot /></span></template>
