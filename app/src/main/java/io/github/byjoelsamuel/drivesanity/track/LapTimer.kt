package io.github.byjoelsamuel.drivesanity.track

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A start/finish line: a point on the road and the direction you cross it in. The line itself runs
 * across the road, [halfWidthM] either side of the point.
 */
data class FinishLine(
    val name: String,
    val lat: Double,
    val lon: Double,
    val headingDeg: Double,
    val halfWidthM: Double = 25.0,
)

data class Lap(val number: Int, val ms: Long, val topKmh: Float)

/**
 * Times laps from GPS fixes. A lap starts and ends when the path between two fixes crosses the
 * line in the right direction, and the exact moment is worked out between the two fixes, so the
 * lap time isn't limited to the one-second spacing of the fixes.
 *
 * It also keeps how far round the lap you are against the time, so the current lap can be
 * compared with the best one as you go (the delta).
 *
 * Times are in milliseconds on any one steady clock; the app uses the elapsed-realtime clock.
 */
class LapTimer(val line: FinishLine, private val minLapMs: Long = 15_000) {
    private val mPerDegLat = 111_320.0
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(line.lat))
    private val fx = sin(Math.toRadians(line.headingDeg))  // forward, east part
    private val fy = cos(Math.toRadians(line.headingDeg))  // forward, north part

    // The line's two ends, with the middle of the line at (0, 0). It runs across the forward direction.
    private val ax = -fy * line.halfWidthM
    private val ay = fx * line.halfWidthM
    private val bx = fy * line.halfWidthM
    private val by = -fx * line.halfWidthM

    private var lastX = 0.0
    private var lastY = 0.0
    private var lastT = 0L
    private var haveLast = false

    private var lapStartMs: Long? = null
    private var lapTopKmh = 0f
    private var dist = 0.0
    private var cur = ArrayList<DoubleArray>() // (distance m, time ms) along the current lap
    private var bestProfile: List<DoubleArray> = emptyList()

    private val done = ArrayList<Lap>()
    val laps: List<Lap> get() = done
    val last: Lap? get() = done.lastOrNull()
    val best: Lap? get() = done.minByOrNull { it.ms }

    /** The lap being run: 0 before the first crossing, then 1, 2, … */
    val lapNumber: Int get() = if (lapStartMs == null) 0 else done.size + 1

    /** How far ahead (negative) or behind (positive) the best lap you are, as of the last fix. */
    var deltaMs: Long? = null
        private set

    /** Milliseconds into the current lap, or null before the first crossing. */
    fun currentMs(nowMs: Long): Long? = lapStartMs?.let { (nowMs - it).coerceAtLeast(0) }

    /** Feeds one fix. Returns true if it crossed the line and that counted. */
    fun onFix(tMs: Long, lat: Double, lon: Double, speedMps: Float): Boolean {
        val x = (lon - line.lon) * mPerDegLon
        val y = (lat - line.lat) * mPerDegLat
        var crossed = false
        if (haveLast && tMs > lastT) {
            val dx = x - lastX
            val dy = y - lastY
            val seg = hypot(dx, dy)
            val s = crossing(dx, dy)
            if (s != null && dx * fx + dy * fy > 0) {
                val tCross = lastT + ((tMs - lastT) * s).toLong()
                crossed = cross(tCross, tMs, dist + seg * s, seg * (1 - s))
            } else {
                advance(tMs, seg)
            }
        }
        lastX = x; lastY = y; lastT = tMs; haveLast = true
        lapStartMs?.let { if (speedMps * 3.6f > lapTopKmh) lapTopKmh = speedMps * 3.6f }
        updateDelta()
        return crossed
    }

    /** Where along the step from the last fix (0 to 1) it crosses the line, or null if it doesn't. */
    private fun crossing(dx: Double, dy: Double): Double? {
        val ex = bx - ax
        val ey = by - ay
        val denom = dx * ey - dy * ex
        if (abs(denom) < 1e-9) return null
        val px = ax - lastX
        val py = ay - lastY
        val s = (px * ey - py * ex) / denom
        val u = (px * dy - py * dx) / denom
        return if (s in 0.0..1.0 && u in 0.0..1.0) s else null
    }

    private fun advance(tMs: Long, seg: Double) {
        val start = lapStartMs ?: return
        dist += seg
        cur.add(doubleArrayOf(dist, (tMs - start).toDouble()))
    }

    private fun cross(tCross: Long, tFix: Long, distAtLine: Double, distAfter: Double): Boolean {
        val start = lapStartMs
        if (start != null && tCross - start < minLapMs) return false // bounced over the line
        if (start != null) {
            val ms = tCross - start
            cur.add(doubleArrayOf(distAtLine, ms.toDouble()))
            val lap = Lap(done.size + 1, ms, lapTopKmh)
            if (done.isEmpty() || ms < done.minOf { it.ms }) bestProfile = ArrayList(cur)
            done.add(lap)
        }
        lapStartMs = tCross
        lapTopKmh = 0f
        dist = distAfter
        cur = arrayListOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(distAfter, (tFix - tCross).toDouble()))
        return true
    }

    private fun updateDelta() {
        val here = cur.lastOrNull()
        if (here == null || bestProfile.size < 2 || done.isEmpty()) {
            deltaMs = null
            return
        }
        // Where the best lap was, in time, when it had gone this far.
        val d = here[0]
        var tBest = bestProfile.last()[1]
        for (i in 1 until bestProfile.size) {
            val a = bestProfile[i - 1]
            val b = bestProfile[i]
            if (d <= b[0]) {
                val span = b[0] - a[0]
                tBest = if (span <= 0) b[1] else a[1] + (b[1] - a[1]) * (d - a[0]) / span
                break
            }
        }
        deltaMs = (here[1] - tBest).toLong()
    }
}
