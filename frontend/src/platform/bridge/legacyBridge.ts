export const VUE_READY_EVENT = "dutylog:vue-ready";
export const LEGACY_COMMAND_EVENT = "dutylog:legacy-command";
export const LEGACY_STATE_EVENT = "dutylog:legacy-state";

export type LegacyCommand =
  | { type: "navigate"; view: string }
  | { type: "open-modal"; id: string; focusId?: string | null }
  | { type: "logout" };

export interface LegacyBridge {
  connected(): boolean;
  snapshot(): DutyLogLegacySnapshot | null;
  navigate(view: string): void;
  openModal(id: string, focusId?: string | null): void;
  logout(): void;
  retireDomainOwners(domain: "absence-time-bank"): void;
  subscribe(listener: (snapshot: DutyLogLegacySnapshot) => void): () => void;
}

function normalizeView(view: string): string {
  return view.trim().replace(/^#/, "");
}

export function createLegacyBridge(target: Window = window): LegacyBridge {
  const adapter = () => target.DutyLogLegacyPlatform;
  const emitFallback = (command: LegacyCommand) => {
    target.dispatchEvent(new CustomEvent<LegacyCommand>(LEGACY_COMMAND_EVENT, { detail: command }));
  };

  return {
    connected: () => Boolean(adapter()),
    snapshot: () => adapter()?.snapshot() ?? null,
    navigate(view: string) {
      const normalized = normalizeView(view);
      if (!normalized) return;
      if (adapter()) adapter()?.navigate(normalized);
      else emitFallback({ type: "navigate", view: normalized });
    },
    openModal(id: string, focusId: string | null = null) {
      if (!id.trim()) return;
      if (adapter()) adapter()?.openModal(id, focusId);
      else emitFallback({ type: "open-modal", id, focusId });
    },
    logout() {
      if (adapter()) adapter()?.logout();
      else emitFallback({ type: "logout" });
    },
    retireDomainOwners(domain) {
      adapter()?.retireDomainOwners?.(domain);
    },
    subscribe(listener) {
      const direct = adapter()?.subscribe(listener);
      if (direct) return direct;
      const handler = (event: Event) => listener((event as CustomEvent<DutyLogLegacySnapshot>).detail);
      target.addEventListener(LEGACY_STATE_EVENT, handler);
      return () => target.removeEventListener(LEGACY_STATE_EVENT, handler);
    },
  };
}

export function announceVueReady(target: Window, platform: DutyLogVuePlatform): void {
  target.dispatchEvent(new CustomEvent<DutyLogVuePlatform>(VUE_READY_EVENT, { detail: platform }));
}
