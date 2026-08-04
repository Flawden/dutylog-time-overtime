import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetFrontendDiagnosticsForTests, diagnosticsSnapshot } from "@/platform/diagnostics/frontendDiagnostics";
import { createDutyLogHttpClient, csrfTokenFromCookie } from "./httpClient";

beforeEach(() => resetFrontendDiagnosticsForTests());

describe("DutyLog HTTP client", () => {
  it("reads and decodes the Spring XSRF cookie", () => {
    expect(csrfTokenFromCookie("theme=dark; XSRF-TOKEN=a%2Fb%3D; language=ru")).toBe("a/b=");
    expect(csrfTokenFromCookie("theme=dark")).toBeNull();
  });

  it("adds same-origin credentials, request id and CSRF only to mutations", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200, headers: { "X-Request-Id": "server-get" } }))
      .mockResolvedValueOnce(new Response(null, { status: 204, headers: { "X-Request-Id": "server-put" } }));
    const request = createDutyLogHttpClient({
      fetchImpl: fetchImpl as typeof fetch,
      readCookie: () => "XSRF-TOKEN=token-1",
      requestIdFactory: () => "client-fixed",
    });

    await request<{ ok: boolean }>("/api/profile");
    await request<null, { language: string }>("/api/profile", { method: "PUT", body: { language: "ru" } });

    const getInit = fetchImpl.mock.calls[0]?.[1];
    const putInit = fetchImpl.mock.calls[1]?.[1];
    expect(getInit?.credentials).toBe("same-origin");
    expect(new Headers(getInit?.headers).get("X-Request-Id")).toBe("client-fixed");
    expect(new Headers(getInit?.headers).has("X-XSRF-TOKEN")).toBe(false);
    expect(new Headers(putInit?.headers).get("X-XSRF-TOKEN")).toBe("token-1");
    expect(putInit?.body).toBe(JSON.stringify({ language: "ru" }));
    expect(diagnosticsSnapshot().requestId).toBe("server-put");
  });

  it("normalizes API errors without losing status, code and request id", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: "Closed period", code: "PERIOD_CLOSED", requestId: "payload-409" }), {
        status: 409,
        headers: { "Content-Type": "application/json", "X-Request-Id": "header-409" },
      }),
    );
    const request = createDutyLogHttpClient({ fetchImpl: fetchImpl as typeof fetch, requestIdFactory: () => "client-409" });

    await expect(request("/api/example", { method: "POST", body: {} })).rejects.toMatchObject({
      message: "Closed period",
      status: 409,
      code: "PERIOD_CLOSED",
      requestId: "payload-409",
      url: "/api/example",
      method: "POST",
    });
  });

  it("turns transport failures into correlated API errors", async () => {
    const request = createDutyLogHttpClient({
      fetchImpl: vi.fn().mockRejectedValue(new TypeError("offline")) as unknown as typeof fetch,
      requestIdFactory: () => "network-req-1",
    });
    await expect(request("/api/v1/modules")).rejects.toMatchObject({ status: 0, requestId: "network-req-1" });
    expect(diagnosticsSnapshot()).toMatchObject({
      fatal: null,
      lastRequest: { method: "GET", url: "/api/v1/modules", status: 0, requestId: "network-req-1" },
    });
  });
});
