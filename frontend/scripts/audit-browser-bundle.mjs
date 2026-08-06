import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { gzipSync } from "node:zlib";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const frontendDirectory = resolve(scriptDirectory, "..");
const bundlePath = resolve(frontendDirectory, "dist/dutylog-vue-app-shell.js");
const budgetPath = resolve(frontendDirectory, "browser-bundle-budget.json");
const bundle = await readFile(bundlePath, "utf8");
const budget = JSON.parse(await readFile(budgetPath, "utf8"));

const forbiddenRuntimePatterns = [
  ["unreplaced process.env", /\bprocess\.env\b/g],
  ["CommonJS require", /\brequire\s*\(/g],
  ["CommonJS module.exports", /\bmodule\.exports\b/g],
  ["Node __dirname", /\b__dirname\b/g],
  ["Node __filename", /\b__filename\b/g],
];

const violations = [];
for (const [label, pattern] of forbiddenRuntimePatterns) {
  const matches = [...bundle.matchAll(pattern)];
  for (const match of matches.slice(0, 5)) {
    const start = Math.max(0, (match.index ?? 0) - 60);
    const end = Math.min(bundle.length, (match.index ?? 0) + match[0].length + 60);
    violations.push(`${label}: ${bundle.slice(start, end).replaceAll("\n", " ")}`);
  }
}

const bytes = Buffer.byteLength(bundle, "utf8");
const gzipBytes = gzipSync(bundle, { level: 9 }).byteLength;
if (!Number.isInteger(budget.maxBytes) || !Number.isInteger(budget.maxGzipBytes)) {
  violations.push(`invalid browser bundle budget: ${budgetPath}`);
} else {
  if (bytes > budget.maxBytes) violations.push(`raw bundle ${bytes} B exceeds ${budget.maxBytes} B`);
  if (gzipBytes > budget.maxGzipBytes) violations.push(`gzip bundle ${gzipBytes} B exceeds ${budget.maxGzipBytes} B`);
}

if (violations.length > 0) {
  console.error(`Browser bundle audit failed for ${bundlePath}`);
  for (const violation of violations) console.error(`- ${violation}`);
  process.exitCode = 1;
} else {
  console.log(`Browser bundle audit passed: ${bundlePath}`);
  console.log(`Browser bundle budget: ${bytes}/${budget.maxBytes} B raw, ${gzipBytes}/${budget.maxGzipBytes} B gzip`);
}
