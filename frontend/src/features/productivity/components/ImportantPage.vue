<script setup lang="ts">
import { ref } from "vue";
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";
import { useProductivityStore } from "../stores/productivityStore";
import { importantScheduleLabel } from "../types/model";
const shell = useShellStore();
const store = useProductivityStore();
const { filteredImportantDays, importantScope, importantSearch, workDate } = storeToRefs(store);
const filtersExpanded = ref(false);
async function edit(itemId: number): Promise<void> {
  await store.openImportantEdit(itemId);
}
</script>
<template>
  <section class="view vue-domain-view" id="view-important" data-vue-domain-owner="productivity" data-vue-domain-route="important" :class="{ moduleHidden: shell.modules.important_dates === false }">
    <div class="importantBoardCard" id="importantBoardCard">
      <div class="importantBoardHead"><div><div class="eyebrow">Важные дни</div><div class="small">Vue read model · плавающие даты и абсолютные события не смешиваются.</div></div><div class="status" id="importantBoardStatus">{{ filteredImportantDays.length }}</div></div>
      <div class="importantBoardEditor importantBoardCreateBar"><button class="primary" id="importantBoardNew" type="button" @click="store.openImportantCreate()">＋ Новое событие</button><span class="small">Важная дата, событие со временем или многодневный период.</span></div>
      <button class="mobileFilterToggle" id="importantBoardFiltersToggle" type="button" aria-controls="importantBoardFilters" :aria-expanded="filtersExpanded ? 'true' : 'false'" @click="filtersExpanded = !filtersExpanded"><span>Фильтры</span><b>⌄</b></button>
      <div class="importantBoardFilters" id="importantBoardFilters">
        <select id="importantBoardScope" v-model="importantScope" title="Период"><option value="all">все даты</option><option value="upcoming">ближайшие</option><option value="past">прошедшие</option><option value="recurring">повторяющиеся</option></select>
        <input id="importantBoardSearch" v-model="importantSearch" placeholder="поиск: название или дата…" type="text"/><button id="importantBoardToday" type="button" @click="store.openImportantCreate(workDate)">сегодня</button><button id="importantBoardClear" type="button" @click="importantScope='all'; importantSearch=''">сброс</button>
      </div>
      <div class="importantBoardList" id="importantBoardList">
        <div v-for="item in filteredImportantDays" :key="item.id" class="importantBoardRow" :data-important-id="item.id" role="button" tabindex="0" @click="store.openImportantDetails(item.id)" @keydown.enter.prevent="store.openImportantDetails(item.id)">
          <span class="importantDot" :style="{ background: item.color }"></span>
          <span class="importantBoardMain"><b>{{ item.icon || '★' }} {{ item.title }}</b><small>{{ importantScheduleLabel(item) }} · {{ item.repeatMode }}</small><span class="importantBoardMetaLine">{{ [item.place, item.category].filter(Boolean).join(' · ') }}</span></span>
          <span class="importantBoardNext"><small>Дата</small><b class="mono">{{ item.date }}</b></span>
          <span class="importantBoardActions"><button type="button" :data-important-details="item.id" @click.stop="store.openImportantDetails(item.id)">Подробнее</button><button type="button" :data-important-edit="item.id" @click.stop="edit(item.id)">ред.</button></span>
        </div>
        <div v-if="!filteredImportantDays.length" class="emptyLine">Событий нет.</div>
      </div>
    </div>
  </section>
</template>
