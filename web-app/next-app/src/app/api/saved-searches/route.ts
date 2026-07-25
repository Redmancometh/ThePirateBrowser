import { errorResponse, requireCsrf, requireUser } from "@/lib/auth";
import { createSavedSearch, getDb, listSavedSearches } from "@/lib/db";
import { type NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  try {
    const user = await requireUser(request);
    return NextResponse.json(await listSavedSearches(await getDb(), user.id));
  } catch (error) {
    return errorResponse(error);
  }
}

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const saved = await createSavedSearch(await getDb(), user.id, await request.json());
    return NextResponse.json(saved, { status: 201 });
  } catch (error) {
    return errorResponse(error);
  }
}
