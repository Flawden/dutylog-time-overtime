<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { FRONTEND_ARCHITECTURE, RELEASE_VERSION } from "@/platform/version";
import { navigationItem, type DutyLogRoute } from "./navigation";
import { useShellStore } from "./shellStore";
import AppNavigation from "./AppNavigation.vue";
import AppIcon from "@/shared/ui/AppIcon.vue";
import UiBadge from "@/shared/ui/UiBadge.vue";
import UiButton from "@/shared/ui/UiButton.vue";
import UiCard from "@/shared/ui/UiCard.vue";
import UiModal from "@/shared/overlays/UiModal.vue";
import ToastHost from "@/shared/overlays/ToastHost.vue";
import AbsenceTimeBankWorkspace from "@/features/absence-time-bank/components/AbsenceTimeBankWorkspace.vue";
import CalendarTimelineWorkspace from "@/features/calendar-timeline/components/CalendarTimelineWorkspace.vue";
import ProductivityWorkspace from "@/features/productivity/components/ProductivityWorkspace.vue";
import SettingsWorkspace from "@/features/settings-workspace/components/SettingsWorkspace.vue";
import PayrollWorkspace from "@/features/payroll/components/PayrollWorkspace.vue";
import AdminWorkspace from "@/features/admin/components/AdminWorkspace.vue";
import "@/features/settings-workspace/settings-workspace.css";
import { navigateHashRoute } from "@/platform/router/hashRoute";

const props = defineProps<{ bridge: LegacyBridge }>();
const shell = useShellStore();
const { activeRoute, language, online, modulesLoaded, displayName, initials, secondaryNavigation, availableNavigation } = storeToRefs(shell);

const text = computed(() => language.value === "en" ? {
  product: "Time & Overtime",
  profile: "Open profile",
  online: "Online",
  offline: "Offline",
  loading: "Loading modules",
  moreTitle: "DutyLog sections",
  moreDescription: "The Vue shell owns navigation while product screens are migrated one domain at a time.",
  allSections: "Available sections",
  architecture: "Frontend architecture",
  logout: "Sign out",
  close: "Close",
} : {
  product: "Time & Overtime",
  profile: "Открыть профиль",
  online: "Онлайн",
  offline: "Нет сети",
  loading: "Загрузка модулей",
  moreTitle: "Разделы DutyLog",
  moreDescription: "Vue уже управляет оболочкой и навигацией, а продуктовые экраны переносятся по одному домену.",
  allSections: "Доступные разделы",
  architecture: "Архитектура frontend",
  logout: "Выйти",
  close: "Закрыть",
});

const secondaryItems = computed(() => {
  const routes = secondaryNavigation.value.length ? secondaryNavigation.value : availableNavigation.value;
  return routes.map(navigationItem).filter((item): item is NonNullable<typeof item> => item !== null);
});

function navigate(route: DutyLogRoute): void {
  shell.closeMore();
  navigateHashRoute(route);
}

function openProfile(): void { navigateHashRoute("settings-profile"); }
function logout(): void { props.bridge.logout(); }
</script>

<template>
  <div class="vue-app-shell" data-vue-app-shell>
    <header class="vue-shell-header">
      <button class="vue-shell-brand" type="button" data-vue-shell-brand @click="navigate('today')" aria-label="DutyLog — сегодня">
        <span class="vue-shell-brand__glyph" aria-hidden="true"><AppIcon name="check" /></span>
        <span class="vue-shell-brand__copy"><strong>Duty<span>Log</span></strong><small>{{ text.product }}</small></span>
      </button>
      <div class="vue-shell-header__actions">
        <UiBadge :tone="online ? 'success' : 'warning'">
          <span class="vue-shell-status-dot" aria-hidden="true"></span>
          {{ online ? text.online : text.offline }}
        </UiBadge>
        <UiBadge v-if="!modulesLoaded" tone="neutral">{{ text.loading }}</UiBadge>
        <button class="vue-shell-profile" type="button" data-vue-shell-profile :title="text.profile" :aria-label="text.profile" @click="openProfile">
          <span>{{ initials }}</span><b>{{ displayName }}</b>
        </button>
      </div>
    </header>
    <AppNavigation />
  </div>

  <CalendarTimelineWorkspace :bridge="bridge" />
  <AbsenceTimeBankWorkspace :bridge="bridge" />
  <ProductivityWorkspace :bridge="bridge" />
  <SettingsWorkspace :bridge="bridge" />
  <PayrollWorkspace />
  <AdminWorkspace />

  <UiModal
    :open="shell.moreOpen"
    :title="text.moreTitle"
    :description="text.moreDescription"
    :close-label="text.close"
    @close="shell.closeMore()"
  >
    <div class="vue-shell-more-grid" :aria-label="text.allSections">
      <button
        v-for="item in secondaryItems"
        :key="item.route"
        type="button"
        :class="{ 'is-active': activeRoute === item.route }"
        :aria-current="activeRoute === item.route ? 'page' : false"
        :data-route="item.route"
        @click="navigate(item.route)"
      >
        <AppIcon :name="item.icon" />
        <span>{{ item.labels[language] }}</span>
      </button>
    </div>
    <UiCard class="vue-shell-architecture-card">
      <small>{{ text.architecture }}</small>
      <strong>Vue 3 · TypeScript · {{ FRONTEND_ARCHITECTURE }}</strong>
      <code>DutyLog {{ RELEASE_VERSION }}</code>
    </UiCard>
    <template #footer>
      <UiButton variant="danger" data-vue-shell-logout @click="logout">{{ text.logout }}</UiButton>
      <UiButton variant="secondary" data-vue-shell-close @click="shell.closeMore()">{{ text.close }}</UiButton>
    </template>
  </UiModal>

  <ToastHost />
</template>
