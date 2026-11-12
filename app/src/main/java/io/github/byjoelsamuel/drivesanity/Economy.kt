package io.github.byjoelsamuel.drivesanity

/**
 * Fuel use, estimated from how much air the engine is taking in: a petrol engine burns about
 * one gram of fuel for every 14.7 grams of air. It's an estimate (it assumes the injectors
 * are doing what the computer asks), good for watching the trend rather than for billing.
 * Diesels run far leaner, so air flow says nothing about their fuel; they're left out.
 */
class Economy {
    /** Fuel burned and distance covered since the last [reset]. */
    var litres = 0.0
        private set
    var metres = 0.0
        private set

    /** Current use, smoothed: litres per hour, and litres per 100 km once moving. */
    var litresPerHour: Float? = null
        private set
    var litresPer100Km: Float? = null
        private set

    val tripLitresPer100Km: Float? get() = if (metres >= 500.0) (litres / metres * 100_000.0).toFloat() else null

    private var lastMs = 0L

    fun update(nowMs: Long, mafGramsPerSec: Float?, lambda: Float?, speedMps: Float?) {
        if (mafGramsPerSec == null) {
            lastMs = 0L
            litresPerHour = null
            litresPer100Km = null
            return
        }
        val dt = if (lastMs == 0L) 0f else ((nowMs - lastMs) / 1000f).coerceIn(0f, 2f)
        lastMs = nowMs
        val lam = lambda?.takeIf { it > 0.5f } ?: 1f
        // Far leaner than ideal means the injectors are shut off (coasting): no fuel.
        val gramsPerSec = if (lam > LEAN_CUT) 0f else mafGramsPerSec / (STOICH * lam)
        val lps = gramsPerSec / DENSITY
        val v = speedMps ?: 0f
        litres += lps * dt
        metres += v * dt
        litresPerHour = ema(litresPerHour, lps * 3600f)
        litresPer100Km = if (v > MOVING) ema(litresPer100Km, lps / v * 100_000f) else null
    }

    fun reset() {
        litres = 0.0
        metres = 0.0
    }

    private fun ema(prev: Float?, x: Float) = if (prev == null) x else prev + (x - prev) * 0.2f

    private companion object {
        const val STOICH = 14.7f   // grams of air per gram of petrol
        const val DENSITY = 745f   // grams of petrol per litre
        const val LEAN_CUT = 1.6f  // leaner than this: fuel cut
        const val MOVING = 2.5f    // m/s; slower than this there's no "per 100 km"
    }
}
