package com.delminiusapps.rideforge.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DataSourceState(
    val isUsingMockData: Boolean = false,
    val message: String? = null,
)

class DataSourceMonitor {
    private val _state = MutableStateFlow(DataSourceState())
    val state: StateFlow<DataSourceState> = _state

    fun markRemote() {
        _state.value = DataSourceState(isUsingMockData = false)
    }

    fun markFallback(reason: Throwable) {
        _state.value = DataSourceState(
            isUsingMockData = true,
            message = reason.message ?: "Backend unavailable. Showing mock data.",
        )
    }
}
