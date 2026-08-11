export type SettingsLanguage = "ru" | "en";
export type ThemePreference = "system" | "light" | "dark";
export type WorkspaceId = "shift-worker" | "planner" | "minimal" | "custom";
export type LayoutId = "dashboard" | "compact" | "focus" | "sidebar" | "mobile-flow";
export type ThemeId = "default" | "custom" | "midnight" | "oled" | "forest" | "sunset" | "industrial" | "softPurple";
export type PaletteId = "theme" | "gold-teal" | "teal-gold" | "violet" | "ember" | "custom";
export type DecorationId = "none" | "grid";
export type CalendarDensity = "comfortable" | "compact";
export type CalendarLayerStyle = "pills" | "dots";

export interface ThemeConfig {
  appBg: string;
  panelBg: string;
  panelAltBg: string;
  textColor: string;
  mutedColor: string;
  borderColor: string;
  buttonStyle: "solid" | "soft" | "outline" | "ghost";
  cardStyle: "default" | "flat" | "soft" | "contrast" | "warm";
  cardRadius: number;
  shadowLevel: "none" | "low" | "soft" | "medium" | "strong";
  density: "compact" | "comfortable" | "spacious";
  uiContract: 2;
  workspaceId: WorkspaceId;
  layoutId: LayoutId;
  themeId: ThemeId;
  paletteId: PaletteId;
  decorationId: DecorationId;
  accentSecondary: string;
  todayWidgets: string[];
  navigationOrder: string[];
  navigationVisible: string[];
  calendarDensity: CalendarDensity;
  calendarLayerStyle: CalendarLayerStyle;
}

export interface AppearancePreferences {
  themePreference: ThemePreference;
  accentColor: string;
  themePreset: ThemeId;
  themeConfig: ThemeConfig;
}

export interface CatalogEntry {
  id: string;
  labelRu: string;
  labelEn: string;
  descriptionRu?: string;
  descriptionEn?: string;
}

export interface WorkspaceEntry extends CatalogEntry {
  id: WorkspaceId;
  navigation: string[];
  todayWidgets: string[];
}

export interface PaletteEntry extends CatalogEntry {
  id: PaletteId;
  accent: string | null;
  secondary: string | null;
}

export interface ScreenEntry extends CatalogEntry {
  module: string;
  required?: boolean;
}

export interface WidgetEntry extends CatalogEntry {
  module: string;
  required?: boolean;
}

const NAVIGATION_UNIVERSE = Object.freeze(["today", "calendar", "vacation", "overtime", "payroll", "tasks", "important", "settings"]);
const WIDGET_UNIVERSE = Object.freeze(["shift", "overtime", "tasks", "important"]);
const APPEARANCE_SWATCHES = Object.freeze(["#F5B841", "#E0653A", "#C97BB8", "#7B8CE0", "#4FA3A5", "#6FBF73", "#9B7BE0", "#E05780"]);

export const appearanceSwatches = APPEARANCE_SWATCHES;

export const workspaces: Record<WorkspaceId, WorkspaceEntry> = Object.freeze({
  "shift-worker": Object.freeze({ id: "shift-worker", labelRu: "Работник по сменам", labelEn: "Shift Worker", descriptionRu: "Смены, календарь и переработки находятся в первом плане.", descriptionEn: "Shifts, calendar and overtime stay in the foreground.", navigation: ["today", "calendar", "vacation", "overtime", "settings"], todayWidgets: ["shift", "overtime", "tasks", "important"] }),
  planner: Object.freeze({ id: "planner", labelRu: "Планировщик", labelEn: "Planner", descriptionRu: "Задачи и важные даты поднимаются выше рабочего графика.", descriptionEn: "Tasks and important dates move ahead of the work schedule.", navigation: ["today", "tasks", "calendar", "vacation", "settings"], todayWidgets: ["tasks", "important", "shift", "overtime"] }),
  minimal: Object.freeze({ id: "minimal", labelRu: "Минимум", labelEn: "Minimal", descriptionRu: "Только сегодня, календарь и быстрый доступ к остальным разделам.", descriptionEn: "Today and calendar first, with the remaining sections under More.", navigation: ["today", "calendar", "settings"], todayWidgets: ["shift", "tasks"] }),
  custom: Object.freeze({ id: "custom", labelRu: "Своя рабочая область", labelEn: "Custom workspace", descriptionRu: "Собственный порядок навигации и карточек без удаления данных.", descriptionEn: "Your own navigation and card order without deleting data.", navigation: [], todayWidgets: [] }),
});

