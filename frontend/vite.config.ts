import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vitest/config";

const releaseVersion = "27.36.5";

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
    sourcemap: true,
    cssCodeSplit: false,
    lib: {
      entry: fileURLToPath(new URL("./src/main.ts", import.meta.url)),
      formats: ["es"],
      fileName: () => "dutylog-vue-app-shell.js",
    },
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
        entryFileNames: "dutylog-vue-app-shell.js",
        assetFileNames: assetInfo => assetInfo.name === "style.css"
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
