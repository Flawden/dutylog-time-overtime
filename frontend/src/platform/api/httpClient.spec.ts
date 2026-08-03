import { describe, expect, it, vi } from "vitest";
import { createDutyLogHttpClient, csrfTokenFromCookie, DutyLogApiError } from "./httpClient";

describe("DutyLog HTTP client", () => {
  it("reads and decodes the Spring XSRF cookie", () => {
    expect(csrfTokenFromCookie("theme=dark; XSRF-TOKEN=a%2Fb%3D; language=ru")).toBe("a/b=");
    expect(csrfTokenFromCookie("theme=dark")).toBeNull();
  });

  it("adds same-origin credentials and CSRF only to mutations", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const request = createDutyLogHttpClient({ fetchImpl: fetchImpl as typeof fetch, readCookie: () => "XSRF-TOKEN=token-1" });

    await request<{ ok: boolean }>("/api/profile");
    await request<null, { language: string }>("/api/profile", { method: "PUT", body: { language: "ru" } });

    const getInit = fetchImpl.mock.calls[0]?.[1];
    const putInit = fetchImpl.mock.calls[1]?.[1];
    expect(getInit?.credentials).toBe("same-origin");
    expect(new Headers(getInit?.headers).has("X-XSRF-TOKEN")).toBe(false);
    expect(new Headers(putInit?.headers).get("X-XSRF-TOKEN")).toBe("token-1");
    expect(putInit?.body).toBe(JSON.stringify({ language: "ru" }));
  });

  it("normalizes API errors without losing status and code", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: "Closed period", code: "PERIOD_CLOSED" }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const request = createDutyLogHttpClient({ fetchImpl: fetchImpl as typeof fetch });

    await expect(request("/api/example", { method: "POST", body: {} })).rejects.toMatchObject({
      message: "Closed period",
      status: 409,
      code: "PERIOD_CLOSED",
      url: "/api/example",
      method: "POST",
    });
  });
});
