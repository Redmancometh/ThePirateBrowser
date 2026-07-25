CREATE TABLE registration_invites (
    id TEXT PRIMARY KEY,
    code_hash TEXT NOT NULL UNIQUE,
    code_hint TEXT NOT NULL,
    label TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT,
    used_by TEXT,
    used_at TEXT,
    revoked_at TEXT
);

CREATE INDEX registration_invites_active_idx
    ON registration_invites(used_at, revoked_at, expires_at);

CREATE INDEX registration_invites_created_at_idx
    ON registration_invites(created_at DESC);
