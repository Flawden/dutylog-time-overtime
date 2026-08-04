import { createPinia } from "pinia";
import { createApp, nextTick } from "vue";
import App from "./App.vue";
import { announceVueReady, createLegacyBridge } from "./platform/bridge/legacyBridge";
import {
  captureFrontendFailure,
  diagnosticsSnapshot,
  installUnhandledRejectionDiagnostics,
} from "./platform/diagnostics/frontendDiagnostics";
import { platformRouter } from "./platform/router";
import { usePlatformStore } from "./platform/stores/platformStore";
import { FRONTEND_ARCHITECTURE, RELEASE_VERSION } from "./platform/version";
import "./styles/foundation.css";
import "./styles/design-system.css";

function freezeSnapshot(store: ReturnType<typeof usePlatformStore>) {
  return Object.freeze({
    releaseVersion: store.releaseVersion,
    architecture: store.architecture,
    phase: store.phase,
    legacyConnected: store.legacyConnected,
    shellReady: store.shellReady,
  });
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, character => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[character] ?? character);
}

function renderBootRecovery(host: HTMLElement, error: unknown): void {
  const failure = captureFrontendFailure(error, "boot");
  host.removeAttribute("aria-hidden");
  host.dataset.vueReady = "failed";
  host.innerHTML = "";
  const panel = document.createElement("section");
  panel.className = "vue-recovery";
  panel.dataset.vueRecoveryUi = "";
  panel.setAttribute("role", "alert");
  panel.innerHTML = `<div class="vue-recovery__card"><p class="vue-recovery__eyebrow">DutyLog recovery</p><h1>Интерфейс не загрузился</h1><p>Данные не удалены. Перезагрузите приложение.</p><dl><div><dt>Версия</dt><dd>${escapeHtml(failure.releaseVersion)}</dd></div><div><dt>Маршрут</dt><dd>${escapeHtml(failure.route)}</dd></div></dl><div class="vue-recovery__actions"><button type="button" class="ui-button ui-button--primary" data-vue-recovery-reload>Перезагрузить</button></div></div>`;
  panel.querySelector("[data-vue-recovery-reload]")?.addEventListener("click", () => globalThis.location.reload());
  host.append(panel);
}

async function boot(): Promise<void> {
  const host = document.getElementById("dutylog-vue-root");
  if (!host) return;

  installUnhandledRejectionDiagnostics(window);
  const bridge = createLegacyBridge(window);
  const pinia = createPinia();
  const app = createApp(App, { bridge });
  app.config.errorHandler = (error, _instance, info) => {
    captureFrontendFailure(error, "vue");
    console.error(`[DutyLog Vue error] ${info}`, error);
  };
  app.use(pinia);
  app.use(platformRouter);

  try {
    host.removeAttribute("aria-hidden");
    app.mount(host);
    await platformRouter.isReady();
    await nextTick();

    const store = usePlatformStore(pinia);
    store.markReady(bridge.connected());
    host.dataset.vueReady = "true";
    host.dataset.vueVersion = RELEASE_VERSION;
    host.dataset.vueArchitecture = FRONTEND_ARCHITECTURE;

    const platform: DutyLogVuePlatform = Object.freeze({
      version: RELEASE_VERSION,
      architecture: FRONTEND_ARCHITECTURE,
      mountedAt: new Date().toISOString(),
      snapshot: () => freezeSnapshot(store),
      diagnostics: () => diagnosticsSnapshot(),
      navigateLegacy: (view: string) => bridge.navigate(view),
    });

    window.DutyLogVuePlatform = platform;
    announceVueReady(window, platform);
  } catch (error) {
    renderBootRecovery(host, error);
    throw error;
  }
}

void boot();
