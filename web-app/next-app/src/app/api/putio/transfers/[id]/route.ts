import { requireCsrf, requireUser } from "@/lib/auth";
import { getDb, getEnv, recordAudit } from "@/lib/db";
import { cancelTransfer, type PutIoEnv } from "@/lib/putio/client";
import { putIoErrorResponse } from "@/lib/putio/http";
import { type NextRequest, NextResponse } from "next/server";

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const id = Number((await params).id);
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error("A valid transfer is required.");
    await cancelTransfer((await getEnv()) as PutIoEnv, id);
    await recordAudit(await getDb(), user.username, "TRANSFER_CANCEL", "transfer", String(id));
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return putIoErrorResponse(error);
  }
}
