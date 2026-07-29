/*
 * 12-ui-platform.js — DutyLog UI Core & Workspace Foundation v1
 *
 * Declarative registries connect one DOM/business layer with independent
 * workspaces, layouts, themes and palettes. The file intentionally contains
 * no API calls and no feature business logic.
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
    "Classic завершён: все окружения, компоновки, темы и палитры работают поверх одного UI Core. Для аварийного отката используются проверенные Git/Docker-релизы, а не второй интерфейс внутри приложения.":"Classic has been retired: all workspaces, layouts, themes and palettes now run on one UI Core. Emergency recovery uses tested Git/Docker releases instead of a second in-app interface."
  });
  Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

  const workspaces = Object.freeze({
    "shift-worker": Object.freeze({
      id:"shift-worker",
      labelRu:"Работник по сменам",
      labelEn:"Shift Worker",
      descriptionRu:"Смены, календарь и переработки находятся в первом плане.",
      descriptionEn:"Shifts, calendar and overtime stay in the foreground.",
      navigation:["today","calendar","overtime","tasks","settings"],
      todayWidgets:["shift","overtime","tasks","important"]
    }),
    planner: Object.freeze({
      id:"planner",
      labelRu:"Планировщик",
      labelEn:"Planner",
      descriptionRu:"Задачи и важные даты поднимаются выше рабочего графика.",
      descriptionEn:"Tasks and important dates move ahead of the work schedule.",
      navigation:["today","tasks","calendar","overtime","settings"],
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
    })
  });

  const themes = Object.freeze({
    default: Object.freeze({ id:"default", preset:"default", labelRu:"DutyLog Default", labelEn:"DutyLog Default", uiContract:1 }),
    custom: Object.freeze({ id:"custom", preset:"custom", labelRu:"Пользовательская", labelEn:"Custom", uiContract:1 }),
    midnight: Object.freeze({ id:"midnight", preset:"midnight", labelRu:"Midnight", labelEn:"Midnight", uiContract:1 }),
    oled: Object.freeze({ id:"oled", preset:"oled", labelRu:"OLED Black", labelEn:"OLED Black", uiContract:1 }),
    forest: Object.freeze({ id:"forest", preset:"forest", labelRu:"Forest", labelEn:"Forest", uiContract:1 }),
    sunset: Object.freeze({ id:"sunset", preset:"sunset", labelRu:"Sunset", labelEn:"Sunset", uiContract:1 }),
    industrial: Object.freeze({ id:"industrial", preset:"industrial", labelRu:"Industrial", labelEn:"Industrial", uiContract:1 }),
    softPurple: Object.freeze({ id:"softPurple", preset:"softPurple", labelRu:"Soft Purple", labelEn:"Soft Purple", uiContract:1 })
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
    none: Object.freeze({ id:"none", labelRu:"Без декораций", labelEn:"No decorations", uiContract:1 })
  });

  const screens = Object.freeze({
    today:Object.freeze({ id:"today", elementId:"view-today", module:"core" }),
    calendar:Object.freeze({ id:"calendar", elementId:"view-calendar", module:"calendar" }),
    overtime:Object.freeze({ id:"overtime", elementId:"view-overtime", module:"overtime" }),
    tasks:Object.freeze({ id:"tasks", elementId:"view-tasks", module:"tasks" }),
    important:Object.freeze({ id:"important", elementId:"view-important", module:"important_dates" }),
    settings:Object.freeze({ id:"settings", elementId:"view-settings", module:"core" }),
    admin:Object.freeze({ id:"admin", elementId:"view-admin", module:"admin" })
  });

  const widgets = Object.freeze({
    shift:Object.freeze({ id:"shift", elementId:"todayShiftCard", module:"shifts", required:true }),
    overtime:Object.freeze({ id:"overtime", elementId:"todayOvertimeCard", module:"overtime" }),
    tasks:Object.freeze({ id:"tasks", elementId:"todayTasksCard", module:"tasks" }),
    important:Object.freeze({ id:"important", elementId:"todayUpcomingCard", module:"important_dates" })
  });

  const label = entry => state.language === "en" ? entry.labelEn : entry.labelRu;
  const description = entry => state.language === "en" ? entry.descriptionEn : entry.descriptionRu;
  const entries = registry => Object.values(registry);

  function configFrom(prefs = state.preferences){
    return normalizeThemeConfig(prefs?.themeConfig || {});
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
    return {
      uiContract:UI_CONTRACT_VERSION,
      workspaceId:$('uiWorkspace')?.value || cfg.workspaceId,
      layoutId:$('uiLayout')?.value || cfg.layoutId,
      themeId:$('appearancePreset')?.value || cfg.themeId,
      paletteId:$('uiPalette')?.value || cfg.paletteId,
      decorationId:cfg.decorationId,
      accentSecondary:$('uiAccentSecondary')?.value || cfg.accentSecondary,
      todayWidgets:Array.isArray(cfg.todayWidgets) ? [...cfg.todayWidgets] : []
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
  }

  const navigationUniverse = Object.freeze(["today","calendar","overtime","tasks","important","settings"]);

  function applyNavigation(cfg){
    const workspace = workspaces[cfg.workspaceId] || workspaces["shift-worker"];
    const tabbar = $("tabbar");
    if (!tabbar) return;
    const anchors = [...tabbar.querySelectorAll("a[data-view]")];
    const byView = Object.fromEntries(anchors.map(anchor => [anchor.dataset.view, anchor]));
    const primary = workspace.navigation;
    const order = [...primary, ...navigationUniverse.filter(view => !primary.includes(view))];
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

  function applyTodayWidgets(cfg){
    const workspace = workspaces[cfg.workspaceId] || workspaces["shift-worker"];
    const grid = document.querySelector(".todayDashboardGrid");
    if (!grid) return;
    const requested = Array.isArray(cfg.todayWidgets) && cfg.todayWidgets.length
      ? cfg.todayWidgets.filter(id => widgets[id])
      : workspace.todayWidgets;
    const order = [...requested, ...Object.keys(widgets).filter(id => !requested.includes(id))];
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
    const workspace = workspaces[cfg.workspaceId] || workspaces["shift-worker"];
    const hidden = Object.values(screens).filter(screen => {
      if (["admin","settings","today","calendar"].includes(screen.id)) return false;
      if (workspace.navigation.includes(screen.id)) return false;
      return typeof moduleEnabled !== "function" || moduleEnabled(screen.module);
    });
    box.innerHTML = hidden.length
      ? hidden.map(screen => {
          const anchor = document.querySelector(`#tabbar a[data-view="${screen.id}"] .navLabel`);
          const text = anchor?.textContent?.trim() || screen.id;
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

  function renderControls(prefs = state.preferences){
    const cfg = configFrom(prefs);
    populateSelect("uiWorkspace", workspaces);
    populateSelect("uiLayout", layouts);
    populateSelect("uiPalette", palettes);
    if ($("uiWorkspace")) $("uiWorkspace").value = cfg.workspaceId;
    if ($("uiLayout")) $("uiLayout").value = cfg.layoutId;
    if ($("uiPalette")) $("uiPalette").value = cfg.paletteId;
    if ($("uiAccentSecondary") && isHexColor(cfg.accentSecondary)) $("uiAccentSecondary").value = cfg.accentSecondary;
    renderPaletteState(prefs);
    const workspace = workspaces[cfg.workspaceId] || workspaces["shift-worker"];
    const layout = layouts[cfg.layoutId] || layouts.dashboard;
    const status = $("uiPlatformStatus");
    if (status) {
      status.innerHTML = `<b>UI Core v${esc(cfg.uiContract)}</b><span>${esc(label(workspace))} · ${esc(label(layout))}</span>`;
    }
    const descriptionBox = $("uiPlatformDescription");
    if (descriptionBox) descriptionBox.textContent = `${description(workspace)} ${description(layout)}`;
    renderWorkspaceRoutes(cfg);
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
    readControls,
    selectPalette,
    apply,
    renderControls,
    renderPaletteState,
    renderWorkspaceRoutes
  });

  window.DutyLogUI = api;
  applyAppearance(state.preferences);
})();
