CREATE TABLE IF NOT EXISTS crash_reports (
    report_id TEXT PRIMARY KEY,
    received_at TEXT NOT NULL,
    occurred_at_ms INTEGER NOT NULL,
    canary TEXT NOT NULL,
    version_name TEXT NOT NULL,
    android_release TEXT NOT NULL,
    sdk_int INTEGER NOT NULL,
    manufacturer TEXT NOT NULL,
    model TEXT NOT NULL,
    thread_name TEXT NOT NULL,
    exception_type TEXT NOT NULL,
    exception_message TEXT NOT NULL,
    stack_trace TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS crash_reports_received_at
    ON crash_reports(received_at DESC);
