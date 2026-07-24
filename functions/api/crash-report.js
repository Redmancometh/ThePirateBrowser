const MAX_BODY_BYTES = 64 * 1024;
const MAX_TEXT = 512;
const MAX_STACK = 48_500;

export async function onRequestPost({ request, env }) {
  const contentLength = Number(request.headers.get("content-length") || "0");
  if (contentLength > MAX_BODY_BYTES) {
    return json({ error: "Report is too large" }, 413);
  }

  let report;
  try {
    const body = await request.text();
    if (new TextEncoder().encode(body).length > MAX_BODY_BYTES) {
      return json({ error: "Report is too large" }, 413);
    }
    report = JSON.parse(body);
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  if (
    report?.schema !== 1 ||
    !isUuid(report.reportId) ||
    !isCanary(report.canary) ||
    !isText(report.stackTrace, 1, MAX_STACK)
  ) {
    return json({ error: "Invalid crash report" }, 400);
  }

  await env.CRASH_DB.prepare(
    `INSERT OR IGNORE INTO crash_reports (
      report_id, received_at, occurred_at_ms, canary, version_name,
      android_release, sdk_int, manufacturer, model, thread_name,
      exception_type, exception_message, stack_trace
    ) VALUES (?, datetime('now'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    report.reportId,
    integer(report.occurredAtMs),
    text(report.canary, 16),
    text(report.versionName, 64),
    text(report.androidRelease, 64),
    integer(report.sdkInt),
    text(report.manufacturer, 128),
    text(report.model, 128),
    text(report.threadName, 128),
    text(report.exceptionType, MAX_TEXT),
    text(report.exceptionMessage, 2_000),
    text(report.stackTrace, MAX_STACK)
  ).run();

  return json({ accepted: true, reportId: report.reportId }, 202);
}

export function onRequest() {
  return json({ error: "Method not allowed" }, 405, { Allow: "POST" });
}

function json(value, status, headers = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...headers,
    },
  });
}

function text(value, limit) {
  return String(value ?? "").slice(0, limit);
}

function integer(value) {
  return Number.isSafeInteger(Number(value)) ? Number(value) : 0;
}

function isText(value, minimum, maximum) {
  return typeof value === "string"
    && value.length >= minimum
    && value.length <= maximum;
}

function isUuid(value) {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function isCanary(value) {
  return typeof value === "string" && /^(?:[0-9a-f]{7}|local)$/i.test(value);
}
