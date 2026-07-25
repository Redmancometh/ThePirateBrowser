import { requireUser } from "@/lib/auth";
import { getEnv } from "@/lib/db";
import { streamFile, type PutIoEnv } from "@/lib/putio/client";
import { putIoErrorResponse } from "@/lib/putio/http";
import { type NextRequest } from "next/server";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    await requireUser(request);
    const id = Number((await params).id);
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error("A valid file is required.");
    return await streamFile((await getEnv()) as PutIoEnv, id, request.headers.get("Range"));
  } catch (error) {
    return putIoErrorResponse(error);
  }
}
