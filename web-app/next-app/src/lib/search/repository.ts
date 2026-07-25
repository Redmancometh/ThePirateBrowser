import type { D1DatabaseLike } from "@/lib/db";

export type SavedSearchRow = {
  id: string;
  name: string;
  query: string;
  minimum_seeders: number;
  enabled: number;
  known_magnets: string;
  last_checked_at: string | null;
  created_at: string;
};

function parseMagnets(value: string): string[] {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === "string")
      : [];
  } catch {
    return [];
  }
}

function savedView(row: SavedSearchRow) {
  return {
    id: row.id,
    name: row.name,
    query: row.query,
    minimumSeeders: row.minimum_seeders,
    enabled: Boolean(row.enabled),
    lastCheckedAt: row.last_checked_at,
    createdAt: row.created_at,
    knownResultCount: parseMagnets(row.known_magnets).length,
  };
}

export async function getSavedSearch(
  db: D1DatabaseLike,
  userId: string,
  id: string,
): Promise<SavedSearchRow | null> {
  return db
    .prepare(
      `SELECT id, name, query, minimum_seeders, enabled, known_magnets,
              last_checked_at, created_at
       FROM saved_searches WHERE id = ? AND user_id = ?`,
    )
    .bind(id, userId)
    .first<SavedSearchRow>();
}

export function knownMagnets(row: SavedSearchRow): Set<string> {
  return new Set(parseMagnets(row.known_magnets));
}

export async function checkedSavedSearch(
  db: D1DatabaseLike,
  userId: string,
  row: SavedSearchRow,
  magnets: Set<string>,
) {
  const now = new Date().toISOString();
  const known = JSON.stringify([...magnets]);
  await db
    .prepare(
      `UPDATE saved_searches
       SET known_magnets = ?, last_checked_at = ?, updated_at = ?
       WHERE id = ? AND user_id = ?`,
    )
    .bind(known, now, now, row.id, userId)
    .run();
  return savedView({ ...row, known_magnets: known, last_checked_at: now });
}
