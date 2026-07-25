import { NextRequest, NextResponse } from "next/server";
import {
  HttpError,
  errorResponse,
  parseBody,
  requireAdmin,
  requireCsrf,
} from "@/lib/auth";
import { findUserById, getDb, publicUser, recordAudit } from "@/lib/db";

export async function PATCH(request: NextRequest) {
  try {
    requireCsrf(request);
    const actor = await requireAdmin(request);
    const body = await parseBody(request);
    const id = String(body.id ?? "");
    const enabled = body.enabled === true;
    if (!id) throw new HttpError(400, "User id is required.");
    if (id === actor.id && !enabled) throw new HttpError(400, "You cannot disable your own account.");
    const db = await getDb();
    const existing = await findUserById(db, id);
    if (!existing) throw new HttpError(404, "User not found.");
    await db.batch([
      db
        .prepare("UPDATE user_accounts SET enabled = ?, updated_at = ? WHERE id = ?")
        .bind(enabled ? 1 : 0, new Date().toISOString(), id),
      ...(enabled
        ? []
        : [db.prepare("DELETE FROM user_sessions WHERE user_id = ?").bind(id)]),
    ]);
    const updated = await findUserById(db, id);
    await recordAudit(
      db,
      actor.username,
      "ACCOUNT_ENABLED",
      "user",
      id,
      String(enabled),
    );
    return NextResponse.json(publicUser(updated!));
  } catch (error) {
    return errorResponse(error);
  }
}
