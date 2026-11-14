package io.github.byjoelsamuel.drivesanity.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.byjoelsamuel.drivesanity.CarProfiles
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.MapLook
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.StartupSoundMode
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.map.Tiles
import kotlin.math.roundToInt

/**
 * The settings people change on the Settings tab, held as a draft until they're applied: what you
 * tap shows at once on the page, a bar at the bottom offers Apply or Discard, and nothing reaches
 * the car or the saved settings until Apply is confirmed. Because the draft is Compose state, every
 * page redraws the moment it changes.
 */
class SettingsDraft(context: Context) {
    private val ctx = context.applicationContext

    /** One value for each setting that's staged. [redline] is only set once it's been moved. */
    data class Values(
        val carId: String,
        val redline: Float?,
        val units: Units,
        val layout: Prefs.Layout,
        val mapLook: MapLook,
        val alerts: Boolean,
        val startupAnim: Boolean,
        val sound: StartupSoundMode,
        val letCarSleep: Boolean,
        val advancedReads: Boolean,
    )

    /** What each part of the draft covers, for "Reset to default". */
    enum class Group { CAR, UNITS, SCREEN, MAP, ALERTS, STARTUP, BATTERY, ALL }

    class Change(val label: String, val from: String, val to: String)

    private fun read() = Values(
        carId = CarProfiles.byId(Prefs.carId(ctx)).id,
        redline = null,
        units = Prefs.units(ctx),
        layout = Prefs.layout(ctx),
        mapLook = Prefs.mapLook(ctx),
        alerts = Prefs.alerts(ctx),
        startupAnim = Prefs.startupAnim(ctx),
        sound = Prefs.startupSound(ctx),
        letCarSleep = Prefs.letCarSleep(ctx),
        advancedReads = Prefs.advancedReads(ctx),
    )

    /** What the app is using right now. */
    var applied by mutableStateOf(read())
        private set

    /** What the pages show: [applied] plus whatever has been tapped since. */
    var values by mutableStateOf(applied)
        private set

    /** A short line to show after applying or resetting, then clear. */
    var notice by mutableStateOf<String?>(null)

    val dirty get() = values != applied

    /** The redline a car shows: the owner's if they set one, else the profile's. */
    fun redlineOf(v: Values): Float = v.redline ?: savedRedline(v.carId)

    private fun savedRedline(carId: String): Float = Prefs.redline(ctx, carId) ?: CarProfiles.byId(carId).redline

    /** A redline that matches what's already saved for that car counts as no change. */
    private fun normalized(v: Values): Values =
        if (v.redline != null && v.carId == applied.carId && v.redline == savedRedline(v.carId)) v.copy(redline = null) else v

    fun edit(change: (Values) -> Values) {
        values = normalized(change(values))
    }

    fun setCar(id: String) = edit { it.copy(carId = id, redline = null) }

    fun nudgeRedline(by: Float) = edit { it.copy(redline = (redlineOf(it) + by).coerceIn(3_000f, 9_000f)) }

    /** Picks up changes made elsewhere (the car's own buttons change the screen, say) while nothing is staged. */
    fun refresh() {
        val now = read()
        if (!dirty) values = now
        applied = now
    }

    fun discard() {
        values = applied
    }

    private fun defaults() = Values(
        carId = CarProfiles.DEFAULT_ID,
        redline = CarProfiles.byId(CarProfiles.DEFAULT_ID).redline,
        units = Units.MPH,
        layout = Prefs.Layout.GAUGES,
        mapLook = MapLook.SIMPLIFIED,
        alerts = true,
        startupAnim = true,
        sound = StartupSoundMode.READER,
        letCarSleep = true,
        advancedReads = false,
    )

