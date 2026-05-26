CREATE TABLE workout_analyses (
    session_id TEXT PRIMARY KEY REFERENCES workout_sessions(id) ON DELETE CASCADE,
    completion_percent INTEGER NOT NULL,
    interval_success_rate INTEGER NOT NULL,
    erg_compliance_score INTEGER,
    cadence_consistency_score INTEGER,
    power_fade DOUBLE PRECISION,
    hr_drift DOUBLE PRECISION,
    estimated_rpe DOUBLE PRECISION NOT NULL,
    classification TEXT NOT NULL,
    coach_notes_summary TEXT NOT NULL,
    coach_notes_recommendation TEXT NOT NULL,
    coach_notes_recovery TEXT NOT NULL,
    coach_notes_next_workout TEXT NOT NULL
);

CREATE TABLE ftp_history (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    estimated_ftp INTEGER NOT NULL,
    previous_ftp INTEGER NOT NULL,
    session_id TEXT NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_ftp_history_user ON ftp_history(user_id, created_at DESC);

CREATE TABLE progression_levels (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_type TEXT NOT NULL,
    level DOUBLE PRECISION NOT NULL,
    updated_at TEXT NOT NULL,
    CONSTRAINT uq_user_workout_type UNIQUE (user_id, workout_type)
);

CREATE INDEX idx_progression_levels_user ON progression_levels(user_id);
