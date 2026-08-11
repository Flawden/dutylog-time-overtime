<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";
import SettingsCard from "./SettingsCard.vue";

const props = defineProps<{ active: boolean }>();
const emit = defineEmits<{ open: [] }>();
const settings = useSettingsWorkspaceStore();
const { scheduleTemplates, calendarLayers, shiftTypes, profile } = storeToRefs(settings);

const templateEditorOpen = ref(false);
const templateId = ref<number | null>(null);
const templateName = ref("");
const templateDescription = ref("");
const templateAlignment = ref<"CYCLE_START" | "WEEKDAY">("CYCLE_START");
const stepShiftId = ref<number | null>(null);
const stepIds = ref<number[]>([]);

const layerEditorOpen = ref(false);
const layerId = ref<number | null>(null);
const layerName = ref("");
const layerColor = ref("#7AB8FF");
const layerTimezone = ref("UTC");
const layerTemplateId = ref<number | null>(null);
const layerAnchor = ref("");
const layerStart = ref("");
const layerEnd = ref("");
const layerVisible = ref(true);

const language = computed(() => settings.language);
const text = computed(() => language.value === "en" ? {
  eyebrow: "Schedules", title: "Templates and calendar layers", hint: "Build repeating cycles and read-only calendar layers without legacy Settings ownership.", templates: "Schedule templates", templatesHint: "Up to 64 steps in a cycle.", addTemplate: "＋ New", name: "Name", description: "Description", alignment: "Alignment", cycleStart: "from anchor date", weekday: "by weekday (7 steps)", addShift: "Add shift", addStep: "＋ Add", saveTemplate: "Save template", cancel: "Cancel", remove: "remove", delete: "Delete", layers: "Calendar layers", layersHint: "For example: family member or teammate.", addLayer: "＋ New", color: "Color", timezone: "Timezone", template: "Template", anchor: "Anchor date", start: "Show from", end: "Stop after", visible: "show layer", saveLayer: "Save layer", emptyLayers: "Add the first read-only layer.", system: "system", custom: "custom",
} : {
  eyebrow: "Графики", title: "Шаблоны и календарные слои", hint: "Повторяющиеся циклы и read-only слои теперь полностью принадлежат Vue Settings.", templates: "Шаблоны графика", templatesHint: "До 64 элементов в цикле.", addTemplate: "＋ Новый", name: "Название", description: "Описание", alignment: "Привязка", cycleStart: "от опорной даты", weekday: "по дням недели (7 шагов)", addShift: "Добавить смену", addStep: "＋ В цикл", saveTemplate: "Сохранить шаблон", cancel: "Отмена", remove: "убрать", delete: "Удалить", layers: "Слои календаря", layersHint: "Например: мама, отец, напарник.", addLayer: "＋ Новый", color: "Цвет", timezone: "Часовой пояс", template: "Шаблон", anchor: "Опорная дата", start: "Начало показа", end: "Остановить после", visible: "показывать слой", saveLayer: "Сохранить слой", emptyLayers: "Добавьте первый слой близкого человека.", system: "встроенный", custom: "пользовательский",
});

const timezoneOptions = computed(() => {
  let supported: string[] = [];
  try {
    const intl = Intl as typeof Intl & { supportedValuesOf?: (key: "timeZone") => string[] };
    supported = intl.supportedValuesOf?.("timeZone") ?? [];
  } catch { supported = []; }
  return [...new Set(["UTC", profile.value?.workTimezone || "UTC", layerTimezone.value, ...supported])].sort((a, b) => a.localeCompare(b));
});

