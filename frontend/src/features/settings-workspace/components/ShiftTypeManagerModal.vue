<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";

const settings = useSettingsWorkspaceStore();
const { shiftTypes, shiftTypeManagerOpen, shiftTypeEditingId, shiftTypeMutationPending } = storeToRefs(settings);
const SWATCHES = ["#F5B841", "#E0653A", "#C97BB8", "#7B8CE0", "#4FA3A5", "#6FBF73", "#B5A642", "#8B929E"] as const;

interface ShiftDraft {
  name: string;
  hours: string;
  color: string;
  startTime: string;
  endTime: string;
  breakMinutes: string;
  plannedHours: string;
  notificationsEnabled: boolean;
  notificationMinutesBefore: string;
}

function blankDraft(): ShiftDraft {
  return { name: "", hours: "", color: "#F5B841", startTime: "", endTime: "", breakMinutes: "0", plannedHours: "", notificationsEnabled: true, notificationMinutesBefore: "" };
}

const draft = ref<ShiftDraft>(blankDraft());
const message = ref("");
const messageOk = ref(false);
const editing = computed(() => shiftTypes.value.find(item => Number(item.id) === Number(shiftTypeEditingId.value)) ?? null);
const colorLocked = computed(() => editing.value?.builtin === true);
const language = computed(() => settings.language);
const text = computed(() => language.value === "en" ? {
  eyebrow: "Shifts", title: "Shift types", hint: "Create new shift types and configure time, break, planned hours and notifications.", available: "Available shifts", newShift: "New shift", editShift: "Edit shift", name: "Name", hours: "Calendar, h", start: "Start", end: "End", break: "Break, min", plan: "Planned, h", notify: "Notify before this shift", reminder: "Custom reminder minutes", reminderPlaceholder: "empty = global setting", cancelEdit: "Cancel editing", add: "Add shift", save: "Save shift", configure: "configure", builtin: "built-in", remove: "delete", close: "Close", example: "For example: Watch", auto: "auto", calendarHours: "Calendar, h — short calendar label. Planned, h — work hours used for overtime calculations.", calculated: "Planned hours calculated from shift time", saved: "Shift saved", created: "Shift added", removed: "Shift deleted", missingName: "Enter a shift name", invalidBreak: "Break: 0 to 1440 minutes", invalidHours: "Hours: 0 to 24", invalidPlan: "Planned hours: 0 to 24", invalidReminder: "Shift reminder: 0 to 1440 minutes", deleteConfirm: "Delete shift",
} : {
  eyebrow: "Смены", title: "Типы смен", hint: "Создавай новые типы и редактируй время, обед, норму и уведомления существующих.", available: "Доступные смены", newShift: "Новая смена", editShift: "Редактирование смены", name: "Название", hours: "Календарь, ч", start: "Начало", end: "Конец", break: "Обед, мин", plan: "Норма, ч", notify: "Уведомлять перед этой сменой", reminder: "Своё время напоминания, мин", reminderPlaceholder: "пусто = глобальная настройка", cancelEdit: "Отменить редактирование", add: "Добавить смену", save: "Сохранить смену", configure: "настроить", builtin: "встроенная", remove: "удалить", close: "Закрыть", example: "Например: Вахта", auto: "авто", calendarHours: "Календарь, ч — короткая метка. Норма, ч — рабочие часы смены.", calculated: "Норма рассчитана по времени смены", saved: "Смена обновлена", created: "Смена добавлена", removed: "Смена удалена", missingName: "укажи название смены", invalidBreak: "обед: от 0 до 1440 минут", invalidHours: "часы: от 0 до 24", invalidPlan: "норма: от 0 до 24 часов", invalidReminder: "напоминание смены: от 0 до 1440 минут", deleteConfirm: "Удалить смену",
});

