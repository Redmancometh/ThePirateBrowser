import { errorResponse, HttpError, requireUser } from "@/lib/auth";
import { getDb, getEnabledSources } from "@/lib/db";
import { searchTorrents } from "@/lib/search/search";
import { TORRENT_SOURCES, type TorrentSource } from "@/lib/search/types";
import { type NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  try {
    const user = await requireUser(request);
    const query = request.nextUrl.searchParams.get("q")?.trim() ?? "";
    const minimumSeeders = Number(request.nextUrl.searchParams.get("minimumSeeders") ?? "0");
    if (!query || query.length > 250) throw new HttpError(400, "Query must be 1-250 characters.");
    if (!Number.isInteger(minimumSeeders) || minimumSeeders < 0 || minimumSeeders > 100_000) {
      throw new HttpError(400, "Minimum seeders is outside the allowed range.");
    }
    const db = await getDb();
    const configured = await getEnabledSources(db, user.id);
    const enabled = configured.filter((value): value is TorrentSource =>
      TORRENT_SOURCES.includes(value as TorrentSource),
    );
    const outcome = await searchTorrents(query, enabled);
    return NextResponse.json({
      ...outcome,
      results: outcome.results.filter((result) => result.seeders >= minimumSeeders),
    });
  } catch (error) {
    return errorResponse(error);
  }
}
