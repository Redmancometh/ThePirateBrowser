PRAGMA foreign_keys = ON;

CREATE TABLE user_accounts (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE user_sessions (
    token_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX user_sessions_user_id_idx ON user_sessions(user_id);
CREATE INDEX user_sessions_expires_at_idx ON user_sessions(expires_at);

CREATE TABLE user_settings (
    user_id TEXT PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    enabled_sources TEXT NOT NULL
);

CREATE TABLE saved_searches (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    query TEXT NOT NULL,
    minimum_seeders INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    known_magnets TEXT NOT NULL DEFAULT '[]',
    last_checked_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX saved_searches_user_id_idx
    ON saved_searches(user_id, created_at DESC);

CREATE TABLE audit_events (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT,
    detail TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX audit_events_created_at_idx ON audit_events(created_at DESC);
CREATE INDEX audit_events_username_idx ON audit_events(username);

CREATE TABLE cast_grants (
    token_hash TEXT PRIMARY KEY,
    file_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX cast_grants_expires_at_idx ON cast_grants(expires_at);
