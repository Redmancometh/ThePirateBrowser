import { NextRequest, NextResponse } from "next/server";
import {
  HttpError,
  errorResponse,
  hashPassword,
  parseBody,
  requireAdmin,
  requireCsrf,
  validatePassword,
} from "@/lib/auth";
import { findUserById, getDb, publicUser, recordAudit } from "@/lib/db";

export async function PATCH(request: NextRequest) {
  try {
    requireCsrf(request);
    const actor = await requireAdmin(request);
    const body = await parseBody(request);
    const id = String(body.id ?? "");
    const password = String(body.password ?? "");
    validatePassword(password);
    const db = await getDb();
    const existing = await findUserById(db, id);
    if (!existing) throw new HttpError(404, "User not found.");
    await db.batch([
      db
        .prepare("UPDATE user_accounts SET password_hash = ?, updated_at = ? WHERE id = ?")
        .bind(await hashPassword(password), new Date().toISOString(), id),
      db.prepare("DELETE FROM user_sessions WHERE user_id = ?").bind(id),
    ]);
    await recordAudit(db, actor.username, "ACCOUNT_PASSWORD_RESET", "user", id);
    return NextResponse.json(publicUser((await findUserById(db, id))!));
  } catch (error) {
    return errorResponse(error);
  }
}
