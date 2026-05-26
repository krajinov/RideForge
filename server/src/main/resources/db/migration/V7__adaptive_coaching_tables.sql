-- Alter existing workout_analyses table to add new metrics
ALTER TABLE workout_analyses ADD COLUMN avg_deviation_power DOUBLE PRECISION;
ALTER TABLE workout_analyses ADD COLUMN best_5s_power INTEGER;
ALTER TABLE workout_analyses ADD COLUMN best_30s_power INTEGER;
ALTER TABLE workout_analyses ADD COLUMN best_1m_power INTEGER;
ALTER TABLE workout_analyses ADD COLUMN best_5m_power INTEGER;
ALTER TABLE workout_analyses ADD COLUMN best_20m_power INTEGER;

-- Create ftp_estimates table for detailed FTP detection/recommendations
CREATE TABLE ftp_estimates (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    current_ftp INTEGER NOT NULL,
    estimated_ftp INTEGER NOT NULL,
    confidence_score INTEGER NOT NULL,
    recommendation TEXT NOT NULL, -- 'KEEP', 'INCREASE', 'DECREASE', 'TEST_REQUIRED'
    status TEXT NOT NULL, -- 'pending_approval', 'approved', 'dismissed'
    message TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_ftp_estimates_user ON ftp_estimates(user_id, created_at DESC);

-- Create fatigue_snapshots table to log daily CTL, ATL, TSB status
CREATE TABLE fatigue_snapshots (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date TEXT NOT NULL,
    ctl DOUBLE PRECISION NOT NULL,
    atl DOUBLE PRECISION NOT NULL,
    tsb DOUBLE PRECISION NOT NULL,
    freshness_status TEXT NOT NULL, -- 'FRESH', 'BALANCED', 'FATIGUED', 'OVERREACHED'
    created_at TEXT NOT NULL,
    CONSTRAINT uq_user_date UNIQUE (user_id, date)
);

CREATE INDEX idx_fatigue_snapshots_user ON fatigue_snapshots(user_id, date DESC);

-- Create adaptive_recommendations table
CREATE TABLE adaptive_recommendations (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL, -- 'RECOVERY', 'TRAINING', 'TEST'
    workout_id TEXT REFERENCES workouts(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_adaptive_recs_user ON adaptive_recommendations(user_id, created_at DESC);

-- Create coach_insights table
CREATE TABLE coach_insights (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    severity TEXT NOT NULL, -- 'positive', 'neutral', 'warning'
    source_metric TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_coach_insights_user ON coach_insights(user_id, created_at DESC);
