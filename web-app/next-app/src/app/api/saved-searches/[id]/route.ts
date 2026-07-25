import { errorResponse, requireCsrf, requireUser } from "@/lib/auth";
import { deleteSavedSearch, getDb, updateSavedSearch } from "@/lib/db";
import { type NextRequest, NextResponse } from "next/server";

type Context = { params: Promise<{ id: string }> };

export async function PUT(request: NextRequest, context: Context) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const { id } = await context.params;
    return NextResponse.json(
      await updateSavedSearch(await getDb(), user.id, id, await request.json()),
    );
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(request: NextRequest, context: Context) {
  try {
    requireCsrf(request);
    const user = await requireUser(request);
    const { id } = await context.params;
    await deleteSavedSearch(await getDb(), user.id, id);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
