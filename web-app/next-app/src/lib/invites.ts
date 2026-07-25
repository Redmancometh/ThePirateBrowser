import { HttpError } from "./auth";
import type { D1DatabaseLike } from "./db";

const encoder = new TextEncoder();
const ALLOWED_EXPIRY_DAYS = new Set([1, 7, 30, 90]);

interface InviteRow {
  id: string;
  code_plain: string | null;
  code_hint: string;
  label: string;
  created_by: string;
  created_at: string;
  expires_at: string | null;
  used_by: string | null;
  used_at: string | null;
  revoked_at: string | null;
}

export interface InviteView {
  id: string;
  code: string | null;
  codeHint: string;
  label: string;
  createdBy: string;
  createdAt: string;
  expiresAt: string | null;
  usedBy: string | null;
  usedAt: string | null;
  revokedAt: string | null;
  status: "ACTIVE" | "USED" | "EXPIRED" | "REVOKED";
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function hashInvite(code: string): Promise<string> {
  const normalized = code.trim().toUpperCase();
  return base64Url(
    new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(normalized))),
  );
}

function view(row: InviteRow): InviteView {
  const expired = Boolean(row.expires_at && row.expires_at <= new Date().toISOString());
  return {
    id: row.id,
    code: row.code_plain,
    codeHint: row.code_hint,
    label: row.label,
    createdBy: row.created_by,
    createdAt: row.created_at,
    expiresAt: row.expires_at,
    usedBy: row.used_by,
    usedAt: row.used_at,
    revokedAt: row.revoked_at,
    status: row.revoked_at
      ? "REVOKED"
      : row.used_at
        ? "USED"
        : expired
          ? "EXPIRED"
          : "ACTIVE",
  };
}

export async function listInvites(db: D1DatabaseLike): Promise<InviteView[]> {
  const result = await db
    .prepare("SELECT * FROM registration_invites ORDER BY created_at DESC LIMIT 100")
    .all<InviteRow>();
  return (result.results ?? []).map(view);
}

export async function createInvite(
  db: D1DatabaseLike,
  actor: string,
  rawLabel: string,
  expiryDays: number | null,
): Promise<{ code: string; invite: InviteView }> {
  const label = rawLabel.trim() || "Friend invite";
  if (label.length > 80) throw new HttpError(400, "Invite label must be 80 characters or fewer.");
  if (expiryDays !== null && !ALLOWED_EXPIRY_DAYS.has(expiryDays)) {
    throw new HttpError(400, "Invite expiry is invalid.");
  }
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  const code = `PB-${[...bytes].map((value) => value.toString(16).padStart(2, "0")).join("").toUpperCase()}`;
  const now = new Date();
  const expiresAt = expiryDays === null
    ? null
    : new Date(now.getTime() + expiryDays * 86_400_000).toISOString();
  const id = crypto.randomUUID();
  await db
    .prepare(
      `INSERT INTO registration_invites
       (id, code_hash, code_plain, code_hint, label, created_by, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      id,
      await hashInvite(code),
      code,
      code.slice(-6),
      label,
      actor,
      now.toISOString(),
      expiresAt,
    )
    .run();
  const row = await db
    .prepare("SELECT * FROM registration_invites WHERE id = ?")
    .bind(id)
    .first<InviteRow>();
  if (!row) throw new Error("Generated invitation could not be loaded.");
  return { code, invite: view(row) };
}

export async function hasActiveInvite(db: D1DatabaseLike): Promise<boolean> {
  const row = await db
    .prepare(
      `SELECT id FROM registration_invites
       WHERE used_at IS NULL AND revoked_at IS NULL
         AND (expires_at IS NULL OR expires_at > ?)
       LIMIT 1`,
    )
    .bind(new Date().toISOString())
    .first<{ id: string }>();
  return Boolean(row);
}

export async function reserveInvite(
  db: D1DatabaseLike,
  code: string,
  userId: string,
): Promise<string> {
  const now = new Date().toISOString();
  const row = await db
    .prepare(
      `SELECT id FROM registration_invites
       WHERE (code_plain = ? OR code_hash = ?) AND used_at IS NULL AND revoked_at IS NULL
         AND (expires_at IS NULL OR expires_at > ?)
       LIMIT 1`,
    )
    .bind(code.trim().toUpperCase(), await hashInvite(code), now)
    .first<{ id: string }>();
  if (!row) throw new HttpError(403, "Invitation code is invalid, expired, or already used.");
  const result = await db
    .prepare(
      `UPDATE registration_invites SET used_at = ?, used_by = ?
       WHERE id = ? AND used_at IS NULL AND revoked_at IS NULL
         AND (expires_at IS NULL OR expires_at > ?)`,
    )
    .bind(now, userId, row.id, now)
    .run();
  if ((result.meta?.changes ?? 0) !== 1) {
    throw new HttpError(409, "That invitation was just used. Ask for a new one.");
  }
  return row.id;
}

export async function releaseInvite(
  db: D1DatabaseLike,
  inviteId: string,
  userId: string,
): Promise<void> {
  await db
    .prepare(
      "UPDATE registration_invites SET used_at = NULL, used_by = NULL WHERE id = ? AND used_by = ?",
    )
    .bind(inviteId, userId)
    .run();
}

export async function revokeInvite(db: D1DatabaseLike, id: string): Promise<void> {
  const result = await db
    .prepare(
      "UPDATE registration_invites SET revoked_at = ? WHERE id = ? AND used_at IS NULL AND revoked_at IS NULL",
    )
    .bind(new Date().toISOString(), id)
    .run();
  if ((result.meta?.changes ?? 0) !== 1) {
    throw new HttpError(404, "Active invitation not found.");
  }
}
