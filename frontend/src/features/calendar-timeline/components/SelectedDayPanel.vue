<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useShellStore } from "@/app/shellStore";
import { calendarTimelineApi } from "../api/calendarTimelineApi";
import { useCalendarTimelineStore } from "../stores/calendarTimelineStore";
import type { CalendarDaySection } from "../types/domain";
import { dateLabel, dayFacts, timePart } from "../types/model";
import NativeWorkdayCard from "./NativeWorkdayCard.vue";

const props = defineProps<{ bridge: LegacyBridge }>();
const emit = defineEmits<{ dayTruthChanged: [] }>();
const shell = useShellStore();
const store = useCalendarTimelineStore();
const { language, modules, modulesLoaded } = storeToRefs(shell);
const { bundle, focusDate, dayPanelSection } = storeToRefs(store);

const DAY_EMOJI_PRESETS = ["🔥","😴","✅","⚠️","💰","🏥","🎉","🛠️","🌙","☕","🚗","💪","📌","🧠","🛌","❤️"] as const;
const ACC_STORE = "acc-open-v1";
const SECTION_IDS: Record<CalendarDaySection, string> = {
  shift: "accShift", emoji: "accEmoji", schedule: "accSched", overtime: "accOt",
  vacation: "accVacation", important: "accImp", tasks: "accTasks", notes: "accNote",
};

function readOpenSections(): Set<string> {
  try {
    const parsed = JSON.parse(localStorage.getItem(ACC_STORE) || "null");
    if (Array.isArray(parsed)) return new Set(parsed.map(String));
  } catch { /* use defaults */ }
  return new Set(["accShift"]);
}
const openSections = ref(readOpenSections());
function isOpen(id: string): boolean { return openSections.value.has(id); }
function persistOpenSections(): void {
  try { localStorage.setItem(ACC_STORE, JSON.stringify([...openSections.value])); } catch { /* private mode */ }
}
function toggleSection(id: string, event: Event): void {
  const next = new Set(openSections.value);
  const opened = (event.currentTarget as HTMLDetailsElement).open;
  if (opened) next.add(id); else next.delete(id);
  openSections.value = next;
  persistOpenSections();
  if (id === "accSched" && opened && scheduleTemplates.value.length === 0) void loadScheduleTemplates();
}
function openRequestedSection(section: CalendarDaySection | null): void {
  if (!section) return;
  const id = SECTION_IDS[section];
  const next = new Set(openSections.value);
  next.add(id);
  openSections.value = next;
  persistOpenSections();
  store.clearDayPanelSectionRequest();
}
watch(dayPanelSection, openRequestedSection, { immediate: true });

function moduleEnabled(key: string): boolean { return !modulesLoaded.value || modules.value[key] !== false; }
const facts = computed(() => dayFacts(bundle.value, focusDate.value));
const selectedOccurrence = computed(() => facts.value.occurrences[0] ?? null);
const shiftSourceDate = computed(() => selectedOccurrence.value?.sourceDate || focusDate.value);
const shiftSourceDay = computed(() => bundle.value?.days.find(item => item.date === shiftSourceDate.value) ?? null);
const activeShiftId = computed(() => selectedOccurrence.value?.shiftTypeId ?? shiftSourceDay.value?.shiftTypeId ?? null);
const activeShift = computed(() => bundle.value?.shiftTypes.find(item => Number(item.id) === Number(activeShiftId.value)) ?? null);
const selectedEmoji = computed(() => String(facts.value.day?.dayEmoji ?? ""));
const emojiCustom = ref("");
watch([focusDate, selectedEmoji], () => { emojiCustom.value = selectedEmoji.value; }, { immediate: true });

const panelWeekday = computed(() => dateLabel(focusDate.value, language.value, { weekday: "long" }));
const panelDate = computed(() => dateLabel(focusDate.value, language.value, { day: "numeric", month: "long", year: "numeric" }));
const shiftSummary = computed(() => activeShift.value ? `${activeShift.value.name}${activeShift.value.plannedHours ? ` · ${activeShift.value.plannedHours}ч` : ""}` : (language.value === "en" ? "not set" : "не отмечена"));
function shortDate(date: string): string {
  const parts = date.split("-");
  return parts.length === 3 ? `${parts[2]}.${parts[1]}` : date;
}
function dateTimeRange(start: string | null | undefined, end: string | null | undefined): string {
  const startText = String(start ?? "");
  const endText = String(end ?? "");
  const startDate = startText.slice(0, 10);
  const endDate = endText.slice(0, 10);
  const startTime = timePart(startText);
  const endTime = timePart(endText);
  if (!startTime || !endTime) return "";
  return startDate && endDate && startDate !== endDate
    ? `${shortDate(startDate)} ${startTime}–${shortDate(endDate)} ${endTime}`
    : `${startTime}–${endTime}`;
}
async function refreshNativeWorkday(): Promise<void> {
  await store.refresh();
  emit("dayTruthChanged");
}
function openNativeWorkdaySection(section: "shift"): void {
  openRequestedSection(section);
}

