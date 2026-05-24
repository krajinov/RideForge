package com.delminiusapps.rideforge.utils

fun formatDuration(seconds: Int): String {
    val clampedSeconds = seconds.coerceAtLeast(0)
    val minutes = clampedSeconds / 60
    val remainingSeconds = clampedSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
}

fun formatDateLabel(month: String, day: Int, year: Int): String = "$month $day, $year"

fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        "${rounded.toInt()}.0"
    } else {
        rounded.toString()
    }
}
