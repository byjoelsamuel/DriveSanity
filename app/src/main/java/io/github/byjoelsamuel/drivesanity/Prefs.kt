package io.github.byjoelsamuel.drivesanity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

enum class Units(val perMps: Float, val label: String) {
    KMH(3.6f, "km/h"),
    MPH(2.2369363f, "MPH");

    /** The one shown small under the big number. */
    val other get() = if (this == MPH) KMH else MPH

    fun speed(mps: Float) = mps * perMps
    fun distance(m: Double) = if (this == KMH) m / 1000 else m / 1609.344
    val distanceLabel get() = if (this == KMH) "km" else "mi"
}

/** Small things kept between launches: the units, and the calibration (the phone usually stays in its mount). */
object Prefs {
    private const val FILE = "drive"
    @Volatile private var units: Units? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The big speed number's units: MPH unless switched (tap the phone dashboard). km/h shows small underneath. */
    fun units(ctx: Context): Units = units ?: run {
        val saved = prefs(ctx).getString("primary_units", null)
        (Units.entries.firstOrNull { it.name == saved } ?: Units.MPH).also { units = it }
    }

    fun toggleUnits(ctx: Context) {
        val next = units(ctx).other
        units = next
        prefs(ctx).edit { putString("primary_units", next.name) }
    }

    /** Which dashboard the car and phone show. The switch button cycles through them. */
    enum class Layout(val title: String) {
        GAUGES("Gauges"),
        MAP("Map"),
        RETRO("Retro");

        val next get() = entries[(ordinal + 1) % entries.size]
    }

    @Volatile private var layout: Layout? = null

    fun layout(ctx: Context): Layout = layout ?: run {
        val saved = prefs(ctx).getString("layout", null)
        (Layout.entries.firstOrNull { it.name == saved } ?: Layout.GAUGES).also { layout = it }
    }

    fun toggleLayout(ctx: Context) {
        val next = layout(ctx).next
        layout = next
        prefs(ctx).edit { putString("layout", next.name) }
    }

    fun gears(ctx: Context): String = prefs(ctx).getString("gears", "") ?: ""

    fun saveGears(ctx: Context, saved: String) = prefs(ctx).edit { putString("gears", saved) }

    fun startupAnim(ctx: Context): Boolean = prefs(ctx).getBoolean("startup_anim", true)

    fun setStartupAnim(ctx: Context, on: Boolean) = prefs(ctx).edit { putBoolean("startup_anim", on) }

    data class ObdDevice(val address: String, val name: String, val ble: Boolean)

    fun obdDevice(ctx: Context): ObdDevice? {
        val p = prefs(ctx)
        val address = p.getString("obd_address", null) ?: return null
        return ObdDevice(address, p.getString("obd_name", null) ?: address, p.getBoolean("obd_ble", false))
    }

    fun saveObdDevice(ctx: Context, d: ObdDevice?) {
        prefs(ctx).edit {
            if (d == null) {
                remove("obd_address"); remove("obd_name"); remove("obd_ble")
            } else {
                putString("obd_address", d.address); putString("obd_name", d.name); putBoolean("obd_ble", d.ble)
            }
        }
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
