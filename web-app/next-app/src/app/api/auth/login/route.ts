import { NextRequest, NextResponse } from "next/server";
import {
  DUMMY_PASSWORD_HASH,
  HttpError,
  bootstrapAdminIfConfigured,
  createSession,
  errorResponse,
  parseBody,
  requireCsrf,
  verifyPassword,
} from "@/lib/auth";
import { findUserByUsername, getEnv, publicUser, recordAudit } from "@/lib/db";

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const env = await getEnv();
    await bootstrapAdminIfConfigured(env.DB, env);
    const body = await parseBody(request);
    const username = String(body.username ?? "").trim().toLowerCase();
    const password = String(body.password ?? "");
    const user = await findUserByUsername(env.DB, username);
    const passwordMatches = await verifyPassword(
      password,
      user?.password_hash ?? DUMMY_PASSWORD_HASH,
    );
    if (!user || user.enabled !== 1 || !passwordMatches) {
      throw new HttpError(401, "Username or password is incorrect.");
    }
    const response = NextResponse.json(publicUser(user));
    await createSession(response, env.DB, env, user.id);
    await recordAudit(env.DB, user.username, "ACCOUNT_LOGIN", "session", null);
    return response;
  } catch (error) {
    return errorResponse(error);
  }
}
