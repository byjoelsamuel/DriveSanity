package io.github.byjoelsamuel.drivesanity.track

/**
 * Times a standing start: 0–60 mph, 0–100 km/h and the quarter mile with its trap speed. It waits
 * for the car to be still, starts when it moves off, and stops when you lift off or stop. Speeds
 * are in metres per second and times in milliseconds.
 */
class AccelTimer {
    data class Run(
        val to60Ms: Long?,
        val to100kmhMs: Long?,
        val quarterMs: Long?,
        val quarterTrapKmh: Float?,
        val topKmh: Float,
    ) {
        val hasAnything get() = to60Ms != null || to100kmhMs != null || quarterMs != null
    }

    enum class State { WAITING, READY, RUNNING }

    var state = State.WAITING
        private set

    /** The last finished run. */
    var result: Run? = null
        private set

    private var t0 = 0L
    private var lastT = 0L
    private var lastV = 0f
    private var peak = 0f
    private var dist = 0.0
    private var to60: Long? = null
    private var to100: Long? = null
    private var quarter: Long? = null
    private var trap: Float? = null

    /** Forgets the last finished run. */
    fun clearResult() {
        result = null
    }

    /** Time since the run started, while one is going. */
    fun liveMs(nowMs: Long): Long? = if (state == State.RUNNING) nowMs - t0 else null

    fun liveRun(): Run? = if (state == State.RUNNING) Run(to60, to100, quarter, trap, peak * 3.6f) else null

    fun onSpeed(tMs: Long, mps: Float) {
        when (state) {
            State.WAITING -> if (mps < STILL) state = State.READY
            State.READY -> if (mps >= STILL) start(tMs, mps)
            State.RUNNING -> step(tMs, mps)
        }
        lastT = tMs
        lastV = mps
    }

    private fun start(tMs: Long, mps: Float) {
        state = State.RUNNING
        // The first reading above standstill comes a little after the car actually moved off. Follow
        // the speed back down to zero to find when that was.
        val dtMs = tMs - lastT
        val slope = if (dtMs > 0 && mps > lastV) (mps - lastV) / dtMs else 0f // m/s per ms
        val backMs = if (slope > 0f) (mps / slope).toLong().coerceIn(0L, 1_000L) else 0L
        t0 = tMs - backMs
        peak = mps
        dist = 0.5 * mps * backMs / 1000.0
        to60 = null; to100 = null; quarter = null; trap = null
        lastT = tMs
        lastV = mps
    }

    private fun step(tMs: Long, v: Float) {
        val dt = (tMs - lastT) / 1000.0
        if (dt <= 0) return
        dist += (v + lastV) / 2 * dt
        if (v > peak) peak = v
        fun when1(th: Float): Long? =
            if (lastV < th && v >= th) lastT + ((th - lastV) / (v - lastV) * (tMs - lastT)).toLong() - t0 else null
        if (to60 == null) to60 = when1(MPH60)
        if (to100 == null) to100 = when1(KMH100)
        if (quarter == null && dist >= QUARTER_M) {
            val before = dist - (v + lastV) / 2 * dt
            val f = ((QUARTER_M - before) / (dist - before)).coerceIn(0.0, 1.0)
            quarter = lastT + ((tMs - lastT) * f).toLong() - t0
            trap = (lastV + (v - lastV) * f.toFloat()) * 3.6f
        }
        // Lifting off, braking hard or stopping ends the run.
        if (v < STILL || v < peak - 3f || tMs - t0 > MAX_RUN_MS) finish()
    }

    private fun finish() {
        val run = Run(to60, to100, quarter, trap, peak * 3.6f)
        if (run.hasAnything) result = run
        state = State.WAITING
    }

    companion object {
        const val STILL = 0.6f
        const val MPH60 = 26.8224f
        const val KMH100 = 27.7778f
        const val QUARTER_M = 402.336
        const val MAX_RUN_MS = 60_000L
    }
}
