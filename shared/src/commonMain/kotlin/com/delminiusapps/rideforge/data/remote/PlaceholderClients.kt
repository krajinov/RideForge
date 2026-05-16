package com.delminiusapps.rideforge.data.remote

interface ApiClient {
    suspend fun get(path: String): String
    suspend fun post(path: String, body: String): String
}

class MockApiClient : ApiClient {
    override suspend fun get(path: String): String = "{}"
    override suspend fun post(path: String, body: String): String = "{}"
}
