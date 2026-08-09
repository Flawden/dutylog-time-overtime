<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useProductivityStore } from "../stores/productivityStore";
import { taskScheduleLabel } from "../types/model";

const store = useProductivityStore();
const { taskEditorOpen, taskDetailsOpen, taskDraft, taskDetails, metadata, mutationPending, error } = storeToRefs(store);
const planningSummary = computed(() => {
  if (taskDraft.value.allDay) return "Весь день";
  const minutes = Number(taskDraft.value.scheduledDurationMinutes || 0);
  return `${taskDraft.value.date} · ${taskDraft.value.scheduledStartTime || '—'}–${taskDraft.value.scheduledEndTime || '—'}${minutes ? ` · ${minutes} мин` : ''}`;
});
const detailsProgress = computed(() => {
  const subs = taskDetails.value?.subtasks ?? [];
  return `${subs.filter(item => item.done).length}/${subs.length}`;
});
function onDuration(minutes: number): void { store.updateTaskDuration(minutes); }
function addOnEnter(event: KeyboardEvent, index: number): void {
  if (event.key !== "Enter") return;
  event.preventDefault();
  if (index === taskDraft.value.subtasks.length - 1) store.addSubtaskDraft();
}
function closeEditor(): void { store.taskEditorOpen = false; store.error = ""; }
function closeDetails(): void { store.taskDetailsOpen = false; }
</script>

