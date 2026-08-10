<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";
import {
  applyPalette,
  applyThemePreset,
  appearanceSwatches,
  customizeTheme,
  customizeWorkspace,
  decorations,
  description,
  label,
  layouts,
  moveStudioItem,
  normalizeAppearance,
  palettes,
  screens,
  setWorkspace,
  studioNavigationOrder,
  studioWidgetOrder,
  themePresets,
  toggleStudioItem,
  widgets,
  workspaces,
  workspaceDefinition,
  type AppearancePreferences,
  type DecorationId,
  type LayoutId,
  type PaletteId,
  type ThemeId,
  type WorkspaceId,
} from "../types/model";
import SettingsCard from "./SettingsCard.vue";

const props = defineProps<{ bridge: LegacyBridge; active: boolean }>();
const emit = defineEmits<{ open: [section: string] }>();
const store = useSettingsWorkspaceStore();
const { appearance, modules } = storeToRefs(store);
const studioMessage = ref("");

const lang = computed(() => store.language);
const text = computed(() => lang.value === "en" ? {
  eyebrow: "Personalization", title: "Appearance", hint: "Safe Theme Builder: presets, color pickers, lists and sliders only. Custom CSS is never stored.",
  single: "Single DutyLog interface", notice: "Classic has been retired: all workspaces, layouts, themes and palettes run on one UI Core. Recovery uses tested Git/Docker releases, not a second in-app shell.",
  platform: "UI Core and workspace", platformHint: "One interface foundation with independent workspaces, layouts, themes and palettes.", workspace: "Workspace", layout: "Layout", palette: "Color palette", decoration: "Decoration", secondary: "Secondary accent", calendarDensity: "Calendar density", calendarLayers: "Schedule layers", comfortable: "Comfortable", compact: "Compact", pills: "Pills", dots: "Dots", themeColors: "Theme colors", customized: "Customized", presetPalette: "Preset palette", restoreTheme: "Restore theme colors", hiddenRoutes: "Sections outside primary navigation", allPrimary: "All enabled sections are already in the main navigation.", studio: "Workspace Studio", studioHint: "Order and visibility are saved automatically and never delete data.", customize: "Customize a copy", resetOrder: "Reset order", navigation: "Primary navigation", widgets: "Today screen cards", preset: "Preset", readyTheme: "Theme preset", baseMode: "Base mode", accent: "Accent", system: "system", dark: "dark", light: "light", fine: "Fine tuning", appBg: "App background", panelBg: "Cards", panelAlt: "Inner blocks", text: "Primary text", muted: "Secondary text", border: "Borders", button: "Button style", card: "Card style", shadow: "Shadows", density: "Density", radius: "Card radius", save: "Save now", reset: "Reset appearance", preview: "Live preview", variants: "Button variants", primary: "Primary", normal: "Secondary", outline: "Outline", ghost: "Ghost", danger: "Danger", link: "Link", more: "More actions", maxNav: "Primary navigation can contain at most five sections.",
} : {
  eyebrow: "Персонализация", title: "Внешний вид", hint: "Безопасный Theme Builder: только пресеты, color picker, списки и ползунки. Пользовательский CSS не поддерживается и не хранится.",
  single: "Единый интерфейс DutyLog", notice: "Classic завершён: все окружения, компоновки, темы и палитры работают поверх одного UI Core. Для аварийного отката используются проверенные Git/Docker-релизы, а не второй интерфейс внутри приложения.",
  platform: "UI Core и рабочее окружение", platformHint: "Одна база интерфейса, независимые окружения, компоновки, темы и палитры.", workspace: "Рабочее окружение", layout: "Компоновка", palette: "Цветовая палитра", decoration: "Декорация", secondary: "Дополнительный акцент", calendarDensity: "Плотность календаря", calendarLayers: "Слои графиков", comfortable: "Комфортная", compact: "Компактная", pills: "Полосы", dots: "Точки", themeColors: "Цвета темы", customized: "Изменено пользователем", presetPalette: "Готовая палитра", restoreTheme: "Вернуть цвета темы", hiddenRoutes: "Разделы вне основной навигации", allPrimary: "Все включённые разделы уже находятся в основной навигации.", studio: "Студия рабочего пространства", studioHint: "Порядок и видимость сохраняются автоматически и не удаляют данные.", customize: "Настроить копию", resetOrder: "Сбросить порядок", navigation: "Основная навигация", widgets: "Карточки экрана «Сегодня»", preset: "Пресет", readyTheme: "Готовая тема", baseMode: "Базовый режим", accent: "Акцент", system: "как в системе", dark: "тёмная", light: "светлая", fine: "Точная настройка", appBg: "Фон приложения", panelBg: "Карточки", panelAlt: "Внутренние блоки", text: "Основной текст", muted: "Вторичный текст", border: "Границы", button: "Стиль кнопок", card: "Стиль карточек", shadow: "Тени", density: "Плотность", radius: "Скругление карточек", save: "Сохранить сейчас", reset: "Сбросить оформление", preview: "Live preview", variants: "Варианты кнопок", primary: "Главная кнопка", normal: "Обычная", outline: "Контурная", ghost: "Призрачная", danger: "Опасная", link: "Ссылка", more: "Ещё действия", maxNav: "В основной навигации можно оставить не более пяти разделов.",
});

