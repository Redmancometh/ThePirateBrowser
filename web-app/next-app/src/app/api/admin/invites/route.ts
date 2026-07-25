import { NextRequest, NextResponse } from "next/server";
import {
  HttpError,
  errorResponse,
  parseBody,
  requireAdmin,
  requireCsrf,
} from "@/lib/auth";
import { getDb, recordAudit } from "@/lib/db";
import { createInvite, listInvites, revokeInvite } from "@/lib/invites";

export async function GET(request: NextRequest) {
  try {
    await requireAdmin(request);
    return NextResponse.json(await listInvites(await getDb()));
  } catch (error) {
    return errorResponse(error);
  }
}

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const actor = await requireAdmin(request);
    const body = await parseBody(request);
    const rawExpiry = body.expiryDays;
    const expiryDays = rawExpiry === null || rawExpiry === "never"
      ? null
      : Number(rawExpiry ?? 30);
    const db = await getDb();
    const created = await createInvite(
      db,
      actor.username,
      String(body.label ?? ""),
      expiryDays,
    );
    await recordAudit(
      db,
      actor.username,
      "INVITE_CREATE",
      "invite",
      created.invite.id,
      created.invite.label,
    );
    return NextResponse.json(created, { status: 201 });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(request: NextRequest) {
  try {
    requireCsrf(request);
    const actor = await requireAdmin(request);
    const body = await parseBody(request);
    const id = String(body.id ?? "");
    if (!id) throw new HttpError(400, "Invite id is required.");
    const db = await getDb();
    await revokeInvite(db, id);
    await recordAudit(db, actor.username, "INVITE_REVOKE", "invite", id);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
