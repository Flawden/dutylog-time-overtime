import { readonly, reactive } from "vue";
import { RELEASE_VERSION } from "@/platform/version";

export type FrontendFailureSource = "vue" | "promise" | "boot" | "network";

export interface FrontendFailure {
  readonly source: FrontendFailureSource;
  readonly message: string;
  readonly route: string;
  readonly releaseVersion: string;
  readonly requestId: string | null;
  readonly occurredAt: string;
}

export interface RequestDiagnostics {
  readonly method: string;
  readonly url: string;
  readonly status: number;
  readonly requestId: string;
}

interface MutableDiagnosticsState {
  route: string;
  requestId: string | null;
  lastRequest: RequestDiagnostics | null;
  fatal: FrontendFailure | null;
}

const mutableState = reactive<MutableDiagnosticsState>({
  route: "today",
  requestId: null,
  lastRequest: null,
  fatal: null,
});

export const frontendDiagnostics = readonly(mutableState);

function boundedText(value: string, maxLength: number): string {
  return value.replace(/[\u0000-\u001f\u007f]+/g, " ").trim().slice(0, maxLength);
}

export function normalizeRequestId(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = boundedText(value, 96);
  return normalized || null;
}

export function safeDiagnosticUrl(value: string): string {
  try {
    const base = globalThis.location?.origin ?? "http://dutylog.invalid";
    return boundedText(new URL(value, base).pathname, 180) || "/";
  } catch {
    return boundedText(value.split(/[?#]/, 1)[0] ?? "", 180) || "/";
  }
}

function errorMessage(value: unknown): string {
  if (value instanceof Error && value.message.trim()) return boundedText(value.message, 240);
  if (typeof value === "string" && value.trim()) return boundedText(value, 240);
  return "Unexpected frontend failure";
}

function errorRequestId(value: unknown): string | null {
  if (!value || typeof value !== "object") return null;
  return normalizeRequestId(Reflect.get(value, "requestId"));
}

export function updateFrontendRoute(route: string): void {
  const normalized = boundedText(route.replace(/^#/, "").split(/[?&]/, 1)[0] ?? "", 80);
  if (normalized) mutableState.route = normalized;
}

export function recordRequestDiagnostics(diagnostics: RequestDiagnostics): void {
  const requestId = normalizeRequestId(diagnostics.requestId);
  if (!requestId) return;
  mutableState.requestId = requestId;
  mutableState.lastRequest = Object.freeze({
    method: boundedText(diagnostics.method.toUpperCase(), 12),
    url: safeDiagnosticUrl(diagnostics.url),
    status: Number.isInteger(diagnostics.status) && diagnostics.status >= 0 && diagnostics.status <= 599
      ? diagnostics.status
      : 0,
    requestId,
  });
}

export function captureFrontendFailure(error: unknown, source: FrontendFailureSource): FrontendFailure {
  const failure: FrontendFailure = Object.freeze({
    source,
    message: errorMessage(error),
    route: mutableState.route,
    releaseVersion: RELEASE_VERSION,
    requestId: errorRequestId(error) ?? mutableState.requestId,
    occurredAt: new Date().toISOString(),
  });
  mutableState.fatal = failure;
  return failure;
}

export function clearFrontendFailure(): void {
  mutableState.fatal = null;
}

export function diagnosticsSnapshot(): Readonly<{
  releaseVersion: string;
  route: string;
  requestId: string | null;
  lastRequest: RequestDiagnostics | null;
  fatal: FrontendFailure | null;
}> {
  return Object.freeze({
    releaseVersion: RELEASE_VERSION,
    route: mutableState.route,
    requestId: mutableState.requestId,
    lastRequest: mutableState.lastRequest,
    fatal: mutableState.fatal,
  });
}

export function installUnhandledRejectionDiagnostics(target: Window = window): () => void {
  const handler = (event: PromiseRejectionEvent) => {
    captureFrontendFailure(event.reason, "promise");
    // Deliberately do not call preventDefault(): strict browser collectors must still see unexpected failures.
  };
  target.addEventListener("unhandledrejection", handler);
  return () => target.removeEventListener("unhandledrejection", handler);
}

export function resetFrontendDiagnosticsForTests(): void {
  mutableState.route = "today";
  mutableState.requestId = null;
  mutableState.lastRequest = null;
  mutableState.fatal = null;
}
