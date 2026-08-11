import { beforeEach, describe, expect, it } from "vitest";
import packageMetadata from "../../../package.json";
import {
  captureFrontendFailure,
  diagnosticsSnapshot,
  recordRequestDiagnostics,
  resetFrontendDiagnosticsForTests,
  updateFrontendRoute,
} from "./frontendDiagnostics";

beforeEach(() => resetFrontendDiagnosticsForTests());

describe("frontend diagnostics", () => {
  it("correlates a controlled failure with route, release and request id", () => {
    updateFrontendRoute("#vacation");
    recordRequestDiagnostics({
      method: "GET",
      url: "/api/v1/vacation-planner?privateToken=must-not-leak",
      status: 500,
      requestId: `server-req-42${"x".repeat(120)}`,
    });
    const failure = captureFrontendFailure(new Error("Planner render failed"), "vue");

    expect(failure).toMatchObject({
      source: "vue",
      message: "Planner render failed",
      route: "vacation",
      requestId: `server-req-42${"x".repeat(83)}`,
    });
    expect(failure.releaseVersion).toBe(packageMetadata.version);
    expect(diagnosticsSnapshot().lastRequest?.url).toBe("/api/v1/vacation-planner");
    expect(diagnosticsSnapshot().requestId).toHaveLength(96);
  });

  it("prefers a request id carried by the error", () => {
    const error = Object.assign(new Error("Conflict"), { requestId: "conflict-409" });
    expect(captureFrontendFailure(error, "network").requestId).toBe("conflict-409");
    expect(diagnosticsSnapshot().fatal?.message).toBe("Conflict");
  });
});