export const layouts: Record<LayoutId, CatalogEntry & { id: LayoutId }> = Object.freeze({
  dashboard: Object.freeze({ id: "dashboard", labelRu: "Dashboard", labelEn: "Dashboard", descriptionRu: "Сбалансированная сетка карточек для телефона и компьютера.", descriptionEn: "Balanced card grid for phone and desktop." }),
  compact: Object.freeze({ id: "compact", labelRu: "Компактная", labelEn: "Compact", descriptionRu: "Больше данных на экране, меньше отступов и крупнее рабочая область.", descriptionEn: "More data on screen with tighter spacing and a wider canvas." }),
  focus: Object.freeze({ id: "focus", labelRu: "Фокус", labelEn: "Focus", descriptionRu: "Одна основная колонка без визуальной суеты.", descriptionEn: "One primary column with fewer competing surfaces." }),
  sidebar: Object.freeze({ id: "sidebar", labelRu: "Боковая панель", labelEn: "Sidebar", descriptionRu: "На широком экране навигация закрепляется слева, а контент получает больше места.", descriptionEn: "On wide screens navigation stays on the left and content gets more room." }),
  "mobile-flow": Object.freeze({ id: "mobile-flow", labelRu: "Мобильный поток", labelEn: "Mobile Flow", descriptionRu: "Узкая последовательная колонка даже на большом экране.", descriptionEn: "A narrow sequential column even on a large screen." }),
});

export const palettes: Record<PaletteId, PaletteEntry> = Object.freeze({
  theme: Object.freeze({ id: "theme", labelRu: "Палитра темы", labelEn: "Theme palette", accent: null, secondary: null }),
  "gold-teal": Object.freeze({ id: "gold-teal", labelRu: "Золото + бирюза", labelEn: "Gold + teal", accent: "#D4B83F", secondary: "#14CDB4" }),
  "teal-gold": Object.freeze({ id: "teal-gold", labelRu: "Бирюза + золото", labelEn: "Teal + gold", accent: "#14CDB4", secondary: "#D4B83F" }),
  violet: Object.freeze({ id: "violet", labelRu: "Фиолетовая", labelEn: "Violet", accent: "#9B7BE0", secondary: "#58C6C8" }),
  ember: Object.freeze({ id: "ember", labelRu: "Тёплый уголь", labelEn: "Ember", accent: "#E0653A", secondary: "#F5B841" }),
  custom: Object.freeze({ id: "custom", labelRu: "Своя палитра", labelEn: "Custom palette", accent: null, secondary: null }),
});

export const decorations: Record<DecorationId, CatalogEntry & { id: DecorationId }> = Object.freeze({
  none: Object.freeze({ id: "none", labelRu: "Без декораций", labelEn: "No decorations" }),
  grid: Object.freeze({ id: "grid", labelRu: "Спокойная сетка", labelEn: "Calm grid" }),
});

