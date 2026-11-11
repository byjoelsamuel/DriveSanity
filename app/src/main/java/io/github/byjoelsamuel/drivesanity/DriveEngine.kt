package io.github.byjoelsamuel.drivesanity

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.map.LatLon
import io.github.byjoelsamuel.drivesanity.map.Tiles
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.SpeedLimit
import io.github.byjoelsamuel.drivesanity.obd.Obd
import io.github.byjoelsamuel.drivesanity.obd.Pid
import java.io.File

/** What the screens draw, taken in one go. */
data class Snapshot(
    val speedMps: Float?,
    val accuracyM: Float?,
    val g: CarG?,
    val trail: List<CarG>,
    val calibrated: Boolean,
    val calibrating: Boolean,
    val recording: Boolean,
    val gpsOn: Boolean,
    // From OBD2; null until an adapter is connected and the car answers.
    val rpm: Float? = null,
    val coolantC: Float? = null,
    val intakeC: Float? = null,
    val volts: Float? = null,
    val fuelPct: Float? = null,
    val obdLive: Boolean = false,
    /** Whether the car reports fuel level at all (known once connected). */
    val fuelSupported: Boolean = true,
    val tripM: Double = 0.0,
    // For the map screen.
    val position: LatLon? = null,
    /** Direction of travel, degrees from north; null when stopped or unknown. */
    val headingDeg: Float? = null,
    /** Learned from speed and RPM; null until it knows. */
    val gear: Int? = null,
)

/**
 * Reads GPS and the accelerometer and keeps the numbers both screens show, the phone and
 * Android Auto. It runs while anything holds it (the phone screen, the car screen, or a
 * recording) and stops when the last one lets go. Everything runs on the main thread.
 */
@SuppressLint("StaticFieldLeak") // only ever holds the application context
object DriveEngine : SensorEventListener, LocationListener {
    const val PHONE = "phone"
    const val CAR = "car"
    private const val REC = "rec"

    private const val CALIBRATION_NS = 1_500_000_000L
    private const val TRAIL_EVERY_NS = 50_000_000L   // 20 Hz
    private const val TRAIL_POINTS = 40              // 2 s
    private const val LOG_EVERY_NS = 100_000_000L    // 10 Hz
    private const val STALE_FIX_MS = 3_000L

    private lateinit var app: Context
    private val holders = mutableSetOf<String>()
    private var running = false
    private var gpsOn = false
    private var serviceOn = false
    private var loaded = false

    private val smooth = LowPass3(tauSec = 0.20f)
    private var lastSensorNs = 0L
    private var calibration: Calibration? = null
    private var calSamples: MutableList<Vec3>? = null
    private var calStartNs = 0L

    /** One line for the phone screen about what's going on. */
    var status = "Not calibrated"
        private set

    private var carG: CarG? = null
    private val trail = ArrayDeque<CarG>()
    private var lastTrailNs = 0L

    private var lastFix: Location? = null
    private var lastFixAtMs = 0L
    var gpsAccelG: Float? = null
        private set

    private val gears = GearLearner()
    private var gear: Int? = null
    private var lastGearNs = 0L
    private var heading: Float? = null

    val stats = TripStats()
    private var log: TripLog? = null
    private var lastLogNs = 0L
    val recording get() = log != null
    val recordingFile: File? get() = log?.file

    private val freshFix: Location?
        get() = lastFix?.takeIf { SystemClock.elapsedRealtime() - lastFixAtMs < STALE_FIX_MS }

    /** GPS speed, or the car's own (OBD2) when GPS has dropped out, e.g. in a tunnel. */
    val speedMps: Float?
        get() = freshFix?.takeIf { it.hasSpeed() }?.speed ?: Obd.speedKmh?.let { it / 3.6f }

    fun snapshot() = Snapshot(
        speedMps = speedMps,
        accuracyM = freshFix?.accuracy,
        g = carG,
        trail = trail.toList(),
        calibrated = calibration != null,
        calibrating = calSamples != null,
        recording = recording,
        gpsOn = gpsOn,
        rpm = Obd.rpm,
        coolantC = Obd.coolantC,
        intakeC = Obd.intakeC,
        volts = Obd.volts,
        fuelPct = Obd.fuelPct,
        obdLive = Obd.status.link == Obd.Link.LIVE,
        fuelSupported = Obd.status.link != Obd.Link.LIVE || Pid.FUEL.code in Obd.supported,
        tripM = stats.distanceM,
        position = lastFix?.takeIf { SystemClock.elapsedRealtime() - lastFixAtMs < 30_000 }?.let { LatLon(it.latitude, it.longitude) },
        headingDeg = heading,
        gear = gear,
    )

    // --- who's using it ---------------------------------------------------------------------

    fun hold(context: Context, who: String) {
        app = context.applicationContext
        if (!loaded) {
            loaded = true
            calibration = Prefs.loadRest(app)?.let { Calibration.fromRest(it) }
            gears.load(Prefs.gears(app))
            Tiles.init(app)
            status = if (calibration != null) "Calibrated" else "Not calibrated"
        }
        holders += who
        if (!running) start()
        // The foreground service keeps GPS going when the phone app is in the background.
        // It may only be started while the phone app is on screen.
        if (who == PHONE) ensureService()
    }

