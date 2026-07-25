import { errorResponse, HttpError, requireCsrf, requireUser } from "@/lib/auth";
import { getDb, getEnabledSources } from "@/lib/db";
import {
  checkedSavedSearch,
  getSavedSearch,
  knownMagnets,
} from "@/lib/search/repository";
import { searchTorrents } from "@/lib/search/search";
import { TORRENT_SOURCES, type TorrentSource } from "@/lib/search/types";
import { type NextRequest, NextResponse } from "next/server";

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const db = await getDb();
    const row = await getSavedSearch(db, user.id, (await params).id);
    if (!row) throw new HttpError(404, "Saved search not found.");
    const sources = (await getEnabledSources(db, user.id)).filter(
      (value): value is TorrentSource =>
        TORRENT_SOURCES.includes(value as TorrentSource),
    );
    const outcome = await searchTorrents(row.query, sources);
    const results = outcome.results.filter((result) => result.seeders >= row.minimum_seeders);
    const previous = knownMagnets(row);
    const current = new Set(results.map((result) => result.magnet));
    const newCount = [...current].filter((magnet) => !previous.has(magnet)).length;
    current.forEach((magnet) => previous.add(magnet));
    return NextResponse.json({
      savedSearch: await checkedSavedSearch(db, user.id, row, previous),
      newCount,
      results,
      failures: outcome.failures,
    });
  } catch (error) {
    return errorResponse(error);
  }
}
