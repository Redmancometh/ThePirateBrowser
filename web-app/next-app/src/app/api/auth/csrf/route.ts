import { NextResponse } from "next/server";
import { getEnv } from "@/lib/db";

export async function GET() {
  const env = await getEnv();
  const inviteCode =
    env.REGISTRATION_INVITE_CODE ?? env.WEB_REGISTRATION_INVITE_CODE;
  const tokenBytes = crypto.getRandomValues(new Uint8Array(32));
  const token = btoa(String.fromCharCode(...tokenBytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
  const secure = env.COOKIE_SECURE?.toLowerCase() !== "false";
  const response = NextResponse.json({
    headerName: "X-XSRF-TOKEN",
    parameterName: "_csrf",
    token,
    registrationEnabled: Boolean(inviteCode?.trim()),
  });
  response.cookies.set("XSRF-TOKEN", token, {
    httpOnly: false,
    secure,
    sameSite: "strict",
    path: "/",
    maxAge: 60 * 60,
  });
  return response;
}
