package com.delminiusapps.rideforge.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.delminiusapps.rideforge.config.JwtConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

data class TokenPair(val accessToken: String, val refreshToken: String)

class JwtService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun issueTokens(userId: String): TokenPair {
        return TokenPair(
            accessToken = createToken(userId, "access", Instant.now().plus(config.accessTokenMinutes, ChronoUnit.MINUTES)),
            refreshToken = createToken(userId, "refresh", Instant.now().plus(config.refreshTokenDays, ChronoUnit.DAYS)),
        )
    }

    fun validateRefreshToken(token: String): String? {
        val decoded = verifier.verify(token)
        return if (decoded.getClaim("type").asString() == "refresh") {
            decoded.getClaim("userId").asString()
        } else {
            null
        }
    }

    private fun createToken(userId: String, type: String, expiresAt: Instant): String {
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("userId", userId)
            .withClaim("type", type)
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
    }
}
