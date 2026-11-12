package io.github.byjoelsamuel.drivesanity

import kotlin.math.abs

/**
 * When to write a row to a recording. A row every [baseMs] keeps the file small (the spreadsheet is
 * a few kilobytes a minute instead of hundreds), and a row the moment something changes, no more
 * than once every [minMs], keeps the corners, launches and stops in it. The biggest g-forces
 * between rows are written on the next row, so a hard stop between two rows isn't lost.
 */
class LogPolicy(private val baseMs: Long = 5_000, private val minMs: Long = 1_000) {
    private var lastMs = Long.MIN_VALUE
    private var lastKmh = 0f
    private var lastHeading: Float? = null

    /** Rows every second while lap timing, since a lap needs more than a point every five seconds. */
    var fast = false

    fun due(nowMs: Long, speedKmh: Float?, headingDeg: Float?, gTotal: Float): Boolean {
        val since = nowMs - lastMs
        val every = if (fast) minMs else baseMs
        val change = since >= minMs && (
            abs((speedKmh ?: 0f) - lastKmh) >= 10f ||
                (headingDeg != null && lastHeading != null && angle(headingDeg, lastHeading!!) >= 15f) ||
                gTotal >= 0.35f
            )
        val go = lastMs == Long.MIN_VALUE || since >= every || change
        if (go) {
            lastMs = nowMs
            lastKmh = speedKmh ?: 0f
            lastHeading = headingDeg
        }
        return go
    }

    private fun angle(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
