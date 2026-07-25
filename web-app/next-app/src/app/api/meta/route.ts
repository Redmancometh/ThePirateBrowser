import { NextResponse } from "next/server";
import { getEnv } from "@/lib/db";

export async function GET() {
  const env = await getEnv();
  const inviteCode =
    env.REGISTRATION_INVITE_CODE ?? env.WEB_REGISTRATION_INVITE_CODE;
  return NextResponse.json({
    canary: (env.BUILD_CANARY || env.APP_VERSION || "local").toUpperCase(),
    registrationEnabled: Boolean(inviteCode?.trim()),
  });
}