export const screens: Record<string, ScreenEntry> = Object.freeze({
  today: Object.freeze({ id: "today", module: "core", labelRu: "Сегодня", labelEn: "Today", required: true }),
  calendar: Object.freeze({ id: "calendar", module: "calendar", labelRu: "Календарь", labelEn: "Calendar" }),
  vacation: Object.freeze({ id: "vacation", module: "vacation", labelRu: "Отпуск", labelEn: "Vacation" }),
  overtime: Object.freeze({ id: "overtime", module: "overtime", labelRu: "Переработки", labelEn: "Overtime" }),
  payroll: Object.freeze({ id: "payroll", module: "payroll", labelRu: "Зарплата", labelEn: "Payroll" }),
  tasks: Object.freeze({ id: "tasks", module: "tasks", labelRu: "Задачи", labelEn: "Tasks" }),
  important: Object.freeze({ id: "important", module: "important_dates", labelRu: "Важное", labelEn: "Important" }),
  settings: Object.freeze({ id: "settings", module: "core", labelRu: "Настройки", labelEn: "Settings", required: true }),
});

export const widgets: Record<string, WidgetEntry> = Object.freeze({
  shift: Object.freeze({ id: "shift", module: "shifts", required: true, labelRu: "Смена", labelEn: "Shift" }),
  overtime: Object.freeze({ id: "overtime", module: "overtime", labelRu: "Переработки", labelEn: "Overtime" }),
  tasks: Object.freeze({ id: "tasks", module: "tasks", labelRu: "Задачи", labelEn: "Tasks" }),
  important: Object.freeze({ id: "important", module: "important_dates", labelRu: "Ближайшее", labelEn: "Upcoming" }),
});

interface ThemePreset {
  label: string;
  themePreference: ThemePreference;
  accentColor: string;
  themeConfig: Partial<ThemeConfig>;
}

function freezeThemePreset(preset: ThemePreset): Readonly<ThemePreset> { return Object.freeze(preset); }

export const themePresets: Readonly<Record<ThemeId, Readonly<ThemePreset>>> = Object.freeze({
  default: freezeThemePreset({ label: "DutyLog Default", themePreference: "system", accentColor: "#F5B841", themeConfig: { appBg: "", panelBg: "", panelAltBg: "", textColor: "", mutedColor: "", borderColor: "", buttonStyle: "solid", cardStyle: "default", cardRadius: 14, shadowLevel: "medium", density: "comfortable", accentSecondary: "#14CDB4" } }),
  custom: freezeThemePreset({ label: "Custom", themePreference: "system", accentColor: "#F5B841", themeConfig: { appBg: "", panelBg: "", panelAltBg: "", textColor: "", mutedColor: "", borderColor: "", buttonStyle: "solid", cardStyle: "default", cardRadius: 14, shadowLevel: "medium", density: "comfortable", accentSecondary: "#14CDB4" } }),
  midnight: freezeThemePreset({ label: "Midnight", themePreference: "dark", accentColor: "#7B8CE0", themeConfig: { appBg: "#0F1220", panelBg: "#181C2B", panelAltBg: "#20263A", textColor: "#EEF2FF", mutedColor: "#A7B0C9", borderColor: "#2D3550", buttonStyle: "soft", cardStyle: "contrast", cardRadius: 16, shadowLevel: "medium", density: "comfortable", accentSecondary: "#58C6C8" } }),
  oled: freezeThemePreset({ label: "OLED Black", themePreference: "dark", accentColor: "#00D1B2", themeConfig: { appBg: "#000000", panelBg: "#080A0D", panelAltBg: "#11151A", textColor: "#F2F5F7", mutedColor: "#9AA4AE", borderColor: "#20262E", buttonStyle: "solid", cardStyle: "flat", cardRadius: 12, shadowLevel: "none", density: "compact", accentSecondary: "#F5B841" } }),
  forest: freezeThemePreset({ label: "Forest", themePreference: "dark", accentColor: "#6FBF73", themeConfig: { appBg: "#101812", panelBg: "#182219", panelAltBg: "#203020", textColor: "#EAF4EA", mutedColor: "#9CAF9E", borderColor: "#314335", buttonStyle: "soft", cardStyle: "default", cardRadius: 18, shadowLevel: "soft", density: "comfortable", accentSecondary: "#A7C957" } }),
  sunset: freezeThemePreset({ label: "Sunset", themePreference: "dark", accentColor: "#E0653A", themeConfig: { appBg: "#1C1413", panelBg: "#2A1B19", panelAltBg: "#35231F", textColor: "#FFF0E8", mutedColor: "#C9A397", borderColor: "#4A302A", buttonStyle: "solid", cardStyle: "warm", cardRadius: 18, shadowLevel: "medium", density: "comfortable", accentSecondary: "#F5B841" } }),
  industrial: freezeThemePreset({ label: "Industrial", themePreference: "dark", accentColor: "#B5A642", themeConfig: { appBg: "#121417", panelBg: "#1B1F24", panelAltBg: "#242A31", textColor: "#ECEFF3", mutedColor: "#A0A8B2", borderColor: "#343C46", buttonStyle: "outline", cardStyle: "contrast", cardRadius: 8, shadowLevel: "low", density: "compact", accentSecondary: "#58C6C8" } }),
  softPurple: freezeThemePreset({ label: "Soft Purple", themePreference: "light", accentColor: "#9B7BE0", themeConfig: { appBg: "#F7F3FF", panelBg: "#FFFFFF", panelAltBg: "#EFE7FF", textColor: "#231B33", mutedColor: "#685B79", borderColor: "#D8C9F5", buttonStyle: "soft", cardStyle: "soft", cardRadius: 20, shadowLevel: "soft", density: "comfortable", accentSecondary: "#58C6C8" } }),
});

