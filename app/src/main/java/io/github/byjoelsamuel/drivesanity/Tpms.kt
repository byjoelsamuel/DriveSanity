package io.github.byjoelsamuel.drivesanity

import kotlin.math.roundToInt

/** Tire pressure display: PSI with MPH, kPa with km/h. Built for a car that wants about 36 PSI. */
object Tpms {
    val NAMES = listOf("FL", "FR", "RL", "RR")

    fun unit(units: Units) = if (units == Units.MPH) "PSI" else "kPa"

    fun value(psi: Float, units: Units): Int = if (units == Units.MPH) psi.roundToInt() else (psi * 6.89476f).roundToInt()

    fun text(psi: Float?, units: Units): String = psi?.let { value(it, units).toString() } ?: "–"

    /** Just the digits, or nothing when unknown (for the seven-segment screen). */
    fun digits(psi: Float?, units: Units): String = psi?.let { value(it, units).toString() } ?: ""

    /** 0 fine, 1 a bit low or high, 2 low enough to need air now. */
    fun level(psi: Float?): Int = when {
        psi == null -> 0
        psi < 27f -> 2
        psi < 32f || psi > 44f -> 1
        else -> 0
    }
}
