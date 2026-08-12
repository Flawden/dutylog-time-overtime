"use strict";

// Runs synchronously in <head> before CSS to prevent shell/theme/layout flash.
(() => {
  const root = document.documentElement;
  const allowed = (value, values, fallback) => values.includes(String(value || "")) ? String(value) : fallback;
  const color = value => /^#[0-9a-fA-F]{6}$/.test(String(value || "")) ? String(value).toUpperCase() : null;
  const palettes = {
    "gold-teal":["#D4B83F", "#14CDB4"],
    "teal-gold":["#14CDB4", "#D4B83F"],
    violet:["#9B7BE0", "#58C6C8"],
    ember:["#E0653A", "#F5B841"]
  };
  const applyColor = (name, value) => { if (value) root.style.setProperty(name, value); };

  try {
    const appearance = JSON.parse(localStorage.getItem("dutylog.appearance.v2") || "{}");
    const cfg = appearance?.themeConfig && typeof appearance.themeConfig === "object" ? appearance.themeConfig : {};
    const preset = allowed(appearance?.themePreset, ["default","custom","midnight","oled","forest","sunset","industrial","softPurple"], "default");
    const paletteId = allowed(cfg.paletteId, ["theme","gold-teal","teal-gold","violet","ember","custom"], "theme");
    const themePreference = allowed(appearance?.themePreference, ["system","light","dark"], "system");
    const effectiveTheme = themePreference === "system"
      ? (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark")
      : themePreference;

    root.dataset.theme = effectiveTheme;
    root.dataset.shell = "next";
    root.dataset.uiContract = "2";
    root.dataset.uiWorkspace = allowed(cfg.workspaceId, ["shift-worker","planner","minimal","custom"], "shift-worker");
    root.dataset.uiLayout = allowed(cfg.layoutId, ["dashboard","compact","focus","sidebar","mobile-flow"], "dashboard");
    root.dataset.uiTheme = allowed(cfg.themeId, ["default","custom","midnight","oled","forest","sunset","industrial","softPurple"], preset);
    root.dataset.uiPalette = paletteId;
    root.dataset.uiDecoration = allowed(cfg.decorationId, ["none","grid"], "none");
    root.dataset.uiCalendarDensity = allowed(cfg.calendarDensity, ["comfortable","compact"], "comfortable");
    root.dataset.uiCalendarLayers = allowed(cfg.calendarLayerStyle, ["pills","dots"], "pills");

    const packagedPalette = palettes[paletteId];
    const accent = packagedPalette?.[0] || color(appearance?.accentColor) || "#F5B841";
    const secondary = packagedPalette?.[1] || color(cfg.accentSecondary) || "#14CDB4";
    applyColor("--accent", accent);
    applyColor("--color-accent", accent);
    applyColor("--accent-secondary", secondary);
    applyColor("--color-accent-secondary", secondary);

    // Only Custom owns direct surface variables. Built-in theme packages stay
    // independent and are selected solely through data-ui-theme.
    if (root.dataset.uiTheme === "custom") {
      const mapping = {
        appBg:["--bg","--color-background"],
        panelBg:["--panel","--color-surface"],
        panelAltBg:["--panel2","--color-surface-elevated"],
        textColor:["--text","--color-text-primary"],
        mutedColor:["--mut","--color-text-secondary"],
        borderColor:["--line","--color-border"]
      };
      for (const [key, names] of Object.entries(mapping)) {
        const value = color(cfg[key]);
        for (const name of names) applyColor(name, value);
      }
    }
  } catch (_) {
    root.dataset.theme = "dark";
    root.dataset.shell = "next";
    root.dataset.uiContract = "2";
    root.dataset.uiWorkspace = "shift-worker";
    root.dataset.uiLayout = "dashboard";
    root.dataset.uiTheme = "default";
    root.dataset.uiPalette = "theme";
    root.dataset.uiDecoration = "none";
    root.dataset.uiCalendarDensity = "comfortable";
    root.dataset.uiCalendarLayers = "pills";
  }
})();

// The promise exists before any deferred module executes, so browser tests and
// future legacy features can await the Vue migration boundary without polling.
window.__dutylogVueReady = new Promise(resolve => {
  window.addEventListener("dutylog:vue-ready", event => {
    if (String(event.detail?.architecture || "").startsWith("vue-shell")) {
      for (const id of ["nextTopbar", "tabbar"]) document.getElementById(id)?.remove();
      document.documentElement.dataset.vueShell = "ready";
    }
    resolve(event.detail);
  }, { once:true });
});