const DEFAULT_CONFIG: ThemeConfig = {
  appBg: "", panelBg: "", panelAltBg: "", textColor: "", mutedColor: "", borderColor: "",
  buttonStyle: "solid", cardStyle: "default", cardRadius: 14, shadowLevel: "medium", density: "comfortable",
  uiContract: 2, workspaceId: "shift-worker", layoutId: "dashboard", themeId: "default", paletteId: "theme",
  decorationId: "none", accentSecondary: "#14CDB4", todayWidgets: [], navigationOrder: [...NAVIGATION_UNIVERSE],
  navigationVisible: ["today", "calendar", "vacation", "overtime", "settings"], calendarDensity: "comfortable", calendarLayerStyle: "pills",
};

export const DEFAULT_APPEARANCE: AppearancePreferences = Object.freeze({
  themePreference: "system", accentColor: "#F5B841", themePreset: "default", themeConfig: Object.freeze({ ...DEFAULT_CONFIG }),
}) as AppearancePreferences;

const isHex = (value: unknown): value is string => /^#[0-9a-fA-F]{6}$/.test(String(value ?? ""));
const safeEnum = <T extends string>(value: unknown, values: readonly T[], fallback: T): T => values.includes(String(value) as T) ? String(value) as T : fallback;
const completeOrder = (value: unknown, universe: readonly string[]): string[] => {
  const raw = Array.isArray(value) ? value.map(String).filter(id => universe.includes(id)) : [];
  return [...new Set([...raw, ...universe])];
};
const record = (value: unknown): Record<string, unknown> => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};

export function label(entry: CatalogEntry, language: SettingsLanguage): string { return language === "en" ? entry.labelEn : entry.labelRu; }
export function description(entry: CatalogEntry, language: SettingsLanguage): string { return language === "en" ? entry.descriptionEn ?? "" : entry.descriptionRu ?? ""; }

