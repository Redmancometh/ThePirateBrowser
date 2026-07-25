import { getDb, getEnv } from "@/lib/db";
import { streamFile, type PutIoEnv } from "@/lib/putio/client";
import { requireCastGrant } from "@/lib/putio/repository";
import { type NextRequest, NextResponse } from "next/server";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ token: string }> },
) {
  const token = (await params).token;
  if (!/^[A-Za-z0-9_-]{40,64}$/.test(token)) {
    return NextResponse.json({ error: "Cast link not found." }, { status: 404 });
  }
  const fileId = await requireCastGrant(await getDb(), token);
  if (!fileId) return NextResponse.json({ error: "Cast link expired." }, { status: 410 });
  try {
    const response = await streamFile(
      (await getEnv()) as PutIoEnv,
      fileId,
      request.headers.get("Range"),
    );
    const headers = new Headers(response.headers);
    headers.set("Cache-Control", "private, no-store");
    headers.set("Access-Control-Allow-Origin", "*");
    return new Response(response.body, { status: response.status, headers });
  } catch {
    return NextResponse.json({ error: "The cast stream is unavailable." }, { status: 502 });
  }
}

export async function HEAD(
  request: NextRequest,
  context: { params: Promise<{ token: string }> },
) {
  const response = await GET(request, context);
  return new Response(null, { status: response.status, headers: response.headers });
}
