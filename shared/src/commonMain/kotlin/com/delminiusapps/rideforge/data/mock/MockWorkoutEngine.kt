package com.delminiusapps.rideforge.data.mock

import com.delminiusapps.rideforge.models.MetricSample
import com.delminiusapps.rideforge.models.Workout
import com.delminiusapps.rideforge.models.WorkoutInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class ActiveWorkoutState(
    val workout: Workout,
    val ftpWatts: Int = 240,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = workout.intervals.sumOf { it.durationSeconds },
    val currentIntervalIndex: Int = 0,
    val currentIntervalElapsedSeconds: Int = 0,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val sample: MetricSample = MetricSample(0, workout.intervals.first().targetPower(ftpWatts), workout.intervals.first().targetPower(ftpWatts), 88, 118),
    val samples: List<MetricSample> = emptyList(),
) {
    val currentInterval: WorkoutInterval = workout.intervals[currentIntervalIndex]
    val nextInterval: WorkoutInterval? = workout.intervals.getOrNull(currentIntervalIndex + 1)
    val totalProgress: Float = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val intervalProgress: Float = (currentIntervalElapsedSeconds.toFloat() / currentInterval.durationSeconds.toFloat()).coerceIn(0f, 1f)
}

class MockWorkoutEngine(
    private val workout: Workout,
    private val ftpWatts: Int,
    private val scope: CoroutineScope,
    initialElapsedSeconds: Int = 0,
    initialSamples: List<MetricSample> = emptyList(),
    initiallyPaused: Boolean = false,
) {
    private val totalSeconds = workout.intervals.sumOf { it.durationSeconds }
    private val random = Random(24)
    private var job: Job? = null

    private val _state = MutableStateFlow(
        restoredState(
            elapsedSeconds = initialElapsedSeconds.coerceIn(0, totalSeconds),
            samples = initialSamples,
            isPaused = initiallyPaused,
        ),
    )
    val state: StateFlow<ActiveWorkoutState> = _state

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (!_state.value.isComplete) {
                delay(1_000)
                if (!_state.value.isPaused) tick()
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        _state.value = _state.value.copy(isPaused = false)
    }

    fun skipInterval() {
        val current = _state.value
        val nextIndex = min(current.currentIntervalIndex + 1, workout.intervals.lastIndex)
        val nextElapsed = elapsedAtIntervalStart(nextIndex)
        updateElapsed(nextElapsed)
    }

    fun end() {
        _state.value = _state.value.copy(isComplete = true, isPaused = true)
        job?.cancel()
        job = null
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun tick() {
        val nextElapsed = min(_state.value.elapsedSeconds + 1, totalSeconds)
        updateElapsed(nextElapsed)
    }

    private fun updateElapsed(elapsed: Int) {
        val intervalIndex = intervalIndexAt(elapsed)
        val intervalStart = elapsedAtIntervalStart(intervalIndex)
        val interval = workout.intervals[intervalIndex]
        val previousHr = _state.value.sample.heartRateBpm
        val target = interval.targetPower(ftpWatts)
        val hard = target >= (ftpWatts * 1.05)
        val powerNoise = random.nextInt(-12, 13)
        val cadence = if (hard) random.nextInt(89, 96) else random.nextInt(85, 92)
        val heartRate = if (hard) {
            min(previousHr + random.nextInt(1, 4), 174)
        } else {
            max(previousHr - random.nextInt(0, 3), 112)
        }
        val sample = MetricSample(
            elapsedSeconds = elapsed,
            currentPowerWatts = max(target + powerNoise, 70),
            targetPowerWatts = target,
            cadenceRpm = cadence,
            heartRateBpm = heartRate,
        )
        val samples = (_state.value.samples + sample).takeLast(96)
        _state.value = _state.value.copy(
            elapsedSeconds = elapsed,
            currentIntervalIndex = intervalIndex,
            currentIntervalElapsedSeconds = elapsed - intervalStart,
            isComplete = elapsed >= totalSeconds,
            sample = sample,
            samples = samples,
        )
    }

    private fun intervalIndexAt(elapsed: Int): Int {
        var cursor = 0
        workout.intervals.forEachIndexed { index, interval ->
            val end = cursor + interval.durationSeconds
            if (elapsed < end) return index
            cursor = end
        }
        return workout.intervals.lastIndex
    }

    private fun elapsedAtIntervalStart(index: Int): Int {
        return workout.intervals.take(index).sumOf { it.durationSeconds }
    }

    private fun restoredState(
        elapsedSeconds: Int,
        samples: List<MetricSample>,
        isPaused: Boolean,
    ): ActiveWorkoutState {
        val intervalIndex = intervalIndexAt(elapsedSeconds)
        val intervalStart = elapsedAtIntervalStart(intervalIndex)
        val interval = workout.intervals[intervalIndex]
        val sample = samples.lastOrNull() ?: MetricSample(
            elapsedSeconds = elapsedSeconds,
            currentPowerWatts = interval.targetPower(ftpWatts),
            targetPowerWatts = interval.targetPower(ftpWatts),
            cadenceRpm = 88,
            heartRateBpm = 118,
        )
        return ActiveWorkoutState(
            workout = workout,
            ftpWatts = ftpWatts,
            elapsedSeconds = elapsedSeconds,
            currentIntervalIndex = intervalIndex,
            currentIntervalElapsedSeconds = elapsedSeconds - intervalStart,
            isPaused = isPaused,
            isComplete = elapsedSeconds >= totalSeconds,
            sample = sample,
            samples = samples.takeLast(96),
        )
    }
}
