/*
 * 12-ui-platform.js — DutyLog Workspace, Layout & Theme Studio v2
 *
 * Declarative registries connect one DOM/business layer with independent
 * workspaces, layouts, themes, palettes and safe decoration packages. The file
 * intentionally contains no API calls and no feature business logic.
 */

"use strict";

(() => {
  Object.assign(I18N_EN, {
    "Сохраняется…":"Saving…",
    "Сохранено автоматически":"Saved automatically",
    "Ошибка сохранения":"Save failed",
    "UI Core и рабочее окружение":"UI Core and workspace",
    "Рабочее окружение":"Workspace",
    "Компоновка":"Layout",
    "Цветовая палитра":"Color palette",
    "Дополнительный акцент":"Secondary accent",
    "Цвета темы":"Theme colors",
    "Изменено пользователем":"Customized",
    "Готовая палитра":"Preset palette",
    "Вернуть цвета темы":"Restore theme colors",
    "Текущая тема снова станет источником основного и дополнительного акцента.":"The current theme will again provide the primary and secondary accents.",
    "Разделы вне основной навигации":"Sections outside primary navigation",
    "Единый интерфейс DutyLog":"Single DutyLog interface",
    "Classic завершён: все окружения, компоновки, темы и палитры работают поверх одного UI Core. Для аварийного отката используются проверенные Git/Docker-релизы, а не второй интерфейс внутри приложения.":"Classic has been retired: all workspaces, layouts, themes and palettes now run on one UI Core. Emergency recovery uses tested Git/Docker releases instead of a second in-app interface.",
    "Студия рабочего пространства":"Workspace Studio",
    "Основная навигация":"Primary navigation",
    "Карточки экрана «Сегодня»":"Today screen cards",
    "Настроить копию":"Customize a copy",
    "Сбросить порядок":"Reset order",
    "Вверх":"Move up",
    "Вниз":"Move down",
    "Показывать":"Show",
    "Обязательный раздел":"Required section",
    "В основной навигации можно оставить не более пяти разделов.":"Primary navigation can contain at most five sections.",
    "Порядок и видимость сохраняются автоматически и не удаляют данные.":"Order and visibility are saved automatically and never delete data.",
    "Календарь":"Calendar",
    "Плотность календаря":"Calendar density",
    "Слои графиков":"Schedule layers",
    "Декорация":"Decoration",
    "Комфортная":"Comfortable",
    "Компактная":"Compact",
    "Полосы":"Pills",
    "Точки":"Dots",
    "Без декораций":"No decorations",
    "Спокойная сетка":"Calm grid"
  });
  Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

  const workspaces = Object.freeze({
    "shift-worker": Object.freeze({
      id:"shift-worker",
      labelRu:"Работник по сменам",
      labelEn:"Shift Worker",
      descriptionRu:"Смены, календарь и переработки находятся в первом плане.",
      descriptionEn:"Shifts, calendar and overtime stay in the foreground.",
      navigation:["today","calendar","vacation","overtime","settings"],
      todayWidgets:["shift","overtime","tasks","important"]
    }),
    planner: Object.freeze({
      id:"planner",
      labelRu:"Планировщик",
      labelEn:"Planner",
      descriptionRu:"Задачи и важные даты поднимаются выше рабочего графика.",
      descriptionEn:"Tasks and important dates move ahead of the work schedule.",
      navigation:["today","tasks","calendar","vacation","settings"],
      todayWidgets:["tasks","important","shift","overtime"]
    }),
    minimal: Object.freeze({
      id:"minimal",
      labelRu:"Минимум",
      labelEn:"Minimal",
      descriptionRu:"Только сегодня, календарь и быстрый доступ к остальным разделам.",
      descriptionEn:"Today and calendar first, with the remaining sections under More.",
      navigation:["today","calendar","settings"],
      todayWidgets:["shift","tasks"]
    }),
    custom: Object.freeze({
      id:"custom",
      labelRu:"Своя рабочая область",
      labelEn:"Custom workspace",
      descriptionRu:"Собственный порядок навигации и карточек без удаления данных.",
      descriptionEn:"Your own navigation and card order without deleting data.",
      navigation:[],
      todayWidgets:[]
    })
  });

  const layouts = Object.freeze({
    dashboard: Object.freeze({
      id:"dashboard",
      labelRu:"Dashboard",
      labelEn:"Dashboard",
      descriptionRu:"Сбалансированная сетка карточек для телефона и компьютера.",
      descriptionEn:"Balanced card grid for phone and desktop."
    }),
    compact: Object.freeze({
      id:"compact",
      labelRu:"Компактная",
      labelEn:"Compact",
      descriptionRu:"Больше данных на экране, меньше отступов и крупнее рабочая область.",
      descriptionEn:"More data on screen with tighter spacing and a wider canvas."
    }),
    focus: Object.freeze({
      id:"focus",
      labelRu:"Фокус",
      labelEn:"Focus",
      descriptionRu:"Одна основная колонка без визуальной суеты.",
      descriptionEn:"One primary column with fewer competing surfaces."
    }),
    sidebar: Object.freeze({
      id:"sidebar",
      labelRu:"Боковая панель",
      labelEn:"Sidebar",
      descriptionRu:"На широком экране навигация закрепляется слева, а контент получает больше места.",
      descriptionEn:"On wide screens navigation stays on the left and content gets more room."
    }),
    "mobile-flow": Object.freeze({
      id:"mobile-flow",
      labelRu:"Мобильный поток",
      labelEn:"Mobile Flow",
      descriptionRu:"Узкая последовательная колонка даже на большом экране.",
      descriptionEn:"A narrow sequential column even on a large screen."
    })
  });

  const themePackage = (id, preset, labelRu, labelEn, cssPath) => Object.freeze({
    id, preset, labelRu, labelEn, cssPath, uiContract:UI_CONTRACT_VERSION,
    supportsCustomPalette:true, tokenScope:`html[data-ui-theme="${id}"]`
  });
  const themes = Object.freeze({
    default:themePackage("default", "default", "DutyLog Default", "DutyLog Default", "ui/themes/dutylog-default.css"),
    custom:themePackage("custom", "custom", "Пользовательская", "Custom", null),
    midnight:themePackage("midnight", "midnight", "Midnight", "Midnight", "ui/themes/midnight.css"),
    oled:themePackage("oled", "oled", "OLED Black", "OLED Black", "ui/themes/oled.css"),
    forest:themePackage("forest", "forest", "Forest", "Forest", "ui/themes/forest.css"),
    sunset:themePackage("sunset", "sunset", "Sunset", "Sunset", "ui/themes/sunset.css"),
    industrial:themePackage("industrial", "industrial", "Industrial", "Industrial", "ui/themes/industrial.css"),
    softPurple:themePackage("softPurple", "softPurple", "Soft Purple", "Soft Purple", "ui/themes/soft-purple.css")
  });

  const palettes = Object.freeze({
    theme: Object.freeze({ id:"theme", labelRu:"Палитра темы", labelEn:"Theme palette", accent:null, secondary:null }),
    "gold-teal": Object.freeze({ id:"gold-teal", labelRu:"Золото + бирюза", labelEn:"Gold + teal", accent:"#D4B83F", secondary:"#14CDB4" }),
    "teal-gold": Object.freeze({ id:"teal-gold", labelRu:"Бирюза + золото", labelEn:"Teal + gold", accent:"#14CDB4", secondary:"#D4B83F" }),
    violet: Object.freeze({ id:"violet", labelRu:"Фиолетовая", labelEn:"Violet", accent:"#9B7BE0", secondary:"#58C6C8" }),
    ember: Object.freeze({ id:"ember", labelRu:"Тёплый уголь", labelEn:"Ember", accent:"#E0653A", secondary:"#F5B841" }),
    custom: Object.freeze({ id:"custom", labelRu:"Своя палитра", labelEn:"Custom palette", accent:null, secondary:null })
  });

  const decorations = Object.freeze({
    none: Object.freeze({ id:"none", labelRu:"Без декораций", labelEn:"No decorations", uiContract:UI_CONTRACT_VERSION, pointerEvents:"none" }),
    grid: Object.freeze({ id:"grid", labelRu:"Спокойная сетка", labelEn:"Calm grid", uiContract:UI_CONTRACT_VERSION, pointerEvents:"none" })
  });

  const screens = Object.freeze({
    today:Object.freeze({ id:"today", elementId:"view-today", module:"core", labelRu:"Сегодня", labelEn:"Today", required:true }),
    calendar:Object.freeze({ id:"calendar", elementId:"view-calendar", module:"calendar", labelRu:"Календарь", labelEn:"Calendar" }),
    vacation:Object.freeze({ id:"vacation", elementId:"view-vacation", module:"vacation", labelRu:"Отпуск", labelEn:"Vacation" }),
    overtime:Object.freeze({ id:"overtime", elementId:"view-overtime", module:"overtime", labelRu:"Переработки", labelEn:"Overtime" }),
    payroll:Object.freeze({ id:"payroll", elementId:"view-payroll", module:"payroll", labelRu:"Зарплата", labelEn:"Payroll" }),
    tasks:Object.freeze({ id:"tasks", elementId:"view-tasks", module:"tasks", labelRu:"Задачи", labelEn:"Tasks" }),
    important:Object.freeze({ id:"important", elementId:"view-important", module:"important_dates", labelRu:"Важное", labelEn:"Important" }),
    settings:Object.freeze({ id:"settings", elementId:"view-settings", module:"core", labelRu:"Настройки", labelEn:"Settings", required:true }),
    admin:Object.freeze({ id:"admin", elementId:"view-admin", module:"admin", labelRu:"Админ", labelEn:"Admin" })
  });

  const widgets = Object.freeze({
    shift:Object.freeze({ id:"shift", elementId:"todayShiftCard", module:"shifts", required:true, labelRu:"Смена", labelEn:"Shift" }),
    overtime:Object.freeze({ id:"overtime", elementId:"todayOvertimeCard", module:"overtime", labelRu:"Переработки", labelEn:"Overtime" }),
    tasks:Object.freeze({ id:"tasks", elementId:"todayTasksCard", module:"tasks", labelRu:"Задачи", labelEn:"Tasks" }),
    important:Object.freeze({ id:"important", elementId:"todayUpcomingCard", module:"important_dates", labelRu:"Ближайшее", labelEn:"Upcoming" })
  });

  const navigationUniverse = Object.freeze(["today","calendar","vacation","overtime","payroll","tasks","important","settings"]);
  const widgetUniverse = Object.freeze(Object.keys(widgets));
  const label = entry => state.language === "en" ? entry.labelEn : entry.labelRu;
  const description = entry => state.language === "en" ? entry.descriptionEn : entry.descriptionRu;
  const entries = registry => Object.values(registry);

  function completeOrder(order, universe){
    const normalized = Array.isArray(order) ? order.filter(id => universe.includes(id)) : [];
    return [...new Set([...normalized, ...universe])];
  }

  function configFrom(prefs = state.preferences){
    return normalizeThemeConfig(prefs?.themeConfig || {});
  }

  function workspaceDefinition(cfg){
    const preset = workspaces[cfg.workspaceId] || workspaces["shift-worker"];
    if (cfg.workspaceId !== "custom") return preset;
    const order = completeOrder(cfg.navigationOrder, navigationUniverse);
    const visible = new Set(cfg.navigationVisible);
    return {
      ...preset,
      navigation:order.filter(id => visible.has(id)),
      todayWidgets:Array.isArray(cfg.todayWidgets) && cfg.todayWidgets.length ? cfg.todayWidgets : ["shift"]
    };
  }

  function populateSelect(id, registry){
    const select = $(id);
    if (!select) return;
    const signature = entries(registry).map(item => item.id).join("|");
    if (select.dataset.registrySignature !== signature || select.dataset.registryLanguage !== state.language) {
      select.innerHTML = entries(registry)
        .map(item => `<option value="${esc(item.id)}">${esc(label(item))}</option>`)
        .join("");
      select.dataset.registrySignature = signature;
      select.dataset.registryLanguage = state.language;
    }
  }

  function readControls(baseConfig = configFrom()){
    const cfg = normalizeThemeConfig(baseConfig);
    const selectedWorkspace = $('uiWorkspace')?.value || cfg.workspaceId;
    const sourceWorkspace = workspaceDefinition(cfg);
    const switchingToCustom = selectedWorkspace === "custom" && cfg.workspaceId !== "custom";
    const navigationOrder = switchingToCustom
      ? completeOrder(sourceWorkspace.navigation, navigationUniverse)
      : completeOrder(cfg.navigationOrder, navigationUniverse);
    const navigationVisible = switchingToCustom
      ? [...sourceWorkspace.navigation]
      : [...cfg.navigationVisible];
    return {
      uiContract:UI_CONTRACT_VERSION,
      workspaceId:selectedWorkspace,
      layoutId:$('uiLayout')?.value || cfg.layoutId,
      themeId:$('appearancePreset')?.value || cfg.themeId,
      paletteId:$('uiPalette')?.value || cfg.paletteId,
      decorationId:$('uiDecoration')?.value || cfg.decorationId,
      accentSecondary:$('uiAccentSecondary')?.value || cfg.accentSecondary,
      todayWidgets:Array.isArray(cfg.todayWidgets) ? [...cfg.todayWidgets] : [],
      navigationOrder,
      navigationVisible,
      calendarDensity:$('uiCalendarDensity')?.value || cfg.calendarDensity,
      calendarLayerStyle:$('uiCalendarLayerStyle')?.value || cfg.calendarLayerStyle
    };
  }

  function applyDataAttributes(cfg){
    const root = document.documentElement;
    root.dataset.uiContract = String(cfg.uiContract || UI_CONTRACT_VERSION);
    root.dataset.uiWorkspace = cfg.workspaceId;
    root.dataset.uiLayout = cfg.layoutId;
    root.dataset.uiTheme = cfg.themeId;
    root.dataset.uiPalette = cfg.paletteId;
    root.dataset.uiDecoration = cfg.decorationId;
    root.dataset.uiCalendarDensity = cfg.calendarDensity;
    root.dataset.uiCalendarLayers = cfg.calendarLayerStyle;
  }

  function applyNavigation(cfg){
    const workspace = workspaceDefinition(cfg);
    const tabbar = $("tabbar");
    if (!tabbar) return;
    const anchors = [...tabbar.querySelectorAll("a[data-view]")];
    const byView = Object.fromEntries(anchors.map(anchor => [anchor.dataset.view, anchor]));
    const primary = workspace.navigation;
    const order = cfg.workspaceId === "custom"
      ? completeOrder(cfg.navigationOrder, navigationUniverse)
      : [...primary, ...navigationUniverse.filter(view => !primary.includes(view))];
    for (const view of order) {
      const anchor = byView[view];
      if (!anchor) continue;
      anchor.classList.toggle("workspaceHidden", !primary.includes(view));
      tabbar.appendChild(anchor);
    }
    tabbar.style.setProperty("--workspace-nav-count", String(Math.max(1, primary.length)));
    renderWorkspaceRoutes(cfg);
  }

  function enabledForWidget(widget){
    if (widget.required) return true;
    return typeof moduleEnabled !== "function" || moduleEnabled(widget.module);
  }

  function requestedWidgets(cfg){
    const workspace = workspaceDefinition(cfg);
    const requested = Array.isArray(cfg.todayWidgets) && cfg.todayWidgets.length
      ? cfg.todayWidgets.filter(id => widgets[id])
      : workspace.todayWidgets;
    return requested.includes("shift") ? requested : ["shift", ...requested];
  }

  function applyTodayWidgets(cfg){
    const grid = document.querySelector(".todayDashboardGrid");
    if (!grid) return;
    const requested = requestedWidgets(cfg);
    const order = completeOrder(requested, widgetUniverse);
    for (const id of order) {
      const widget = widgets[id];
      const element = $(widget.elementId);
      if (!element) continue;
      const visible = requested.includes(id) && enabledForWidget(widget);
      element.classList.toggle("workspaceHidden", !visible);
      grid.appendChild(element);
    }
  }

  function renderWorkspaceRoutes(cfg){
    const box = $("workspaceRouteLinks");
    if (!box) return;
    const workspace = workspaceDefinition(cfg);
    const hidden = Object.values(screens).filter(screen => {
      if (["admin","settings","today","calendar"].includes(screen.id)) return false;
      if (workspace.navigation.includes(screen.id)) return false;
      return typeof moduleEnabled !== "function" || moduleEnabled(screen.module);
    });
    box.innerHTML = hidden.length
      ? hidden.map(screen => {
          const anchor = document.querySelector(`#tabbar a[data-view="${screen.id}"] .navLabel`);
          const text = anchor?.textContent?.trim() || label(screen);
          return `<a href="#${esc(screen.id)}" data-workspace-route="${esc(screen.id)}">${esc(text)}</a>`;
        }).join("")
      : `<span>${esc(state.language === "en" ? "All enabled sections are already in the main navigation." : "Все включённые разделы уже находятся в основной навигации.")}</span>`;
  }

  function applyPalette(prefs, cfg){
    const palette = palettes[cfg.paletteId] || palettes.theme;
    const root = document.documentElement;
    const primary = palette.accent || prefs.accentColor;
    const secondary = palette.secondary || cfg.accentSecondary || primary;
    root.style.setProperty("--accent", primary);
    root.style.setProperty("--color-accent", primary);
    root.style.setProperty("--accent-secondary", secondary);
    root.style.setProperty("--color-accent-secondary", secondary);
  }

  function apply(prefs = state.preferences, normalizedConfig = null){
    const cfg = normalizedConfig || configFrom(prefs);
    applyDataAttributes(cfg);
    applyPalette(prefs, cfg);
    applyNavigation(cfg);
    applyTodayWidgets(cfg);
    renderControls(prefs);
    return cfg;
  }

  function renderPaletteState(prefs = state.preferences){
    const cfg = configFrom(prefs);
    const status = $("uiPaletteState");
    if (!status) return;
    const palette = palettes[cfg.paletteId] || palettes.theme;
    const text = cfg.paletteId === "theme"
      ? (state.language === "en" ? "Theme colors" : "Цвета темы")
      : cfg.paletteId === "custom"
        ? (state.language === "en" ? "Customized" : "Изменено пользователем")
        : `${state.language === "en" ? "Preset palette" : "Готовая палитра"}: ${label(palette)}`;
    status.dataset.paletteMode = cfg.paletteId === "theme" ? "theme" : (cfg.paletteId === "custom" ? "custom" : "preset");
    status.innerHTML = `<span class="paletteModeDot" aria-hidden="true"></span><span>${esc(text)}</span>`;
  }

  function moveItem(order, id, direction){
    const copy = [...order];
    const from = copy.indexOf(id);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= copy.length) return copy;
    [copy[from], copy[to]] = [copy[to], copy[from]];
    return copy;
  }

  function studioMessage(message = ""){
    const box = $("workspaceStudioMessage");
    if (box) box.textContent = message;
  }

  function persistStudioPatch(patch){
    const current = normalizeThemeConfig(state.preferences?.themeConfig);
    state.preferences = normalizeAppearance({
      ...state.preferences,
      themeConfig:{ ...current, ...patch, uiContract:UI_CONTRACT_VERSION }
    });
    applyAppearance(state.preferences);
    if (typeof scheduleAppearanceAutoSave === "function") scheduleAppearanceAutoSave();
  }

  function navigationRows(cfg){
    const workspace = workspaceDefinition(cfg);
    const order = cfg.workspaceId === "custom"
      ? completeOrder(cfg.navigationOrder, navigationUniverse)
      : completeOrder(workspace.navigation, navigationUniverse);
    const visible = new Set(workspace.navigation);
    const editable = cfg.workspaceId === "custom";
    return order.map((id, index) => {
      const screen = screens[id];
      const required = !!screen?.required;
      return `<div class="workspaceStudioRow" data-studio-kind="navigation" data-studio-id="${esc(id)}">
        <label><input type="checkbox" data-studio-visible ${visible.has(id) ? "checked" : ""} ${(!editable || required) ? "disabled" : ""}/><span>${esc(label(screen))}</span></label>
        <div class="workspaceStudioMove">
          <button type="button" class="buttonIcon" data-studio-move="-1" aria-label="${esc(t("Вверх"))}" ${(!editable || index === 0) ? "disabled" : ""}>↑</button>
          <button type="button" class="buttonIcon" data-studio-move="1" aria-label="${esc(t("Вниз"))}" ${(!editable || index === order.length - 1) ? "disabled" : ""}>↓</button>
        </div>
      </div>`;
    }).join("");
  }

  function widgetRows(cfg){
    const selected = requestedWidgets(cfg);
    const order = completeOrder(selected, widgetUniverse);
    const visible = new Set(selected);
    return order.map((id, index) => {
      const widget = widgets[id];
      return `<div class="workspaceStudioRow" data-studio-kind="widget" data-studio-id="${esc(id)}">
        <label><input type="checkbox" data-studio-visible ${visible.has(id) ? "checked" : ""} ${widget.required ? "disabled" : ""}/><span>${esc(label(widget))}</span></label>
        <div class="workspaceStudioMove">
          <button type="button" class="buttonIcon" data-studio-move="-1" aria-label="${esc(t("Вверх"))}" ${index === 0 ? "disabled" : ""}>↑</button>
          <button type="button" class="buttonIcon" data-studio-move="1" aria-label="${esc(t("Вниз"))}" ${index === order.length - 1 ? "disabled" : ""}>↓</button>
        </div>
      </div>`;
    }).join("");
  }

  function renderStudio(cfg){
    const nav = $("workspaceNavigationList");
    const cards = $("todayWidgetList");
    if (nav) nav.innerHTML = navigationRows(cfg);
    if (cards) cards.innerHTML = widgetRows(cfg);
    const customize = $("workspaceCustomize");
    if (customize) customize.hidden = cfg.workspaceId === "custom";
    studioMessage("");
  }

  function bindStudio(){
    const studio = $("workspaceStudio");
    if (!studio || studio.dataset.bound === "true") return;
    studio.dataset.bound = "true";

    $("workspaceCustomize")?.addEventListener("click", () => {
      const cfg = configFrom();
      const workspace = workspaceDefinition(cfg);
      persistStudioPatch({
        workspaceId:"custom",
        navigationOrder:completeOrder(workspace.navigation, navigationUniverse),
        navigationVisible:[...workspace.navigation]
      });
    });

    $("workspaceStudioReset")?.addEventListener("click", () => {
      persistStudioPatch({
        workspaceId:"shift-worker",
        navigationOrder:[...navigationUniverse],
        navigationVisible:[...workspaces["shift-worker"].navigation],
        todayWidgets:[]
      });
    });

    studio.addEventListener("click", event => {
      const button = event.target.closest("[data-studio-move]");
      if (!button) return;
      const row = button.closest("[data-studio-kind][data-studio-id]");
      if (!row) return;
      const direction = Number(button.dataset.studioMove || 0);
      const cfg = configFrom();
      if (row.dataset.studioKind === "navigation") {
        if (cfg.workspaceId !== "custom") return;
        persistStudioPatch({ navigationOrder:moveItem(completeOrder(cfg.navigationOrder, navigationUniverse), row.dataset.studioId, direction) });
      } else {
        const selected = requestedWidgets(cfg);
        const fullOrder = moveItem(completeOrder(selected, widgetUniverse), row.dataset.studioId, direction);
        persistStudioPatch({ todayWidgets:fullOrder.filter(id => selected.includes(id)) });
      }
    });

    studio.addEventListener("change", event => {
      const checkbox = event.target.closest("[data-studio-visible]");
      if (!checkbox) return;
      const row = checkbox.closest("[data-studio-kind][data-studio-id]");
      if (!row) return;
      const cfg = configFrom();
      const id = row.dataset.studioId;
      if (row.dataset.studioKind === "navigation") {
        if (cfg.workspaceId !== "custom") return;
        const selected = new Set(cfg.navigationVisible);
        checkbox.checked ? selected.add(id) : selected.delete(id);
        selected.add("today");
        selected.add("settings");
        const ordered = completeOrder(cfg.navigationOrder, navigationUniverse).filter(item => selected.has(item));
        if (ordered.length > 5) {
          checkbox.checked = false;
          studioMessage(t("В основной навигации можно оставить не более пяти разделов."));
          return;
        }
        persistStudioPatch({ navigationVisible:ordered });
      } else {
        const selected = new Set(requestedWidgets(cfg));
        checkbox.checked ? selected.add(id) : selected.delete(id);
        selected.add("shift");
        const ordered = completeOrder(requestedWidgets(cfg), widgetUniverse).filter(item => selected.has(item));
        persistStudioPatch({ todayWidgets:ordered });
      }
    });
  }

  function renderControls(prefs = state.preferences){
    const cfg = configFrom(prefs);
    populateSelect("uiWorkspace", workspaces);
    populateSelect("uiLayout", layouts);
    populateSelect("uiPalette", palettes);
    populateSelect("uiDecoration", decorations);
    if ($("uiWorkspace")) $("uiWorkspace").value = cfg.workspaceId;
    if ($("uiLayout")) $("uiLayout").value = cfg.layoutId;
    if ($("uiPalette")) $("uiPalette").value = cfg.paletteId;
    if ($("uiDecoration")) $("uiDecoration").value = cfg.decorationId;
    if ($("uiCalendarDensity")) $("uiCalendarDensity").value = cfg.calendarDensity;
    if ($("uiCalendarLayerStyle")) $("uiCalendarLayerStyle").value = cfg.calendarLayerStyle;
    if ($("uiAccentSecondary") && isHexColor(cfg.accentSecondary)) $("uiAccentSecondary").value = cfg.accentSecondary;
    renderPaletteState(prefs);
    const workspace = workspaces[cfg.workspaceId] || workspaces["shift-worker"];
    const layout = layouts[cfg.layoutId] || layouts.dashboard;
    const status = $("uiPlatformStatus");
    if (status) status.innerHTML = `<b>UI Core v${esc(cfg.uiContract)}</b><span>${esc(label(workspace))} · ${esc(label(layout))}</span>`;
    const descriptionBox = $("uiPlatformDescription");
    if (descriptionBox) descriptionBox.textContent = `${description(workspace)} ${description(layout)}`;
    renderWorkspaceRoutes(cfg);
    renderStudio(cfg);
    bindStudio();
  }

  function selectPalette(paletteId){
    const palette = palettes[paletteId] || palettes.theme;
    if (palette.accent && $("appearanceAccent")) $("appearanceAccent").value = palette.accent;
    if (palette.secondary && $("uiAccentSecondary")) $("uiAccentSecondary").value = palette.secondary;
  }

  function views(){
    return Object.fromEntries(Object.values(screens).map(screen => [screen.id, screen.elementId]));
  }

  const api = Object.freeze({
    contractVersion:UI_CONTRACT_VERSION,
    workspaces,
    layouts,
    themes,
    palettes,
    decorations,
    screens,
    widgets,
    views,
    configFrom,
    workspaceDefinition,
    readControls,
    selectPalette,
    apply,
    renderControls,
    renderPaletteState,
    renderWorkspaceRoutes,
    renderStudio
  });

  window.DutyLogUI = api;
  applyAppearance(state.preferences);
})();
