import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, relative, resolve } from "node:path";
import { gzipSync } from "node:zlib";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const frontendDirectory = resolve(scriptDirectory, "..");
const distDirectory = resolve(frontendDirectory, "dist");
const entryRelativePath = "dutylog-vue-app-shell.js";
const budgetPath = resolve(frontendDirectory, "browser-bundle-budget.json");
const budget = JSON.parse(await readFile(budgetPath, "utf8"));

async function collectJavaScriptBundles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) files.push(...await collectJavaScriptBundles(path));
    else if (entry.isFile() && entry.name.endsWith(".js")) files.push(path);
  }
  return files.sort();
}

const bundlePaths = await collectJavaScriptBundles(distDirectory);
const entryPath = resolve(distDirectory, entryRelativePath);
const forbiddenRuntimePatterns = [
  ["unreplaced process.env", /\bprocess\.env\b/g],
  ["CommonJS require", /\brequire\s*\(/g],
  ["CommonJS module.exports", /\bmodule\.exports\b/g],
  ["Node __dirname", /\b__dirname\b/g],
  ["Node __filename", /\b__filename\b/g],
];

const violations = [];
const stats = [];
for (const bundlePath of bundlePaths) {
  const bundle = await readFile(bundlePath, "utf8");
  const label = relative(distDirectory, bundlePath).replaceAll("\\", "/");
  for (const [patternLabel, pattern] of forbiddenRuntimePatterns) {
    pattern.lastIndex = 0;
    const matches = [...bundle.matchAll(pattern)];
    for (const match of matches.slice(0, 5)) {
      const start = Math.max(0, (match.index ?? 0) - 60);
      const end = Math.min(bundle.length, (match.index ?? 0) + match[0].length + 60);
      violations.push(`${label}: ${patternLabel}: ${bundle.slice(start, end).replaceAll("\n", " ")}`);
    }
  }
  stats.push({ path: bundlePath, label, bytes: Buffer.byteLength(bundle, "utf8"), gzipBytes: gzipSync(bundle, { level: 9 }).byteLength });
}

const entry = stats.find(item => item.path === entryPath);
const chunks = stats.filter(item => item.path !== entryPath);
const totalBytes = stats.reduce((sum, item) => sum + item.bytes, 0);
const totalGzipBytes = stats.reduce((sum, item) => sum + item.gzipBytes, 0);

for (const key of ["maxEntryBytes", "maxEntryGzipBytes", "maxChunkBytes", "maxChunkGzipBytes", "maxTotalBytes", "maxTotalGzipBytes"]) {
  if (!Number.isInteger(budget[key])) violations.push(`invalid browser bundle budget field ${key}: ${budgetPath}`);
}
if (!entry) violations.push(`entry bundle is missing: ${entryRelativePath}`);
if (chunks.length < 2) violations.push(`bundle segmentation missing: expected at least 2 async chunks, found ${chunks.length}`);
if (entry) {
  if (entry.bytes > budget.maxEntryBytes) violations.push(`entry raw bundle ${entry.bytes} B exceeds ${budget.maxEntryBytes} B`);
  if (entry.gzipBytes > budget.maxEntryGzipBytes) violations.push(`entry gzip bundle ${entry.gzipBytes} B exceeds ${budget.maxEntryGzipBytes} B`);
}
for (const chunk of chunks) {
  if (chunk.bytes > budget.maxChunkBytes) violations.push(`${chunk.label} raw ${chunk.bytes} B exceeds per-chunk ${budget.maxChunkBytes} B`);
  if (chunk.gzipBytes > budget.maxChunkGzipBytes) violations.push(`${chunk.label} gzip ${chunk.gzipBytes} B exceeds per-chunk ${budget.maxChunkGzipBytes} B`);
}
if (totalBytes > budget.maxTotalBytes) violations.push(`total raw bundles ${totalBytes} B exceed ${budget.maxTotalBytes} B`);
if (totalGzipBytes > budget.maxTotalGzipBytes) violations.push(`total gzip bundles ${totalGzipBytes} B exceed ${budget.maxTotalGzipBytes} B`);

if (violations.length > 0) {
  console.error(`Browser bundle audit failed for ${distDirectory}`);
  for (const violation of violations) console.error(`- ${violation}`);
  process.exitCode = 1;
} else {
  console.log(`Browser bundle audit passed: ${stats.length} JS files (${chunks.length} async chunks)`);
  console.log(`Entry budget: ${entry.bytes}/${budget.maxEntryBytes} B raw, ${entry.gzipBytes}/${budget.maxEntryGzipBytes} B gzip`);
  console.log(`Total JS budget: ${totalBytes}/${budget.maxTotalBytes} B raw, ${totalGzipBytes}/${budget.maxTotalGzipBytes} B gzip`);
}
