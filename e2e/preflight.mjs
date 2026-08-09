import { statSync } from "node:fs";
import { resolve } from "node:path";

const required = [
  "frontend/dist/dutylog-vue-app-shell.js",
  "frontend/dist/dutylog-vue-app-shell.css",
];
const missing = required.filter(path => {
  try { return statSync(resolve(path)).size <= 0; } catch { return true; }
});
if (missing.length) {
  console.error("DutyLog E2E preflight failed: the Vue bundle has not been built.");
  for (const path of missing) console.error(`  missing: ${path}`);
  console.error("Run the frontend gate first: .\\deploy\\scripts\\frontend-gate.ps1 (Windows) or bash ./deploy/scripts/frontend-gate.sh (Linux/CI).");
  process.exit(1);
}
console.log("DutyLog E2E preflight passed: Vue JS/CSS bundle is present.");
