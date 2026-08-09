<script setup lang="ts">
import { ref } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { useProductivityStore } from "../stores/productivityStore";
import { taskScheduleLabel } from "../types/model";
const shell = useShellStore();
const store = useProductivityStore();
const { board, metadata, boardStatus, boardCategory, boardProject, boardPriority, boardSearch, boardFrom, boardTo, boardLoading, inbox, visibleInbox, inboxSearch, inboxShowArchived, queuedMutations } = storeToRefs(store);
const capture = ref("");
const filtersExpanded = ref(false);
async function saveCapture(): Promise<void> { const text = capture.value.trim(); if (!text) return; capture.value = ""; await store.captureInbox(text); }
async function filters(): Promise<void> { await store.setBoardFilters(); }
function progress(task: typeof board.value.items[number]): string { const total = task.subtasks?.length ?? 0; return total ? `${task.subtasks.filter(item => item.done).length}/${total}` : ""; }
</script>
<template>
  <section class="view vue-domain-view" id="view-tasks" data-vue-domain-owner="productivity" data-vue-domain-route="tasks" :class="{ moduleHidden: shell.modules.tasks === false }">
    <div class="taskBoardCard" id="taskBoardCard">
      <div class="taskBoardHead"><div><div class="eyebrow">Все задачи</div><div class="small">Vue read model · Generated API · offline-safe completion</div></div><div class="taskBoardHeadActions"><div class="status" id="taskBoardStatus">{{ board.total }} задач<span v-if="queuedMutations"> · {{ queuedMutations }} оффлайн</span></div><button class="primary" id="taskBoardCreate" type="button" @click="store.openTaskCreate()">＋ Новая задача</button></div></div>
      <details class="taskInboxTray" id="taskInboxCard">
        <summary><span class="taskInboxIdentity"><span class="taskInboxIcon">↘</span><span><b>Входящие</b><small>Временные записи, которые можно разобрать позже</small></span></span><span class="statusChip inboxCountChip"><b id="inboxCount">{{ inbox.filter(item => item.status === 'OPEN').length }}</b></span></summary>
        <div class="taskInboxTrayBody">
          <div class="inboxCaptureRow"><textarea id="inboxQuickText" v-model="capture" maxlength="2000" rows="2" placeholder="Быстро записать…" @keydown.enter.exact.prevent="saveCapture"></textarea><button class="primary" id="inboxQuickSave" type="button" @click="saveCapture">Во Входящие</button></div>
          <div class="inboxToolbar"><span class="inboxStatus" id="inboxStatus">{{ queuedMutations ? `${queuedMutations} ждёт синхронизации` : '' }}</span><input id="inboxSearch" v-model="inboxSearch" type="search" maxlength="200" placeholder="поиск во входящих…"/><label class="inlineCheck"><input id="inboxShowArchived" v-model="inboxShowArchived" type="checkbox" @change="store.loadInbox()"/> показать разобранные</label><button id="inboxRefresh" type="button" @click="store.loadInbox()">Обновить</button></div>
          <div class="inboxList" id="inboxList"><div v-for="item in visibleInbox" :key="item.id" class="inboxItem" :data-inbox-id="item.id"><span>{{ item.text }}</span><div><button type="button" @click="store.convertInboxToTask(item)">В задачу</button><button class="dangerGhost" type="button" @click="store.deleteInbox(item.id)">×</button></div></div><div v-if="!visibleInbox.length" class="emptyLine">Входящие пусты.</div></div>
        </div>
      </details>
      <button class="mobileFilterToggle" id="taskBoardFiltersToggle" type="button" aria-controls="taskBoardFilters" :aria-expanded="filtersExpanded ? 'true' : 'false'" @click="filtersExpanded = !filtersExpanded"><span>Фильтры</span><b>⌄</b></button>
      <div class="taskBoardFilters" id="taskBoardFilters">
        <button id="taskBoardOpen" type="button" @click="boardStatus='open'; filters()">открытые</button><button id="taskBoardOverdue" type="button" @click="boardStatus='overdue'; filters()">просроченные</button><button id="taskBoardAll" type="button" @click="boardStatus='all'; filters()">все</button>
        <label class="mono">с <input id="taskBoardFrom" v-model="boardFrom" type="date" @change="filters"/></label><label class="mono">по <input id="taskBoardTo" v-model="boardTo" type="date" @change="filters"/></label>
        <select id="taskBoardStatusFilter" v-model="boardStatus" title="Статус задач" @change="filters"><option value="open">открытые</option><option value="overdue">просроченные</option><option value="done">выполненные</option><option value="all">все задачи</option></select>
        <select id="taskBoardCategory" v-model="boardCategory" title="Категория" @change="filters"><option value="">все категории</option><option v-for="item in metadata.categories" :key="item" :value="item">{{ item }}</option></select>
        <select id="taskBoardProject" v-model="boardProject" title="Проект" @change="filters"><option value="">все проекты</option><option v-for="item in metadata.projects" :key="item" :value="item">{{ item }}</option></select>
        <select id="taskBoardPriority" v-model="boardPriority" title="Приоритет" @change="filters"><option value="">любой приоритет</option><option value="URGENT">срочные</option><option value="HIGH">важные</option><option value="NORMAL">обычные</option><option value="LOW">низкие</option></select>
        <input id="taskBoardSearch" v-model="boardSearch" placeholder="поиск: текст, проект, категория, дата…" type="text" @keyup.enter="filters"/><button id="taskBoardThisMonth" type="button" @click="boardFrom=`${store.workDate.slice(0,7)}-01`; boardTo=''; filters()">этот месяц</button><button id="taskBoardClear" type="button" @click="boardCategory='';boardProject='';boardPriority='';boardSearch='';boardFrom='';boardTo='';boardStatus='open';filters()">сброс</button>
      </div>
      <div class="taskBoardStats" id="taskBoardStats">{{ board.total }} всего</div><div class="pagerRow" id="taskBoardPager"></div>
      <div class="taskBoardList" id="taskBoardList" :aria-busy="boardLoading">
        <article v-for="task in board.items" :key="task.id" class="taskBoardItem" :class="{ done: task.done, overdue: task.overdue }" :data-task-id="task.id">
          <input type="checkbox" :checked="task.done" @change="store.toggleTask(task, ($event.target as HTMLInputElement).checked)"/>
          <button class="taskBoardBody" type="button" @click="store.openTaskDetails(task.id)"><b>{{ task.text }}</b><small>{{ [task.project, task.category, taskScheduleLabel(task)].filter(Boolean).join(' · ') }}</small><span v-if="progress(task)" class="taskSubtaskProgress">{{ progress(task) }}</span></button>
        </article><div v-if="!boardLoading && !board.items.length" class="emptyLine">Задач по фильтру нет.</div>
      </div>
    </div>
  </section>
</template>
