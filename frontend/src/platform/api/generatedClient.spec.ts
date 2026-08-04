import { describe, expect, it, vi } from "vitest";
import { createGeneratedDutyLogApiClient } from "./generatedClient";

function jsonResponse(body: unknown, requestId = "request-typed-1"): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
  });
}

describe("generated DutyLog API client", () => {
  it("resolves operationId, path parameters and query parameters", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ id: 7, text: "Typed" }));
    const client = createGeneratedDutyLogApiClient({
      fetchImpl: fetchImpl as typeof fetch,
      requestIdFactory: () => "client-generated-1",
    });

    await client.request("getTaskDetails", {
      path: { taskId: 7 },
      query: { include: "subtasks", archived: false, empty: null },
    });

    await client.request("updateSubtask", {
      path: { taskId: 7, subtaskId: 9 },
      body: { done: true },
    });

    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(fetchImpl.mock.calls[0]?.[0]).toBe("/api/v1/tasks/7?include=subtasks&archived=false");
    expect(fetchImpl.mock.calls[0]?.[1]).toMatchObject({ method: "GET", credentials: "same-origin" });
    expect(fetchImpl.mock.calls[1]?.[0]).toBe("/api/v1/tasks/7/subtasks/9");
    expect(fetchImpl.mock.calls[1]?.[1]).toMatchObject({ method: "PATCH", body: JSON.stringify({ done: true }) });
  });

  it("fails locally when a required path parameter is absent", async () => {
    const client = createGeneratedDutyLogApiClient({ fetchImpl: vi.fn() as unknown as typeof fetch });
    await expect(client.request("getTaskDetails")).rejects.toThrow("Missing OpenAPI path parameter: taskId");
  });
});
