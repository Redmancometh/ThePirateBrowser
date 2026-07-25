import { describe, expect, it, vi } from "vitest";
import {
  PutIoNotConfiguredError,
  addTransfer,
  isPutIoConfigured
} from "./client";

describe("put.io Worker client", () => {
  it("does not make a request when the server secret is absent", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");

    expect(isPutIoConfigured({})).toBe(false);
    await expect(addTransfer({}, "magnet:?xt=urn:btih:test"))
      .rejects.toBeInstanceOf(PutIoNotConfiguredError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects non-magnet transfer input before contacting put.io", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");

    await expect(addTransfer({ PUTIO_OAUTH_TOKEN: "server-only" }, "https://example.test"))
      .rejects.toThrow("valid magnet");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
