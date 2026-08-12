<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { useCalendarTimelineStore } from "@/features/calendar-timeline/stores/calendarTimelineStore";
import { useProductivityStore } from "../stores/productivityStore";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const shell = useShellStore();
const calendar = useCalendarTimelineStore();
const store = useProductivityStore();
const { modules } = storeToRefs(shell);
const { quickActionsOpen, quickActionText, quickActionDate, mutationPending, error } = storeToRefs(store);
const textInput = ref<HTMLTextAreaElement | null>(null);

const tasksEnabled = computed(() => modules.value.tasks !== false);
const notesEnabled = computed(() => modules.value.notes !== false);
const importantEnabled = computed(() => modules.value.important_dates !== false);
const overtimeEnabled = computed(() => modules.value.overtime !== false);
const vacationEnabled = computed(() => modules.value.vacation !== false);
const hasDraftAction = computed(() => tasksEnabled.value || notesEnabled.value || importantEnabled.value);

function close(): void { store.closeQuickActions(); }

async function saveInbox(): Promise<void> {
  if (!tasksEnabled.value) return;
  if (!quickActionText.value.trim()) {
    store.error = "Напиши, что нужно запомнить.";
    return;
  }
  const saved = await store.captureInbox(quickActionText.value);
  if (saved) close();
}

async function createTask(): Promise<void> {
  if (!tasksEnabled.value) return;
  const text = quickActionText.value.trim();
  const date = quickActionDate.value;
  close();
  await store.openTaskCreate(date, text);
}

async function createNote(): Promise<void> {
  if (!notesEnabled.value) return;
  const text = quickActionText.value.trim();
  const date = quickActionDate.value;
  close();
  await calendar.openDayPanel(date, "notes");
  navigateHashRoute("calendar");
  await store.createNote(date, text);
}

async function createImportant(): Promise<void> {
  if (!importantEnabled.value) return;
  const text = quickActionText.value.trim();
  const date = quickActionDate.value;
  close();
  navigateHashRoute("important");
  await store.openImportantCreate(date, text);
}

async function createCredit(): Promise<void> {
  if (!overtimeEnabled.value) return;
  const date = quickActionDate.value;
  close();
  await window.DutyLogVueDomains?.absenceTimeBank?.openCreditEditor(date);
}

async function createAbsence(): Promise<void> {
  if (!vacationEnabled.value) return;
  const date = quickActionDate.value;
  close();
  await window.DutyLogVueDomains?.absenceTimeBank?.openAbsenceComposer({ date, source: "quick-add" });
}

function handleKeydown(event: KeyboardEvent): void {
  if (tasksEnabled.value && event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    void saveInbox();
  }
}

watch(quickActionsOpen, async open => {
  if (!open) return;
  await nextTick();
  if (hasDraftAction.value) textInput.value?.focus({ preventScroll: true });
});
</script>

<template>
  <div v-if="quickActionsOpen" class="appModal quickActionsModal vue-owned-modal" id="quickActionsModal">
    <button class="appModalBackdrop" id="quickActionsBackdrop" type="button" aria-label="Закрыть" @click="close"></button>
    <div class="appModalPanel quickActionsPanel" role="dialog" aria-modal="true" aria-labelledby="quickActionsTitle">
      <div class="appModalHead">
        <div>
          <div class="eyebrow">Быстро добавить</div>
          <div class="appModalTitle" id="quickActionsTitle">{{ hasDraftAction ? 'Что нужно запомнить?' : 'Что добавить?' }}</div>
          <div class="appModalHint" id="quickActionsHint">{{ tasksEnabled ? 'Сохрани как входящее или используй текст как заготовку для действия.' : (hasDraftAction ? 'Выбери действие — текст будет подставлен автоматически.' : 'Выбери нужное действие.') }}</div>
        </div>
        <button class="appModalClose" id="quickActionsClose" type="button" aria-label="Закрыть" @click="close">×</button>
      </div>

      <div v-if="hasDraftAction" class="quickActionCapture" id="quickActionCapture">
        <textarea ref="textInput" id="quickActionText" v-model="quickActionText" maxlength="2000" rows="3" placeholder="Запиши одной строкой…" @keydown="handleKeydown"></textarea>
        <div class="quickActionCaptureFooter">
          <span class="quickActionKeyHint" id="quickActionKeyHint">{{ tasksEnabled ? 'Enter — во Входящие · Shift+Enter — новая строка' : 'Выбери действие — текст будет подставлен автоматически.' }}</span>
          <button v-if="tasksEnabled" class="primary" id="quickActionInbox" type="button" :disabled="mutationPending" @click="saveInbox">Во Входящие</button>
        </div>
        <div class="appModalMessage" id="quickActionMessage" role="status" aria-live="polite">{{ error }}</div>
      </div>

      <div v-if="hasDraftAction" class="quickActionDivider" id="quickActionDivider"><span>или сразу оформить</span></div>
      <div class="quickActionGrid">
        <button v-if="tasksEnabled" id="quickActionTask" type="button" @click="createTask"><b>✓</b><span>Создать задачу<small>текст уже будет подставлен</small></span></button>
        <button v-if="notesEnabled" id="quickActionNote" type="button" @click="createNote"><b>▤</b><span>Заметка на сегодня<small>открыть или дописать</small></span></button>
        <button v-if="importantEnabled" id="quickActionImportant" type="button" @click="createImportant"><b>★</b><span>Важный день<small>дата и повтор</small></span></button>
        <button v-if="overtimeEnabled" id="quickActionCredit" type="button" @click="createCredit"><b>＋</b><span>Добавить переработку<small>начислить часы</small></span></button>
        <button v-if="vacationEnabled" id="quickActionUsage" type="button" @click="createAbsence"><b>☂</b><span>Оформить отсутствие<small>отпуск, отгул, больничный</small></span></button>
      </div>
    </div>
  </div>
</template>
