package io.github.byjoelsamuel.drivesanity

import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.nav.Limit
import kotlin.math.roundToInt

data class Alert(val text: String, val severe: Boolean)

/** What deserves a flashing warning, and how close the revs are to the redline. */
object Alerts {
    const val HOT_C = 108f

    /** The clock the flashing runs on; a test can freeze it. */
    var now: () -> Long = { SystemClock.uptimeMillis() }

    /** The warnings that apply right now, most important first. */
    fun active(s: Snapshot, units: Units, limit: Limit?): List<Alert> {
        val out = mutableListOf<Alert>()
        s.coolantC?.takeIf { it > HOT_C }?.let { out += Alert("ENGINE HOT  ${temp(it, units)}", true) }
        val running = (s.rpm ?: 0f) > 800f
        s.volts?.let { if (it < 11.8f || (running && it < 12.5f)) out += Alert("LOW BATTERY  %.1f V".format(it), true) }
        s.tires.forEachIndexed { i, psi ->
            if (Tpms.level(psi) == 2) out += Alert("LOW TIRE ${Tpms.NAMES[i]}  ${Tpms.text(psi, units)} ${Tpms.unit(units)}", true)
        }
        s.fuelPct?.takeIf { it < 12.5f }?.let { out += Alert("LOW FUEL  ${it.roundToInt()}%", false) }
        val mps = s.speedMps
        if (mps != null && limit != null) {
            val posted = if (units == Units.MPH) limit.inMph() else limit.inKmh()
            if (units.speed(mps) > posted + (if (units == Units.MPH) 5 else 8)) out += Alert("OVER LIMIT  $posted", false)
        }
        return out
    }

    /** 0 fine, 1 getting close to the redline, 2 shift now. */
    fun shiftLevel(rpm: Float?, redline: Float): Int = when {
        rpm == null -> 0
        rpm >= redline * 0.985f -> 2
        rpm >= redline * 0.92f -> 1
        else -> 0
    }

    private fun temp(cel: Float, units: Units) =
        if (units == Units.MPH) "${(cel * 9 / 5 + 32).roundToInt()}°F" else "${cel.roundToInt()}°C"
}
