import { NextRequest, NextResponse } from "next/server";
import { errorResponse, requireAdmin } from "@/lib/auth";
import { getDb } from "@/lib/db";

interface AuditRow {
  id: string;
  username: string;
  action: string;
  target_type: string;
  target_id: string | null;
  detail: string | null;
  created_at: string;
}

export async function GET(request: NextRequest) {
  try {
    await requireAdmin(request);
    const db = await getDb();
    const result = await db
      .prepare("SELECT * FROM audit_events ORDER BY created_at DESC LIMIT 200")
      .all<AuditRow>();
    return NextResponse.json(
      (result.results ?? []).map((row) => ({
        id: row.id,
        username: row.username,
        action: row.action,
        targetType: row.target_type,
        targetId: row.target_id,
        detail: row.detail,
        createdAt: row.created_at,
      })),
    );
  } catch (error) {
    return errorResponse(error);
  }
}
