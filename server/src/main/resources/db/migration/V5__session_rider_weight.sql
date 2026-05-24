ALTER TABLE workout_sessions
    ADD COLUMN rider_weight_kg DOUBLE PRECISION;

UPDATE workout_sessions
SET rider_weight_kg = users.weight_kg
FROM users
WHERE workout_sessions.user_id = users.id;

ALTER TABLE workout_sessions
    ALTER COLUMN rider_weight_kg SET DEFAULT 78.0;

ALTER TABLE workout_sessions
    ALTER COLUMN rider_weight_kg SET NOT NULL;
