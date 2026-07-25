import { requireCsrf, requireUser } from "@/lib/auth";
import { getDb, getEnv, recordAudit } from "@/lib/db";
import {
  addTransfer,
  listTransfers,
  type PutIoEnv,
} from "@/lib/putio/client";
import { putIoErrorResponse } from "@/lib/putio/http";
import { type NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  try {
    await requireUser(request);
    return NextResponse.json(await listTransfers((await getEnv()) as PutIoEnv));
  } catch (error) {
    return putIoErrorResponse(error);
  }
}

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const body = (await request.json()) as { magnet?: string };
    await addTransfer((await getEnv()) as PutIoEnv, body.magnet ?? "");
    await recordAudit(await getDb(), user.username, "TRANSFER_ADD", "transfer");
    return new NextResponse(null, { status: 201 });
  } catch (error) {
    return putIoErrorResponse(error);
  }
}
