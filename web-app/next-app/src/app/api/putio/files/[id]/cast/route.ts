import { requireCsrf, requireUser } from "@/lib/auth";
import { getDb, recordAudit } from "@/lib/db";
import { putIoErrorResponse } from "@/lib/putio/http";
import { createCastGrant } from "@/lib/putio/repository";
import { type NextRequest, NextResponse } from "next/server";

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const id = Number((await params).id);
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error("A valid file is required.");
    const db = await getDb();
    const grant = await createCastGrant(db, id, user.id);
    await recordAudit(db, user.username, "CAST_GRANT_CREATE", "file", String(id));
    return NextResponse.json({
      url: new URL(`/api/cast/${grant.token}`, request.nextUrl.origin).toString(),
      expiresAt: grant.expiresAt,
    });
  } catch (error) {
    return putIoErrorResponse(error);
  }
}
