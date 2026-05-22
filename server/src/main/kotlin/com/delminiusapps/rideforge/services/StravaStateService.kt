package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.newId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class StravaStateService(
    private val secret: String,
    private val ttlSeconds: Long = 600,
) {
    fun issue(userId: String): String {
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        val payload = "$userId|$expiresAt|${newId("strava-state")}"
        val encodedPayload = base64(payload.toByteArray(StandardCharsets.UTF_8))
        val signature = sign(encodedPayload)
        return "$encodedPayload.$signature"
    }

    fun verify(state: String): String {
        val parts = state.split(".")
        if (parts.size != 2) badRequest("Invalid Strava OAuth state")
        val expectedSignature = sign(parts[0])
        if (!MessageDigest.isEqual(parts[1].toByteArray(), expectedSignature.toByteArray())) {
            badRequest("Invalid Strava OAuth state")
        }
        val payload = String(base64Decode(parts[0]), StandardCharsets.UTF_8)
        val fields = payload.split("|")
        if (fields.size != 3) badRequest("Invalid Strava OAuth state")
        val expiresAt = fields[1].toLongOrNull() ?: badRequest("Invalid Strava OAuth state")
        if (expiresAt < Instant.now().epochSecond) badRequest("Expired Strava OAuth state")
        return fields[0]
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return base64(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun base64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64Decode(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)
}
