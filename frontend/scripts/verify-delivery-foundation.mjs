import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const packageJson = JSON.parse(readFileSync(`${root}/package.json`, "utf8"));
const lock = JSON.parse(readFileSync(`${root}/package-lock.json`, "utf8"));
const npmrc = readFileSync(`${root}/.npmrc`, "utf8");
const expectedNode = readFileSync(`${root}/.node-version`, "utf8").trim();
const expectedNpm = readFileSync(`${root}/.npm-version`, "utf8").trim();
const actualNode = process.version.replace(/^v/, "");
const actualNpm = execFileSync("npm", ["--version"], { encoding: "utf8" }).trim();

function fail(message) {
  console.error(`Vue delivery foundation violation: ${message}`);
  process.exitCode = 1;
}

if (actualNode !== expectedNode) fail(`Node ${actualNode} is running; ${expectedNode} is required.`);
if (actualNpm !== expectedNpm) fail(`npm ${actualNpm} is running; ${expectedNpm} is required.`);
if (packageJson.engines?.node !== expectedNode) fail("package.json engines.node differs from .node-version.");
if (packageJson.engines?.npm !== expectedNpm) fail("package.json engines.npm differs from .npm-version.");
if (packageJson.packageManager !== `npm@${expectedNpm}`) fail("packageManager does not pin the required npm version.");
if (lock.lockfileVersion !== 3) fail("frontend/package-lock.json must use lockfileVersion 3.");
if (lock.packages?.[""]?.version !== packageJson.version) fail("lockfile root version differs from package.json.");
if (!/^package-lock=true$/m.test(npmrc)) fail("frontend/.npmrc must enable package-lock.");
if (!/^engine-strict=true$/m.test(npmrc)) fail("frontend/.npmrc must enable engine-strict.");

for (const section of ["dependencies", "devDependencies"]) {
  for (const [name, version] of Object.entries(packageJson[section] ?? {})) {
    if (/^[~^*]|\s|\|\||[<>]/.test(version)) fail(`${section}.${name} is not exact: ${version}`);
    const locked = lock.packages?.[`node_modules/${name}`]?.version;
    if (locked !== version) fail(`${name} is ${version} in package.json but ${locked ?? "missing"} in package-lock.json.`);
  }
}

if (process.exitCode) process.exit(process.exitCode);
console.log(`Vue delivery foundation verified with Node ${actualNode}, npm ${actualNpm} and lockfile v${lock.lockfileVersion}.`);
