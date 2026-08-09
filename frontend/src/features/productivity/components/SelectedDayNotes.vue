<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useProductivityStore } from "../stores/productivityStore";
import { useCalendarTimelineStore } from "@/features/calendar-timeline/stores/calendarTimelineStore";
import { useShellStore } from "@/app/shellStore";
import { noteLabel } from "../types/model";

const store = useProductivityStore();
const calendar = useCalendarTimelineStore();
const shell = useShellStore();
const { selectedNotes, currentNote, noteSearch, noteSearchResults } = storeToRefs(store);
const title = ref("");
const content = ref("");
const preview = ref(false);
let timer: ReturnType<typeof globalThis.setTimeout> | null = null;
let syncing = false;

watch(currentNote, note => {
  syncing = true;
  title.value = note?.title ?? "";
  content.value = note?.content ?? "";
  queueMicrotask(() => { syncing = false; });
}, { immediate: true });

function scheduleSave(): void {
  if (syncing || !currentNote.value) return;
  if (timer != null) globalThis.clearTimeout(timer);
  const id = currentNote.value.id;
  timer = globalThis.setTimeout(() => {
    timer = null;
    void store.updateNote(id, { title: title.value.trim() || null, content: content.value });
  }, 420);
}
watch([title, content], scheduleSave);
onBeforeUnmount(() => {
  if (timer != null) globalThis.clearTimeout(timer);
  if (currentNote.value) void store.updateNote(currentNote.value.id, { title: title.value.trim() || null, content: content.value });
});
async function openSearchResult(date: string, id: number): Promise<void> {
  await calendar.openDate(date, "month");
  await store.loadSelectedDate(date);
  store.selectNote(id);
}
const summary = computed(() => selectedNotes.value.length ? String(selectedNotes.value.length) : "");
</script>

<template>
  <Teleport to="#sumNote"><span>{{ summary }}</span></Teleport>
  <div class="dayNotesModule vue-productivity-selected" data-vue-selected-notes>
    <div class="dayNotesToolbar">
      <button class="primary" id="noteAdd" type="button" :disabled="!shell.online" @click="store.createNote()">＋ Новая заметка</button>
      <input id="noteSearch" v-model="noteSearch" type="search" placeholder="поиск по заметкам…" autocomplete="off" aria-controls="noteSearchResults" @input="store.searchNotesNow()" />
      <a class="buttonLike" id="noteExport" href="/api/export/notes">Экспорт</a>
      <span class="dayPanelHint" id="noteOfflineHint">Несколько независимых заметок на один день. Изменения текста можно сохранить оффлайн.</span>
    </div>
    <div class="noteSearchResults" id="noteSearchResults" :hidden="!noteSearch.trim()">
      <button v-for="note in noteSearchResults" :key="note.id" class="noteSearchResult" type="button" @click="openSearchResult(note.date, note.id)">
        <b>{{ noteLabel(note) }}</b><small>{{ note.date }}</small>
      </button>
      <div v-if="noteSearch.trim() && !noteSearchResults.length" class="emptyLine">Ничего не найдено.</div>
    </div>
    <div class="dayNotesLayout">
      <div class="dayNoteList" id="noteList">
        <button v-for="note in selectedNotes" :key="note.id" type="button" class="dayNoteCard" :class="{ on: currentNote?.id === note.id, pinned: note.pinned }" :data-note-id="note.id" @click="store.selectNote(note.id)">
          <span class="dayNoteCardPin">{{ note.pinned ? '📌' : '' }}</span>
          <span class="dayNoteCardText"><b class="dayNoteCardTitle">{{ noteLabel(note) }}</b><small class="dayNoteCardPreview">{{ note.content.slice(0, 90) || 'Пустая заметка' }}</small></span>
          <small class="dayNoteCardTime">{{ note.updatedAt ? note.updatedAt.slice(11,16) : '' }}</small>
        </button>
      </div>
      <div class="dayNoteEditor" id="noteEditorPane">
        <template v-if="currentNote">
          <div class="dayNoteEditorHead">
            <input id="noteTitle" v-model="title" maxlength="200" placeholder="Название заметки (необязательно)" type="text" />
            <div class="dayNoteActions">
              <button id="notePin" type="button" title="Закрепить заметку" @click="store.updateNote(currentNote.id, { pinned: !currentNote.pinned })">📌</button>
              <button id="noteMoveUp" type="button" title="Выше" @click="store.moveNote(currentNote.id, 'UP')">↑</button>
              <button id="noteMoveDown" type="button" title="Ниже" @click="store.moveNote(currentNote.id, 'DOWN')">↓</button>
              <button id="noteDelete" class="dangerGhost" type="button" title="Удалить заметку" @click="store.deleteNote(currentNote.id)">🗑</button>
            </div>
          </div>
          <div class="tabs">
            <button id="tabEdit" type="button" :class="{ on: !preview }" @click="preview = false">Редактор</button>
            <button id="tabPrev" type="button" :class="{ on: preview }" @click="preview = true">Превью</button>
          </div>
          <textarea v-show="!preview" id="noteEdit" v-model="content" placeholder="# Заголовок&#10;**жирный**, *курсив*, `код`" spellcheck="false"></textarea>
          <div v-show="preview" id="notePrev" class="notePreviewText">{{ content || 'Пусто.' }}</div>
        </template>
        <div v-else class="dayNoteEmpty" id="noteEmpty">Создайте первую заметку для этого дня.</div>
      </div>
    </div>
  </div>
</template>
