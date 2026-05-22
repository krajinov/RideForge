ALTER TABLE workout_sessions
    ADD COLUMN has_real_trainer_data BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE metric_samples
    ADD COLUMN elapsed_seconds INTEGER;

CREATE TABLE strava_connections (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    athlete_id TEXT,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at_epoch_seconds BIGINT NOT NULL,
    scope TEXT NOT NULL,
    connected_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE strava_syncs (
    session_id TEXT PRIMARY KEY REFERENCES workout_sessions(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    upload_id TEXT,
    activity_id TEXT,
    activity_url TEXT,
    error TEXT,
    synced_at TEXT,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_strava_syncs_user_status
    ON strava_syncs(user_id, status, updated_at DESC);
