package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.dto.ConnectDeviceRequest
import com.delminiusapps.rideforge.models.Device
import com.delminiusapps.rideforge.repositories.DeviceRepository
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.nowIso

class DeviceService(private val devices: DeviceRepository) {
    suspend fun list(userId: String): List<Device> = devices.listAvailable(userId)

    suspend fun connect(userId: String, request: ConnectDeviceRequest): Device {
        if (request.name.isBlank()) badRequest("Device name is required")
        return devices.connect(
            Device(
                id = request.deviceId ?: newId("device"),
                userId = userId,
                name = request.name.trim(),
                type = request.type.ifBlank { "smart_trainer" },
                connectionStatus = "connected",
                supportsErg = request.supportsErg,
                lastConnectedAt = nowIso(),
            ),
        )
    }

    suspend fun disconnect(userId: String): Device? = devices.disconnect(userId)

    suspend fun current(userId: String): Device? = devices.current(userId)
}