function todayKey(): string {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
function shiftName(id: number): string { return shiftTypes.value.find(item => item.id === id)?.name ?? `#${id}`; }
function shiftColor(id: number): string { return shiftTypes.value.find(item => item.id === id)?.color ?? "#7AB8FF"; }

function newTemplate(): void {
  templateId.value = null;
  templateName.value = "";
  templateDescription.value = "";
  templateAlignment.value = "CYCLE_START";
  stepIds.value = [];
  stepShiftId.value = shiftTypes.value[0]?.id ?? null;
  templateEditorOpen.value = true;
}
function editTemplate(template: DutyLogApiSchemas.ScheduleTemplate): void {
  // System presets are opened as safe user-copy drafts; built-ins remain immutable.
  templateId.value = template.systemPreset ? null : template.id;
  templateName.value = template.systemPreset ? `${language.value === "en" ? "Copy" : "Копия"}: ${template.name}` : template.name;
  templateDescription.value = template.description ?? "";
  templateAlignment.value = template.alignmentMode;
  stepIds.value = template.steps.map(step => step.shiftTypeId);
  stepShiftId.value = shiftTypes.value[0]?.id ?? null;
  templateEditorOpen.value = true;
}
function addStep(): void {
  if (stepShiftId.value == null || stepIds.value.length >= 64) return;
  stepIds.value = [...stepIds.value, stepShiftId.value];
}
function moveStep(index: number, delta: -1 | 1): void {
  const target = index + delta;
  if (target < 0 || target >= stepIds.value.length) return;
  const next = [...stepIds.value];
  const currentValue = next[index];
  const targetValue = next[target];
  if (currentValue == null || targetValue == null) return;
  next[index] = targetValue;
  next[target] = currentValue;
  stepIds.value = next;
}
function removeStep(index: number): void {
  stepIds.value = stepIds.value.filter((_, rowIndex) => rowIndex !== index);
}
async function saveTemplate(): Promise<void> {
  if (!templateName.value.trim() || !stepIds.value.length) return;
  await settings.saveScheduleTemplate(templateId.value, {
    name: templateName.value.trim(),
    description: templateDescription.value.trim() || null,
    alignmentMode: templateAlignment.value,
    shiftTypeIds: [...stepIds.value],
  });
  templateEditorOpen.value = false;
}
async function deleteTemplate(): Promise<void> {
  if (templateId.value == null || !window.confirm(language.value === "en" ? "Delete this user template?" : "Удалить пользовательский шаблон?")) return;
  await settings.deleteScheduleTemplate(templateId.value);
  templateEditorOpen.value = false;
}

function newLayer(): void {
  const today = todayKey();
  layerId.value = null;
  layerName.value = "";
  layerColor.value = "#7AB8FF";
  layerTimezone.value = profile.value?.workTimezone || "UTC";
  layerTemplateId.value = scheduleTemplates.value[0]?.id ?? null;
  layerAnchor.value = today;
  layerStart.value = today;
  layerEnd.value = "";
  layerVisible.value = true;
  layerEditorOpen.value = true;
}
function editLayer(layer: DutyLogApiSchemas.CalendarLayer): void {
  layerId.value = layer.id;
  layerName.value = layer.name;
  layerColor.value = layer.color || "#7AB8FF";
  layerTimezone.value = layer.timezone;
  layerTemplateId.value = layer.templateId;
  layerAnchor.value = layer.anchorDate;
  layerStart.value = layer.startDate;
  layerEnd.value = layer.endDate ?? "";
  layerVisible.value = layer.visible;
  layerEditorOpen.value = true;
}
async function saveLayer(): Promise<void> {
  if (!layerName.value.trim() || layerTemplateId.value == null || !layerAnchor.value || !layerStart.value) return;
  await settings.saveCalendarLayer(layerId.value, {
    name: layerName.value.trim(),
    color: layerColor.value,
    timezone: layerTimezone.value,
    visible: layerVisible.value,
    templateId: layerTemplateId.value,
    anchorDate: layerAnchor.value,
    startDate: layerStart.value,
    endDate: layerEnd.value || null,
  });
  layerEditorOpen.value = false;
}
async function deleteLayer(): Promise<void> {
  if (layerId.value == null || !window.confirm(language.value === "en" ? "Delete this layer?" : "Удалить слой?")) return;
  await settings.deleteCalendarLayer(layerId.value);
  layerEditorOpen.value = false;
}
async function toggleLayer(layer: DutyLogApiSchemas.CalendarLayer, visible: boolean): Promise<void> {
  await settings.saveCalendarLayer(layer.id, {
    name: layer.name,
    color: layer.color,
    timezone: layer.timezone,
    visible,
    templateId: layer.templateId,
    anchorDate: layer.anchorDate,
    startDate: layer.startDate,
    endDate: layer.endDate ?? null,
  });
}
</script>

<template>
  <SettingsCard id="scheduleSettingsCard" section="schedule" :active="props.active" :eyebrow="text.eyebrow" :title="text.title" :hint="text.hint" @open="emit('open')">
    <template #status><div id="scheduleSettingsStatus" class="status" :class="{ ok: settings.scheduleMessageOk }">{{ settings.scheduleMessage || `${scheduleTemplates.length} / ${calendarLayers.length}` }}</div></template>
    <div class="scheduleSettingsGrid" data-vue-settings-native-section="schedule">
      <section class="schedulePane" aria-labelledby="scheduleTemplatesTitle">
        <div class="schedulePaneHead"><div><b id="scheduleTemplatesTitle">{{ text.templates }}</b><span>{{ text.templatesHint }}</span></div><button id="scheduleTemplateNew" class="primary" type="button" @click="newTemplate">{{ text.addTemplate }}</button></div>
        <div id="scheduleTemplateList" class="scheduleTemplateList">
          <button v-for="template in scheduleTemplates" :key="template.id" class="scheduleTemplateCard" type="button" @click="editTemplate(template)">
            <span><b>{{ template.name }}</b><small>{{ template.description || (template.systemPreset ? text.system : text.custom) }}</small></span>
            <span>{{ template.steps.length }}</span>
          </button>
        </div>
        <form id="scheduleTemplateForm" v-show="templateEditorOpen" class="scheduleEditor" @submit.prevent="saveTemplate">
          <input id="scheduleTemplateId" type="hidden" :value="templateId ?? ''" />
          <label>{{ text.name }} <input id="scheduleTemplateName" v-model="templateName" maxlength="100" required /></label>
          <label>{{ text.description }} <textarea id="scheduleTemplateDescription" v-model="templateDescription" maxlength="400" rows="2"></textarea></label>
          <label>{{ text.alignment }}
            <select id="scheduleTemplateAlignment" v-model="templateAlignment"><option value="CYCLE_START">{{ text.cycleStart }}</option><option value="WEEKDAY">{{ text.weekday }}</option></select>
          </label>
          <div class="scheduleStepBuilder"><label>{{ text.addShift }} <select id="scheduleStepShift" v-model.number="stepShiftId"><option v-for="shift in shiftTypes" :key="shift.id" :value="shift.id">{{ shift.name }}</option></select></label><button id="scheduleStepAdd" type="button" @click="addStep">{{ text.addStep }}</button></div>
          <div id="scheduleStepList" class="scheduleStepList">
            <div v-for="(id, index) in stepIds" :key="`${index}-${id}`" class="scheduleStepRow"><i :style="{ background: shiftColor(id) }"></i><span>{{ index + 1 }}. {{ shiftName(id) }}</span><button type="button" :disabled="index === 0" @click="moveStep(index, -1)">↑</button><button type="button" :disabled="index === stepIds.length - 1" @click="moveStep(index, 1)">↓</button><button type="button" @click="removeStep(index)">{{ text.remove }}</button></div>
          </div>
          <div class="scheduleEditorActions"><button class="primary" type="submit">{{ text.saveTemplate }}</button><button id="scheduleTemplateCancel" type="button" @click="templateEditorOpen = false">{{ text.cancel }}</button><button v-if="templateId != null" id="scheduleTemplateDelete" class="dangerGhost" type="button" @click="deleteTemplate">{{ text.delete }}</button></div>
          <div id="scheduleTemplateMessage" class="profileMsg" :class="{ ok: settings.scheduleMessageOk }">{{ settings.scheduleMessage }}</div>
        </form>
      </section>

      <section class="schedulePane" aria-labelledby="calendarLayersTitle">
        <div class="schedulePaneHead"><div><b id="calendarLayersTitle">{{ text.layers }}</b><span>{{ text.layersHint }}</span></div><button id="calendarLayerNew" class="primary" type="button" @click="newLayer">{{ text.addLayer }}</button></div>
        <div id="calendarLayerList" class="calendarLayerList">
          <div v-for="layer in calendarLayers" :key="layer.id" class="calendarLayerCard" :style="{ '--layer-color': layer.color }">
            <i></i><button class="calendarLayerCardMain" type="button" @click="editLayer(layer)"><b>{{ layer.name }}</b><small>{{ layer.templateName || `#${layer.templateId}` }} · {{ layer.timezone }}</small></button><label><input type="checkbox" :checked="layer.visible" @change="toggleLayer(layer, ($event.target as HTMLInputElement).checked)" /> {{ layer.visible ? 'виден' : 'скрыт' }}</label>
          </div>
          <div v-if="!calendarLayers.length" class="scheduleEmpty">{{ text.emptyLayers }}</div>
        </div>
        <form id="calendarLayerForm" v-show="layerEditorOpen" class="scheduleEditor" @submit.prevent="saveLayer">
          <input id="calendarLayerId" type="hidden" :value="layerId ?? ''" />
          <div class="scheduleEditorGrid two">
            <label>{{ text.name }} <input id="calendarLayerName" v-model="layerName" maxlength="80" required /></label>
            <label>{{ text.color }} <input id="calendarLayerColor" v-model="layerColor" type="color" /></label>
            <label>{{ text.timezone }} <select id="calendarLayerTimezone" v-model="layerTimezone"><option v-for="zone in timezoneOptions" :key="zone" :value="zone">{{ zone }}</option></select></label>
            <label>{{ text.template }} <select id="calendarLayerTemplate" v-model.number="layerTemplateId"><option v-for="template in scheduleTemplates" :key="template.id" :value="template.id">{{ template.name }}</option></select></label>
            <label>{{ text.anchor }} <input id="calendarLayerAnchor" v-model="layerAnchor" type="date" required /></label>
            <label>{{ text.start }} <input id="calendarLayerStart" v-model="layerStart" type="date" required /></label>
            <label>{{ text.end }} <input id="calendarLayerEnd" v-model="layerEnd" type="date" /></label>
            <label class="scheduleCheck"><input id="calendarLayerVisible" v-model="layerVisible" type="checkbox" /> {{ text.visible }}</label>
          </div>
          <div class="scheduleEditorActions"><button class="primary" type="submit">{{ text.saveLayer }}</button><button id="calendarLayerCancel" type="button" @click="layerEditorOpen = false">{{ text.cancel }}</button><button v-if="layerId != null" id="calendarLayerDelete" class="dangerGhost" type="button" @click="deleteLayer">{{ text.delete }}</button></div>
          <div id="calendarLayerMessage" class="profileMsg" :class="{ ok: settings.scheduleMessageOk }">{{ settings.scheduleMessage }}</div>
        </form>
      </section>
    </div>
  </SettingsCard>
</template>
