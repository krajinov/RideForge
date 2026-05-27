CREATE TABLE user_joined_plans (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id TEXT NOT NULL REFERENCES training_plans(id) ON DELETE CASCADE,
    joined_at TEXT NOT NULL,
    PRIMARY KEY (user_id, plan_id)
);

CREATE TABLE user_completed_plan_workouts (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id TEXT NOT NULL REFERENCES training_plans(id) ON DELETE CASCADE,
    workout_id TEXT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    completed_at TEXT NOT NULL,
    PRIMARY KEY (user_id, plan_id, workout_id)
);

INSERT INTO user_joined_plans (user_id, plan_id, joined_at)
SELECT id, enrolled_plan_id, TO_CHAR(NOW() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
FROM users
WHERE enrolled_plan_id IS NOT NULL;
