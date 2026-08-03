<script setup lang="ts">
withDefaults(defineProps<{
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md";
  type?: "button" | "submit" | "reset";
  disabled?: boolean;
}>(), {
  variant: "secondary",
  size: "md",
  type: "button",
  disabled: false,
});
</script>

<template>
  <button
    class="ui-button"
    :class="[`ui-button--${variant}`, `ui-button--${size}`]"
    :type="type"
    :disabled="disabled"
  >
    <slot name="icon" />
    <span v-if="$slots.default" class="ui-button__label"><slot /></span>
  </button>
</template>
