import { requireCsrf, requireUser } from "@/lib/auth";
import { getDb, getEnv, recordAudit } from "@/lib/db";
import {
  deleteFile,
  listFiles,
  renameFile,
  type PutIoEnv,
} from "@/lib/putio/client";
import { putIoErrorResponse } from "@/lib/putio/http";
import { type NextRequest, NextResponse } from "next/server";

type Context = { params: Promise<{ id: string }> };

async function fileId(context: Context): Promise<number> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id < 0) throw new Error("A valid file is required.");
  return id;
}

export async function GET(request: NextRequest, context: Context) {
  try {
    await requireUser(request);
    return NextResponse.json(
      await listFiles((await getEnv()) as PutIoEnv, await fileId(context)),
    );
  } catch (error) {
    return putIoErrorResponse(error);
  }
}

export async function PATCH(request: NextRequest, context: Context) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const id = await fileId(context);
    const body = (await request.json()) as { name?: string; parentId?: number };
    const parentId = Number(body.parentId ?? 0);
    await renameFile((await getEnv()) as PutIoEnv, id, body.name ?? "");
    await recordAudit(await getDb(), user.username, "FILE_RENAME", "file", String(id));
    return NextResponse.json(
      await listFiles((await getEnv()) as PutIoEnv, parentId),
    );
  } catch (error) {
    return putIoErrorResponse(error);
  }
}

export async function DELETE(request: NextRequest, context: Context) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const id = await fileId(context);
    await deleteFile((await getEnv()) as PutIoEnv, id);
    await recordAudit(await getDb(), user.username, "FILE_DELETE", "file", String(id));
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return putIoErrorResponse(error);
  }
}