export function normalizeAppearance(value: unknown): AppearancePreferences {
  const raw = record(value);
  const rawConfig = record(raw.themeConfig);
  const themePreset = safeEnum(raw.themePreset, Object.keys(themePresets) as ThemeId[], "default");
  const config: ThemeConfig = {
    appBg: isHex(rawConfig.appBg) ? rawConfig.appBg.toUpperCase() : "",
    panelBg: isHex(rawConfig.panelBg) ? rawConfig.panelBg.toUpperCase() : "",
    panelAltBg: isHex(rawConfig.panelAltBg) ? rawConfig.panelAltBg.toUpperCase() : "",
    textColor: isHex(rawConfig.textColor) ? rawConfig.textColor.toUpperCase() : "",
    mutedColor: isHex(rawConfig.mutedColor) ? rawConfig.mutedColor.toUpperCase() : "",
    borderColor: isHex(rawConfig.borderColor) ? rawConfig.borderColor.toUpperCase() : "",
    buttonStyle: safeEnum(rawConfig.buttonStyle, ["solid", "soft", "outline", "ghost"] as const, DEFAULT_CONFIG.buttonStyle),
    cardStyle: safeEnum(rawConfig.cardStyle, ["default", "flat", "soft", "contrast", "warm"] as const, DEFAULT_CONFIG.cardStyle),
    cardRadius: Math.max(6, Math.min(28, Math.round(Number(rawConfig.cardRadius) || DEFAULT_CONFIG.cardRadius))),
    shadowLevel: safeEnum(rawConfig.shadowLevel, ["none", "low", "soft", "medium", "strong"] as const, DEFAULT_CONFIG.shadowLevel),
    density: safeEnum(rawConfig.density, ["compact", "comfortable", "spacious"] as const, DEFAULT_CONFIG.density),
    uiContract: 2,
    workspaceId: safeEnum(rawConfig.workspaceId, Object.keys(workspaces) as WorkspaceId[], DEFAULT_CONFIG.workspaceId),
    layoutId: safeEnum(rawConfig.layoutId, Object.keys(layouts) as LayoutId[], DEFAULT_CONFIG.layoutId),
    themeId: safeEnum(rawConfig.themeId ?? themePreset, Object.keys(themePresets) as ThemeId[], themePreset),
    paletteId: safeEnum(rawConfig.paletteId, Object.keys(palettes) as PaletteId[], DEFAULT_CONFIG.paletteId),
    decorationId: safeEnum(rawConfig.decorationId, Object.keys(decorations) as DecorationId[], DEFAULT_CONFIG.decorationId),
    accentSecondary: isHex(rawConfig.accentSecondary) ? rawConfig.accentSecondary.toUpperCase() : DEFAULT_CONFIG.accentSecondary,
    todayWidgets: [...new Set((Array.isArray(rawConfig.todayWidgets) ? rawConfig.todayWidgets.map(String) : []).filter(id => WIDGET_UNIVERSE.includes(id)))],
    navigationOrder: completeOrder(rawConfig.navigationOrder, NAVIGATION_UNIVERSE),
    navigationVisible: [],
    calendarDensity: safeEnum(rawConfig.calendarDensity, ["comfortable", "compact"] as const, DEFAULT_CONFIG.calendarDensity),
    calendarLayerStyle: safeEnum(rawConfig.calendarLayerStyle, ["pills", "dots"] as const, DEFAULT_CONFIG.calendarLayerStyle),
  };
  if (config.todayWidgets.length && !config.todayWidgets.includes("shift")) config.todayWidgets.unshift("shift");
  const visibleRaw = Array.isArray(rawConfig.navigationVisible) ? rawConfig.navigationVisible.map(String).filter(id => NAVIGATION_UNIVERSE.includes(id)) : [...DEFAULT_CONFIG.navigationVisible];
  const visible = new Set(visibleRaw);
  visible.add("today"); visible.add("settings");
  config.navigationVisible = config.navigationOrder.filter(id => visible.has(id)).slice(0, 5);
  if (!config.navigationVisible.includes("today")) config.navigationVisible.unshift("today");
  if (!config.navigationVisible.includes("settings")) {
    if (config.navigationVisible.length >= 5) config.navigationVisible.pop();
    config.navigationVisible.push("settings");
  }
  return {
    themePreference: safeEnum(raw.themePreference, ["system", "light", "dark"] as const, "system"),
    accentColor: isHex(raw.accentColor) ? raw.accentColor.toUpperCase() : DEFAULT_APPEARANCE.accentColor,
    themePreset,
    themeConfig: config,
  };
}

