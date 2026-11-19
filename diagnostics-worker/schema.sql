CREATE TABLE IF NOT EXISTS diagnostic_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id TEXT NOT NULL UNIQUE,
    installation_id TEXT NOT NULL,
    received_at TEXT NOT NULL DEFAULT (datetime('now')),
    client_time_ms INTEGER NOT NULL,
    app_version TEXT NOT NULL,
    android_api INTEGER NOT NULL,
    device_model TEXT NOT NULL,
    service TEXT,
    event TEXT NOT NULL,
    phase TEXT,
    kind TEXT,
    angle INTEGER,
    result TEXT,
    language_tag TEXT NOT NULL DEFAULT 'und',
    locked INTEGER NOT NULL,
    interactive INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS diagnostic_events_installation_time
    ON diagnostic_events (installation_id, received_at);
CREATE INDEX IF NOT EXISTS diagnostic_events_received_at
    ON diagnostic_events (received_at);
