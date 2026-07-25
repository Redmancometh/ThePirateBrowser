import { NextRequest, NextResponse } from "next/server";
import {
  createUser,
  errorResponse,
  normalizeUsername,
  parseBody,
  requireCsrf,
  validatePassword,
} from "@/lib/auth";
import { getEnv, publicUser, recordAudit } from "@/lib/db";
import { releaseInvite, reserveInvite } from "@/lib/invites";

function sameSecret(left: string, right: string): boolean {
  const encoder = new TextEncoder();
  const a = encoder.encode(left);
  const b = encoder.encode(right);
  let mismatch = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) {
    mismatch |= (a[index % a.length] ?? 0) ^ (b[index % b.length] ?? 0);
  }
  return mismatch === 0;
}

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const env = await getEnv();
    const expected = (
      env.REGISTRATION_INVITE_CODE ?? env.WEB_REGISTRATION_INVITE_CODE
    )?.trim();
    const body = await parseBody(request);
    const invite = String(body.inviteCode ?? "");
    const username = normalizeUsername(String(body.username ?? ""));
    const password = String(body.password ?? "");
    validatePassword(password);
    const userId = crypto.randomUUID();
    const usesLegacyCode = Boolean(expected && sameSecret(invite, expected));
    const inviteId = usesLegacyCode
      ? null
      : await reserveInvite(env.DB, invite, userId);
    try {
      const user = await createUser(env.DB, username, password, "USER", userId);
      await recordAudit(
        env.DB,
        user.username,
        "ACCOUNT_REGISTER",
        "user",
        user.id,
        inviteId ? `invite:${inviteId}` : "legacy invite",
      );
      return NextResponse.json(publicUser(user), { status: 201 });
    } catch (error) {
      if (inviteId) await releaseInvite(env.DB, inviteId, userId);
      throw error;
    }
  } catch (error) {
    return errorResponse(error);
  }
}
