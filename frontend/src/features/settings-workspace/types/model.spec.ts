import { describe, expect, it } from "vitest";
import { applyPalette, applyThemePreset, customizeWorkspace, DEFAULT_APPEARANCE, moveStudioItem, normalizeAppearance, toggleStudioItem } from "./model";

describe("settings workspace appearance model", () => {
  it("keeps Today and Settings mandatory and caps primary navigation at five", () => {
    let value = customizeWorkspace(normalizeAppearance(DEFAULT_APPEARANCE));
    value = toggleStudioItem(value, "navigation", "tasks", true).appearance;
    const result = toggleStudioItem(value, "navigation", "important", true);
    expect(result.rejected).toBe(true);
    expect(result.appearance.themeConfig.navigationVisible).toContain("today");
    expect(result.appearance.themeConfig.navigationVisible).toContain("settings");
    expect(result.appearance.themeConfig.navigationVisible).toHaveLength(5);
  });

  it("restores theme-owned palette colors", () => {
    const forest = applyThemePreset(normalizeAppearance(DEFAULT_APPEARANCE), "forest");
    expect(forest.accentColor).toBe("#6FBF73");
    expect(forest.themeConfig.accentSecondary).toBe("#A7C957");
    const custom = normalizeAppearance({ ...forest, accentColor: "#E05780", themeConfig: { ...forest.themeConfig, paletteId: "custom" } });
    const restored = applyPalette(custom, "theme");
    expect(restored.accentColor).toBe("#6FBF73");
    expect(restored.themeConfig.accentSecondary).toBe("#A7C957");
  });

  it("moves custom workspace rows without dropping navigation or resurrecting hidden Today widgets", () => {
    const custom = customizeWorkspace(normalizeAppearance(DEFAULT_APPEARANCE));
    const moved = moveStudioItem(custom, "navigation", "settings", -1);
    expect(moved.themeConfig.navigationOrder).toContain("settings");
    expect(new Set(moved.themeConfig.navigationOrder).size).toBe(moved.themeConfig.navigationOrder.length);

    const withoutOvertime = toggleStudioItem(custom, "widget", "overtime", false).appearance;
    expect(withoutOvertime.themeConfig.todayWidgets).not.toContain("overtime");
    const reordered = moveStudioItem(withoutOvertime, "widget", "tasks", -1);
    expect(reordered.themeConfig.todayWidgets).not.toContain("overtime");
    expect(reordered.themeConfig.todayWidgets.indexOf("tasks")).toBeLessThan(reordered.themeConfig.todayWidgets.indexOf("shift"));
  });
});
