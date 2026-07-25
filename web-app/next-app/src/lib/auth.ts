import { NextRequest, NextResponse } from "next/server";
import {
  CloudflareEnv,
  D1DatabaseLike,
  PublicUser,
  UserRow,
  findUserById,
  findUserByUsername,
  getDb,
  getEnv,
  publicUser,
  recordAudit,
} from "./db";

// Cloudflare Workers Web Crypto rejects PBKDF2 iteration counts above 100,000.
const PASSWORD_ITERATIONS = 100_000;
const SESSION_SECONDS = 60 * 60 * 24 * 14;
const USERNAME = /^[a-z0-9][a-z0-9._-]{2,39}$/;
export const DUMMY_PASSWORD_HASH =
  "pbkdf2-sha256$100000$cGlyYXRlLWJyb3dzZXIh$JOpY4_uzPfplVy517uXxd6csgwPWqlRvyZXLfS6SlXs";
const encoder = new TextEncoder();

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function decodeBase64Url(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/");
  const binary = atob(normalized + "=".repeat((4 - (normalized.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function randomToken(bytes = 32): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(bytes)));
}

async function sha256(value: string): Promise<string> {
  return base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value))));
}

export async function hashPassword(password: string): Promise<string> {
  validatePassword(password);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const material = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: PASSWORD_ITERATIONS },
    material,
    256,
  );
  return `pbkdf2-sha256$${PASSWORD_ITERATIONS}$${base64Url(salt)}$${base64Url(new Uint8Array(bits))}`;
}

export async function verifyPassword(password: string, encoded: string): Promise<boolean> {
  try {
    const [scheme, iterationsValue, saltValue, expectedValue] = encoded.split("$");
    if (scheme !== "pbkdf2-sha256") return false;
    const iterations = Number(iterationsValue);
    if (!Number.isInteger(iterations) || iterations < 100_000 || iterations > 1_000_000) {
      return false;
    }
    const material = await crypto.subtle.importKey(
      "raw",
      encoder.encode(password),
      "PBKDF2",
      false,
      ["deriveBits"],
    );
    const actual = new Uint8Array(
      await crypto.subtle.deriveBits(
        { name: "PBKDF2", hash: "SHA-256", salt: decodeBase64Url(saltValue), iterations },
        material,
        256,
      ),
    );
    return timingSafeEqual(actual, decodeBase64Url(expectedValue));
  } catch {
    return false;
  }
}

function timingSafeEqual(left: Uint8Array, right: Uint8Array): boolean {
  let mismatch = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    mismatch |= (left[index % left.length] ?? 0) ^ (right[index % right.length] ?? 0);
  }
  return mismatch === 0;
}

function equalText(left: string, right: string): boolean {
  return timingSafeEqual(encoder.encode(left), encoder.encode(right));
}

export function normalizeUsername(raw: string): string {
  const username = raw?.trim().toLowerCase();
  if (!USERNAME.test(username)) {
    throw new HttpError(
      400,
      "Username must be 3-40 characters using letters, numbers, dot, underscore, or dash.",
    );
  }
  return username;
}

export function validatePassword(password: string): void {
  if (typeof password !== "string" || password.length < 12 || password.length > 200) {
    throw new HttpError(400, "Password must be 12-200 characters.");
  }
}

function secureCookies(env: CloudflareEnv): boolean {
  return env.COOKIE_SECURE?.toLowerCase() !== "false";
}

function sessionCookieName(env: CloudflareEnv): string {
  return secureCookies(env) ? "__Host-pb_session" : "pb_session";
}

export async function createSession(
  response: NextResponse,
  db: D1DatabaseLike,
  env: CloudflareEnv,
  userId: string,
): Promise<void> {
  const token = randomToken();
  const now = new Date();
  const expires = new Date(now.getTime() + SESSION_SECONDS * 1000);
  await db.batch([
    db.prepare("DELETE FROM user_sessions WHERE expires_at <= ?").bind(now.toISOString()),
    db
      .prepare(
        "INSERT INTO user_sessions (token_hash, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)",
      )
      .bind(await sha256(token), userId, expires.toISOString(), now.toISOString()),
  ]);
  response.cookies.set(sessionCookieName(env), token, {
    httpOnly: true,
    secure: secureCookies(env),
    sameSite: "strict",
    path: "/",
    maxAge: SESSION_SECONDS,
  });
}

export async function destroySession(
  request: NextRequest,
  response: NextResponse,
  db: D1DatabaseLike,
  env: CloudflareEnv,
): Promise<void> {
  const token =
    request.cookies.get(sessionCookieName(env))?.value ??
    request.cookies.get("pb_session")?.value ??
    request.cookies.get("__Host-pb_session")?.value;
  if (token) {
    await db.prepare("DELETE FROM user_sessions WHERE token_hash = ?").bind(await sha256(token)).run();
  }
  for (const name of new Set([sessionCookieName(env), "pb_session", "__Host-pb_session"])) {
    response.cookies.set(name, "", {
      httpOnly: true,
      secure: name.startsWith("__Host-") || secureCookies(env),
      sameSite: "strict",
      path: "/",
      maxAge: 0,
    });
  }
}

