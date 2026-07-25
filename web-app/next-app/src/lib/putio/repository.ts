import type { D1DatabaseLike } from "@/lib/db";

const CAST_LIFETIME_MS = 8 * 60 * 60 * 1000;

export async function createCastGrant(
  db: D1DatabaseLike,
  fileId: number,
  userId: string,
) {
  if (!Number.isSafeInteger(fileId) || fileId <= 0) throw new Error("A valid file is required.");
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  const token = btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token)),
  );
  binary = "";
  for (const byte of digest) binary += String.fromCharCode(byte);
  const tokenHash = btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
  const now = new Date();
  const expiresAt = new Date(now.getTime() + CAST_LIFETIME_MS).toISOString();
  await db
    .prepare("DELETE FROM cast_grants WHERE expires_at <= ?")
    .bind(now.toISOString())
    .run();
  await db
    .prepare(
      `INSERT INTO cast_grants (token_hash, file_id, created_by, expires_at, created_at)
       VALUES (?, ?, ?, ?, ?)`,
    )
    .bind(tokenHash, String(fileId), userId, expiresAt, now.toISOString())
    .run();
  return { token, expiresAt };
}

export async function requireCastGrant(
  db: D1DatabaseLike,
  token: string,
): Promise<number | null> {
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token)),
  );
  let binary = "";
  for (const byte of digest) binary += String.fromCharCode(byte);
  const tokenHash = btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
  const row = await db
    .prepare("SELECT file_id, expires_at FROM cast_grants WHERE token_hash = ?")
    .bind(tokenHash)
    .first<{ file_id: string; expires_at: string }>();
  if (!row) return null;
  if (Date.parse(row.expires_at) <= Date.now()) {
    await db.prepare("DELETE FROM cast_grants WHERE token_hash = ?").bind(tokenHash).run();
    return null;
  }
  return Number(row.file_id);
}
