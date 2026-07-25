import { NextRequest, NextResponse } from "next/server";
import {
  HttpError,
  createUser,
  errorResponse,
  parseBody,
  requireCsrf,
} from "@/lib/auth";
import { getEnv, publicUser, recordAudit } from "@/lib/db";

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
    if (!expected) throw new HttpError(403, "Registration is disabled.");
    const body = await parseBody(request);
    const invite = String(body.inviteCode ?? "");
    if (!sameSecret(invite, expected)) throw new HttpError(403, "Invitation code is invalid.");
    const user = await createUser(
      env.DB,
      String(body.username ?? ""),
      String(body.password ?? ""),
    );
    await recordAudit(env.DB, user.username, "ACCOUNT_REGISTER", "user", user.id);
    return NextResponse.json(publicUser(user), { status: 201 });
  } catch (error) {
    return errorResponse(error);
  }
}
