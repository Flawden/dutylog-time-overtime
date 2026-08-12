import { normalizeSection, type DutyLogRoute } from "@/app/navigation";


export interface DutyLogRouteSnapshot {
  readonly rawRoute: string;
  readonly activeRoute: DutyLogRoute;
}

export interface DutyLogRouteAccess {
  readonly profileLoaded: boolean;
  readonly admin: boolean;
  readonly modulesLoaded: boolean;
  readonly modules: Readonly<Record<string, boolean>>;
}

const ROUTE_MODULE: Partial<Record<DutyLogRoute, string>> = Object.freeze({
  vacation: "vacation",
  overtime: "overtime",
  payroll: "payroll",
  tasks: "tasks",
  important: "important_dates",
  admin: "admin",
});

function normalizeHash(value: string): string {
  const normalized = value.trim().replace(/^#/, "");
  return normalized || "today";
}

export function readHashRoute(target: Window = window): DutyLogRouteSnapshot {
  const rawRoute = normalizeHash(target.location.hash);
  return Object.freeze({ rawRoute, activeRoute: normalizeSection(rawRoute) });
}

export function guardHashRoute(
  requested: DutyLogRouteSnapshot,
  access: DutyLogRouteAccess,
): DutyLogRouteSnapshot {
  const activeRoute = requested.activeRoute;
  if (activeRoute === "admin" && access.profileLoaded && !access.admin) {
    return Object.freeze({ rawRoute: "calendar", activeRoute: "calendar" });
  }
  const moduleKey = ROUTE_MODULE[activeRoute];
  if (moduleKey && access.modulesLoaded && access.modules[moduleKey] === false) {
    return Object.freeze({ rawRoute: "calendar", activeRoute: "calendar" });
  }
  return requested;
}

export function navigateHashRoute(view: string, target: Window = window): void {
  const rawRoute = normalizeHash(view);
  const nextHash = `#${rawRoute}`;
  if (target.location.hash !== nextHash) target.location.hash = nextHash;
}

export function subscribeHashRoute(
  listener: (snapshot: DutyLogRouteSnapshot) => void,
  target: Window = window,
): () => void {
  const handler = () => listener(readHashRoute(target));
  target.addEventListener("hashchange", handler);
  return () => target.removeEventListener("hashchange", handler);
}
