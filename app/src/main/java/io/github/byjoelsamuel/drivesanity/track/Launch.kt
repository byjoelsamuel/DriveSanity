package io.github.byjoelsamuel.drivesanity.track

import android.os.SystemClock

enum class Phase {
    /** Not stopped yet, so a run can't be timed. */
    MOVING,

    /** Stopped, ready to go. */
    READY,

    /** The 3, 2, 1. */
    COUNTDOWN,

    /** The countdown's over: go. */
    GO,

    /** Moving off; the clock is running. */
    RUNNING,

    /** A run just finished. */
    DONE,

    /** The car moved before the countdown finished. */
    TOO_EARLY,
}

/** What the screens show of the 0–60 timer. */
data class LaunchView(
    val phase: Phase,
    /** 3, 2 or 1 during the countdown. */
    val count: Int?,
    /** Time on the clock while a run is going. */
    val liveMs: Long?,
    /** The last finished run: time to 60 mph and to 100 km/h (null if that run didn't get there). */
    val to60Ms: Long?,
    val to100Ms: Long?,
    /** How far through the current countdown number it is, 0 to 1. */
    val frac: Float = 0f,
) {
    val hasResult get() = to60Ms != null || to100Ms != null
}

/**
 * The 0–60 mph / 0–100 km/h timer. It waits for the car to be still, then times from the moment it
 * moves off, on its own. The countdown is optional: when it's on, [startCountdown] gives a 3, 2, 1 and
 * a GO, and moving before it's over is flagged as too early. The clock itself is the car's movement,
 * never the countdown.
 */
object Launch {
    private var accel = AccelTimer()
    private val lock = Any()
    private var countdownStartMs: Long? = null
    private var tooEarlyAtMs: Long? = null
    private var finishedAtMs: Long? = null
    private var lastSpeed = 0f

    const val STEP_MS = 1_000L
    const val GO_MS = 3 * STEP_MS
    private const val GO_SHOWN_MS = 900L
    private const val TOO_EARLY_SHOWN_MS = 2_500L
    private const val DONE_SHOWN_MS = 20_000L

    /** A speed reading, from the car when it's connected or GPS when it isn't. */
    fun onSpeed(tMs: Long, mps: Float) {
        synchronized(lock) {
            val before = accel.state
            accel.onSpeed(tMs, mps)
            lastSpeed = mps
            if (before == AccelTimer.State.RUNNING && accel.state != AccelTimer.State.RUNNING) finishedAtMs = SystemClock.elapsedRealtime()
            val start = countdownStartMs
            if (start != null) {
                val elapsed = SystemClock.elapsedRealtime() - start
                // Moving off before the GO is a jump start: the countdown is cancelled.
                if (elapsed < GO_MS && mps > JUMP_MPS) {
                    countdownStartMs = null
                    tooEarlyAtMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** Starts the 3, 2, 1. Only does anything when the car is stopped. */
    fun startCountdown(nowMs: Long = SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            if (accel.state == AccelTimer.State.READY && lastSpeed < JUMP_MPS) {
                countdownStartMs = nowMs
                tooEarlyAtMs = null
            }
        }
    }

    /** Clears the last run and any countdown. */
    fun reset() {
        synchronized(lock) {
            countdownStartMs = null
            tooEarlyAtMs = null
            finishedAtMs = null
            accel = AccelTimer() // it waits for the car to stop again before it will time anything
            lastSpeed = 0f
        }
    }

    val countingDown: Boolean
        get() = synchronized(lock) { countdownStartMs != null }

    fun view(nowMs: Long = SystemClock.elapsedRealtime()): LaunchView = synchronized(lock) {
        val result = accel.result
        val to60 = result?.to60Ms
        val to100 = result?.to100kmhMs
        val start = countdownStartMs
        if (start != null) {
            val elapsed = nowMs - start
            when {
                elapsed < GO_MS -> return LaunchView(Phase.COUNTDOWN, 3 - (elapsed / STEP_MS).toInt(), null, to60, to100, (elapsed % STEP_MS) / STEP_MS.toFloat())
                elapsed < GO_MS + GO_SHOWN_MS -> return LaunchView(Phase.GO, null, null, to60, to100)
                else -> countdownStartMs = null
            }
        }
        val early = tooEarlyAtMs
        if (early != null) {
            if (nowMs - early < TOO_EARLY_SHOWN_MS) return LaunchView(Phase.TOO_EARLY, null, null, to60, to100)
            tooEarlyAtMs = null
        }
        return when (accel.state) {
            AccelTimer.State.RUNNING -> LaunchView(Phase.RUNNING, null, accel.liveMs(nowMs), to60, to100)
            AccelTimer.State.READY -> LaunchView(Phase.READY, null, null, to60, to100)
            AccelTimer.State.WAITING -> {
                val done = finishedAtMs
                if (result != null && done != null && nowMs - done < DONE_SHOWN_MS) LaunchView(Phase.DONE, null, null, to60, to100)
                else LaunchView(Phase.MOVING, null, null, to60, to100)
            }
        }
    }

    private const val JUMP_MPS = 1.5f
}
