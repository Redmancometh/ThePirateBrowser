import { getCloudflareContext } from "@opennextjs/cloudflare";

export interface D1ResultLike<T = unknown> {
  results: T[];
  success: boolean;
  meta?: { changes?: number };
}

export interface D1StatementLike {
  bind(...values: unknown[]): D1StatementLike;
  first<T = Record<string, unknown>>(column?: string): Promise<T | null>;
  all<T = Record<string, unknown>>(): Promise<D1ResultLike<T>>;
  run<T = Record<string, unknown>>(): Promise<D1ResultLike<T>>;
}

export interface D1DatabaseLike {
  prepare(query: string): D1StatementLike;
  batch<T = unknown>(statements: D1StatementLike[]): Promise<D1ResultLike<T>[]>;
}

export interface CloudflareEnv {
  DB: D1DatabaseLike;
  WEB_REGISTRATION_INVITE_CODE?: string;
  WEB_ADMIN_USERNAME?: string;
  WEB_ADMIN_PASSWORD?: string;
  REGISTRATION_INVITE_CODE?: string;
  ADMIN_BOOTSTRAP_USERNAME?: string;
  ADMIN_BOOTSTRAP_PASSWORD?: string;
  PUTIO_OAUTH_TOKEN?: string;
  BUILD_CANARY?: string;
  APP_VERSION?: string;
  COOKIE_SECURE?: string;
}

export async function getEnv(): Promise<CloudflareEnv> {
  const context = await getCloudflareContext({ async: true });
  const env = context.env as unknown as CloudflareEnv;
  if (!env.DB) throw new Error("The DB D1 binding is not configured.");
  return env;
}

export async function getDb(): Promise<D1DatabaseLike> {
  return (await getEnv()).DB;
}

export interface UserRow {
  id: string;
  username: string;
  password_hash: string;
  role: "USER" | "ADMIN";
  enabled: number;
  created_at: string;
  updated_at: string;
}

export interface PublicUser {
  id: string;
  username: string;
  role: "USER" | "ADMIN";
  enabled: boolean;
  createdAt: string;
}

export function publicUser(row: UserRow): PublicUser {
  return {
    id: row.id,
    username: row.username,
    role: row.role,
    enabled: row.enabled === 1,
    createdAt: row.created_at,
  };
}

export async function findUserByUsername(
  db: D1DatabaseLike,
  username: string,
): Promise<UserRow | null> {
  return db
    .prepare("SELECT * FROM user_accounts WHERE username = ? COLLATE NOCASE")
    .bind(username)
    .first<UserRow>();
}

export async function findUserById(
  db: D1DatabaseLike,
  id: string,
): Promise<UserRow | null> {
  return db.prepare("SELECT * FROM user_accounts WHERE id = ?").bind(id).first<UserRow>();
}

export async function recordAudit(
  db: D1DatabaseLike,
  username: string,
  action: string,
  targetType: string,
  targetId: string | null = null,
  detail: string | null = null,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO audit_events
       (id, username, action, target_type, target_id, detail, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      crypto.randomUUID(),
      username.slice(0, 40),
      action.slice(0, 80),
      targetType.slice(0, 40),
      targetId?.slice(0, 120) ?? null,
      detail?.slice(0, 500) ?? null,
      new Date().toISOString(),
    )
    .run();
}

export const DEFAULT_SOURCES = [
  "PIRATE_BAY",
  "KNABEN",
  "MAGNETZ",
  "TORRENTS_CSV",
  "NYAA",
  "EZTV",
  "YTS",
] as const;

export async function getEnabledSources(
  db: D1DatabaseLike,
  userId: string,
): Promise<string[]> {
  const row = await db
    .prepare("SELECT enabled_sources FROM user_settings WHERE user_id = ?")
    .bind(userId)
    .first<{ enabled_sources: string }>();
  if (!row) return [...DEFAULT_SOURCES];
  try {
    const values = JSON.parse(row.enabled_sources);
    return Array.isArray(values)
      ? values.filter((value): value is string =>
          DEFAULT_SOURCES.includes(value as (typeof DEFAULT_SOURCES)[number]),
        )
      : [...DEFAULT_SOURCES];
  } catch {
    return [...DEFAULT_SOURCES];
  }
}

export async function setEnabledSources(
  db: D1DatabaseLike,
  userId: string,
  sources: string[],
): Promise<string[]> {
  const normalized = [
    ...new Set(
      sources.filter((value) =>
        DEFAULT_SOURCES.includes(value as (typeof DEFAULT_SOURCES)[number]),
      ),
    ),
  ];
  await db
    .prepare(
      `INSERT INTO user_settings (user_id, enabled_sources) VALUES (?, ?)
       ON CONFLICT(user_id) DO UPDATE SET enabled_sources = excluded.enabled_sources`,
    )
    .bind(userId, JSON.stringify(normalized))
    .run();
  return normalized;
}

