"use strict";

// Runs synchronously in <head> before CSS to prevent a Classic/Next flash.
(() => {
  try {
    const appearance = JSON.parse(localStorage.getItem("dutylog.appearance.v2") || "{}");
    document.documentElement.dataset.shell = appearance?.themeConfig?.shellMode === "classic" ? "classic" : "next";
  } catch (_) {
    document.documentElement.dataset.shell = "next";
  }
})();
