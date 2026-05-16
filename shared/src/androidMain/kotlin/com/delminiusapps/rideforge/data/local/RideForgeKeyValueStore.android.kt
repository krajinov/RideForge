package com.delminiusapps.rideforge.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

private var localStorageContext: Context? = null

fun configureRideForgeLocalStorageContext(context: Context) {
    localStorageContext = context.applicationContext
}

actual fun createRideForgeKeyValueStore(): RideForgeKeyValueStore {
    val context = localStorageContext
    return if (context == null) {
        InMemoryRideForgeKeyValueStore()
    } else {
        SqlDelightRideForgeKeyValueStore(
            AndroidSqliteDriver(RideForgeDatabase.Schema, context, DatabaseName),
        )
    }
}

private class InMemoryRideForgeKeyValueStore : RideForgeKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun readString(key: String): String? = values[key]

    override suspend fun writeString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private const val DatabaseName = "rideforge_workouts.db"