export interface SavedSearchInput {
  name: string;
  query: string;
  minimumSeeders: number;
  enabled?: boolean;
  knownMagnets?: string[];
}

interface SavedSearchRow {
  id: string;
  user_id: string;
  name: string;
  query: string;
  minimum_seeders: number;
  enabled: number;
  known_magnets: string;
  last_checked_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface SavedSearchView {
  id: string;
  name: string;
  query: string;
  minimumSeeders: number;
  enabled: boolean;
  lastCheckedAt: string | null;
  createdAt: string;
  knownResultCount: number;
}

function validateSavedSearch(input: SavedSearchInput): SavedSearchInput {
  const name = input?.name?.trim();
  const query = input?.query?.trim();
  if (!name || name.length > 100) throw new Error("Saved-search name must be 1-100 characters.");
  if (!query || query.length > 250) throw new Error("Query must be 1-250 characters.");
  if (
    !Number.isInteger(input.minimumSeeders) ||
    input.minimumSeeders < 0 ||
    input.minimumSeeders > 100_000
  ) {
    throw new Error("Minimum seeders is outside the allowed range.");
  }
  return { ...input, name, query };
}

function savedSearchView(row: SavedSearchRow): SavedSearchView {
  let knownResultCount = 0;
  try {
    const parsed = JSON.parse(row.known_magnets);
    knownResultCount = Array.isArray(parsed) ? parsed.length : 0;
  } catch {
    // Corrupt historical state should not make the saved-search list unusable.
  }
  return {
    id: row.id,
    name: row.name,
    query: row.query,
    minimumSeeders: row.minimum_seeders,
    enabled: row.enabled === 1,
    lastCheckedAt: row.last_checked_at,
    createdAt: row.created_at,
    knownResultCount,
  };
}

export async function listSavedSearches(
  db: D1DatabaseLike,
  userId: string,
): Promise<SavedSearchView[]> {
  const result = await db
    .prepare("SELECT * FROM saved_searches WHERE user_id = ? ORDER BY created_at DESC")
    .bind(userId)
    .all<SavedSearchRow>();
  return (result.results ?? []).map(savedSearchView);
}

export async function createSavedSearch(
  db: D1DatabaseLike,
  userId: string,
  rawInput: SavedSearchInput,
): Promise<SavedSearchView> {
  const input = validateSavedSearch(rawInput);
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  const known = [...new Set(input.knownMagnets ?? [])];
  await db
    .prepare(
      `INSERT INTO saved_searches
       (id, user_id, name, query, minimum_seeders, enabled, known_magnets, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      id,
      userId,
      input.name,
      input.query,
      input.minimumSeeders,
      input.enabled === false ? 0 : 1,
      JSON.stringify(known),
      now,
      now,
    )
    .run();
  return (await requireSavedSearch(db, userId, id));
}

export async function updateSavedSearch(
  db: D1DatabaseLike,
  userId: string,
  id: string,
  rawInput: SavedSearchInput,
): Promise<SavedSearchView> {
  const input = validateSavedSearch(rawInput);
  const existing = await requireSavedSearchRow(db, userId, id);
  await db
    .prepare(
      `UPDATE saved_searches
       SET name = ?, query = ?, minimum_seeders = ?, enabled = ?, updated_at = ?
       WHERE id = ? AND user_id = ?`,
    )
    .bind(
      input.name,
      input.query,
      input.minimumSeeders,
      input.enabled === false ? 0 : 1,
      new Date().toISOString(),
      existing.id,
      userId,
    )
    .run();
  return requireSavedSearch(db, userId, id);
}

export async function deleteSavedSearch(
  db: D1DatabaseLike,
  userId: string,
  id: string,
): Promise<void> {
  await requireSavedSearchRow(db, userId, id);
  await db
    .prepare("DELETE FROM saved_searches WHERE id = ? AND user_id = ?")
    .bind(id, userId)
    .run();
}

async function requireSavedSearchRow(
  db: D1DatabaseLike,
  userId: string,
  id: string,
): Promise<SavedSearchRow> {
  const row = await db
    .prepare("SELECT * FROM saved_searches WHERE id = ? AND user_id = ?")
    .bind(id, userId)
    .first<SavedSearchRow>();
  if (!row) throw new Error("Saved search not found.");
  return row;
}

async function requireSavedSearch(
  db: D1DatabaseLike,
  userId: string,
  id: string,
): Promise<SavedSearchView> {
  return savedSearchView(await requireSavedSearchRow(db, userId, id));
}
