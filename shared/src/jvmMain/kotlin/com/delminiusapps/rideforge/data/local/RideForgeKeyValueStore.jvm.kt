package com.delminiusapps.rideforge.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual fun createRideForgeKeyValueStore(): RideForgeKeyValueStore {
    val databaseFile = File(System.getProperty("user.home"), ".rideforge/rideforge_workouts.db")
    databaseFile.parentFile?.mkdirs()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
    runCatching {
        RideForgeDatabase.Schema.create(driver)
    }.onFailure {
        runCatching {
            RideForgeDatabase.Schema.migrate(driver, 1, RideForgeDatabase.Schema.version)
        }
    }
    return SqlDelightRideForgeKeyValueStore(driver)
}
