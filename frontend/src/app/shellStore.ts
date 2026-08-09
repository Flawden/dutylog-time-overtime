import { defineStore } from "pinia";
import { navigationItem, normalizeSection, type DutyLogRoute } from "./navigation";

interface ShellToast { id: number; message: string; tone: "success" | "info" | "warning" | "danger" }

const FALLBACK_NAVIGATION: readonly DutyLogRoute[] = Object.freeze(["today", "calendar", "vacation", "overtime", "tasks", "settings"]);

function booleanMapEquals(current: Readonly<Record<string, boolean>>, next: Readonly<Record<string, boolean>>): boolean {
  const currentEntries = Object.entries(current);
  const nextEntries = Object.entries(next);
  if (currentEntries.length !== nextEntries.length) return false;
  return nextEntries.every(([key, value]) => current[key] === value);
}

function validRoutes(routes: readonly string[] | undefined): DutyLogRoute[] {
  const result = (routes ?? []).map(navigationItem).filter((item): item is NonNullable<typeof item> => item !== null).map(item => item.route);
  return result.length ? [...new Set(result)] : [...FALLBACK_NAVIGATION];
}

export const useShellStore = defineStore("dutylog-shell", {
  state: () => ({
    activeRoute: "today" as DutyLogRoute,
    rawRoute: "today",
    language: "ru" as "ru" | "en",
    online: true,
    modulesLoaded: false,
    onboardingCompleted: false,
    modules: {} as Record<string, boolean>,
    primaryNavigation: [...FALLBACK_NAVIGATION] as DutyLogRoute[],
    availableNavigation: [...FALLBACK_NAVIGATION] as DutyLogRoute[],
    displayName: "DutyLog",
    initials: "DL",
    admin: false,
    moreOpen: false,
    toasts: [] as ShellToast[],
    nextToastId: 1,
  }),
  getters: {
    secondaryNavigation(state): DutyLogRoute[] {
      return state.availableNavigation.filter(route => !state.primaryNavigation.includes(route));
    },
  },
  actions: {
    synchronize(snapshot: DutyLogLegacySnapshot | null): void {
      if (!snapshot) return;
      this.rawRoute = snapshot.route;
      this.activeRoute = normalizeSection(snapshot.route);
      this.language = snapshot.language;
      this.online = snapshot.online;
      this.modulesLoaded = snapshot.modulesLoaded;
      this.onboardingCompleted = snapshot.profile?.onboardingCompleted === true;
      if (snapshot.modules && !booleanMapEquals(this.modules, snapshot.modules)) this.modules = { ...snapshot.modules };
      this.primaryNavigation = validRoutes(snapshot.navigation);
      this.availableNavigation = validRoutes(snapshot.availableViews);
      this.displayName = snapshot.profile?.displayName || "DutyLog";
      this.initials = snapshot.profile?.initials || "DL";
      this.admin = Boolean(snapshot.profile?.admin);
      if (this.admin && !this.availableNavigation.includes("admin")) this.availableNavigation.push("admin");
    },
    openMore(): void { this.moreOpen = true; },
    closeMore(): void { this.moreOpen = false; },
    announce(message: string, tone: ShellToast["tone"] = "info"): void {
      const id = this.nextToastId++;
      this.toasts.push({ id, message, tone });
      globalThis.setTimeout(() => { this.toasts = this.toasts.filter(item => item.id !== id); }, 3600);
    },
  },
});
