<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { useProductivityStore } from "../stores/productivityStore";
const store = useProductivityStore();
const { selectedDate, selectedImportant } = storeToRefs(store);
const title = ref("");
const repeat = ref<"NONE" | "MONTHLY" | "YEARLY">("YEARLY");
const color = ref("#F5B841");
const summary = computed(() => selectedImportant.value.length ? String(selectedImportant.value.length) : "");
async function add(): Promise<void> {
  await store.openImportantCreate(selectedDate.value);
  store.importantDraft.title = title.value;
  store.importantDraft.repeatMode = repeat.value;
  store.importantDraft.color = color.value;
  if (title.value.trim()) await store.saveImportant();
  title.value = "";
}
</script>
<template>
  <Teleport to="#sumImp"><span>{{ summary }}</span></Teleport>
  <div class="dayForm vue-productivity-selected" data-vue-selected-important>
    <div class="importantList" id="importantList">
      <button v-for="item in selectedImportant" :key="`${item.id}-${item.date}`" type="button" class="importantItem" :data-important-id="item.id" @click="store.openImportantDetails(item.id)">
        <span class="importantDot" :style="{ background: item.color }"></span><b>{{ item.title }}</b><small>{{ item.startTime ? `${item.startTime}${item.endTime ? `–${item.endTime}` : ''}` : item.repeatMode }}</small>
      </button>
    </div>
    <div class="row">
      <label>Дата <input id="impDate" v-model="selectedDate" type="date" @change="store.loadSelectedDate(selectedDate)" /></label>
      <button id="impDateSelected" type="button">выбранный день</button>
      <button id="impDateToday" type="button" @click="store.loadSelectedDate(new Date().toISOString().slice(0,10))">сегодня</button>
    </div>
    <div class="row">
      <input id="impTitle" v-model="title" placeholder="Например: день рождения Макса" type="text" />
      <select id="impRepeat" v-model="repeat"><option value="YEARLY">каждый год</option><option value="MONTHLY">каждый месяц</option><option value="NONE">один раз</option></select>
      <input id="impColor" v-model="color" title="Цвет важного дня" type="color" />
      <button class="addSmall" id="impAdd" type="button" @click="add">Добавить</button>
    </div>
    <div class="dayPanelHint">Для события со временем или периода используйте раздел «Важные дни».</div>
  </div>
</template>
