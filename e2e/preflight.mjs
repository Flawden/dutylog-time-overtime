import { readdirSync, statSync } from "node:fs";
import { resolve } from "node:path";

const required = [
  "frontend/dist/dutylog-vue-app-shell.js",
  "frontend/dist/dutylog-vue-app-shell.css",
];
const missing = required.filter(path => {
  try { return statSync(resolve(path)).size <= 0; } catch { return true; }
});
let chunkCount = 0;
try { chunkCount = readdirSync(resolve("frontend/dist/chunks"), { withFileTypes: true }).filter(entry => entry.isFile() && entry.name.endsWith(".js")).length; } catch { chunkCount = 0; }
if (missing.length || chunkCount === 0) {
  console.error("DutyLog E2E preflight failed: the Vue bundle has not been built. Segmented async chunks are missing.");
  for (const path of missing) console.error(`  missing: ${path}`);
  if (chunkCount === 0) console.error("  missing: frontend/dist/chunks/*.js");
  console.error("Run the frontend gate first: .\\deploy\\scripts\\frontend-gate.ps1 (Windows) or bash ./deploy/scripts/frontend-gate.sh (Linux/CI).");
  process.exit(1);
}
console.log(`DutyLog E2E preflight passed: Vue entry/CSS and ${chunkCount} async chunks are present.`);
