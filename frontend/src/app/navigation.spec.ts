import { describe, expect, it } from "vitest";
import { navigationItem, normalizeSection } from "./navigation";

describe("app shell navigation", () => {
  it("normalizes nested settings routes to the settings section", () => {
    expect(normalizeSection("settings-profile")).toBe("settings");
    expect(normalizeSection("#settings-notifications")).toBe("settings");
  });

  it("falls unknown routes back to Today", () => {
    expect(normalizeSection("unknown")).toBe("today");
    expect(navigationItem("overtime")?.labels.ru).toBe("Переработки");
  });
});
