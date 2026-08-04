import { normalizeRequestId, recordRequestDiagnostics, safeDiagnosticUrl } from "@/platform/diagnostics/frontendDiagnostics";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export class DutyLogApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly url: string;
  readonly method: string;
  readonly requestId: string;

  constructor(message: string, options: { status: number; code?: string | null; url: string; method: string; requestId: string }) {
    super(message);
    this.name = "DutyLogApiError";
    this.status = options.status;
    this.code = options.code ?? null;
    this.url = options.url;
    this.method = options.method;
    this.requestId = options.requestId;
  }
}

export function csrfTokenFromCookie(cookie: string): string | null {
  const match = cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

function defaultRequestId(): string {
  const random = globalThis.crypto?.randomUUID?.().replace(/-/g, "").slice(0, 16)
    ?? Math.random().toString(36).slice(2, 18);
  return `web-${Date.now().toString(36)}-${random}`.slice(0, 64);
}

export interface DutyLogRequestOptions<TBody = unknown> extends Omit<RequestInit, "body" | "method" | "headers"> {
  method?: string;
  body?: TBody;
  headers?: HeadersInit;
}

export interface DutyLogHttpClientOptions {
  fetchImpl?: typeof fetch;
  readCookie?: () => string;
  onUnauthorized?: () => void;
  requestIdFactory?: () => string;
}

export function createDutyLogHttpClient(options: DutyLogHttpClientOptions = {}) {
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis);
  const readCookie = options.readCookie ?? (() => globalThis.document?.cookie ?? "");
  const requestIdFactory = options.requestIdFactory ?? defaultRequestId;
  const onUnauthorized = options.onUnauthorized ?? (() => {
    if (globalThis.window) globalThis.window.location.assign("/login.html");
  });

  return async function requestJson<TResponse, TBody = unknown>(
    url: string,
    request: DutyLogRequestOptions<TBody> = {},
  ): Promise<TResponse | null> {
    const { method: rawMethod, headers: rawHeaders, body: requestBody, ...rest } = request;
    const method = (rawMethod ?? "GET").toUpperCase();
    const headers = new Headers(rawHeaders);
    const clientRequestId = normalizeRequestId(headers.get("X-Request-Id"))
      ?? normalizeRequestId(requestIdFactory())
      ?? defaultRequestId();
    headers.set("Accept", "application/json");
    headers.set("X-Request-Id", clientRequestId);

    let body: BodyInit | undefined;
    if (requestBody !== undefined) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(requestBody);
    }

    if (!SAFE_METHODS.has(method)) {
      const token = csrfTokenFromCookie(readCookie());
      if (token) headers.set("X-XSRF-TOKEN", token);
    }

    const init: RequestInit = {
      ...rest,
      method,
      headers,
      credentials: "same-origin",
    };
    if (body !== undefined) init.body = body;

    let response: Response;
    try {
      response = await fetchImpl(url, init);
    } catch (error) {
      recordRequestDiagnostics({ method, url, status: 0, requestId: clientRequestId });
      throw new DutyLogApiError("Network request failed", {
        status: 0,
        url: safeDiagnosticUrl(url),
        method,
        requestId: clientRequestId,
      });
    }

    const requestId = normalizeRequestId(response.headers.get("X-Request-Id")) ?? clientRequestId;
    recordRequestDiagnostics({ method, url, status: response.status, requestId });

    if (response.status === 401) {
      onUnauthorized();
      throw new DutyLogApiError("401: unauthenticated", { status: 401, url: safeDiagnosticUrl(url), method, requestId });
    }

    if (!response.ok) {
      let message = `${method} ${url} → ${response.status}`;
      let code: string | null = null;
      let payloadRequestId: string | null = null;
      try {
        const payload = await response.json() as { error?: string; message?: string; code?: string; requestId?: string };
        message = payload.error ?? payload.message ?? message;
        code = payload.code ?? null;
        payloadRequestId = normalizeRequestId(payload.requestId);
      } catch {
        // Non-JSON errors keep the HTTP fallback message.
      }
      throw new DutyLogApiError(message, { status: response.status, code, url: safeDiagnosticUrl(url), method, requestId: payloadRequestId ?? requestId });
    }

    if (response.status === 204) return null;
    const text = await response.text();
    if (!text.trim()) return null;
    return JSON.parse(text) as TResponse;
  };
}
