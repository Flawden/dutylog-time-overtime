<script setup lang="ts">
const props = defineProps<{
  id: string;
  section: string;
  active: boolean;
  eyebrow: string;
  title: string;
  hint: string;
  status?: string;
  statusId?: string;
}>();
const emit = defineEmits<{ open: [section: string] }>();
</script>

<template>
  <div :id="id" class="settingsCard" :class="active ? 'is-open' : 'is-collapsed'" :data-settings-section="section">
    <a class="settingsAnchor" :id="`settings-${section}`"></a>
    <div class="settingsHead" @click="emit('open', props.section)">
      <div>
        <div class="eyebrow">{{ eyebrow }}</div>
        <div class="settingsTitle">{{ title }}</div>
        <div class="settingsHint">{{ hint }}</div>
      </div>
      <slot name="status"><div v-if="status" class="status" :id="statusId">{{ status }}</div></slot>
    </div>
    <div class="settingsCollapsedNote">{{ hint }}</div>
    <slot />
  </div>
</template>
