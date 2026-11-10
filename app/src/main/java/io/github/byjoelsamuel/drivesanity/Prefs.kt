package io.github.byjoelsamuel.drivesanity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import java.util.Locale

enum class Units(val perMps: Float, val label: String) {
    KMH(3.6f, "km/h"),
    MPH(2.2369363f, "mph");

    fun speed(mps: Float) = mps * perMps
    fun distance(m: Double) = if (this == KMH) m / 1000 else m / 1609.344
    val distanceLabel get() = if (this == KMH) "km" else "mi"
}

/** Small things kept between launches: the units, and the calibration (the phone usually stays in its mount). */
object Prefs {
    private const val FILE = "drive"
    @Volatile private var units: Units? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun units(ctx: Context): Units = units ?: run {
        val default = if (Locale.getDefault().country in setOf("US", "GB", "LR", "MM")) Units.MPH else Units.KMH
        val saved = prefs(ctx).getString("units", null)
        (Units.entries.firstOrNull { it.name == saved } ?: default).also { units = it }
    }

    fun toggleUnits(ctx: Context) {
        val next = if (units(ctx) == Units.KMH) Units.MPH else Units.KMH
        units = next
        prefs(ctx).edit { putString("units", next.name) }
    }

    fun saveRest(ctx: Context, rest: Vec3) {
        prefs(ctx).edit { putFloat("rest_x", rest.x); putFloat("rest_y", rest.y); putFloat("rest_z", rest.z) }
    }

    fun loadRest(ctx: Context): Vec3? {
        val p = prefs(ctx)
        if (!p.contains("rest_x")) return null
        return Vec3(p.getFloat("rest_x", 0f), p.getFloat("rest_y", 0f), p.getFloat("rest_z", 0f))
    }
}

fun hasLocationPermission(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
