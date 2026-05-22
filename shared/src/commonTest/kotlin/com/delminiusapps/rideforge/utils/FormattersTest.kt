package com.delminiusapps.rideforge.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {

    @Test
    fun formatDuration_formatsPositiveSeconds() {
        assertEquals("01:15", formatDuration(75))
    }

    @Test
    fun formatDuration_clampsNegativeSecondsToZero() {
        assertEquals("00:00", formatDuration(-42))
    }
}