    fun release(who: String) {
        holders -= who
        // A recording started in the car, with the phone app never opened, has no foreground
        // service to keep it alive once Android Auto disconnects, so save it now.
        if (who == CAR && recording && !serviceOn && PHONE !in holders) toggleRecording(app)
        if (holders.isEmpty() && running) stop()
    }

    /** Call after the user answers the location permission prompt. */
    fun onPermissionsChanged() {
        if (!running) return
        startGps()
        if (PHONE in holders) ensureService()
    }

    private fun start() {
        running = true
        val sm = app.getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run { status = "This phone has no accelerometer" }
        startGps()
        Obd.start(app)
    }

    private fun startGps() {
        if (gpsOn || !hasLocationPermission(app)) return
        try {
            app.getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this, Looper.getMainLooper())
            gpsOn = true
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) { // no GPS on this device
        }
    }

    private fun stop() {
        running = false
        Obd.stop()
        Prefs.saveGears(app, gears.save())
        app.getSystemService(SensorManager::class.java).unregisterListener(this)
        if (gpsOn) {
            app.getSystemService(LocationManager::class.java).removeUpdates(this)
            gpsOn = false
        }
        if (serviceOn) {
            DriveService.stop(app)
            serviceOn = false
        }
        smooth.reset()
        lastSensorNs = 0L
        calSamples = null
        carG = null
        trail.clear()
        lastFix = null
        gpsAccelG = null
    }

    private fun ensureService() {
        if (!serviceOn && hasLocationPermission(app)) {
            serviceOn = DriveService.start(app)
        }
    }

    // --- controls ---------------------------------------------------------------------------

    /** Starts a 1.5 s calibration. The car has to be stopped and the phone in its mount. */
    fun calibrate() {
        if (!running) return
        val speed = speedMps
        if (speed != null && speed > 1.5f) {
            status = "Stop the car first, then calibrate"
            return
        }
        calSamples = mutableListOf()
        carG = null
        trail.clear()
        status = "Hold still… calibrating"
    }

    fun toggleRecording(context: Context) {
        val current = log
        if (current != null) {
            current.close()
            log = null
            status = "Saved ${current.file.name}"
            release(REC)
        } else {
            hold(context, REC)
            log = TripLog.open(app.filesDir)
            lastLogNs = 0L
            status = "Recording"
        }
    }

    // --- sensors ----------------------------------------------------------------------------

    override fun onSensorChanged(e: SensorEvent) {
        val raw = Vec3(e.values[0], e.values[1], e.values[2])
        val dt = if (lastSensorNs == 0L) 0f else (e.timestamp - lastSensorNs) / 1e9f
        lastSensorNs = e.timestamp
        val v = smooth.feed(raw, dt)

        val samples = calSamples
        if (samples != null) {
            if (samples.isEmpty()) calStartNs = e.timestamp
            samples += raw
            if (e.timestamp - calStartNs >= CALIBRATION_NS) finishCalibration(samples)
            return
        }

        // Gear, from the car's own speed and RPM, five times a second.
        if (e.timestamp - lastGearNs >= 200_000_000L) {
            lastGearNs = e.timestamp
            val rpm = Obd.rpm
            val kmh = Obd.speedKmh
            gear = if (rpm != null && kmh != null) gears.feed(kmh, rpm) else null
        }

        val cal = calibration ?: return
        val g = cal.toCar(v)
        carG = g
        stats.feedG(g)
        if (e.timestamp - lastTrailNs >= TRAIL_EVERY_NS) {
            trail.addLast(g)
            if (trail.size > TRAIL_POINTS) trail.removeFirst()
            lastTrailNs = e.timestamp
        }
        log?.let {
            if (e.timestamp - lastLogNs >= LOG_EVERY_NS) {
                it.write(g, freshFix, speedMps, gpsAccelG)
                lastLogNs = e.timestamp
            }
        }
    }

    private fun finishCalibration(samples: List<Vec3>) {
        calSamples = null
        val rest = Vec3.mean(samples)
        val cal = if (Calibration.isSteady(samples)) Calibration.fromRest(rest) else null
        if (cal == null) {
            status = "Calibration failed: keep the phone still and try again"
            return
        }
        calibration = cal
        smooth.reset()
        Prefs.saveRest(app, rest)
        status = "Calibrated"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- GPS --------------------------------------------------------------------------------

    override fun onLocationChanged(loc: Location) {
        val prev = lastFix
        if (prev != null && loc.hasSpeed() && prev.hasSpeed()) {
            val dt = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9f
            if (dt in 0.2f..3f) {
                gpsAccelG = (loc.speed - prev.speed) / dt / G
                // Below walking pace GPS drift adds fake distance, so it doesn't count.
                val moving = loc.speed >= 1f
                val dist = if (moving && loc.accuracy <= 25f) prev.distanceTo(loc) else 0f
                stats.feedGps(loc.speed, dist, if (moving) dt else 0f)
            }
        } else if (loc.hasSpeed()) {
            stats.feedGps(loc.speed, 0f, 0f)
        }
        lastFix = loc
        lastFixAtMs = SystemClock.elapsedRealtime()
        // Below walking pace GPS bearing wanders; keep the last good one.
        if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 2f) heading = loc.bearing
        val p = LatLon(loc.latitude, loc.longitude)
        Nav.onLocation(p)
        SpeedLimit.onLocation(p)
    }

    // Older Android versions (before 11) call these and crash if they're missing.
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
