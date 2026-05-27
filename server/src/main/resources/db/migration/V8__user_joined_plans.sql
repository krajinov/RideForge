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

INSERT INTO user_completed_plan_workouts (user_id, plan_id, workout_id, completed_at)
SELECT ws.user_id, w.plan_id, ws.workout_id, ws.completed_at
FROM workout_sessions ws
JOIN workouts w ON ws.workout_id = w.id
JOIN users u ON ws.user_id = u.id
WHERE ws.status = 'completed'
  AND ws.completed_at IS NOT NULL
  AND u.enrolled_plan_id = w.plan_id
ON CONFLICT DO NOTHING;
