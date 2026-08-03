import { createPinia } from "pinia";
import { createApp, nextTick } from "vue";
import App from "./App.vue";
import { announceVueReady, createLegacyBridge } from "./platform/bridge/legacyBridge";
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

async function boot(): Promise<void> {
  const host = document.getElementById("dutylog-vue-root");
  if (!host) return;

  const bridge = createLegacyBridge(window);
  const pinia = createPinia();
  const app = createApp(App, { bridge });
  app.use(pinia);
  app.use(platformRouter);
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
    navigateLegacy: (view: string) => bridge.navigate(view),
  });

  window.DutyLogVuePlatform = platform;
  announceVueReady(window, platform);
}

void boot();