function timeMinutes(value: string): number | null {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  if (!match) return null;
  const hours = Number(match[1]); const minutes = Number(match[2]);
  return hours <= 23 && minutes <= 59 ? hours * 60 + minutes : null;
}
function durationHours(): number {
  const start = timeMinutes(draft.value.startTime); const rawEnd = timeMinutes(draft.value.endTime); const breakMinutes = Number(draft.value.breakMinutes || 0);
  if (start == null || rawEnd == null || !Number.isFinite(breakMinutes) || breakMinutes < 0) return 0;
  const end = rawEnd <= start ? rawEnd + 1440 : rawEnd;
  return Math.round((Math.max(0, end - start - breakMinutes) / 60) * 100) / 100;
}
const calculatedNorm = computed(durationHours);
const planHint = computed(() => calculatedNorm.value > 0 ? `${text.value.calculated}: ${formatHours(calculatedNorm.value)} ${language.value === "en" ? "h" : "ч"}. ${text.value.calendarHours}` : text.value.calendarHours);
function formatHours(value: number): string { return (Math.round(value * 100) / 100).toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1"); }
function shiftMeta(item: DutyLogApiSchemas.ShiftType): string {
  const parts: string[] = [];
  if (item.startTime && item.endTime) parts.push(`${item.startTime}–${item.endTime}${item.breakMinutes ? ` · ${language.value === "en" ? "break" : "обед"} ${item.breakMinutes}${language.value === "en" ? "min" : "м"}` : ""}`);
  if (Number(item.plannedHours || 0) > 0) parts.push(`${language.value === "en" ? "norm" : "норма"} ${formatHours(Number(item.plannedHours))}${language.value === "en" ? "h" : "ч"}`);
  return parts.join(" · ");
}
function resetDraft(): void { draft.value = blankDraft(); settings.shiftTypeEditingId = null; message.value = ""; messageOk.value = false; }
function loadEditing(): void {
  if (!editing.value) { resetDraft(); return; }
  const item = editing.value;
  draft.value = {
    name: item.name ?? "", hours: formatHours(Number(item.hours ?? 0)), color: item.color || "#F5B841", startTime: item.startTime ?? "", endTime: item.endTime ?? "", breakMinutes: String(item.breakMinutes ?? 0), plannedHours: formatHours(Number(item.plannedHours ?? item.hours ?? 0)), notificationsEnabled: item.notificationsEnabled !== false, notificationMinutesBefore: item.notificationMinutesBefore == null ? "" : String(item.notificationMinutesBefore),
  };
  message.value = ""; messageOk.value = false;
}
function edit(id: number): void { settings.shiftTypeEditingId = Number(id); loadEditing(); }
function close(): void { settings.closeShiftTypeManager(); resetDraft(); }
function parseNumber(raw: string, fallback: number): number { const parsed = Number(raw.trim().replace(",", ".")); return raw.trim() === "" ? fallback : parsed; }
function payload(): DutyLogApiSchemas.ShiftTypeCreateRequest | DutyLogApiSchemas.ShiftTypeUpdateRequest {
  const name = draft.value.name.trim();
  if (!editing.value?.builtin && !name) throw new Error(text.value.missingName);
  const breakMinutes = Number(draft.value.breakMinutes || 0);
  if (!Number.isFinite(breakMinutes) || breakMinutes < 0 || breakMinutes > 1440) throw new Error(text.value.invalidBreak);
  const auto = calculatedNorm.value;
  const plannedHours = parseNumber(draft.value.plannedHours, auto || parseNumber(draft.value.hours, 0));
  const hours = parseNumber(draft.value.hours, plannedHours);
  if (!Number.isFinite(hours) || hours < 0 || hours > 24) throw new Error(text.value.invalidHours);
  if (!Number.isFinite(plannedHours) || plannedHours < 0 || plannedHours > 24) throw new Error(text.value.invalidPlan);
  const reminder = draft.value.notificationMinutesBefore.trim() === "" ? -1 : Number(draft.value.notificationMinutesBefore);
  if (!Number.isFinite(reminder) || reminder < -1 || reminder > 1440) throw new Error(text.value.invalidReminder);
  // Empty strings and -1 deliberately preserve the legacy clear semantics:
  // backend update handlers interpret empty local times as null and -1 as a cleared per-shift reminder.
  const common = { hours, startTime: draft.value.startTime, endTime: draft.value.endTime, breakMinutes: Math.round(breakMinutes), plannedHours, notificationsEnabled: draft.value.notificationsEnabled, notificationMinutesBefore: reminder };
  return editing.value?.builtin ? common : { ...common, name, color: draft.value.color };
}
async function save(): Promise<void> {
  try {
    const body = payload();
    if (editing.value) { await settings.updateManagedShiftType(editing.value.id, body); message.value = text.value.saved; }
    else { await settings.createShiftType(body as DutyLogApiSchemas.ShiftTypeCreateRequest); message.value = text.value.created; }
    messageOk.value = true;
    draft.value = blankDraft(); settings.shiftTypeEditingId = null;
  } catch (error) { message.value = error instanceof Error ? error.message : String(error); messageOk.value = false; }
}
async function remove(item: DutyLogApiSchemas.ShiftType): Promise<void> {
  if (item.builtin || !confirm(`${text.value.deleteConfirm} «${item.name}»?`)) return;
  try { await settings.deleteManagedShiftType(item.id); if (shiftTypeEditingId.value === item.id) resetDraft(); message.value = text.value.removed; messageOk.value = true; }
  catch (error) { message.value = error instanceof Error ? error.message : String(error); messageOk.value = false; }
}
function onKeydown(event: KeyboardEvent): void { if (shiftTypeManagerOpen.value && event.key === "Escape") close(); }
watch([shiftTypeManagerOpen, shiftTypeEditingId, shiftTypes], ([open]) => { if (open) loadEditing(); }, { deep: true });
onMounted(() => window.addEventListener("keydown", onKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <div v-if="shiftTypeManagerOpen" class="appModal vue-owned-modal" id="shiftTypeModal">
    <button class="appModalBackdrop" id="shiftTypeBackdrop" type="button" :aria-label="text.close" @click="close"></button>
    <div class="appModalPanel appModalPanelWide" role="dialog" aria-modal="true" aria-labelledby="shiftTypeModalTitle">
      <div class="appModalHead">
        <div><div class="eyebrow">{{ text.eyebrow }}</div><div class="appModalTitle" id="shiftTypeModalTitle">{{ text.title }}</div><div class="appModalHint">{{ text.hint }}</div></div>
        <button class="appModalClose" id="shiftTypeClose" type="button" :aria-label="text.close" @click="close">×</button>
      </div>
      <div class="shiftTypeModalLayout">
        <div class="shiftTypeListPane"><div class="shiftTypePaneTitle">{{ text.available }}</div><div class="custom shiftTypeManagerList" id="customList">
          <div v-for="item in shiftTypes" :key="item.id" :class="{ editing: item.id === shiftTypeEditingId }" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <span class="dot" :style="{ width:'10px', height:'10px', borderRadius:'3px', background:item.color, display:'inline-block' }"></span>
            <span style="flex:1;min-width:150px"><b>{{ item.name }}</b><template v-if="Number(item.plannedHours || 0) > 0"> · {{ language === 'en' ? 'norm' : 'норма' }} {{ formatHours(Number(item.plannedHours)) }}{{ language === 'en' ? 'h' : 'ч' }}</template><span v-if="shiftMeta(item)" style="color:var(--dim)"> · {{ shiftMeta(item) }}</span></span>
            <button type="button" class="del" @click="edit(item.id)">{{ text.configure }}</button><span v-if="item.builtin" class="tag">{{ text.builtin }}</span><button v-else type="button" class="del" @click="remove(item)">{{ text.remove }}</button>
          </div>
        </div></div>
        <form class="appModalForm shiftTypeEditorPane" id="shiftTypeForm" @submit.prevent="save">
          <div class="shiftTypePaneTitle" id="shiftTypeFormTitle">{{ editing ? text.editShift : text.newShift }}</div>
          <label class="appField appFieldWide">{{ text.name }}<input id="nsName" v-model="draft.name" maxlength="80" :placeholder="text.example" type="text" :disabled="colorLocked" required/></label>
          <label class="appField">{{ text.hours }}<input id="nsHours" v-model="draft.hours" inputmode="decimal" max="24" min="0" :placeholder="calculatedNorm ? formatHours(calculatedNorm) : (language === 'en' ? 'h' : 'ч')" step="0.25" type="number"/></label>
          <label class="appField">{{ text.start }}<input id="nsStart" v-model="draft.startTime" type="time"/></label>
          <label class="appField">{{ text.end }}<input id="nsEnd" v-model="draft.endTime" type="time"/></label>
          <label class="appField">{{ text.break }}<input id="nsBreak" v-model="draft.breakMinutes" max="1440" min="0" step="5" type="number"/></label>
          <label class="appField">{{ text.plan }}<input id="nsPlan" v-model="draft.plannedHours" max="24" min="0" :placeholder="calculatedNorm ? formatHours(calculatedNorm) : text.auto" step="0.25" type="number"/></label>
          <label class="appCheck appFieldWide"><input id="nsNotificationsEnabled" v-model="draft.notificationsEnabled" type="checkbox"/> {{ text.notify }}</label>
          <label class="appField appFieldWide" id="nsNotificationMinutesLabel">{{ text.reminder }}<input id="nsNotificationMinutes" v-model="draft.notificationMinutesBefore" max="1440" min="0" :placeholder="text.reminderPlaceholder" step="5" type="number"/></label>
          <div class="small appFieldWide" id="shiftPlanHint">{{ planHint }}</div>
          <div class="shiftColorRow appFieldWide" id="swRow"><button v-for="color in SWATCHES" :key="color" type="button" class="sw" :class="{ on: draft.color.toLowerCase() === color.toLowerCase() }" :style="{ background:color }" :title="color" :disabled="colorLocked" @click="draft.color = color"></button><input v-model="draft.color" type="color" title="Custom color" :disabled="colorLocked"/></div>
          <div class="appModalMessage appFieldWide" id="shiftTypeMessage" role="status" :class="{ ok: messageOk }">{{ message }}</div>
          <div class="appModalActions appFieldWide"><button v-if="editing" type="button" id="shiftTypeCancelEdit" @click="resetDraft">{{ text.cancelEdit }}</button><button class="primary" type="submit" id="shiftTypeSave" :disabled="shiftTypeMutationPending">{{ editing ? text.save : text.add }}</button></div>
        </form>
      </div>
    </div>
  </div>
</template>
