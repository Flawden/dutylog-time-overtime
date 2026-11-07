<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps<{
  modelValue: string;
  language: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (event: "update:modelValue", value: string): void;
  (event: "change"): void;
}>();

const root = ref<HTMLElement | null>(null);
const opened = ref(false);
const pickerYear = ref(2026);
const manualMonth = ref(props.modelValue);
const monthNames = computed(() => Array.from({ length: 12 }, (_, index) =>
  new Intl.DateTimeFormat(props.language === "en" ? "en-US" : "ru-RU", { month: "short", timeZone: "UTC" })
    .format(new Date(Date.UTC(2024, index, 1))).replace(/\.$/, "")
));
function parse(value: string): { year: number; month: number } | null {
  const match = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(value);
  if (!match) return null;
  const year = Number(match[1]);
  return year >= 1 && year <= 9999 ? { year, month: Number(match[2]) } : null;
}
function format(value: string): string {
  const parsed = parse(value);
  if (!parsed) return value;
  const month = new Intl.DateTimeFormat(props.language === "en" ? "en-US" : "ru-RU", { month: "long", timeZone: "UTC" })
    .format(new Date(Date.UTC(2024, parsed.month - 1, 1)));
  return `${month.charAt(0).toUpperCase()}${month.slice(1)} ${parsed.year}`;
}
const selected = computed(() => parse(props.modelValue));
const displayMonth = computed(() => format(props.modelValue));
const label = computed(() => props.language === "en" ? "Accounting month" : "Расчётный месяц");
watch(() => props.modelValue, value => {
  manualMonth.value = value;
  const parsed = parse(value);
  if (parsed) pickerYear.value = parsed.year;
}, { immediate: true });
function toggle(): void {
  if (props.disabled) return;
  opened.value = !opened.value;
  if (opened.value) pickerYear.value = selected.value?.year ?? new Date().getFullYear();
}
function choose(value: string): void {
  if (props.disabled || !parse(value)) return;
  opened.value = false;
  if (value !== props.modelValue) {
    emit("update:modelValue", value);
    emit("change");
  }
}
function chooseNumber(index: number): void {
  choose(`${pickerYear.value}-${String(index + 1).padStart(2, "0")}`);
}
function applyManual(event?: Event): void {
  const value = event?.target instanceof HTMLInputElement ? event.target.value : manualMonth.value;
  if (parse(value)) choose(value);
}
function outsideClick(event: PointerEvent): void {
  if (opened.value && root.value && !root.value.contains(event.target as Node)) opened.value = false;
}
onMounted(() => document.addEventListener("pointerdown", outsideClick));
onBeforeUnmount(() => document.removeEventListener("pointerdown", outsideClick));
</script>

<template>
  <div ref="root" class="payrollMonthPicker">
    <span class="payrollMonthPickerLabel">{{ label }}</span>
    <button id="payrollMonthTrigger" type="button" class="payrollMonthPickerTrigger"
      :aria-expanded="opened" aria-haspopup="dialog" :data-value="modelValue" :disabled="disabled === true" @click="toggle" @keydown.esc="opened = false">
      <span aria-hidden="true">▦</span><strong>{{ displayMonth }}</strong><span aria-hidden="true">⌄</span>
    </button>
    <div v-if="opened" class="payrollMonthPopover" role="dialog" :aria-label="label" @keydown.esc="opened = false">
      <div class="payrollMonthPopoverYear">
        <button type="button" :aria-label="language === 'en' ? 'Previous year' : 'Предыдущий год'"
          :disabled="pickerYear <= 1" @click="pickerYear--">‹</button>
        <strong>{{ pickerYear }}</strong>
        <button type="button" :aria-label="language === 'en' ? 'Next year' : 'Следующий год'"
          :disabled="pickerYear >= 9999" @click="pickerYear++">›</button>
      </div>
      <div class="payrollMonthGrid">
        <button v-for="(name, index) in monthNames" :key="index" type="button" :data-month="String(index + 1).padStart(2, '0')"
          :class="{selected: selected?.year === pickerYear && selected.month === index + 1}"
          :aria-pressed="selected?.year === pickerYear && selected.month === index + 1"
          @click="chooseNumber(index)">{{ name }}</button>
      </div>
      <label class="payrollMonthManual">
        {{ language === 'en' ? 'Or choose a month and year' : 'Или выбери месяц и год' }}
        <input id="payrollMonth" v-model="manualMonth" type="month" @input="applyManual" @change="applyManual" />
      </label>
    </div>
  </div>
</template>

<style scoped>
.payrollMonthPicker { position: relative; min-width: 0; }
.payrollMonthPickerLabel { display: block; margin-bottom: 6px; font-size: .75rem; font-weight: 700; color: var(--mut); }
.payrollMonthPickerTrigger { display: flex; align-items: center; gap: 9px; min-width: 196px; min-height: 42px; width: 100%; padding: 9px 12px; border: 1px solid var(--line, #34424b); border-radius: 11px; color: var(--text); background: var(--panel2); }
.payrollMonthPickerTrigger strong { flex: 1; text-align: left; font-weight: 700; white-space: nowrap; }
.payrollMonthPickerTrigger:focus-visible, .payrollMonthPopover button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.payrollMonthPopover { position: absolute; top: calc(100% + 6px); right: 0; width: 290px; max-width: min(90vw, 290px); padding: 13px; border: 1px solid var(--line, #34424b); border-radius: 13px; box-shadow: 0 15px 35px #0009; background: var(--panel, #0c141b); z-index: 60; }
.payrollMonthPopoverYear { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; }
.payrollMonthPopoverYear button { padding: 4px 13px; font-size: 1.25rem; border: 1px solid var(--line, #34424b); border-radius: 9px; }
.payrollMonthGrid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 5px; }
.payrollMonthGrid button { min-width: 0; min-height: 38px; padding: 7px 3px; border: 1px solid transparent; border-radius: 9px; font-size: .8rem; }
.payrollMonthGrid button:hover { border-color: var(--accent); }
.payrollMonthGrid button.selected { background: color-mix(in srgb, var(--accent) 26%, var(--panel)); border-color: var(--accent); color: var(--text); font-weight: 800; }
.payrollMonthManual { display: grid; gap: 6px; margin-top: 13px; color: var(--mut); font-size: .74rem; }
.payrollMonthManual input { width: 100%; min-height: 39px; }
@media (max-width: 560px) { .payrollMonthPickerTrigger { min-width: 0; } .payrollMonthPopover { left: 0; right: auto; } }
</style>