<template>
  <div v-if="taskDetailsOpen && taskDetails" class="appModal vue-owned-modal" id="taskDetailsModal">
    <button class="appModalBackdrop" id="taskDetailsBackdrop" type="button" aria-label="Закрыть" @click="closeDetails"></button>
    <div class="appModalPanel taskDetailsPanel" role="dialog" aria-modal="true" aria-labelledby="taskDetailsTitle">
      <div class="appModalHead">
        <div><div class="eyebrow">Детали задачи</div><div class="appModalTitle" id="taskDetailsTitle">{{ taskDetails.text }}</div><div class="appModalHint" id="taskDetailsHint">{{ taskDetails.overdue && !taskDetails.done ? 'Задача просрочена' : taskDetails.done ? 'Задача выполнена' : 'Открытая задача' }}</div></div>
        <button class="appModalClose" id="taskDetailsClose" type="button" aria-label="Закрыть" @click="closeDetails">×</button>
      </div>
      <div class="taskDetailsBody">
        <div class="taskMeta taskDetailsMeta" id="taskDetailsMeta"><span v-for="tag in taskDetails.tags" :key="tag" class="statusChip">#{{ tag }}</span></div>
        <section class="taskDetailsSection taskDetailsSchedule" id="taskDetailsSchedule">
          <h3>Запланировано</h3><div class="taskDetailsScheduleMain" id="taskDetailsScheduleMain">{{ taskScheduleLabel(taskDetails) }}<template v-if="taskDetails.scheduledDurationMinutes"> · {{ taskDetails.scheduledDurationMinutes }} мин</template></div>
          <div class="taskDetailsScheduleSource" id="taskDetailsScheduleSource" :hidden="!taskDetails.scheduledSourceTimezone">{{ taskDetails.scheduledSourceTimezone }} · {{ taskDetails.scheduledSourceStartDate }} {{ taskDetails.scheduledSourceStartTime }}</div>
        </section>
        <section class="taskDetailsSection taskDetailsDescription" id="taskDetailsDescription">
          <h3>Описание</h3><div class="taskDetailsDescriptionText" id="taskDetailsDescriptionText">{{ taskDetails.description || '—' }}</div>
        </section>
        <section class="taskDetailsSection" id="taskDetailsChecklistSection">
          <h3 id="taskDetailsChecklistTitle">Подзадачи <small v-if="taskDetails.subtasks.length">{{ detailsProgress }}</small></h3>
          <div class="taskDetailsChecklist" id="taskDetailsChecklist">
            <label v-for="sub in taskDetails.subtasks" :key="sub.id ?? sub.text" class="taskDetailsChecklistRow" :class="{ done: sub.done }">
              <input v-if="sub.id" type="checkbox" :checked="sub.done" @change="store.toggleSubtask(taskDetails.id, Number(sub.id), ($event.target as HTMLInputElement).checked)"/><span>{{ sub.text }}</span><small v-if="sub.dueDate">{{ sub.dueDate }}</small>
            </label>
            <div v-if="!taskDetails.subtasks.length" class="emptyLine">Подзадач нет.</div>
          </div>
        </section>
        <section class="taskDetailsFacts" id="taskDetailsFacts">
          <span v-if="taskDetails.project">Проект: {{ taskDetails.project }}</span>
          <span v-if="taskDetails.category">Категория: {{ taskDetails.category }}</span>
          <span v-if="taskDetails.dueDate">Дедлайн: {{ taskDetails.dueDate }} {{ taskDetails.dueTime || '' }}</span>
          <span v-if="taskDetails.dueSourceTimezone">Исходный часовой пояс: {{ taskDetails.dueSourceTimezone }}</span>
          <span>Приоритет: {{ taskDetails.priority }}</span>
        </section>
        <div class="appModalActions taskDetailsActions">
          <button class="dangerGhost" id="taskDetailsDelete" type="button" @click="store.deleteTask(taskDetails.id)">Удалить</button>
          <button id="taskDetailsEdit" type="button" @click="store.editTaskDetails()">Редактировать</button>
          <button class="primary" id="taskDetailsToggle" type="button" @click="store.toggleTask(taskDetails, !taskDetails.done)">{{ taskDetails.done ? 'Вернуть' : 'Выполнить' }}</button>
        </div>
      </div>
    </div>
  </div>

  <div v-if="taskEditorOpen" class="appModal vue-owned-modal" id="taskEditModal">
    <button class="appModalBackdrop" id="taskEditBackdrop" type="button" aria-label="Закрыть" @click="closeEditor"></button>
    <div class="appModalPanel taskEditorPanel" role="dialog" aria-modal="true" aria-labelledby="taskEditTitle">
      <div class="appModalHead">
        <div><div class="eyebrow">Задача</div><div class="appModalTitle" id="taskEditTitle">{{ taskDraft.id ? 'Редактировать задачу' : 'Новая задача' }}</div><div class="appModalHint" id="taskEditHint">Достаточно текста и даты. Остальное можно заполнить позже.</div></div>
        <button class="appModalClose" id="taskEditClose" type="button" aria-label="Закрыть" @click="closeEditor">×</button>
      </div>
      <form class="appModalForm taskEditorForm" id="taskEditForm" @submit.prevent="store.saveTask()">
        <label class="appField appFieldWide">Текст задачи<textarea id="taskEditText" v-model="taskDraft.text" maxlength="500" rows="4" placeholder="Что нужно сделать?" required></textarea></label>
        <section class="taskPlanningEditor appFieldWide" id="taskPlanningEditor">
          <div class="taskPlanningHead"><div><b>Запланировано</b><small>Интервал не является дедлайном</small></div><label class="appCheck"><input id="taskEditAllDay" v-model="taskDraft.allDay" type="checkbox"/> Весь день</label></div>
          <div class="taskPlanningGrid">
            <label class="appField taskPrimaryDate">Дата начала<input id="taskEditDate" v-model="taskDraft.date" type="date" required @change="taskDraft.scheduledEndDate = taskDraft.date"/></label>
            <label v-show="!taskDraft.allDay" class="appField taskTimedField">Время начала<input id="taskEditStartTime" v-model="taskDraft.scheduledStartTime" type="time" step="60" @change="onDuration(Number(taskDraft.scheduledDurationMinutes || 0))"/></label>
            <label v-show="!taskDraft.allDay" class="appField taskTimedField">Дата окончания<input id="taskEditEndDate" v-model="taskDraft.scheduledEndDate" type="date"/></label>
            <label v-show="!taskDraft.allDay" class="appField taskTimedField">Время окончания<input id="taskEditEndTime" v-model="taskDraft.scheduledEndTime" type="time" step="60"/></label>
            <label v-show="!taskDraft.allDay" class="appField taskTimedField">Длительность, минут<input id="taskEditDuration" v-model="taskDraft.scheduledDurationMinutes" type="number" min="1" max="10080" step="1" inputmode="numeric" @input="onDuration(Number(taskDraft.scheduledDurationMinutes || 0))"/></label>
            <div v-show="!taskDraft.allDay" class="taskDurationPresets taskTimedField" id="taskDurationPresets" aria-label="Быстрый выбор длительности">
              <button v-for="minutes in [15,30,45,60,90,120]" :key="minutes" type="button" :data-task-duration="minutes" @click="onDuration(minutes)">{{ minutes }}</button>
            </div>
          </div>
          <div class="taskPlanningSummary" id="taskPlanningSummary">{{ planningSummary }}</div><div class="taskPlanningError" id="taskPlanningError" role="alert"></div>
        </section>
        <details class="taskSubtaskEditor appFieldWide" id="taskEditSubtasks">
          <summary>Подзадачи <span id="taskEditSubtaskSummary">{{ taskDraft.subtasks.length ? taskDraft.subtasks.length : 'необязательно' }}</span></summary>
          <div class="taskSubtaskEditorBody">
            <div class="taskSubtaskEditorList" id="taskEditSubtaskList">
              <div v-for="(sub, index) in taskDraft.subtasks" :key="sub.id ?? index" class="taskSubtaskEditorRow">
                <input v-model="sub.text" type="text" maxlength="500" placeholder="Подзадача" @keydown="addOnEnter($event, index)"/>
                <input v-model="sub.dueDate" type="date" title="Срок подзадачи"/>
                <button type="button" aria-label="Выше" @click="store.moveSubtaskDraft(index,-1)">↑</button><button type="button" aria-label="Ниже" @click="store.moveSubtaskDraft(index,1)">↓</button><button type="button" aria-label="Удалить" @click="store.removeSubtaskDraft(index)">×</button>
              </div>
            </div>
            <button class="taskSubtaskAdd" id="taskEditSubtaskAdd" type="button" @click="store.addSubtaskDraft()">＋ Добавить подзадачу</button>
          </div>
        </details>
        <details class="taskAdvanced appFieldWide" id="taskEditAdvanced">
          <summary>Дополнительно <span>описание, проект, категория, теги, дедлайн и напоминание</span></summary>
          <div class="taskAdvancedGrid">
            <label class="appField appFieldWide">Описание<textarea id="taskEditDescription" v-model="taskDraft.description" maxlength="4000" rows="5"></textarea></label>
            <label class="appField">Проект<input id="taskEditProject" v-model="taskDraft.project" list="taskProjectSuggestions" maxlength="80" type="text" autocomplete="off"/><datalist id="taskProjectSuggestions"><option v-for="item in metadata.projects" :key="item" :value="item"/></datalist></label>
            <label class="appField">Категория<input id="taskEditCategory" v-model="taskDraft.category" list="taskCategorySuggestions" maxlength="80" type="text" autocomplete="off"/><datalist id="taskCategorySuggestions"><option v-for="item in metadata.categories" :key="item" :value="item"/></datalist></label>
            <label class="appField">Теги<input id="taskEditTags" v-model="taskDraft.tags" list="taskTagSuggestions" maxlength="420" type="text" autocomplete="off"/><datalist id="taskTagSuggestions"><option v-for="item in metadata.tags" :key="item" :value="item"/></datalist></label>
            <div class="taskSuggestedTags appFieldWide" id="taskSuggestedTags" hidden></div>
            <label class="appField">Приоритет<select id="taskEditPriority" v-model="taskDraft.priority"><option value="LOW">низкий</option><option value="NORMAL">обычный</option><option value="HIGH">важный</option><option value="URGENT">срочный</option></select></label>
            <label class="appField">Дедлайн<input id="taskEditDueDate" v-model="taskDraft.dueDate" type="date"/></label>
            <label class="appField">Время дедлайна<input id="taskEditDueTime" v-model="taskDraft.dueTime" type="time" step="60"/></label>
            <label class="appCheck"><input id="taskEditReminderEnabled" v-model="taskDraft.reminderEnabled" type="checkbox"/> Напомнить о задаче</label>
            <label class="appField" id="taskEditReminderBeforeLabel">За сколько минут<input id="taskEditReminderBefore" v-model="taskDraft.reminderMinutesBefore" max="10080" min="0" step="1" type="number"/></label>
            <div class="appModalHint appFieldWide" id="taskEditReminderHint" hidden></div>
          </div>
        </details>
        <div class="appModalMessage appFieldWide" id="taskEditMessage" role="status" aria-live="polite">{{ error }}</div>
        <div class="appModalActions appFieldWide"><button type="button" id="taskEditCancel" @click="closeEditor">Отмена</button><button class="primary" type="submit" id="taskEditSave" :disabled="mutationPending">{{ taskDraft.id ? 'Сохранить' : 'Добавить задачу' }}</button></div>
      </form>
    </div>
  </div>
</template>