export function themePalette(themeId: ThemeId): { accent: string; secondary: string } {
  const preset = themePresets[themeId] ?? themePresets.default;
  return { accent: preset.accentColor.toUpperCase(), secondary: isHex(preset.themeConfig.accentSecondary) ? preset.themeConfig.accentSecondary.toUpperCase() : "#14CDB4" };
}

export function applyThemePreset(currentValue: AppearancePreferences, themeId: ThemeId): AppearancePreferences {
  const current = normalizeAppearance(currentValue);
  const preset = themePresets[themeId] ?? themePresets.default;
  const paletteId = current.themeConfig.paletteId;
  const palette = palettes[paletteId];
  const resolved = themePalette(themeId);
  const accent = paletteId === "theme" ? resolved.accent : palette.accent ?? current.accentColor;
  const secondary = paletteId === "theme" ? resolved.secondary : palette.secondary ?? current.themeConfig.accentSecondary;
  return normalizeAppearance({
    ...preset,
    accentColor: accent,
    themePreset: themeId,
    themeConfig: {
      ...preset.themeConfig,
      uiContract: 2,
      workspaceId: current.themeConfig.workspaceId,
      layoutId: current.themeConfig.layoutId,
      themeId,
      paletteId,
      decorationId: current.themeConfig.decorationId,
      accentSecondary: secondary,
      todayWidgets: current.themeConfig.todayWidgets,
      navigationOrder: current.themeConfig.navigationOrder,
      navigationVisible: current.themeConfig.navigationVisible,
      calendarDensity: current.themeConfig.calendarDensity,
      calendarLayerStyle: current.themeConfig.calendarLayerStyle,
    },
  });
}

export function applyPalette(currentValue: AppearancePreferences, paletteId: PaletteId): AppearancePreferences {
  const current = normalizeAppearance(currentValue);
  const palette = palettes[paletteId] ?? palettes.theme;
  let accent = current.accentColor;
  let secondary = current.themeConfig.accentSecondary;
  if (paletteId === "theme") {
    const resolved = themePalette(current.themeConfig.themeId || current.themePreset);
    accent = resolved.accent; secondary = resolved.secondary;
  } else if (palette.accent) {
    accent = palette.accent; secondary = palette.secondary ?? palette.accent;
  }
  return normalizeAppearance({ ...current, accentColor: accent, themeConfig: { ...current.themeConfig, paletteId, accentSecondary: secondary } });
}

export function customizeTheme(currentValue: AppearancePreferences, patch: Partial<ThemeConfig> & { accentColor?: string }): AppearancePreferences {
  const current = normalizeAppearance(currentValue);
  const accent = patch.accentColor && isHex(patch.accentColor) ? patch.accentColor.toUpperCase() : current.accentColor;
  return normalizeAppearance({ ...current, accentColor: accent, themePreset: "custom", themeConfig: { ...current.themeConfig, ...patch, themeId: "custom", paletteId: patch.accentColor ? "custom" : current.themeConfig.paletteId } });
}

export function workspaceDefinition(config: ThemeConfig): WorkspaceEntry {
  const preset = workspaces[config.workspaceId] ?? workspaces["shift-worker"];
  if (config.workspaceId !== "custom") return preset;
  const visible = new Set(config.navigationVisible);
  return { ...preset, navigation: completeOrder(config.navigationOrder, NAVIGATION_UNIVERSE).filter(id => visible.has(id)), todayWidgets: config.todayWidgets.length ? [...config.todayWidgets] : ["shift"] };
}

export function customizeWorkspace(currentValue: AppearancePreferences): AppearancePreferences {
  const current = normalizeAppearance(currentValue);
  const source = workspaceDefinition(current.themeConfig);
  return normalizeAppearance({ ...current, themeConfig: { ...current.themeConfig, workspaceId: "custom", navigationOrder: completeOrder(source.navigation, NAVIGATION_UNIVERSE), navigationVisible: [...source.navigation], todayWidgets: [...source.todayWidgets] } });
}

