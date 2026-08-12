import { normalizeSection, type DutyLogRoute } from "@/app/navigation";

export interface DutyLogRouteSnapshot {
  readonly rawRoute: string;
  readonly activeRoute: DutyLogRoute;
}

function normalizeHash(value: string): string {
  const normalized = value.trim().replace(/^#/, "");
  return normalized || "today";
}

export function readHashRoute(target: Window = window): DutyLogRouteSnapshot {
  const rawRoute = normalizeHash(target.location.hash);
  return Object.freeze({ rawRoute, activeRoute: normalizeSection(rawRoute) });
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
