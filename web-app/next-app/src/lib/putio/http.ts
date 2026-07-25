import { errorResponse } from "@/lib/auth";
import { PutIoNotConfiguredError } from "@/lib/putio/client";
import { NextResponse } from "next/server";

export function putIoErrorResponse(error: unknown): NextResponse {
  if (error instanceof PutIoNotConfiguredError) {
    return NextResponse.json({ error: error.message }, { status: 503 });
  }
  if (
    error instanceof Error &&
    (error.message.startsWith("A valid ") || error.message.startsWith("Enter a valid "))
  ) {
    return NextResponse.json({ error: error.message }, { status: 400 });
  }
  return errorResponse(error);
}
