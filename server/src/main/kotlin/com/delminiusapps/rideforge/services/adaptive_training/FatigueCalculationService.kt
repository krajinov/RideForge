package com.delminiusapps.rideforge.services.adaptive_training

import com.delminiusapps.rideforge.models.WorkoutSession
import java.time.LocalDate
import kotlin.math.roundToInt

class FatigueCalculationService {

    data class FatigueState(
        val ctl: Double,
        val atl: Double,
        val tsb: Double
    )

    data class DailyFatigue(
        val date: String,
        val ctl: Double,
        val atl: Double,
        val tsb: Double,
        val tss: Int
    )

    fun calculateCurrentFatigue(sessions: List<WorkoutSession>): FatigueState {
        val history = calculateFatigueHistory(sessions)
        val todayStr = LocalDate.now().toString()
        val todayState = history.lastOrNull { it.date == todayStr } 
            ?: history.lastOrNull()
        return FatigueState(
            ctl = todayState?.ctl ?: 0.0,
            atl = todayState?.atl ?: 0.0,
            tsb = todayState?.tsb ?: 0.0
        )
    }

    fun calculateFatigueHistory(sessions: List<WorkoutSession>): List<DailyFatigue> {
        val completedSessions = sessions
            .filter { it.completedAt != null }
            .sortedBy { it.completedAt }

        if (completedSessions.isEmpty()) {
            return emptyList()
        }

        // Group sessions by date (YYYY-MM-DD)
        val tssByDate = completedSessions.groupBy { session ->
            session.completedAt!!.substringBefore("T")
        }.mapValues { entry ->
            entry.value.sumOf { it.tss ?: 0 }
        }

        val firstSessionDateStr = completedSessions.first().completedAt!!.substringBefore("T")
        var startLocalDate = try {
            LocalDate.parse(firstSessionDateStr)
        } catch (e: Exception) {
            LocalDate.now().minusDays(14) // Fallback
        }
        val today = LocalDate.now()

        val endLocalDate = if (today.isBefore(startLocalDate)) startLocalDate else today

        val history = mutableListOf<DailyFatigue>()
        var ctl = 0.0
        var atl = 0.0

        var currentDate = startLocalDate
        while (!currentDate.isAfter(endLocalDate)) {
            val dateStr = currentDate.toString()
            val tss = tssByDate[dateStr] ?: 0

            // Banister model calculation
            ctl = ctl + (tss - ctl) / 42.0
            atl = atl + (tss - atl) / 7.0
            val tsb = ctl - atl

            history.add(
                DailyFatigue(
                    date = dateStr,
                    ctl = round(ctl),
                    atl = round(atl),
                    tsb = round(tsb),
                    tss = tss
                )
            )
            currentDate = currentDate.plusDays(1)
        }

        return history
    }

    private fun round(value: Double): Double {
        return (value * 10.0).roundToInt() / 10.0
    }
}
