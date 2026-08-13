<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { navigationItem, type DutyLogRoute } from "./navigation";
import { useShellStore } from "./shellStore";
import AppIcon from "@/shared/ui/AppIcon.vue";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const shell = useShellStore();
const { activeRoute, language, primaryNavigation, secondaryNavigation } = storeToRefs(shell);

const items = computed(() => primaryNavigation.value.map(navigationItem).filter((item): item is NonNullable<typeof item> => item !== null));
const secondaryActive = computed(() => secondaryNavigation.value.includes(activeRoute.value));
const moreLabel = computed(() => language.value === "en" ? "More sections" : "Другие разделы");

function navigate(route: DutyLogRoute): void {
  navigateHashRoute(route);
}
</script>

<template>
  <nav
    class="vue-shell-nav"
    aria-label="Основная навигация"
    data-vue-shell-navigation
    :style="{ '--vue-shell-nav-count': String(items.length + (secondaryNavigation.length ? 1 : 0)) }"
  >
    <button
      v-for="item in items"
      :key="item.route"
      class="vue-shell-nav__item"
      :class="{ 'is-active': activeRoute === item.route }"
      type="button"
      :aria-label="item.labels[language]"
      :aria-current="activeRoute === item.route ? 'page' : false"
      :data-route="item.route"
      @click="navigate(item.route)"
    >
      <AppIcon :name="item.icon" />
      <span class="vue-shell-nav__label">{{ item.labels[language] }}</span>
    </button>
    <button
      v-if="secondaryNavigation.length"
      class="vue-shell-nav__item vue-shell-nav__more"
      :class="{ 'is-active': secondaryActive }"
      type="button"
      data-vue-shell-more
      :aria-label="moreLabel"
      :aria-current="secondaryActive ? 'page' : false"
      @click="shell.openMore()"
    >
      <AppIcon name="menu"/><span class="vue-shell-nav__label">{{ language === 'en' ? 'More' : 'Ещё' }}</span>
    </button>
  </nav>
</template>
