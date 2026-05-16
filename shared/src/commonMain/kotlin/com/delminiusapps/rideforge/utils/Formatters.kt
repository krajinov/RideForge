package com.delminiusapps.rideforge.utils

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
}

fun formatDateLabel(month: String, day: Int, year: Int): String = "$month $day, $year"
