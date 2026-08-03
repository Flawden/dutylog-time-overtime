import { defineStore } from "pinia";
import { FRONTEND_ARCHITECTURE, RELEASE_VERSION } from "../version";

export const usePlatformStore = defineStore("dutylog-platform", {
  state: () => ({
    releaseVersion: RELEASE_VERSION,
    architecture: FRONTEND_ARCHITECTURE,
    phase: "booting" as "booting" | "mounted" | "ready",
    legacyConnected: false,
    shellReady: false,
  }),
  actions: {
    markMounted(legacyConnected: boolean) {
      this.phase = "mounted";
      this.legacyConnected = legacyConnected;
    },
    markReady(legacyConnected: boolean) {
      this.phase = "ready";
      this.legacyConnected = legacyConnected;
      this.shellReady = true;
    },
  },
});
