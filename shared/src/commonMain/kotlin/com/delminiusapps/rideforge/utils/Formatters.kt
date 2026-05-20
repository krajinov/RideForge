package com.delminiusapps.rideforge.utils

fun formatDuration(seconds: Int): String {
    val clampedSeconds = seconds.coerceAtLeast(0)
    val minutes = clampedSeconds / 60
    val remainingSeconds = clampedSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
}

fun formatDateLabel(month: String, day: Int, year: Int): String = "$month $day, $year"