export async function currentUser(request: NextRequest): Promise<PublicUser | null> {
  const env = await getEnv();
  const token =
    request.cookies.get(sessionCookieName(env))?.value ??
    request.cookies.get("pb_session")?.value ??
    request.cookies.get("__Host-pb_session")?.value;
  if (!token) return null;
  const db = env.DB;
  const row = await db
    .prepare(
      `SELECT u.* FROM user_sessions s
       JOIN user_accounts u ON u.id = s.user_id
       WHERE s.token_hash = ? AND s.expires_at > ? AND u.enabled = 1`,
    )
    .bind(await sha256(token), new Date().toISOString())
    .first<UserRow>();
  return row ? publicUser(row) : null;
}

export async function requireUser(request: NextRequest): Promise<PublicUser> {
  const user = await currentUser(request);
  if (!user) throw new HttpError(401, "Authentication required.");
  return user;
}

export async function requireAdmin(request: NextRequest): Promise<PublicUser> {
  const user = await requireUser(request);
  if (user.role !== "ADMIN") throw new HttpError(403, "Administrator access required.");
  return user;
}

export function requireCsrf(request: NextRequest): void {
  const fetchSite = request.headers.get("sec-fetch-site");
  if (fetchSite === "cross-site") throw new HttpError(403, "Cross-site request rejected.");
  const origin = request.headers.get("origin");
  if (origin && origin !== request.nextUrl.origin) throw new HttpError(403, "Origin rejected.");
  const cookie = request.cookies.get("XSRF-TOKEN")?.value ?? "";
  const header = request.headers.get("X-XSRF-TOKEN") ?? "";
  if (!cookie || !header || !equalText(cookie, header)) {
    throw new HttpError(403, "Invalid CSRF token.");
  }
}

export async function createUser(
  db: D1DatabaseLike,
  rawUsername: string,
  password: string,
  role: "USER" | "ADMIN" = "USER",
  requestedId?: string,
): Promise<UserRow> {
  const username = normalizeUsername(rawUsername);
  validatePassword(password);
  if (await findUserByUsername(db, username)) throw new HttpError(409, "Username is already in use.");
  const now = new Date().toISOString();
  const id = requestedId ?? crypto.randomUUID();
  const passwordHash = await hashPassword(password);
  try {
    await db
      .prepare(
        `INSERT INTO user_accounts
         (id, username, password_hash, role, enabled, created_at, updated_at)
         VALUES (?, ?, ?, ?, 1, ?, ?)`,
      )
      .bind(id, username, passwordHash, role, now, now)
      .run();
  } catch {
    throw new HttpError(409, "Username is already in use.");
  }
  return (await findUserById(db, id))!;
}

export async function bootstrapAdminIfConfigured(
  db: D1DatabaseLike,
  env: CloudflareEnv,
): Promise<void> {
  const configuredUsername = env.ADMIN_BOOTSTRAP_USERNAME ?? env.WEB_ADMIN_USERNAME;
  const configuredPassword = env.ADMIN_BOOTSTRAP_PASSWORD ?? env.WEB_ADMIN_PASSWORD;
  const existing = await db
    .prepare("SELECT id FROM user_accounts WHERE role = 'ADMIN' LIMIT 1")
    .first<{ id: string }>();
  if (existing || !configuredUsername || !configuredPassword) return;
  try {
    const admin = await createUser(db, configuredUsername, configuredPassword, "ADMIN");
    await recordAudit(db, admin.username, "ADMIN_BOOTSTRAP", "user", admin.id);
  } catch (error) {
    if (!(error instanceof HttpError && error.status === 409)) throw error;
    const candidate = await findUserByUsername(db, configuredUsername);
    if (!candidate || !(await verifyPassword(configuredPassword, candidate.password_hash))) {
      throw new Error("Admin bootstrap username conflicts with an existing account.");
    }
    await db
      .prepare("UPDATE user_accounts SET role = 'ADMIN', enabled = 1, updated_at = ? WHERE id = ?")
      .bind(new Date().toISOString(), candidate.id)
      .run();
    await recordAudit(db, candidate.username, "ADMIN_BOOTSTRAP", "user", candidate.id);
  }
}

export async function parseBody(request: NextRequest): Promise<Record<string, unknown>> {
  const type = request.headers.get("content-type") ?? "";
  if (type.includes("application/json")) {
    const body = await request.json();
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      throw new HttpError(400, "A JSON object is required.");
    }
    return body as Record<string, unknown>;
  }
  const form = await request.formData();
  return Object.fromEntries(form.entries());
}

export function errorResponse(error: unknown): NextResponse {
  if (error instanceof HttpError) {
    return NextResponse.json({ error: error.message }, { status: error.status });
  }
  if (error instanceof Error && error.message === "Saved search not found.") {
    return NextResponse.json({ error: error.message }, { status: 404 });
  }
  console.error("Unhandled API error", error);
  return NextResponse.json({ error: "The request could not be completed." }, { status: 500 });
}

export async function cleanupExpiredSessions(): Promise<void> {
  const db = await getDb();
  await db
    .prepare("DELETE FROM user_sessions WHERE expires_at <= ?")
    .bind(new Date().toISOString())
    .run();
}
