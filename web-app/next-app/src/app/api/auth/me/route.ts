import { NextRequest, NextResponse } from "next/server";
import { errorResponse, requireUser } from "@/lib/auth";
import { getEnv } from "@/lib/db";

export async function GET(request: NextRequest) {
  try {
    const [user, env] = await Promise.all([requireUser(request), getEnv()]);
    return NextResponse.json({
      ...user,
      putIoConfigured: Boolean(env.PUTIO_OAUTH_TOKEN?.trim()),
      canary: (env.BUILD_CANARY || env.APP_VERSION || "local").toUpperCase(),
    });
  } catch (error) {
    return errorResponse(error);
  }
}
