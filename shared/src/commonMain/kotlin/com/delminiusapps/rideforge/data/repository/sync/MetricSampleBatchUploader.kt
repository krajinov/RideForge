package com.delminiusapps.rideforge.data.repository.sync

import com.delminiusapps.rideforge.data.local.WorkoutLocalStorage
import com.delminiusapps.rideforge.domain.usecase.UploadMetricBatchUseCase
import com.delminiusapps.rideforge.models.MetricSample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MetricSampleBatchUploader(
    private val uploadMetricBatchUseCase: UploadMetricBatchUseCase,
    private val storage: WorkoutLocalStorage,
) {
    private val mutex = Mutex()

    suspend fun record(sessionId: String, sample: MetricSample) {
        mutex.withLock {
            storage.appendMetricUploadSample(sessionId, sample)
        }
    }

    suspend fun flush(sessionId: String) {
        val batch = mutex.withLock {
            storage.drainMetricUploadSamples(sessionId)
        }
        if (batch.isNotEmpty()) {
            runCatching {
                uploadMetricBatchUseCase(sessionId, batch)
            }.onFailure {
                mutex.withLock {
                    storage.prependMetricUploadSamples(sessionId, batch)
                }
            }
        }
    }
}
