import { describe, expect, it } from "vitest";
import {
  hashPassword,
  normalizeUsername,
  verifyPassword
} from "./auth";

describe("Worker account security", () => {
  it("stores a salted PBKDF2 hash and verifies only the matching password", async () => {
    const encoded = await hashPassword("a-long-test-password");

    expect(encoded).toMatch(/^pbkdf2-sha256\$/);
    expect(encoded).not.toContain("a-long-test-password");
    await expect(verifyPassword("a-long-test-password", encoded)).resolves.toBe(true);
    await expect(verifyPassword("a-different-password", encoded)).resolves.toBe(false);
  });

  it("normalizes valid usernames and rejects unsafe names", () => {
    expect(normalizeUsername("  Redman.Test  ")).toBe("redman.test");
    expect(() => normalizeUsername("../../owner")).toThrow();
  });
});