async function refreshAfterDayWrite(message: string, queued: boolean): Promise<void> {
  await store.refresh();
  shell.announce(queued ? `${message} · сохранится после подключения` : message, queued ? "warning" : "success");
}
async function toggleShift(id: number): Promise<void> {
  const nextId = Number(activeShiftId.value) === Number(id) ? null : id;
  try {
    const result = await props.bridge.writeCalendarDay(shiftSourceDate.value, { shiftTypeId: nextId, shiftInterval: null });
    await refreshAfterDayWrite(nextId == null ? "Смена снята" : "Смена сохранена", result.queued);
  } catch (error) { shell.announce(error instanceof Error ? error.message : "Не удалось сохранить смену", "danger"); }
}
function normalizeEmoji(value: string): string { return value.trim().slice(0, 32); }
async function setDayEmoji(value: string): Promise<void> {
  const emoji = normalizeEmoji(value);
  try {
    const result = await props.bridge.writeCalendarDay(focusDate.value, { dayEmoji: emoji });
    emojiCustom.value = emoji;
    await refreshAfterDayWrite(emoji ? "Маркер сохранён" : "Маркер очищен", result.queued);
  } catch (error) { shell.announce(error instanceof Error ? error.message : "Не удалось сохранить маркер", "danger"); }
}

const scheduleTemplates = ref<DutyLogApiSchemas.ScheduleTemplate[]>([]);
const scheduleTemplateId = ref("");
const scheduleDays = ref(31);
const scheduleOverwrite = ref(false);
const schedulePreview = ref<DutyLogApiSchemas.ScheduleTemplatePreview | null>(null);
const scheduleBusy = ref(false);
const scheduleMessage = ref("");
const selectedTemplate = computed(() => scheduleTemplates.value.find(item => String(item.id) === scheduleTemplateId.value) ?? null);
const scheduleSummary = computed(() => selectedTemplate.value?.name || (language.value === "en" ? "not selected" : "не выбран"));
const scheduleHint = computed(() => {
  const template = selectedTemplate.value;
  if (!template) return language.value === "en" ? "Schedule templates are loading." : "Шаблоны графика ещё не загружены.";
  const names = template.steps.map(step => step.shiftTypeName).join(" → ");
  const mode = template.alignmentMode === "WEEKDAY" ? "привязка к дням недели" : "цикл от выбранной даты";
  return `${mode}: ${names}. Занятые дни по умолчанию не перезаписываются.`;
});
const schedulePreviewText = computed(() => {
  const preview = schedulePreview.value;
  if (!preview) return "";
  return `${preview.totalDays ?? 0} дней · ${preview.writeCount ?? 0} будет записано · ${preview.unchangedCount ?? 0} без изменений · ${preview.conflictCount ?? 0} конфликтов`;
});
function addDays(date: string, offset: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const value = new Date(Date.UTC(year || 1970, (month || 1) - 1, (day || 1) + offset));
  return value.toISOString().slice(0, 10);
}
function schedulePayload(): DutyLogApiSchemas.ScheduleTemplateApplyRequest | null {
  const count = Number(scheduleDays.value);
  if (!Number.isInteger(count) || count < 1 || count > 366) {
    scheduleMessage.value = "Количество дней: от 1 до 366";
    return null;
  }
  return { startDate: focusDate.value, endDate: addDays(focusDate.value, count - 1), anchorDate: focusDate.value, overwriteExistingShift: scheduleOverwrite.value };
}
async function loadScheduleTemplates(): Promise<void> {
  if (scheduleBusy.value) return;
  scheduleBusy.value = true;
  try {
    scheduleTemplates.value = await calendarTimelineApi.listScheduleTemplates();
    if (!scheduleTemplates.value.some(item => String(item.id) === scheduleTemplateId.value)) scheduleTemplateId.value = String(scheduleTemplates.value[0]?.id ?? "");
  } catch (error) { scheduleMessage.value = error instanceof Error ? error.message : "Не удалось загрузить шаблоны"; }
  finally { scheduleBusy.value = false; }
}
async function previewSchedule(): Promise<{ preview: DutyLogApiSchemas.ScheduleTemplatePreview; payload: DutyLogApiSchemas.ScheduleTemplateApplyRequest; template: DutyLogApiSchemas.ScheduleTemplate } | null> {
  const template = selectedTemplate.value;
  const payload = schedulePayload();
  if (!template || !payload) return null;
  scheduleBusy.value = true; scheduleMessage.value = "Проверяю график…";
  try {
    const preview = await calendarTimelineApi.previewScheduleTemplate(template.id, payload);
    schedulePreview.value = preview;
    scheduleMessage.value = preview.conflictCount ? "Предпросмотр готов — есть конфликты" : "Предпросмотр готов";
    return { preview, payload, template };
  } catch (error) { scheduleMessage.value = error instanceof Error ? error.message : "Не удалось построить предпросмотр"; return null; }
  finally { scheduleBusy.value = false; }
}
async function applySchedule(): Promise<void> {
  const prepared = await previewSchedule();
  if (!prepared) return;
  if ((prepared.preview.conflictCount ?? 0) > 0 && prepared.payload.overwriteExistingShift) {
    if (!globalThis.confirm(`Будут заменены занятые дни: ${prepared.preview.conflictCount}. Продолжить?`)) { scheduleMessage.value = "Применение отменено"; return; }
  }
  scheduleBusy.value = true; scheduleMessage.value = "Заполняю график…";
  try {
    const result = await calendarTimelineApi.applyScheduleTemplate(prepared.template.id, prepared.payload);
    schedulePreview.value = null;
    await store.refresh();
    scheduleMessage.value = `График применён: ${result.appliedCount ?? 0} дней`;
    shell.announce(scheduleMessage.value, "success");
  } catch (error) { scheduleMessage.value = error instanceof Error ? error.message : "Не удалось применить график"; }
  finally { scheduleBusy.value = false; }
}

