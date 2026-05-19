package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.auth.JwtService
import com.delminiusapps.rideforge.auth.PasswordHasher
import com.delminiusapps.rideforge.dto.AuthResponse
import com.delminiusapps.rideforge.dto.LoginRequest
import com.delminiusapps.rideforge.dto.RegisterRequest
import com.delminiusapps.rideforge.dto.toResponse
import com.delminiusapps.rideforge.models.User
import com.delminiusapps.rideforge.models.RefreshTokenRecord
import com.delminiusapps.rideforge.repositories.RefreshTokenRepository
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.conflict
import com.delminiusapps.rideforge.utils.newId
import com.delminiusapps.rideforge.utils.notFound
import com.delminiusapps.rideforge.utils.nowIso
import com.delminiusapps.rideforge.utils.unauthorized

class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val hasher: PasswordHasher,
    private val jwt: JwtService,
) {
    suspend fun register(request: RegisterRequest): AuthResponse {
        validateEmail(request.email)
        if (request.password.length < 8) badRequest("Password must be at least 8 characters")
        if (request.name.isBlank()) badRequest("Name is required")
        validateFtp(request.ftp)
        validateWeight(request.weightKg)
        if (users.findByEmail(request.email) != null) conflict("Email is already registered")

        val user = users.create(
            User(
                id = newId("user"),
                email = request.email.trim().lowercase(),
                passwordHash = hasher.hash(request.password),
                name = request.name.trim(),
                ftp = request.ftp,
                weightKg = request.weightKg,
                units = request.units.ifBlank { "metric" },
                createdAt = nowIso(),
            ),
        )
        return issueAuthResponse(user)
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        validateEmail(request.email)
        val user = users.findByEmail(request.email) ?: unauthorized("Invalid email or password")
        if (!hasher.verify(request.password, user.passwordHash)) unauthorized("Invalid email or password")
        return issueAuthResponse(user)
    }

    suspend fun refresh(refreshToken: String): AuthResponse {
        val userId = jwt.validateRefreshToken(refreshToken) ?: unauthorized("Invalid refresh token")
        val tokenHash = tokenHash(refreshToken)
        val record = refreshTokens.findByHash(tokenHash)
        if (record == null || record.userId != userId || record.revokedAt != null) {
            unauthorized("Invalid refresh token")
        }
        val user = users.findById(userId) ?: unauthorized("Invalid refresh token")
        if (!refreshTokens.revokeIfActive(tokenHash)) unauthorized("Invalid refresh token")
        return issueAuthResponse(user)
    }

    suspend fun me(userId: String) = users.findById(userId)?.toResponse() ?: notFound("User")

    suspend fun logout(userId: String, refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) {
            refreshTokens.revokeAllForUser(userId)
        } else {
            refreshTokens.revoke(tokenHash(refreshToken))
        }
    }

    private suspend fun issueAuthResponse(user: User): AuthResponse {
        val tokens = jwt.issueTokens(user.id)
        refreshTokens.save(
            RefreshTokenRecord(
                tokenHash = tokenHash(tokens.refreshToken),
                userId = user.id,
                createdAt = nowIso(),
            ),
        )
        return AuthResponse(tokens.accessToken, tokens.refreshToken, user.toResponse())
    }

    private fun tokenHash(token: String): String = hasher.hash(token)
}

fun validateEmail(email: String) {
    if (!email.contains("@") || !email.contains(".")) badRequest("A valid email is required")
}

fun validateFtp(ftp: Int) {
    if (ftp !in 80..600) badRequest("FTP must be between 80 and 600 watts")
}

fun validateWeight(weightKg: Double) {
    if (weightKg !in 35.0..180.0) badRequest("Weight must be between 35 and 180 kg")
}
