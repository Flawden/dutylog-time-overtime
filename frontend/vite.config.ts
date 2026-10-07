import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import packageMetadata from "./package.json";
import { defineConfig } from "vitest/config";

const releaseVersion = packageMetadata.version;
const productionSourceMaps = process.env.DUTYLOG_FRONTEND_SOURCEMAPS === "true" ? "hidden" : false;

function manualChunkName(id: string): string | undefined {
  const normalizedId = id.replaceAll("\\", "/");
  if (normalizedId.endsWith("/src/main.ts")) return "main";
  if (normalizedId.includes("/node_modules/")) return "vendor";
  if (normalizedId.includes("/src/generated/")) return "api-contract";
  if (normalizedId.includes("/src/platform/")) return "platform";
  if (normalizedId.includes("/src/features/settings-workspace/")) return "settings-workspace";
  return undefined;
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  define: {
    "process.env.NODE_ENV": JSON.stringify("production"),
    __DUTYLOG_RELEASE_VERSION__: JSON.stringify(releaseVersion),
    __DUTYLOG_FRONTEND_ARCHITECTURE__: JSON.stringify("vue-shell-v1"),
  },
  server: {
    strictPort: true,
    proxy: {
      "/api": "http://127.0.0.1:8081",
      "/actuator": "http://127.0.0.1:8081",
      "/login.html": "http://127.0.0.1:8081",
      "/perform_login": "http://127.0.0.1:8081",
      "/logout": "http://127.0.0.1:8081",
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    sourcemap: productionSourceMaps,
    cssCodeSplit: false,
    modulePreload: false,
    // This is an application entry, not a reusable library. Application mode
    // enables whitespace minification and preserves normal chunk hashing.
    rollupOptions: {
      input: fileURLToPath(new URL("./src/entry.ts", import.meta.url)),
      output: {
        entryFileNames: "dutylog-vue-app-shell.js",
        chunkFileNames: "chunks/[name]-[hash].js",
        manualChunks: manualChunkName,
        assetFileNames: assetInfo => assetInfo.name === "style.css" || assetInfo.name?.endsWith(".css")
          ? "dutylog-vue-app-shell.css"
          : "[name][extname]",
      },
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.spec.ts"],
    clearMocks: true,
    restoreMocks: true,
  },
});
