<script setup lang="ts" generic="T extends string">
interface TabOption<TValue extends string> { value: TValue; label: string; disabled?: boolean }

defineProps<{ modelValue: T; options: readonly TabOption<T>[]; label: string }>();
const emit = defineEmits<{ "update:modelValue": [value: T] }>();
</script>

<template>
  <div class="ui-tabs" role="tablist" :aria-label="label">
    <button
      v-for="option in options"
      :key="option.value"
      class="ui-tabs__tab"
      :class="{ 'is-active': option.value === modelValue }"
      role="tab"
      type="button"
      :aria-selected="option.value === modelValue"
      :disabled="option.disabled"
      @click="emit('update:modelValue', option.value)"
    >{{ option.label }}</button>
  </div>
</template>
