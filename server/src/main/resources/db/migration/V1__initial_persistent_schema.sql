CREATE TABLE training_plans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    duration_weeks INTEGER NOT NULL,
    difficulty TEXT NOT NULL,
    workout_count INTEGER NOT NULL
);

CREATE TABLE workouts (
    id TEXT PRIMARY KEY,
    plan_id TEXT NOT NULL REFERENCES training_plans(id),
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    difficulty TEXT NOT NULL,
    target_zones TEXT NOT NULL,
    week_number INTEGER NOT NULL,
    day_number INTEGER NOT NULL,
    workout_type TEXT NOT NULL
);

CREATE INDEX idx_workouts_plan_order
    ON workouts(plan_id, week_number, day_number, id);

CREATE TABLE workout_intervals (
    id TEXT PRIMARY KEY,
    workout_id TEXT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    target_power_watts INTEGER,
    target_ftp_percent INTEGER,
    type TEXT NOT NULL
);

CREATE INDEX idx_workout_intervals_workout
    ON workout_intervals(workout_id, id);

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    ftp INTEGER NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    units TEXT NOT NULL,
    created_at TEXT NOT NULL,
    enrolled_plan_id TEXT REFERENCES training_plans(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_users_email_lower
    ON users(LOWER(email));

CREATE TABLE workout_sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_id TEXT NOT NULL REFERENCES workouts(id),
    status TEXT NOT NULL,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    elapsed_seconds INTEGER NOT NULL DEFAULT 0,
    average_power INTEGER,
    normalized_power INTEGER,
    calories INTEGER,
    tss INTEGER,
    completion_percent INTEGER
);

CREATE INDEX idx_workout_sessions_history
    ON workout_sessions(user_id, status, completed_at DESC, started_at DESC, id);

CREATE TABLE metric_samples (
    id BIGSERIAL PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    timestamp TEXT NOT NULL,
    current_power INTEGER NOT NULL,
    target_power INTEGER NOT NULL,
    cadence INTEGER NOT NULL,
    heart_rate INTEGER NOT NULL,
    speed_kmh DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_metric_samples_session
    ON metric_samples(session_id, id);

CREATE TABLE devices (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    connection_status TEXT NOT NULL,
    supports_erg BOOLEAN NOT NULL,
    last_connected_at TEXT
);

CREATE INDEX idx_devices_user_status
    ON devices(user_id, connection_status, id);

CREATE TABLE refresh_tokens (
    token_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    revoked_at TEXT
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, revoked_at);