    private fun withDefaults(v: Values, g: Group): Values {
        val d = defaults()
        return when (g) {
            Group.CAR -> v.copy(carId = d.carId, redline = d.redline)
            Group.UNITS -> v.copy(units = d.units)
            Group.SCREEN -> v.copy(layout = d.layout)
            Group.MAP -> v.copy(mapLook = d.mapLook)
            Group.ALERTS -> v.copy(alerts = d.alerts)
            Group.STARTUP -> v.copy(startupAnim = d.startupAnim, sound = d.sound)
            Group.BATTERY -> v.copy(letCarSleep = d.letCarSleep, advancedReads = d.advancedReads)
            Group.ALL -> d
        }
    }

    fun isDefault(g: Group) = normalized(withDefaults(values, g)) == values

    /** Stages the defaults for one part of the settings. It still needs Apply. */
    fun resetStaged(g: Group) = edit { withDefaults(it, g) }

    /** What Apply will do, one line each. */
    fun changes(): List<Change> = buildList {
        val a = applied
        val v = values
        if (v.carId != a.carId) add(Change("Car", CarProfiles.byId(a.carId).name, CarProfiles.byId(v.carId).name))
        if (v.redline != null) add(Change("Redline", rpm(redlineOf(a)), rpm(redlineOf(v))))
        if (v.units != a.units) add(Change("Units", unitsName(a.units), unitsName(v.units)))
        if (v.layout != a.layout) add(Change("Car screen", a.layout.title, v.layout.title))
        if (v.mapLook != a.mapLook) add(Change("Map look", a.mapLook.title, v.mapLook.title))
        if (v.alerts != a.alerts) add(Change("Alerts and shift light", onOff(a.alerts), onOff(v.alerts)))
        if (v.startupAnim != a.startupAnim) add(Change("Startup animation", onOff(a.startupAnim), onOff(v.startupAnim)))
        if (v.sound != a.sound) add(Change("Startup sound", a.sound.title, v.sound.title))
        if (v.letCarSleep != a.letCarSleep) add(Change("Let the car sleep", onOff(a.letCarSleep), onOff(v.letCarSleep)))
        if (v.advancedReads != a.advancedReads) add(Change("Manufacturer-specific reads", onOff(a.advancedReads), onOff(v.advancedReads)))
    }

    /** Makes the draft real. Returns how many things changed. */
    fun apply(actions: PhoneActions): Int {
        val a = applied
        val v = values
        val count = changes().size
        if (v.carId != a.carId) actions.setCar(v.carId)
        // A new car starts on its own saved redline, so the redline only moves if the draft moved it.
        v.redline?.let { r ->
            if (r == CarProfiles.byId(v.carId).redline) DriveEngine.resetRedline(ctx) else actions.setRedline(r)
        }
        if (v.units != a.units) actions.swapUnits()
        if (v.layout != a.layout) actions.setCarLayout(v.layout)
        if (v.mapLook != a.mapLook) {
            Prefs.setMapLook(ctx, v.mapLook)
            Tiles.setLook(v.mapLook)
        }
        if (v.alerts != a.alerts) actions.setAlerts(v.alerts)
        if (v.startupAnim != a.startupAnim) actions.setStartupAnim(v.startupAnim)
        if (v.sound != a.sound) actions.setStartupSound(v.sound)
        if (v.letCarSleep != a.letCarSleep) Prefs.setLetCarSleep(ctx, v.letCarSleep)
        if (v.advancedReads != a.advancedReads) Prefs.setAdvancedReads(ctx, v.advancedReads)
        val now = read()
        applied = now
        values = now
        notice = if (count == 1) "1 change applied" else "$count changes applied"
        return count
    }

    /** Puts every setting back to how the app first came, straight away. The adapter, calibration and recordings stay. */
    fun resetAll(actions: PhoneActions) {
        values = normalized(defaults())
        apply(actions)
        notice = "Settings reset to default"
    }

    private fun rpm(r: Float) = "%,d rpm".format(r.roundToInt())
    private fun onOff(b: Boolean) = if (b) "On" else "Off"
    private fun unitsName(u: Units) = if (u == Units.MPH) "MPH · °F · mi" else "km/h · °C · km"
}
