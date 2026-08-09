<script setup lang="ts">
import { storeToRefs } from "pinia";
import { watch } from "vue";
import { useProductivityStore } from "../stores/productivityStore";
import { importantScheduleLabel } from "../types/model";
const store = useProductivityStore();
const { importantEditorOpen, importantDetailsOpen, importantDraft, importantDetails, mutationPending, error } = storeToRefs(store);
function closeEdit(): void { store.importantEditorOpen = false; store.error = ""; }
function closeDetails(): void { store.importantDetailsOpen = false; }
async function openInCalendar(): Promise<void> {
  if (!importantDetails.value) return;
  await window.DutyLogVueDomains?.calendarTimeline?.openDate(importantDetails.value.date, "day");
  closeDetails();
}
watch(() => importantDraft.value.eventType, (eventType) => {
  if (eventType !== "IMPORTANT_DATE") return;
  importantDraft.value.allDay = true;
  importantDraft.value.endDate = importantDraft.value.date;
  importantDraft.value.startTime = "";
  importantDraft.value.endTime = "";
});
watch(() => importantDraft.value.date, (date) => {
  if (importantDraft.value.eventType === "IMPORTANT_DATE") importantDraft.value.endDate = date;
});
function toggleReminder(value: number, checked: boolean): void {
  const current = new Set(importantDraft.value.reminders);
  if (checked) current.add(value); else current.delete(value);
  importantDraft.value.reminders = [...current].sort((a,b) => a-b);
}
</script>
<template>
  <div v-if="importantDetailsOpen && importantDetails" class="appModal vue-owned-modal" id="importantDetailsModal">
    <button class="appModalBackdrop" id="importantDetailsBackdrop" type="button" aria-label="Закрыть" @click="closeDetails"></button>
    <div class="appModalPanel appModalPanelWide" role="dialog" aria-modal="true" aria-labelledby="importantDetailsTitle">
      <div class="appModalHead"><div><div class="eyebrow" id="importantDetailsType">{{ importantDetails.eventType }}</div><div class="appModalTitle" id="importantDetailsTitle">{{ importantDetails.title }}</div><div class="appModalHint" id="importantDetailsSchedule">{{ importantScheduleLabel(importantDetails) }}</div></div><button class="appModalClose" id="importantDetailsClose" type="button" @click="closeDetails">×</button></div>
      <div class="importantDetailsBody" id="importantDetailsBody"><p v-if="importantDetails.place"><b>Место:</b> {{ importantDetails.place }}</p><p v-if="importantDetails.category"><b>Категория:</b> {{ importantDetails.category }}</p><p v-if="importantDetails.description">{{ importantDetails.description }}</p><p v-if="importantDetails.sourceTimezone"><b>Исходная зона:</b> {{ importantDetails.sourceTimezone }}</p></div>
      <div class="appModalActions"><button id="importantDetailsOpenDay" type="button" @click="openInCalendar">Открыть в календаре</button><a class="buttonLike" id="importantDetailsExportIcs" :href="`/api/calendar-sync/events/${importantDetails.id}.ics`">Экспорт .ics</a><button class="primary" id="importantDetailsEdit" type="button" @click="store.editImportantDetails()">Редактировать</button><button class="dangerGhost" id="importantDetailsDelete" type="button" @click="store.deleteImportant(importantDetails.id)">Удалить</button></div>
    </div>
  </div>

  <div v-if="importantEditorOpen" class="appModal vue-owned-modal" id="importantEditModal">
    <button class="appModalBackdrop" id="importantEditBackdrop" type="button" aria-label="Закрыть" @click="closeEdit"></button>
    <div class="appModalPanel appModalPanelWide" role="dialog" aria-modal="true" aria-labelledby="importantEditTitle">
      <div class="appModalHead"><div><div class="eyebrow">Важные события</div><div class="appModalTitle" id="importantEditTitle">{{ importantDraft.id ? 'Редактировать событие' : 'Новое событие' }}</div><div class="appModalHint">Весь день хранится как плавающая дата. Событие со временем хранит абсолютный момент.</div></div><button class="appModalClose" id="importantEditClose" type="button" @click="closeEdit">×</button></div>
      <form class="appModalForm importantEventForm" id="importantEditForm" @submit.prevent="store.saveImportant()">
        <label class="appField appFieldWide">Название<input id="importantEditName" v-model="importantDraft.title" maxlength="120" required/></label>
        <label class="appField">Тип<select id="importantEditType" v-model="importantDraft.eventType"><option value="IMPORTANT_DATE">Важная дата</option><option value="EVENT">Событие</option><option value="PERIOD">Период</option></select></label>
        <label class="appField">Повтор<select id="importantEditRepeat" v-model="importantDraft.repeatMode"><option value="NONE">один раз</option><option value="YEARLY">каждый год</option><option value="MONTHLY">каждый месяц</option></select></label>
        <label class="appField">Дата начала<input id="importantEditStartDate" v-model="importantDraft.date" type="date" required/></label>
        <label v-show="importantDraft.eventType !== 'IMPORTANT_DATE'" class="appField" id="importantEditEndDateField">Дата окончания<input id="importantEditEndDate" v-model="importantDraft.endDate" type="date"/></label>
        <label class="appField checkline"><input id="importantEditAllDay" v-model="importantDraft.allDay" type="checkbox" :disabled="importantDraft.eventType === 'IMPORTANT_DATE'"/><span>Весь день</span></label>
        <label v-show="!importantDraft.allDay && importantDraft.eventType !== 'IMPORTANT_DATE'" class="appField importantTimedField">Начало<input id="importantEditStartTime" v-model="importantDraft.startTime" type="time"/></label>
        <label v-show="!importantDraft.allDay && importantDraft.eventType !== 'IMPORTANT_DATE'" class="appField importantTimedField">Окончание<input id="importantEditEndTime" v-model="importantDraft.endTime" type="time"/></label>
        <label v-show="!importantDraft.allDay && importantDraft.eventType !== 'IMPORTANT_DATE'" class="appField importantTimedField">Исходный часовой пояс<input id="importantEditTimezone" v-model="importantDraft.sourceTimezone" maxlength="80"/></label>
        <label class="appField">Место<input id="importantEditPlace" v-model="importantDraft.place" maxlength="240"/></label><label class="appField">Категория<input id="importantEditCategory" v-model="importantDraft.category" maxlength="80"/></label><label class="appField">Значок<input id="importantEditIcon" v-model="importantDraft.icon" maxlength="32"/></label><label class="appField">Цвет<input id="importantEditColor" v-model="importantDraft.color" type="color"/></label>
        <fieldset class="appField appFieldWide importantReminderSet"><legend>Напомнить заранее</legend><label v-for="item in [{v:0,t:'в момент начала'},{v:30,t:'за 30 минут'},{v:60,t:'за час'},{v:1440,t:'за день'},{v:10080,t:'за неделю'}]" :key="item.v"><input type="checkbox" name="importantReminder" :value="item.v" :checked="importantDraft.reminders.includes(item.v)" @change="toggleReminder(item.v, ($event.target as HTMLInputElement).checked)"/> {{ item.t }}</label></fieldset>
        <label class="appField appFieldWide">Описание<textarea id="importantEditDescription" v-model="importantDraft.description" maxlength="10000" rows="6"></textarea></label>
        <div class="appModalMessage appFieldWide" id="importantEditMessage" role="status">{{ error }}</div><div class="appModalActions appFieldWide"><button id="importantEditCancel" type="button" @click="closeEdit">Отмена</button><button class="primary" id="importantEditSave" type="submit" :disabled="mutationPending">Сохранить</button></div>
      </form>
    </div>
  </div>
</template>
