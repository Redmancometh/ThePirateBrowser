import { NextRequest, NextResponse } from "next/server";
import {
  HttpError,
  createUser,
  errorResponse,
  parseBody,
  requireAdmin,
  requireCsrf,
} from "@/lib/auth";
import { UserRow, getDb, publicUser, recordAudit } from "@/lib/db";

export async function GET(request: NextRequest) {
  try {
    await requireAdmin(request);
    const db = await getDb();
    const result = await db
      .prepare("SELECT * FROM user_accounts ORDER BY username")
      .all<UserRow>();
    return NextResponse.json((result.results ?? []).map(publicUser));
  } catch (error) {
    return errorResponse(error);
  }
}

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const actor = await requireAdmin(request);
    const body = await parseBody(request);
    const role = String(body.role ?? "USER");
    if (role !== "USER" && role !== "ADMIN") throw new HttpError(400, "Role is invalid.");
    const db = await getDb();
    const user = await createUser(
      db,
      String(body.username ?? ""),
      String(body.password ?? ""),
      role,
    );
    await recordAudit(db, actor.username, "ACCOUNT_CREATE", "user", user.id, user.username);
    return NextResponse.json(publicUser(user), { status: 201 });
  } catch (error) {
    return errorResponse(error);
  }
}
