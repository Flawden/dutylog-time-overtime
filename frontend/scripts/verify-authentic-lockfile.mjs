import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const packagePath = `${root}/package.json`;
const lockPath = `${root}/package-lock.json`;

function fail(message) {
  console.error(`Authentic frontend lockfile violation: ${message}`);
  process.exitCode = 1;
}

if (!existsSync(lockPath)) {
  fail("frontend/package-lock.json was not generated.");
  process.exit(process.exitCode);
}

const pkg = JSON.parse(readFileSync(packagePath, "utf8"));
const lock = JSON.parse(readFileSync(lockPath, "utf8"));
const entries = Object.entries(lock.packages ?? {}).filter(([path]) => path !== "");
const registryEntries = entries.filter(([, entry]) => typeof entry.resolved === "string" && entry.resolved.startsWith("https://registry.npmjs.org/"));
const integrityEntries = entries.filter(([, entry]) => typeof entry.integrity === "string" && entry.integrity.startsWith("sha512-"));
const graphEntries = entries.filter(([, entry]) => entry.dependencies || entry.optionalDependencies || entry.peerDependencies);

if (lock.lockfileVersion !== 3) fail(`lockfileVersion ${lock.lockfileVersion ?? "missing"}; expected 3.`);
if (lock.packages?.[""]?.version !== pkg.version) fail("lockfile root version differs from package.json.");
if (entries.length < 80) fail(`dependency graph has only ${entries.length} package entries.`);
if (registryEntries.length < 70) fail(`only ${registryEntries.length} packages pin registry tarballs.`);
if (integrityEntries.length < 70) fail(`only ${integrityEntries.length} packages pin SHA-512 integrity.`);
if (graphEntries.length < 20) fail(`only ${graphEntries.length} package entries carry dependency or peer edges.`);

for (const section of ["dependencies", "devDependencies"]) {
  for (const [name, version] of Object.entries(pkg[section] ?? {})) {
    const entry = lock.packages?.[`node_modules/${name}`];
    if (!entry) fail(`direct dependency ${name} is missing.`);
    else {
      if (entry.version !== version) fail(`${name} resolved to ${entry.version ?? "missing"}; expected ${version}.`);
      if (!entry.resolved?.startsWith("https://registry.npmjs.org/")) fail(`${name} has no npm registry tarball.`);
      if (!entry.integrity?.startsWith("sha512-")) fail(`${name} has no SHA-512 integrity.`);
    }
  }
}

for (const required of [
  "node_modules/vue-tsc",
  "node_modules/@vue/language-core",
  "node_modules/@volar/language-core",
  "node_modules/@volar/typescript",
  "node_modules/typescript",
  "node_modules/muggle-string",
  "node_modules/alien-signals",
]) {
  const entry = lock.packages?.[required];
  if (!entry?.resolved || !entry?.integrity) fail(`${required} is not fully pinned.`);
}

if (process.exitCode) process.exit(process.exitCode);
console.log(`Authentic npm lockfile verified (${entries.length} packages, ${graphEntries.length} graph entries).`);
