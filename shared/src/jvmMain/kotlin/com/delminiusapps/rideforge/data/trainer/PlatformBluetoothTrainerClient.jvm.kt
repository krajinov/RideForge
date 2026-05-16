package com.delminiusapps.rideforge.data.trainer

import com.delminiusapps.rideforge.domain.trainer.BluetoothTrainerClient

actual fun createPlatformBluetoothTrainerClient(): BluetoothTrainerClient {
    return SimulatedBluetoothTrainerClient(platformLabel = "JVM placeholder")
}
