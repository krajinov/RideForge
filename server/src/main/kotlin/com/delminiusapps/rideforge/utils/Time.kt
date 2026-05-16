package com.delminiusapps.rideforge.utils

import java.time.Instant
import java.util.UUID

fun nowIso(): String = Instant.now().toString()

fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
