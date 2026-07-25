import { errorResponse, requireUser } from "@/lib/auth";
import { getEnv } from "@/lib/db";
import { isPutIoConfigured, type PutIoEnv } from "@/lib/putio/client";
import { type NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  try {
    await requireUser(request);
    return NextResponse.json({
      configured: isPutIoConfigured((await getEnv()) as PutIoEnv),
    });
  } catch (error) {
    return errorResponse(error);
  }
}
