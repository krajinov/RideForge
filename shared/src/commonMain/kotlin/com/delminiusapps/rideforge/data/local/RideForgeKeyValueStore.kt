package com.delminiusapps.rideforge.data.local

interface RideForgeKeyValueStore {
    suspend fun readString(key: String): String?
    suspend fun writeString(key: String, value: String)
    suspend fun remove(key: String)
}

expect fun createRideForgeKeyValueStore(): RideForgeKeyValueStore
