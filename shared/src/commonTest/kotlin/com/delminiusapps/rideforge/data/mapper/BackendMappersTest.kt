package com.delminiusapps.rideforge.data.mapper

import com.delminiusapps.rideforge.data.dto.WorkoutSessionDto
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendMappersTest {
    @Test
    fun mapsCompletedAtToSessionCompletionEpochMillis() {
        val session = WorkoutSessionDto(
            id = "session-a",
            userId = "user-a",
            workoutId = "workout-a",
            status = "completed",
            startedAt = "1970-01-01T00:00:00Z",
            completedAt = "1970-01-01T00:00:01Z",
            elapsedSeconds = 1,
            averageSpeedKmh = 31.4,
            totalDistanceKm = 0.0087,
        ).toDomainSummary("Workout A")

        assertEquals(1_000L, session.completedAtEpochMillis)
        assertEquals(31.4, session.averageSpeedKmh)
        assertEquals(0.0087, session.totalDistanceKm)
    }
}