const dayCredits = computed(() => (bundle.value?.overtimeAccount.credits ?? []).filter(item => item.workedDate === focusDate.value));
const dayUsages = computed(() => (bundle.value?.overtimeAccount.usages ?? []).filter(item => item.usageDate === focusDate.value));
const dayEarned = computed(() => dayCredits.value.reduce((sum, item) => sum + Number(item.hours || 0), 0));
const dayUsed = computed(() => dayUsages.value.reduce((sum, item) => sum + Number(item.hours || 0), 0));
const overtimeSummary = computed(() => {
  if (!moduleEnabled("overtime")) return language.value === "en" ? "Hidden by module" : "Скрыто модулем";
  const net = dayEarned.value - dayUsed.value;
  return Math.abs(net) > .001 ? `за день ${net > 0 ? "+" : ""}${formatHours(net)} ч` : `баланс ${formatSignedHours(bundle.value?.overtimeAccount.balanceHours)}`;
});
function formatHours(value: number | null | undefined): string { return Number(value || 0).toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1"); }
function formatSignedHours(value: number | null | undefined): string { const number = Number(value || 0); return `${number > 0 ? "+" : ""}${formatHours(number)} ч`; }
function creditRange(credit: DutyLogApiSchemas.OvertimeCredit): string {
  return credit.displayStart && credit.displayEnd ? dateTimeRange(credit.displayStart, credit.displayEnd) : (credit.timeRange || "");
}
function allocationRangeLabels(allocation: DutyLogApiSchemas.OvertimeAllocation): string[] {
  if (!allocation.exact || !allocation.displayStart || !allocation.displayEnd) return [];
  const start = allocation.displayStart.slice(0, 16);
  const end = allocation.displayEnd.slice(0, 16);
  const startDate = start.slice(0, 10);
  const endDate = end.slice(0, 10);
  const startTime = start.slice(11, 16);
  const endTime = end.slice(11, 16);
  if (!startDate || !endDate || startDate === endDate) return [dateTimeRange(start, end)];

  const labels = [`${shortDate(startDate)} ${startTime}–24:00`];
  const cursor = new Date(`${startDate}T00:00:00Z`);
  cursor.setUTCDate(cursor.getUTCDate() + 1);
  while (cursor.toISOString().slice(0, 10) < endDate) {
    labels.push(`${shortDate(cursor.toISOString().slice(0, 10))} 00:00–24:00`);
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  if (endTime !== "00:00") labels.push(`${shortDate(endDate)} 00:00–${endTime}`);
  return labels;
}
function openShiftTypeManager(): void { window.DutyLogVueDomains?.settingsWorkspace?.openShiftTypeManager(); }
async function addCredit(): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.openCreditEditor(focusDate.value); }
async function editCredit(id: number): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.editCredit(id); }
async function addUsage(): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceComposer({ date: focusDate.value, systemCode: "TIME_OFF", source: "calendar" }); }
async function addSettlement(): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.openSettlementEditor(null, focusDate.value); }
async function openUsage(usage: DutyLogApiSchemas.OvertimeUsage): Promise<void> {
  if (usage.sourceAbsenceId) {
    await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceEditor(Number(usage.sourceAbsenceId));
  } else if (usage.sourceSettlementId) {
    await window.DutyLogVueDomains?.absenceTimeBank?.openSettlementEditor(Number(usage.sourceSettlementId), usage.usageDate);
  } else {
    await window.DutyLogVueDomains?.absenceTimeBank?.openTimeBankUsage(null);
  }
}

