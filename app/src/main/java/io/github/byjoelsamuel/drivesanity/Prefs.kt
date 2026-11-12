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

    /** Fuel use as people in this unit system read it: MPG (US) or litres per 100 km. */
    fun economy(lPer100: Float): Float = if (this == MPH) 235.215f / maxOf(lPer100, 0.1f) else lPer100

    /** "32 MPG" or "5.9 L/100"; standing still, "0.9 gal/h" or "3.2 L/h"; or "–". */
    fun economyText(lPer100: Float?, lph: Float?): String = when {
        lPer100 != null -> if (this == MPH) "%.0f MPG".format(economy(lPer100)) else "%.1f L/100".format(lPer100)
        lph != null -> if (this == MPH) "%.2f gal/h".format(lph / 3.78541f) else "%.1f L/h".format(lph)
        else -> "–"
    }
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
        RETRO("Retro"),
        DIAG("OBD2"),
        SPORT("Sport"),
        MUSIC("Music");

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

    /** Gears learned while driving, kept per car so switching cars doesn't mix them up. */
    fun gears(ctx: Context, carId: String): String = prefs(ctx).getString("gears_$carId", "") ?: ""

    fun saveGears(ctx: Context, carId: String, saved: String) = prefs(ctx).edit { putString("gears_$carId", saved) }

    fun carId(ctx: Context): String = prefs(ctx).getString("car", CarProfiles.DEFAULT_ID) ?: CarProfiles.DEFAULT_ID

    fun setCarId(ctx: Context, id: String) = prefs(ctx).edit { putString("car", id) }

    /** The owner's own redline for a car, or null to use the profile's. */
    fun redline(ctx: Context, carId: String): Float? = prefs(ctx).getFloat("redline_$carId", 0f).takeIf { it > 0f }

    fun setRedline(ctx: Context, carId: String, rpm: Float) = prefs(ctx).edit { putFloat("redline_$carId", rpm) }

    fun setLayout(ctx: Context, to: Layout) {
        layout = to
        prefs(ctx).edit { putString("layout", to.name) }
    }

    /** The Sport button: jump to the Sport screen, or back to the screen you came from. */
    fun toggleSport(ctx: Context) {
        val now = layout(ctx)
        if (now == Layout.SPORT) {
            val back = Layout.entries.firstOrNull { it.name == prefs(ctx).getString("sport_back", null) }
            setLayout(ctx, if (back == null || back == Layout.SPORT) Layout.GAUGES else back)
        } else {
            prefs(ctx).edit { putString("sport_back", now.name) }
            setLayout(ctx, Layout.SPORT)
        }
    }

    /** Flashing warnings and the shift light. */
    /** How the share pictures are put together (see ShareConfig); kept so the next one starts the same way. */
    fun shareConfig(ctx: Context): String? = prefs(ctx).getString("share_config", null)

    fun setShareConfig(ctx: Context, encoded: String) = prefs(ctx).edit { putString("share_config", encoded) }

    /** On the Music screen: the player on the left (and the map on the right), or the other way round. */
    fun musicLeft(ctx: Context): Boolean = prefs(ctx).getBoolean("music_left", true)

    fun setMusicLeft(ctx: Context, left: Boolean) = prefs(ctx).edit { putBoolean("music_left", left) }

    /** True for the modern dark map (CARTO's Dark Matter); false for the classic OpenStreetMap look. */
    fun modernMap(ctx: Context): Boolean = prefs(ctx).getBoolean("modern_map", true)

    fun setModernMap(ctx: Context, on: Boolean) = prefs(ctx).edit { putBoolean("modern_map", on) }

    /** Saved start/finish lines, as JSON (see Track). */
    fun tracksJson(ctx: Context): String = prefs(ctx).getString("tracks", "[]") ?: "[]"

    fun setTracksJson(ctx: Context, json: String) = prefs(ctx).edit { putString("tracks", json) }

    /** Manufacturer-specific reads (fuel level, tire pressure) are off until the person turns them on. They are still only reads. */
    fun advancedReads(ctx: Context): Boolean = prefs(ctx).getBoolean("advanced_reads", false)

    fun setAdvancedReads(ctx: Context, on: Boolean) = prefs(ctx).edit { putBoolean("advanced_reads", on) }

    /** Stop asking the car anything once it's switched off, so it can go to sleep. On unless the person turns it off. */
    fun letCarSleep(ctx: Context): Boolean = prefs(ctx).getBoolean("let_car_sleep", true)

    fun setLetCarSleep(ctx: Context, on: Boolean) = prefs(ctx).edit { putBoolean("let_car_sleep", on) }

    fun alerts(ctx: Context): Boolean = prefs(ctx).getBoolean("alerts", true)

    fun setAlerts(ctx: Context, on: Boolean) = prefs(ctx).edit { putBoolean("alerts", on) }

    fun startupSound(ctx: Context): StartupSoundMode {
        val saved = prefs(ctx).getString("startup_sound", null)
        return StartupSoundMode.entries.firstOrNull { it.name == saved } ?: StartupSoundMode.VOICE
    }

    fun setStartupSound(ctx: Context, mode: StartupSoundMode) = prefs(ctx).edit { putString("startup_sound", mode.name) }

    /** The sound file the owner picked, as a content:// address. */
    fun customSound(ctx: Context): String? = prefs(ctx).getString("custom_sound", null)

    fun setCustomSound(ctx: Context, uri: String?) = prefs(ctx).edit { putString("custom_sound", uri) }

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