export function setWorkspace(currentValue: AppearancePreferences, workspaceId: WorkspaceId): AppearancePreferences {
  const current = normalizeAppearance(currentValue);
  if (workspaceId === "custom" && current.themeConfig.workspaceId !== "custom") return customizeWorkspace(current);
  return normalizeAppearance({ ...current, themeConfig: { ...current.themeConfig, workspaceId } });
}

export function moveStudioItem(currentValue: AppearancePreferences, kind: "navigation" | "widget", id: string, direction: -1 | 1): AppearancePreferences {
  const current = normalizeAppearance(currentValue);
  const visibleWidgets = kind === "widget"
    ? new Set(current.themeConfig.todayWidgets.length ? current.themeConfig.todayWidgets : workspaceDefinition(current.themeConfig).todayWidgets)
    : null;
  const source = kind === "navigation"
    ? completeOrder(current.themeConfig.navigationOrder, NAVIGATION_UNIVERSE)
    : completeOrder(current.themeConfig.todayWidgets, WIDGET_UNIVERSE);
  const from = source.indexOf(id); const to = from + direction;
  if (from < 0 || to < 0 || to >= source.length) return current;
  const fromValue = source[from];
  const toValue = source[to];
  if (fromValue === undefined || toValue === undefined) return current;
  source[from] = toValue;
  source[to] = fromValue;
  if (kind === "navigation") {
    return normalizeAppearance({ ...current, themeConfig: { ...current.themeConfig, navigationOrder: source } });
  }
  // todayWidgets carries both order and visibility. completeOrder() appends hidden
  // universe members for Studio controls, so filter them back out after moving
  // a visible row or a hidden card would be silently re-enabled.
  const orderedVisibleWidgets = source.filter(item => visibleWidgets?.has(item));
  return normalizeAppearance({ ...current, themeConfig: { ...current.themeConfig, todayWidgets: orderedVisibleWidgets } });
}

export function toggleStudioItem(currentValue: AppearancePreferences, kind: "navigation" | "widget", id: string, enabled: boolean): { appearance: AppearancePreferences; rejected: boolean } {
  const current = normalizeAppearance(currentValue);
  if (kind === "navigation") {
    if (screens[id]?.required && !enabled) return { appearance: current, rejected: true };
    const selected = new Set(current.themeConfig.navigationVisible);
    enabled ? selected.add(id) : selected.delete(id);
    selected.add("today"); selected.add("settings");
    const ordered = completeOrder(current.themeConfig.navigationOrder, NAVIGATION_UNIVERSE).filter(item => selected.has(item));
    if (ordered.length > 5) return { appearance: current, rejected: true };
    return { appearance: normalizeAppearance({ ...current, themeConfig: { ...current.themeConfig, workspaceId: "custom", navigationVisible: ordered } }), rejected: false };
  }
  if (widgets[id]?.required && !enabled) return { appearance: current, rejected: true };
  const selected = new Set(current.themeConfig.todayWidgets.length ? current.themeConfig.todayWidgets : workspaceDefinition(current.themeConfig).todayWidgets);
  enabled ? selected.add(id) : selected.delete(id);
  selected.add("shift");
  const ordered = completeOrder(current.themeConfig.todayWidgets, WIDGET_UNIVERSE).filter(item => selected.has(item));
  return { appearance: normalizeAppearance({ ...current, themeConfig: { ...current.themeConfig, workspaceId: "custom", todayWidgets: ordered } }), rejected: false };
}

export function studioNavigationOrder(config: ThemeConfig): string[] { return completeOrder(config.navigationOrder, NAVIGATION_UNIVERSE); }
export function studioWidgetOrder(config: ThemeConfig): string[] { return completeOrder(config.todayWidgets.length ? config.todayWidgets : workspaceDefinition(config).todayWidgets, WIDGET_UNIVERSE); }