const config = computed(() => appearance.value.themeConfig);
const workspace = computed(() => workspaceDefinition(config.value));
const navOrder = computed(() => studioNavigationOrder(config.value));
const widgetOrder = computed(() => studioWidgetOrder(config.value));
const visibleNavigation = computed(() => new Set(workspace.value.navigation));
const visibleWidgets = computed(() => new Set(workspace.value.todayWidgets));
const workspaceEntry = computed(() => workspaces[config.value.workspaceId]);
const layoutEntry = computed(() => layouts[config.value.layoutId]);
const paletteState = computed(() => config.value.paletteId === "theme" ? text.value.themeColors : config.value.paletteId === "custom" ? text.value.customized : `${text.value.presetPalette}: ${label(palettes[config.value.paletteId], lang.value)}`);
const routeLinks = computed(() => Object.values(screens).filter(screen => {
  if (["today", "settings"].includes(screen.id)) return false;
  if (workspace.value.navigation.includes(screen.id)) return false;
  if (screen.module === "core" || screen.module === "calendar") return true;
  const module = modules.value.find(item => item.key === screen.module);
  return module?.enabled ?? false;
}));

function publish(next: AppearancePreferences, delay = 650): void {
  store.previewAppearance(next, props.bridge);
  store.scheduleAppearanceSave(props.bridge, undefined, delay);
}
function patchConfig(patch: Partial<AppearancePreferences["themeConfig"]>): void {
  publish(normalizeAppearance({ ...appearance.value, themeConfig: { ...config.value, ...patch } }));
}
function selectTheme(value: string): void { publish(applyThemePreset(appearance.value, value as ThemeId)); }
function setBaseTheme(value: string): void { publish(normalizeAppearance({ ...appearance.value, themePreference: value })); }
function setAccent(value: string): void { publish(normalizeAppearance({ ...appearance.value, accentColor: value, themeConfig: { ...config.value, paletteId: "custom" } })); }
function setSecondary(value: string): void { publish(normalizeAppearance({ ...appearance.value, themeConfig: { ...config.value, paletteId: "custom", accentSecondary: value } })); }
function selectPalette(value: string): void { publish(applyPalette(appearance.value, value as PaletteId)); }
function selectWorkspace(value: string): void { publish(setWorkspace(appearance.value, value as WorkspaceId)); }
function customize(): void { publish(customizeWorkspace(appearance.value), 0); studioMessage.value = ""; }
function resetStudio(): void {
  const source = workspaces[config.value.workspaceId === "custom" ? "shift-worker" : config.value.workspaceId];
  publish(normalizeAppearance({ ...appearance.value, themeConfig: { ...config.value, workspaceId: "custom", navigationOrder: [...source.navigation, ...Object.keys(screens).filter(id => !source.navigation.includes(id) && id !== "admin")], navigationVisible: [...source.navigation], todayWidgets: [...source.todayWidgets] } }), 0);
}
function setThemeField(key: keyof AppearancePreferences["themeConfig"], value: unknown): void {
  publish(customizeTheme(appearance.value, { [key]: value } as Partial<AppearancePreferences["themeConfig"]>));
}
function toggleStudio(kind: "navigation" | "widget", id: string, checked: boolean): void {
  const result = toggleStudioItem(appearance.value, kind, id, checked);
  if (result.rejected) { studioMessage.value = text.value.maxNav; return; }
  studioMessage.value = ""; publish(result.appearance);
}
function moveStudio(kind: "navigation" | "widget", id: string, direction: -1 | 1): void { publish(moveStudioItem(appearance.value, kind, id, direction)); }
function screenEntry(id: string) {
  const entry = screens[id];
  if (!entry) throw new Error(`Unknown workspace navigation screen: ${id}`);
  return entry;
}
function widgetEntry(id: string) {
  const entry = widgets[id];
  if (!entry) throw new Error(`Unknown Today widget: ${id}`);
  return entry;
}
function restoreThemePalette(): void { publish(applyPalette(appearance.value, "theme"), 0); }
function resetAppearance(): void { publish(normalizeAppearance({}), 0); }
</script>