const vacationSummary = computed(() => {
  if (!moduleEnabled("vacation")) return language.value === "en" ? "Hidden by module" : "Скрыто модулем";
  const items = facts.value.absences;
  if (!items.length) return "—";
  const primary = items.find(item => item.coverage === "FULL_DAY" && item.replacesShift) || items[0];
  return `${primary?.title || primary?.typeName || "Отсутствие"}${items.length > 1 ? ` · +${items.length - 1}` : ""}`;
});
function absenceTime(item: DutyLogApiSchemas.AbsenceOccurrence): string {
  if (item.coverage === "FULL_DAY") return "весь день";
  if (item.startTime || item.endTime) return `${item.startTime || "—"}–${item.endTime || "—"}`;
  return item.chargedMinutes ? `${formatHours(item.chargedMinutes / 60)} ч` : "часть дня";
}
async function editAbsence(id: number): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceEditor(id); }
async function planAbsence(): Promise<void> { await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceComposer({ date: focusDate.value, source: "calendar" }); }

onMounted(() => { document.body.classList.add("panel-open"); void loadScheduleTemplates(); });
onBeforeUnmount(() => { document.body.classList.remove("panel-open"); });
</script>

<template>
  <aside id="panel" class="card vue-selected-day-panel" data-vue-selected-day-panel>
    <header class="pt vue-selected-day-panel__header">
      <div><div id="pWeekday" class="eyebrow">{{ panelWeekday }}</div><div id="pDate" class="d">{{ panelDate }}</div></div>
      <button id="pClose" type="button" class="close" aria-label="Закрыть" @click="store.closeDayPanel()">×</button>
    </header>

    <NativeWorkdayCard
      :date="focusDate"
      :overtime-enabled="moduleEnabled('overtime')"
      @refresh="refreshNativeWorkday"
      @open-section="openNativeWorkdaySection"
    />

    <details id="accShift" class="acc dayPanelModule" data-day-module="shifts" :open="isOpen('accShift')" @toggle="toggleSection('accShift', $event)">
      <summary><span class="accT">Смена</span><span id="sumShift" class="accS">{{ shiftSummary }}</span></summary>
      <div class="accB">
        <div id="chips" class="chips">
          <button v-for="shift in bundle?.shiftTypes ?? []" :key="shift.id" type="button" class="chip" :class="{ on: Number(activeShiftId) === Number(shift.id) }" :data-shift-type-id="shift.id" :aria-pressed="Number(activeShiftId) === Number(shift.id) ? 'true' : 'false'" :style="{ borderColor: shift.color, color: Number(activeShiftId) === Number(shift.id) ? '#14171C' : shift.color, background: Number(activeShiftId) === Number(shift.id) ? shift.color : `color-mix(in srgb, ${shift.color} 12%, transparent)` }" @click="toggleShift(shift.id)">{{ shift.name }} <span v-if="shift.plannedHours" class="h">·{{ formatHours(shift.plannedHours) }}ч</span></button>
          <button type="button" class="chip plus" title="Создать или настроить смену" @click="openShiftTypeManager">+</button>
        </div>
      </div>
    </details>

    <details id="accEmoji" class="acc dayPanelModule" data-day-module="core" :open="isOpen('accEmoji')" @toggle="toggleSection('accEmoji', $event)">
      <summary><span class="accT">Маркер</span><span id="sumEmoji" class="accS">{{ selectedEmoji || '—' }}</span></summary>
      <div class="accB"><div class="dayEmojiBox">
        <div id="dayEmojiGrid" class="dayEmojiGrid"><button v-for="emoji in DAY_EMOJI_PRESETS" :key="emoji" type="button" class="emojiChoice" :class="{ on: emoji === selectedEmoji }" @click="setDayEmoji(emoji)">{{ emoji }}</button></div>
        <div class="row dayEmojiCustomRow"><input id="dayEmojiCustom" v-model="emojiCustom" maxlength="32" placeholder="Вставь emoji с клавиатуры" type="text"/><button id="dayEmojiApply" class="addSmall" type="button" @click="setDayEmoji(emojiCustom)">Поставить</button><button id="dayEmojiClear" type="button" @click="setDayEmoji('')">Очистить</button></div>
        <div id="dayEmojiPreview" class="dayPanelHint">{{ selectedEmoji ? `В календаре будет видно: ${selectedEmoji}` : 'Маркер не выбран.' }}</div>
      </div></div>
    </details>

    <details id="accSched" class="acc dayPanelModule" data-day-module="shifts" :open="isOpen('accSched')" @toggle="toggleSection('accSched', $event)">
      <summary><span class="accT">График</span><span id="sumSched" class="accS">{{ scheduleSummary }}</span></summary>
      <div class="accB"><div id="tplBox" class="tplBox">
        <div class="row scheduleApplyRow"><label class="scheduleTemplateSelectLabel">Шаблон <select id="tplPreset" v-model="scheduleTemplateId" aria-label="Шаблон графика"><option v-for="template in scheduleTemplates" :key="template.id" :value="String(template.id)">{{ template.name }}</option></select></label><label>Дней <input id="tplDays" v-model.number="scheduleDays" max="366" min="1" type="number"/></label></div>
        <div class="row scheduleApplyActions"><label><input id="tplOverwrite" v-model="scheduleOverwrite" type="checkbox"/> перезаписывать занятые дни</label><button id="tplPreviewBtn" type="button" :disabled="scheduleBusy || !selectedTemplate" @click="previewSchedule()">Предпросмотр</button><button id="tplApply" class="apply" type="button" :disabled="scheduleBusy || !selectedTemplate" @click="applySchedule">Применить безопасно</button></div>
        <div id="tplHint" class="tplHint">{{ scheduleHint }}</div>
        <div id="tplPreview" class="schedulePreview" :hidden="!schedulePreview"><b>{{ schedulePreviewText }}</b><div v-for="(item, index) in schedulePreview?.items?.slice(0, 14) ?? []" :key="item.date ?? `preview-${index}`" class="vue-schedule-preview-row"><span>{{ item.date }}</span><span>{{ item.shiftTypeName || 'выходной' }}</span><small>{{ item.action }}</small></div></div>
        <div v-if="scheduleMessage" class="dayPanelHint">{{ scheduleMessage }}</div>
      </div></div>
    </details>

    <details id="accOt" class="acc dayPanelModule" :class="{ moduleHidden: !moduleEnabled('overtime') }" data-day-module="overtime" :open="isOpen('accOt')" @toggle="toggleSection('accOt', $event)">
      <summary><span class="accT">Переработка</span><span id="sumOt" class="accS">{{ overtimeSummary }}</span></summary>
      <div class="accB"><div class="overtimeDayCompact"><div class="overtimeDayActions"><button id="dayAddCredit" class="primary" type="button" @click="addCredit">+ Начислить</button><button id="dayAddUsage" type="button" @click="addUsage">Оформить отгул</button><button id="dayAddSettlement" type="button" @click="addSettlement">К оплате</button><span id="otBalance" class="bal">доступно {{ formatSignedHours(bundle?.overtimeAccount.balanceHours) }}</span></div><p class="dayPanelHint overtimeUsageHint">Отгул создаётся как отсутствие, «к оплате» — как отдельное решение. Оба варианта расходуют один банк по FIFO; денежная сумма пока не рассчитывается.</p>
        <div id="otDayDetails" class="overtimeDayEntries"><div v-if="!dayCredits.length && !dayUsages.length" class="emptyLine">На этот день в журнале переработок записей нет. Начисления не сгорают при переходе между месяцами.</div><div v-for="credit in dayCredits" :key="`credit-${credit.id}`" class="overtimeDayEntry credit"><div><b>+{{ formatHours(credit.hours) }} ч</b><span>{{ creditRange(credit) }}<template v-if="credit.reason"> · {{ credit.reason }}</template></span><small>остаток: {{ formatHours(credit.remainingHours) }} ч</small></div><button type="button" @click="editCredit(credit.id)">ред.</button></div><div v-for="usage in dayUsages" :key="`usage-${usage.id}`" class="overtimeDayEntry usage"><div><b>−{{ formatHours(usage.hours) }} ч</b><span>{{ usage.sourceKind === 'SETTLEMENT' ? 'К оплате' : usage.sourceAbsenceId ? 'Отгул' : 'Списание' }} · {{ usage.reason || 'без комментария' }}</span><small v-for="allocation in usage.allocations" :key="`${usage.id}-${allocation.creditId}`"><template v-for="label in allocationRangeLabels(allocation)" :key="label"><span class="allocationRange">{{ label }}</span></template></small></div><button type="button" @click="openUsage(usage)">{{ usage.sourceAbsenceId ? 'Открыть отсутствие' : usage.sourceSettlementId ? 'Открыть «к оплате»' : 'Открыть банк' }}</button></div></div>
      </div></div>
    </details>

    <details id="accVacation" class="acc dayPanelModule" :class="{ moduleHidden: !moduleEnabled('vacation') }" data-day-module="vacation" :open="isOpen('accVacation')" @toggle="toggleSection('accVacation', $event)">
      <summary><span class="accT">Отпуск и отсутствия</span><span id="sumVacation" class="accS">{{ vacationSummary }}</span></summary>
      <div class="accB dayForm"><div id="vacationDayList" class="vacationDayList"><button v-for="item in facts.absences" :key="item.periodId" type="button" class="vacationDayItem" :class="item.coverage === 'FULL_DAY' ? 'full' : 'partial'" :style="{ '--absence-color': item.typeColor }" @click="editAbsence(item.periodId)"><span class="vacationDayIcon">{{ item.coverage === 'FULL_DAY' ? '●' : item.coverage === 'HOURS_ONLY' ? '◷' : '◴' }}</span><span class="vacationDayPlanFact"><small>Фактически</small><b>{{ item.title || item.typeName }}</b><em>{{ absenceTime(item) }}</em><small v-if="item.plannedShiftName">По графику: {{ item.plannedShiftName }}</small></span><i>›</i></button><div v-if="!facts.absences.length" class="dayPanelHint">На этот день отсутствие не запланировано.</div></div><div class="taskDayToolbar"><button id="vacationPlanSelected" class="primary" type="button" @click="planAbsence">＋ Запланировать отсутствие</button><span class="dayPanelHint">Выбранный день станет началом периода. Смена останется в календаре как отдельный факт.</span></div></div>
    </details>

    <details id="accImp" class="acc dayPanelModule" :class="{ moduleHidden: !moduleEnabled('important_dates') }" data-day-module="important_dates" :open="isOpen('accImp')" @toggle="toggleSection('accImp', $event)"><summary><span class="accT">Важные дни</span><span id="sumImp" class="accS"></span></summary><div class="accB dayForm"><div id="vueSelectedDayImportantMount" data-vue-productivity-mount></div></div></details>
    <details id="accTasks" class="acc dayPanelModule" :class="{ moduleHidden: !moduleEnabled('tasks') }" data-day-module="tasks" :open="isOpen('accTasks')" @toggle="toggleSection('accTasks', $event)"><summary><span class="accT">Задачи</span><span id="sumTasks" class="accS"></span></summary><div class="accB dayForm"><div id="vueSelectedDayTasksMount" data-vue-productivity-mount></div></div></details>
    <details id="accNote" class="acc dayPanelModule" :class="{ moduleHidden: !moduleEnabled('notes') }" data-day-module="notes" :open="isOpen('accNote')" @toggle="toggleSection('accNote', $event)"><summary><span class="accT">Заметки</span><span id="sumNote" class="accS"></span></summary><div class="accB dayNotesModule"><div id="vueSelectedDayNotesMount" data-vue-productivity-mount></div></div></details>
  </aside>
</template>
