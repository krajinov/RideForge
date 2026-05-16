package com.delminiusapps.rideforge.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual fun createRideForgeKeyValueStore(): RideForgeKeyValueStore {
    return SqlDelightRideForgeKeyValueStore(
        NativeSqliteDriver(RideForgeDatabase.Schema, DatabaseName),
    )
}

private const val DatabaseName = "rideforge_workouts.db"
