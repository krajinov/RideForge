package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.dto.UpdateFtpRequest
import com.delminiusapps.rideforge.dto.UpdateProfileRequest
import com.delminiusapps.rideforge.dto.UpdateWeightRequest
import com.delminiusapps.rideforge.dto.toResponse
import com.delminiusapps.rideforge.repositories.UserRepository
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.notFound

class ProfileService(private val users: UserRepository) {
    suspend fun getProfile(userId: String) = users.findById(userId)?.toResponse() ?: notFound("User")

    suspend fun updateProfile(userId: String, request: UpdateProfileRequest) =
        updateUser(userId) { user ->
            request.ftp?.let(::validateFtp)
            request.weightKg?.let(::validateWeight)
            if (request.name != null && request.name.isBlank()) badRequest("Name cannot be blank")
            user.copy(
                name = request.name?.trim() ?: user.name,
                ftp = request.ftp ?: user.ftp,
                weightKg = request.weightKg ?: user.weightKg,
                units = request.units?.ifBlank { user.units } ?: user.units,
            )
        }

    suspend fun updateFtp(userId: String, request: UpdateFtpRequest) =
        updateUser(userId) {
            validateFtp(request.ftp)
            it.copy(ftp = request.ftp)
        }

    suspend fun updateWeight(userId: String, request: UpdateWeightRequest) =
        updateUser(userId) {
            validateWeight(request.weightKg)
            it.copy(weightKg = request.weightKg)
        }

    private suspend fun updateUser(
        userId: String,
        transform: (com.delminiusapps.rideforge.models.User) -> com.delminiusapps.rideforge.models.User,
    ) = users.update(transform(users.findById(userId) ?: notFound("User"))).toResponse()
}
