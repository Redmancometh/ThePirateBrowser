import { afterEach, describe, expect, it, vi } from "vitest";
import { api, primeCsrf } from "./api";

describe("API client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends the server-issued CSRF token on state-changing requests", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: "X-XSRF-TOKEN",
        token: "test-csrf",
        registrationEnabled: true
      }), { status: 200, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await primeCsrf();
    await api("/api/example", { method: "DELETE" });

    const request = fetchMock.mock.calls[1][1] as RequestInit;
    expect(new Headers(request.headers).get("X-XSRF-TOKEN")).toBe("test-csrf");
    expect(request.credentials).toBe("same-origin");
  });

  it("surfaces safe JSON error messages", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: "No access." }), {
        status: 403,
        headers: { "Content-Type": "application/json" }
      })
    ));

    await expect(api("/api/private")).rejects.toMatchObject({
      message: "No access.",
      status: 403
    });
  });
});
