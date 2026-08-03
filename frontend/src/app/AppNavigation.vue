<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { navigationItem, type DutyLogRoute } from "./navigation";
import { useShellStore } from "./shellStore";
import AppIcon from "@/shared/ui/AppIcon.vue";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const { activeRoute, language, primaryNavigation, secondaryNavigation } = storeToRefs(shell);

const items = computed(() => primaryNavigation.value.map(navigationItem).filter((item): item is NonNullable<typeof item> => item !== null));
const moreLabel = computed(() => language.value === "en" ? "More sections" : "Другие разделы");

function navigate(route: DutyLogRoute): void {
  props.bridge.navigate(route);
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
      :aria-current="activeRoute === item.route ? 'page' : undefined"
      :data-route="item.route"
      @click="navigate(item.route)"
    >
      <AppIcon :name="item.icon" />
      <span>{{ item.labels[language] }}</span>
    </button>
    <button
      v-if="secondaryNavigation.length"
      class="vue-shell-nav__item vue-shell-nav__more"
      type="button"
      :aria-label="moreLabel"
      @click="shell.openMore()"
    >
      <AppIcon name="menu"/><span>{{ language === 'en' ? 'More' : 'Ещё' }}</span>
    </button>
  </nav>
</template>
