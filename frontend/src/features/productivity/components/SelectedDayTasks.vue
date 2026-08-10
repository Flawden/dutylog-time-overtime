<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useProductivityStore } from "../stores/productivityStore";

const store = useProductivityStore();
const { selectedTasks, selectedLoading } = storeToRefs(store);
const status = defineModel<"all" | "open" | "overdue" | "done">("status", { default: "all" });
const category = defineModel<string>("category", { default: "all" });
const categories = computed(() => [...new Set(selectedTasks.value.map(task => task.category).filter((value): value is string => Boolean(value)))].sort());
const visible = computed(() => selectedTasks.value.filter(task => {
  if (status.value === "open" && task.done) return false;
  if (status.value === "overdue" && (!task.overdue || task.done)) return false;
  if (status.value === "done" && !task.done) return false;
  if (category.value !== "all" && task.category !== category.value) return false;
  return true;
}));
function progress(task: typeof selectedTasks.value[number]): string {
  const total = task.subtasks?.length ?? 0;
  if (!total) return "";
  return `${task.subtasks.filter(item => item.done).length}/${total}`;
}
</script>

<template>
  <div class="dayForm vue-productivity-selected" data-vue-selected-tasks>
    <div class="taskDayToolbar">
      <button class="primary taskCreateButton" id="taskCreateForDay" type="button" @click="store.openTaskCreate()">＋ Добавить задачу</button>
      <span class="dayPanelHint">Дата выбранного дня подставится автоматически.</span>
    </div>
    <div class="taskFilters">
      <select id="taskStatusFilter" v-model="status" title="Фильтр задач">
        <option value="all">все</option><option value="open">открытые</option><option value="overdue">просроченные</option><option value="done">выполненные</option>
      </select>
      <select id="taskCategoryFilter" v-model="category" title="Категория">
        <option value="all">все категории</option><option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
      </select>
    </div>
    <div class="taskList" id="taskList" :aria-busy="selectedLoading">
      <div v-if="!selectedLoading && !visible.length" class="emptyLine">На этот день задач нет.</div>
      <template v-for="(task, index) in visible" :key="task.id">
        <div v-if="task.done && index > 0 && !visible[index - 1]?.done" class="taskCompletionDivider" role="separator">Выполненные</div>
        <div class="taskItem" :class="{ done: task.done, overdue: task.overdue }" :data-task-id="task.id">
          <input class="taskCheck" type="checkbox" :checked="task.done" :aria-label="`Завершить ${task.text}`" @change="store.toggleTask(task, ($event.target as HTMLInputElement).checked)" />
          <button class="taskItemBody" type="button" @click="store.openTaskDetails(task.id)">
            <b>{{ task.text }}</b>
            <small v-if="task.project || task.category || task.dueDate">{{ [task.project, task.category, task.dueDate ? `до ${task.dueDate}${task.dueTime ? ` ${task.dueTime}` : ''}` : ''].filter(Boolean).join(' · ') }}</small>
            <span v-if="progress(task)" class="taskSubtaskProgress" role="progressbar" aria-valuemin="0" :aria-valuemax="task.subtasks.length" :aria-valuenow="task.subtasks.filter(item => item.done).length">
              <span class="taskSubtaskProgressTrack"><span class="taskSubtaskProgressFill" :style="{ width: `${task.subtasks.length ? Math.round(task.subtasks.filter(item => item.done).length * 100 / task.subtasks.length) : 0}%` }"></span></span>
              <span class="taskSubtaskProgressValue">{{ progress(task) }}</span>
            </span>
          </button>
          <details v-if="task.subtasks?.length" class="taskSubtasksInline">
            <summary>Подзадачи ({{ progress(task) }})</summary>
            <div class="taskSubtaskInlineList">
              <label v-for="sub in task.subtasks" :key="sub.id ?? sub.text" class="taskSubtaskInlineRow" :class="{ done: sub.done }">
                <input v-if="sub.id" type="checkbox" :checked="sub.done" @change="store.toggleSubtask(task.id, Number(sub.id), ($event.target as HTMLInputElement).checked)" />
                <span class="taskSubtaskInlineText">{{ sub.text }}</span>
                <span v-if="sub.dueDate" class="taskSubtaskInlineDue">📅 {{ sub.dueDate.split('-').reverse().join('.') }}</span>
              </label>
            </div>
          </details>
        </div>
      </template>
    </div>
  </div>
</template>
