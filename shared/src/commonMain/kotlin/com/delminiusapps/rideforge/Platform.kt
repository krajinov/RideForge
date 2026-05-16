package com.delminiusapps.rideforge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform