package io.github.byjoelsamuel.drivesanity

import kotlin.math.abs
import kotlin.math.exp

/**
 * Eases values towards their targets once per drawn frame, so bars and numbers glide instead of
 * stepping each time a new reading arrives. Call [frame] at the start of each draw, then [ease]
 * for every value, with a different [key] for each.
 *
 * The easing depends on the time between frames, not the number of them, so it looks the same at
 * 15 or 30 frames a second.
 */
class Smoother {
    private val values = HashMap<String, Float>()
    private var lastNs = 0L

    /** Seconds since the previous frame. */
    var dt = 0.033f
        private set

    fun frame(nowNs: Long = System.nanoTime()) {
        dt = if (lastNs == 0L) 0.033f else ((nowNs - lastNs) / 1e9f).coerceIn(0.001f, 0.25f)
        lastNs = nowNs
    }

    /**
     * The value under [key] after moving it towards [target]; [tau] is roughly how many seconds it
     * takes to cover most of the way. A null target (no reading) forgets the value, so the next
     * reading appears straight away rather than sliding in from an old one.
     */
    fun ease(key: String, target: Float?, tau: Float): Float? {
        if (target == null) {
            values.remove(key)
            return null
        }
        val cur = values[key]
        var next = if (cur == null) target else cur + (target - cur) * (1f - exp(-dt / tau))
        if (abs(target - next) < 0.002f * maxOf(1f, abs(target))) next = target
        values[key] = next
        return next
    }
}