<template>
  <SettingsCard id="appearanceCard" section="appearance" :active="active" :eyebrow="text.eyebrow" :title="text.title" :hint="text.hint" @open="emit('open', $event)">
    <template #status>
      <div class="status statusThemeSummary" id="appearancePreview">
        <span class="statusChip statusChipPrimary">{{ themePresets[appearance.themePreset].label }}</span>
        <span class="statusChip">{{ label(workspaceEntry, lang) }}</span>
        <span class="statusChip">{{ label(layoutEntry, lang) }}</span>
        <span class="statusChip">{{ paletteState }}</span>
      </div>
    </template>

    <div class="profileSub">{{ text.single }}</div>
    <div class="settingsHint" id="singleShellNotice">{{ text.notice }}</div>

    <div class="uiPlatformPanel" aria-labelledby="uiPlatformTitle">
      <div class="uiPlatformHead">
        <div>
          <div class="profileSub" id="uiPlatformTitle">{{ text.platform }}</div>
          <div class="settingsHint" id="uiPlatformDescription">{{ description(workspaceEntry, lang) }} {{ description(layoutEntry, lang) }}</div>
        </div>
        <div class="uiPlatformStatus" id="uiPlatformStatus"><b>UI Core v2</b><span>{{ label(workspaceEntry, lang) }} · {{ label(layoutEntry, lang) }}</span></div>
      </div>
      <div class="appearanceGrid uiPlatformGrid">
        <label>{{ text.workspace }}
          <select id="uiWorkspace" :value="config.workspaceId" @change="selectWorkspace(($event.target as HTMLSelectElement).value)">
            <option v-for="entry in Object.values(workspaces)" :key="entry.id" :value="entry.id">{{ label(entry, lang) }}</option>
          </select>
        </label>
        <label>{{ text.layout }}
          <select id="uiLayout" :value="config.layoutId" @change="patchConfig({ layoutId: ($event.target as HTMLSelectElement).value as LayoutId })">
            <option v-for="entry in Object.values(layouts)" :key="entry.id" :value="entry.id">{{ label(entry, lang) }}</option>
          </select>
        </label>
        <label>{{ text.palette }}
          <select id="uiPalette" :value="config.paletteId" @change="selectPalette(($event.target as HTMLSelectElement).value)">
            <option v-for="entry in Object.values(palettes)" :key="entry.id" :value="entry.id">{{ label(entry, lang) }}</option>
          </select>
        </label>
        <label>{{ text.decoration }}
          <select id="uiDecoration" :value="config.decorationId" @change="patchConfig({ decorationId: ($event.target as HTMLSelectElement).value as DecorationId })">
            <option v-for="entry in Object.values(decorations)" :key="entry.id" :value="entry.id">{{ label(entry, lang) }}</option>
          </select>
        </label>
        <label>{{ text.secondary }}<input id="uiAccentSecondary" type="color" :value="config.accentSecondary" @input="setSecondary(($event.target as HTMLInputElement).value)"/></label>
        <label>{{ text.calendarDensity }}
          <select id="uiCalendarDensity" :value="config.calendarDensity" @change="patchConfig({ calendarDensity: ($event.target as HTMLSelectElement).value as 'comfortable' | 'compact' })"><option value="comfortable">{{ text.comfortable }}</option><option value="compact">{{ text.compact }}</option></select>
        </label>
        <label>{{ text.calendarLayers }}
          <select id="uiCalendarLayerStyle" :value="config.calendarLayerStyle" @change="patchConfig({ calendarLayerStyle: ($event.target as HTMLSelectElement).value as 'pills' | 'dots' })"><option value="pills">{{ text.pills }}</option><option value="dots">{{ text.dots }}</option></select>
        </label>
      </div>
      <div class="paletteControlBar">
        <div class="paletteModeStatus" id="uiPaletteState" :data-palette-mode="config.paletteId === 'theme' ? 'theme' : config.paletteId === 'custom' ? 'custom' : 'preset'"><span class="paletteModeDot" aria-hidden="true"></span><span>{{ paletteState }}</span></div>
        <button class="buttonOutline" id="paletteThemeReset" type="button" @click="restoreThemePalette">{{ text.restoreTheme }}</button>
      </div>
      <div class="workspaceRouteBlock"><span>{{ text.hiddenRoutes }}</span><div class="workspaceRouteLinks" id="workspaceRouteLinks"><a v-for="route in routeLinks" :key="route.id" :href="`#${route.id}`" :data-workspace-route="route.id">{{ label(route, lang) }}</a><span v-if="!routeLinks.length">{{ text.allPrimary }}</span></div></div>
      <div class="workspaceStudio" id="workspaceStudio">
        <div class="workspaceStudioHead"><div><div class="profileSub">{{ text.studio }}</div><div class="settingsHint">{{ text.studioHint }}</div></div><div class="workspaceStudioActions"><button class="buttonOutline" id="workspaceCustomize" type="button" :hidden="config.workspaceId === 'custom'" @click="customize">{{ text.customize }}</button><button class="buttonGhost" id="workspaceStudioReset" type="button" @click="resetStudio">{{ text.resetOrder }}</button></div></div>
        <div class="workspaceStudioGrid">
          <section><div class="workspaceStudioTitle" id="workspaceNavigationTitle">{{ text.navigation }}</div><div class="workspaceStudioList" id="workspaceNavigationList">
            <div v-for="(id,index) in navOrder" :key="id" class="workspaceStudioRow" data-studio-kind="navigation" :data-studio-id="id">
              <label><input type="checkbox" data-studio-visible :checked="visibleNavigation.has(id)" :disabled="config.workspaceId !== 'custom' || screenEntry(id).required === true" @change="toggleStudio('navigation', id, ($event.target as HTMLInputElement).checked)"/><span>{{ label(screenEntry(id), lang) }}</span></label>
              <div class="workspaceStudioMove"><button type="button" class="buttonIcon" data-studio-move="-1" :disabled="config.workspaceId !== 'custom' || index === 0" @click="moveStudio('navigation', id, -1)">↑</button><button type="button" class="buttonIcon" data-studio-move="1" :disabled="config.workspaceId !== 'custom' || index === navOrder.length - 1" @click="moveStudio('navigation', id, 1)">↓</button></div>
            </div>
          </div></section>
          <section><div class="workspaceStudioTitle" id="todayWidgetTitle">{{ text.widgets }}</div><div class="workspaceStudioList" id="todayWidgetList">
            <div v-for="(id,index) in widgetOrder" :key="id" class="workspaceStudioRow" data-studio-kind="widget" :data-studio-id="id">
              <label><input type="checkbox" data-studio-visible :checked="visibleWidgets.has(id)" :disabled="widgetEntry(id).required === true" @change="toggleStudio('widget', id, ($event.target as HTMLInputElement).checked)"/><span>{{ label(widgetEntry(id), lang) }}</span></label>
              <div class="workspaceStudioMove"><button type="button" class="buttonIcon" data-studio-move="-1" :disabled="index === 0" @click="moveStudio('widget', id, -1)">↑</button><button type="button" class="buttonIcon" data-studio-move="1" :disabled="index === widgetOrder.length - 1" @click="moveStudio('widget', id, 1)">↓</button></div>
            </div>
          </div></section>
        </div>
        <div class="profileMsg" id="workspaceStudioMessage">{{ studioMessage }}</div>
      </div>
    </div>

    <div class="themeBuilder">
      <div class="themeBuilderMain">
        <div class="profileSub">{{ text.preset }}</div>
        <div class="appearanceGrid">
          <label>{{ text.readyTheme }}<select id="appearancePreset" :value="appearance.themePreset" @change="selectTheme(($event.target as HTMLSelectElement).value)"><option v-for="(preset,id) in themePresets" :key="id" :value="id">{{ preset.label }}</option></select></label>
          <label>{{ text.baseMode }}<select id="appearanceTheme" :value="appearance.themePreference" @change="setBaseTheme(($event.target as HTMLSelectElement).value)"><option value="system">{{ text.system }}</option><option value="dark">{{ text.dark }}</option><option value="light">{{ text.light }}</option></select></label>
          <label>{{ text.accent }}<input id="appearanceAccent" type="color" :value="appearance.accentColor" @input="setAccent(($event.target as HTMLInputElement).value)"/></label>
        </div>
        <div class="appearanceAccentRow" id="appearanceAccentRow"><button v-for="color in appearanceSwatches" :key="color" type="button" class="accentChoice" :class="{ on: color === appearance.accentColor }" :style="{ background: color }" :title="color" @click="setAccent(color)"></button></div>
        <div class="profileSub">{{ text.fine }}</div>
        <div class="appearanceGrid themeColorGrid">
          <label>{{ text.appBg }} <input id="themeAppBg" type="color" :value="config.appBg || '#14171C'" @input="setThemeField('appBg', ($event.target as HTMLInputElement).value)"/></label>
          <label>{{ text.panelBg }} <input id="themePanelBg" type="color" :value="config.panelBg || '#1C2027'" @input="setThemeField('panelBg', ($event.target as HTMLInputElement).value)"/></label>
          <label>{{ text.panelAlt }} <input id="themePanelAltBg" type="color" :value="config.panelAltBg || '#22262E'" @input="setThemeField('panelAltBg', ($event.target as HTMLInputElement).value)"/></label>
          <label>{{ text.text }} <input id="themeTextColor" type="color" :value="config.textColor || '#E8EAED'" @input="setThemeField('textColor', ($event.target as HTMLInputElement).value)"/></label>
          <label>{{ text.muted }} <input id="themeMutedColor" type="color" :value="config.mutedColor || '#8B929E'" @input="setThemeField('mutedColor', ($event.target as HTMLInputElement).value)"/></label>
          <label>{{ text.border }} <input id="themeBorderColor" type="color" :value="config.borderColor || '#2E333C'" @input="setThemeField('borderColor', ($event.target as HTMLInputElement).value)"/></label>
        </div>
        <div class="appearanceGrid themeShapeGrid">
          <label>{{ text.button }}<select id="themeButtonStyle" :value="config.buttonStyle" @change="setThemeField('buttonStyle', ($event.target as HTMLSelectElement).value)"><option value="solid">solid</option><option value="soft">soft</option><option value="outline">outline</option><option value="ghost">ghost</option></select></label>
          <label>{{ text.card }}<select id="themeCardStyle" :value="config.cardStyle" @change="setThemeField('cardStyle', ($event.target as HTMLSelectElement).value)"><option value="default">default</option><option value="flat">flat</option><option value="soft">soft</option><option value="contrast">contrast</option><option value="warm">warm</option></select></label>
          <label>{{ text.shadow }}<select id="themeShadowLevel" :value="config.shadowLevel" @change="setThemeField('shadowLevel', ($event.target as HTMLSelectElement).value)"><option value="none">none</option><option value="low">low</option><option value="soft">soft</option><option value="medium">medium</option><option value="strong">strong</option></select></label>
          <label>{{ text.density }}<select id="themeDensity" :value="config.density" @change="setThemeField('density', ($event.target as HTMLSelectElement).value)"><option value="compact">compact</option><option value="comfortable">comfortable</option><option value="spacious">spacious</option></select></label>
          <label>{{ text.radius }} <span class="rangeValue" id="themeCardRadiusValue">{{ config.cardRadius }}px</span><input id="themeCardRadius" type="range" min="6" max="28" step="1" :value="config.cardRadius" @input="setThemeField('cardRadius', Number(($event.target as HTMLInputElement).value))"/></label>
        </div>
      </div>
      <div class="themePreviewBox" aria-label="Theme preview"><div class="themePreviewTitle">{{ text.preview }}</div><div class="themePreviewCalendar"><div class="themePreviewCell today"><b>7</b><span>🔥 Day</span></div><div class="themePreviewCell"><b>8</b><span>🌙 Night</span></div><div class="themePreviewCell muted"><b>9</b><span>Off</span></div></div><div class="themePreviewCard"><div class="eyebrow">Card</div><b>Selected day</b><p>Theme tokens, borders, text, radius and shadows.</p><div class="themePreviewVariantTitle">{{ text.variants }}</div><div class="themePreviewActions buttonVariantPreview" id="buttonVariantPreview"><button class="primary" data-preview-variant="primary" type="button">{{ text.primary }}</button><button class="buttonSecondary" data-preview-variant="secondary" type="button">{{ text.normal }}</button><button class="buttonOutline" data-preview-variant="outline" type="button">{{ text.outline }}</button><button class="buttonGhost" data-preview-variant="ghost" type="button">{{ text.ghost }}</button><button class="buttonDanger" data-preview-variant="danger" type="button">{{ text.danger }}</button><button class="buttonLink" data-preview-variant="link" type="button">{{ text.link }}</button><button class="buttonIcon" data-preview-variant="icon" type="button" :aria-label="text.more" :title="text.more">⋯</button></div></div></div>
    </div>
    <div class="appearanceActions"><button class="primary" id="appearanceSave" type="button" @click="store.persistAppearanceNow(bridge)">{{ text.save }}</button><button id="appearanceReset" type="button" @click="resetAppearance">{{ text.reset }}</button></div>
    <div class="profileMsg" id="appearanceMsg" :class="{ ok: store.appearanceMessageOk }">{{ store.appearanceMessage }}</div>
  </SettingsCard>
</template>
