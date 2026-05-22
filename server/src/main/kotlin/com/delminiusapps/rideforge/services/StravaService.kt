package com.delminiusapps.rideforge.services

import com.delminiusapps.rideforge.config.StravaConfig
import com.delminiusapps.rideforge.dto.StravaConnectUrlResponse
import com.delminiusapps.rideforge.dto.StravaStatusResponse
import com.delminiusapps.rideforge.dto.StravaSyncStatusResponse
import com.delminiusapps.rideforge.models.SessionStatus
import com.delminiusapps.rideforge.models.StravaConnection
import com.delminiusapps.rideforge.models.StravaSync
import com.delminiusapps.rideforge.models.StravaSyncStatus
import com.delminiusapps.rideforge.models.WorkoutSession
import com.delminiusapps.rideforge.repositories.SessionRepository
import com.delminiusapps.rideforge.repositories.StravaConnectionRepository
import com.delminiusapps.rideforge.repositories.StravaSyncRepository
import com.delminiusapps.rideforge.repositories.WorkoutRepository
import com.delminiusapps.rideforge.utils.badRequest
import com.delminiusapps.rideforge.utils.forbidden
import com.delminiusapps.rideforge.utils.notFound
import com.delminiusapps.rideforge.utils.nowIso
import com.delminiusapps.rideforge.utils.serviceUnavailable
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class StravaService(
    private val config: StravaConfig,
    private val connections: StravaConnectionRepository,
    private val syncs: StravaSyncRepository,
    private val sessions: SessionRepository,
    private val workouts: WorkoutRepository,
    private val stateService: StravaStateService,
    private val apiClient: StravaApiClient,
    private val tcxExporter: TcxWorkoutExporter,
) {
    suspend fun status(userId: String): StravaStatusResponse {
        val connection = connections.findByUserId(userId)
        return StravaStatusResponse(
            connected = connection != null,
            athleteId = connection?.athleteId,
        )
    }

    fun connectUrl(userId: String): StravaConnectUrlResponse {
        requireConfigured()
        val scope = "read,activity:write"
        val state = stateService.issue(userId)
        val url = buildString {
            append(config.baseUrl.trimEnd('/'))
            append("/oauth/authorize")
            append("?client_id=${encode(config.clientId.orEmpty())}")
            append("&redirect_uri=${encode(config.redirectUri)}")
            append("&response_type=code")
            append("&approval_prompt=auto")
            append("&scope=${encode(scope)}")
            append("&state=${encode(state)}")
        }
        return StravaConnectUrlResponse(url)
    }

    suspend fun completeOAuthCallback(
        code: String?,
        state: String?,
        scope: String?,
        error: String?,
    ): String {
        if (!error.isNullOrBlank()) {
            return callbackPage("Strava connection was not completed.")
        }
        val userId = stateService.verify(state ?: badRequest("Missing Strava OAuth state"))
        val authCode = code?.takeIf { it.isNotBlank() } ?: badRequest("Missing Strava OAuth code")
        val token = apiClient.exchangeCode(authCode)
        val grantedScope = scope ?: token.scope.orEmpty()
        if (!grantedScope.hasScope("activity:write")) {
            badRequest("Strava activity upload permission was not granted")
        }
        val timestamp = nowIso()
        connections.save(
            StravaConnection(
                userId = userId,
                athleteId = token.athleteId,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtEpochSeconds = token.expiresAtEpochSeconds,
                scope = grantedScope,
                connectedAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return callbackPage("Strava is connected. You can return to RideForge.")
    }

    suspend fun disconnect(userId: String): StravaStatusResponse {
        val connection = connections.findByUserId(userId)
        try {
            if (connection != null) {
                val accessToken = try {
                    validConnection(userId).accessToken
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    connection.accessToken
                }
                try {
                    apiClient.deauthorize(accessToken)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Do not let an expired/rejected Strava token keep the local account linked.
                }
            }
        } finally {
            connections.deleteByUserId(userId)
        }
        return StravaStatusResponse(connected = false)
    }

    suspend fun syncWorkout(userId: String, sessionId: String): StravaSyncStatusResponse {
        val session = requireCompletedSession(userId, sessionId)
        val existing = syncs.findBySessionId(sessionId)
        if (!session.hasRealTrainerData) {
            badRequest("Only workouts recorded from a real trainer can be synced to Strava")
        }

        val connection = validConnection(userId)
        val existingForAthlete = existing?.takeIf { it.athleteId == connection.athleteId }
        if (existingForAthlete?.status == StravaSyncStatus.synced) {
            return existingForAthlete.toResponse(session, connected = true)
        }

        if (existingForAthlete?.status == StravaSyncStatus.syncing) {
            val sync = if (existingForAthlete.uploadId != null) {
                refreshUploadStatus(connection, existingForAthlete)
            } else {
                existingForAthlete
            }
            return sync.toResponse(session, connected = true)
        }

        val workout = workouts.findById(session.workoutId) ?: notFound("Workout")
        val metrics = sessions.metricsForSession(sessionId)
        if (metrics.isEmpty()) {
            badRequest("Workout has no trainer metrics to upload")
        }

        val startingSync = StravaSync(
            sessionId = sessionId,
            userId = userId,
            status = StravaSyncStatus.syncing,
            athleteId = connection.athleteId,
            error = null,
            updatedAt = nowIso(),
        )
        if (!syncs.tryStartSync(startingSync)) {
            val current = syncs.findBySessionId(sessionId)
                ?.takeIf { it.athleteId == connection.athleteId }
                ?: notSynced(session)
            val sync = if (current.status == StravaSyncStatus.syncing && current.uploadId != null) {
                refreshUploadStatus(connection, current)
            } else {
                current
            }
            return sync.toResponse(session, connected = true)
        }

        val tcx = tcxExporter.export(session, workout, metrics)
        val externalId = "rideforge-${session.id}.tcx"
        val upload = try {
            apiClient.uploadTcx(
                accessToken = connection.accessToken,
                fileName = externalId,
                tcx = tcx,
                name = workout.name,
                description = "Indoor cycling workout completed in RideForge.",
                externalId = externalId,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failed = StravaSync(
                sessionId = sessionId,
                userId = userId,
                status = StravaSyncStatus.failed,
                athleteId = connection.athleteId,
                error = error.message ?: "Strava upload failed",
                updatedAt = nowIso(),
            )
            syncs.upsert(failed)
            throw error
        }
        val saved = syncFromUpload(session, upload, athleteId = connection.athleteId)
        val refreshed = if (saved.status == StravaSyncStatus.syncing && saved.uploadId != null) {
            refreshUploadStatus(connection, saved)
        } else {
            saved
        }
        return refreshed.toResponse(session, connected = true)
    }

    suspend fun syncStatus(userId: String, sessionId: String): StravaSyncStatusResponse {
        val session = requireCompletedSession(userId, sessionId)
        val connection = connections.findByUserId(userId)
        val existing = syncs.findBySessionId(sessionId)
        val existingForConnection = if (connection == null) {
            existing
        } else {
            existing?.takeIf { it.athleteId == connection.athleteId }
        }
        val sync = if (
            connection != null &&
            existingForConnection?.status == StravaSyncStatus.syncing &&
            existingForConnection.uploadId != null
        ) {
            refreshUploadStatus(validConnection(userId), existingForConnection)
        } else {
            existingForConnection
        }
        return (sync ?: notSynced(session)).toResponse(session, connected = connection != null)
    }

    private suspend fun validConnection(userId: String): StravaConnection {
        val connection = connections.findByUserId(userId) ?: badRequest("Connect Strava before syncing workouts")
        val refreshThreshold = Instant.now().epochSecond + TokenRefreshBufferSeconds
        if (connection.expiresAtEpochSeconds > refreshThreshold) return connection

        val token = apiClient.refreshToken(connection.refreshToken)
        val updated = connection.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAtEpochSeconds = token.expiresAtEpochSeconds,
            scope = token.scope ?: connection.scope,
            updatedAt = nowIso(),
        )
        return connections.save(updated)
    }

    private suspend fun refreshUploadStatus(
        connection: StravaConnection,
        sync: StravaSync,
    ): StravaSync {
        val uploadId = sync.uploadId ?: return sync
        val upload = apiClient.uploadStatus(connection.accessToken, uploadId)
        return syncFromUpload(
            session = sessions.findById(sync.sessionId) ?: return sync,
            upload = upload.copy(uploadId = upload.uploadId ?: uploadId),
            existing = sync,
        )
    }

    private suspend fun syncFromUpload(
        session: WorkoutSession,
        upload: StravaUploadResult,
        existing: StravaSync? = null,
        athleteId: String? = existing?.athleteId,
    ): StravaSync {
        val activityId = upload.activityId
        val now = nowIso()
        val status = when {
            activityId != null -> StravaSyncStatus.synced
            !upload.error.isNullOrBlank() -> StravaSyncStatus.failed
            upload.uploadId != null -> StravaSyncStatus.syncing
            else -> StravaSyncStatus.failed
        }
        val sync = StravaSync(
            sessionId = session.id,
            userId = session.userId,
            status = status,
            athleteId = athleteId,
            uploadId = upload.uploadId ?: existing?.uploadId,
            activityId = activityId ?: existing?.activityId,
            activityUrl = activityId?.let { "${config.baseUrl.trimEnd('/')}/activities/$it" } ?: existing?.activityUrl,
            error = upload.error.takeUnless { it.isNullOrBlank() }
                ?: if (status == StravaSyncStatus.failed) upload.status ?: "Strava upload failed" else null,
            syncedAt = if (status == StravaSyncStatus.synced) now else existing?.syncedAt,
            updatedAt = now,
        )
        return syncs.upsert(sync)
    }

    private suspend fun requireCompletedSession(userId: String, sessionId: String): WorkoutSession {
        val session = sessions.findById(sessionId) ?: notFound("Session")
        if (session.userId != userId) forbidden()
        if (session.status != SessionStatus.completed) badRequest("Only completed workouts can be synced to Strava")
        return session
    }

    private fun requireConfigured() {
        if (config.clientId.isNullOrBlank() || config.clientSecret.isNullOrBlank()) {
            serviceUnavailable("Strava integration is not configured")
        }
    }

    private fun notSynced(session: WorkoutSession): StravaSync = StravaSync(
        sessionId = session.id,
        userId = session.userId,
        status = StravaSyncStatus.not_synced,
        updatedAt = nowIso(),
    )

    private fun StravaSync.toResponse(
        session: WorkoutSession,
        connected: Boolean,
    ): StravaSyncStatusResponse = StravaSyncStatusResponse(
        status = status.name,
        activityId = activityId,
        activityUrl = activityUrl,
        error = error,
        canSync = session.status == SessionStatus.completed && session.hasRealTrainerData,
        connected = connected,
    )

    private fun String.hasScope(required: String): Boolean =
        split(",", " ")
            .map { it.trim() }
            .any { it == required }

    private fun callbackPage(message: String): String =
        """
        <!doctype html>
        <html>
          <head><meta charset="utf-8"><title>RideForge Strava</title></head>
          <body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 32px;">
            <h1>RideForge</h1>
            <p>$message</p>
          </body>
        </html>
        """.trimIndent()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val TokenRefreshBufferSeconds = 60L
    }
}
