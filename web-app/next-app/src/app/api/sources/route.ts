import { errorResponse, requireCsrf, requireUser } from "@/lib/auth";
import { getDb, getEnabledSources, setEnabledSources } from "@/lib/db";
import { SOURCE_DETAILS, TORRENT_SOURCES, type TorrentSource } from "@/lib/search/types";
import { type NextRequest, NextResponse } from "next/server";

async function views(userId: string) {
  const enabled = new Set(await getEnabledSources(await getDb(), userId));
  return TORRENT_SOURCES.map((id) => ({ id, ...SOURCE_DETAILS[id], enabled: enabled.has(id) }));
}

export async function GET(request: NextRequest) {
  try {
    const user = await requireUser(request);
    return NextResponse.json(await views(user.id));
  } catch (error) {
    return errorResponse(error);
  }
}

export async function PUT(request: NextRequest) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const body = (await request.json()) as { enabled?: unknown };
    if (!Array.isArray(body.enabled)) {
      return NextResponse.json({ error: "enabled must be an array." }, { status: 400 });
    }
    const enabled = [...new Set(body.enabled)].filter(
      (value): value is TorrentSource =>
        typeof value === "string" && TORRENT_SOURCES.includes(value as TorrentSource),
    );
    await setEnabledSources(await getDb(), user.id, enabled);
    return NextResponse.json(await views(user.id));
  } catch (error) {
    return errorResponse(error);
  }
}
