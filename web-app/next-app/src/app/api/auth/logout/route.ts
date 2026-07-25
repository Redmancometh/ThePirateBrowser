import { NextRequest, NextResponse } from "next/server";
import {
  destroySession,
  errorResponse,
  requireCsrf,
} from "@/lib/auth";
import { getEnv } from "@/lib/db";

export async function POST(request: NextRequest) {
  try {
    requireCsrf(request);
    const env = await getEnv();
    const response = new NextResponse(null, { status: 204 });
    await destroySession(request, response, env.DB, env);
    return response;
  } catch (error) {
    return errorResponse(error);
  }
}
