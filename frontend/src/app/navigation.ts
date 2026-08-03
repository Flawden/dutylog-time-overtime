export type DutyLogRoute =
  | "today"
  | "calendar"
  | "vacation"
  | "overtime"
  | "payroll"
  | "tasks"
  | "important"
  | "settings"
  | "admin";

export interface NavigationItem {
  route: DutyLogRoute;
  icon: "today" | "calendar" | "vacation" | "overtime" | "payroll" | "tasks" | "important" | "settings" | "admin";
  labels: { ru: string; en: string };
}

export const NAVIGATION_ITEMS: readonly NavigationItem[] = Object.freeze([
  { route: "today", icon: "today", labels: { ru: "Сегодня", en: "Today" } },
  { route: "calendar", icon: "calendar", labels: { ru: "Календарь", en: "Calendar" } },
  { route: "vacation", icon: "vacation", labels: { ru: "Отсутствия", en: "Absences" } },
  { route: "overtime", icon: "overtime", labels: { ru: "Переработки", en: "Time bank" } },
  { route: "payroll", icon: "payroll", labels: { ru: "Зарплата", en: "Payroll" } },
  { route: "tasks", icon: "tasks", labels: { ru: "Задачи", en: "Tasks" } },
  { route: "important", icon: "important", labels: { ru: "Даты", en: "Dates" } },
  { route: "settings", icon: "settings", labels: { ru: "Ещё", en: "More" } },
  { route: "admin", icon: "admin", labels: { ru: "Система", en: "System" } },
]);

const navigationByRoute = new Map(NAVIGATION_ITEMS.map(item => [item.route, item]));

export function navigationItem(route: string): NavigationItem | null {
  return navigationByRoute.get(route as DutyLogRoute) ?? null;
}

export function normalizeSection(route: string): DutyLogRoute {
  const normalized = route.trim().replace(/^#/, "");
  if (normalized.startsWith("settings-")) return "settings";
  return navigationByRoute.has(normalized as DutyLogRoute)
    ? normalized as DutyLogRoute
    : "today";
}
