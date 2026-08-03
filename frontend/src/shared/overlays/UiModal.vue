<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import AppIcon from "@/shared/ui/AppIcon.vue";
import UiButton from "@/shared/ui/UiButton.vue";

const props = withDefaults(defineProps<{
  open: boolean;
  title: string;
  description?: string;
  closeLabel?: string;
}>(), { description: "", closeLabel: "Закрыть" });
const emit = defineEmits<{ close: [] }>();
const panel = ref<HTMLElement | null>(null);
let previouslyFocused: HTMLElement | null = null;

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

watch(() => props.open, async open => {
  if (open) {
    previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    document.body.classList.add("vue-modal-open");
    await nextTick();
    const first = panel.value?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
    (first ?? panel.value)?.focus({ preventScroll: true });
    return;
  }
  document.body.classList.remove("vue-modal-open");
  previouslyFocused?.focus({ preventScroll: true });
  previouslyFocused = null;
});

onBeforeUnmount(() => document.body.classList.remove("vue-modal-open"));

function handleBackdrop(event: MouseEvent): void {
  if (event.target === event.currentTarget) emit("close");
}

function trapFocus(event: KeyboardEvent): void {
  const focusable = [...(panel.value?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? [])];
  if (!focusable.length) {
    event.preventDefault();
    panel.value?.focus({ preventScroll: true });
    return;
  }
  const first = focusable[0];
  const last = focusable.at(-1);
  if (!first || !last) return;
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="ui-overlay">
      <div v-if="open" class="ui-modal-backdrop" role="presentation" @mousedown="handleBackdrop">
        <section
          ref="panel"
          class="ui-modal"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          tabindex="-1"
          @keydown.esc.prevent="emit('close')"
          @keydown.tab="trapFocus"
        >
          <header class="ui-modal__header">
            <div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div>
            <UiButton variant="ghost" size="sm" :aria-label="closeLabel" @click="emit('close')">
              <template #icon><AppIcon name="close" /></template>
            </UiButton>
          </header>
          <div class="ui-modal__body"><slot /></div>
          <footer v-if="$slots.footer" class="ui-modal__footer"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
