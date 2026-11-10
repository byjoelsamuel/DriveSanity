package io.github.byjoelsamuel.drivesanity

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Standard gravity, m/s². One "g". */
const val G = 9.80665f

/** A vector in the phone's own axes: x to the right of the screen, y up the screen, z out of the screen. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): Vec3 = length().let { if (it == 0f) this else this * (1f / it) }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        fun mean(vs: List<Vec3>): Vec3 = vs.fold(ZERO) { a, v -> a + v } * (1f / vs.size)
    }
}

/** Acceleration in the car's frame, in g. lon > 0 is speeding up, < 0 is braking; lat > 0 is turning right. */
data class CarG(val lon: Float, val lat: Float) {
    val total get() = sqrt(lon * lon + lat * lat)
    val brake get() = max(0f, -lon)
}

/**
 * Where the car's axes are in the phone's frame, worked out once while the car is still.
 *
 * At rest the accelerometer reads only gravity's reaction, which points straight up, so the
 * average reading gives "up". That same reading is subtracted from every later sample, which
 * removes gravity and any sensor offset in one go (it only works while the phone stays put in
 * its mount).
 *
 * Forward is assumed from how phones get mounted: the back of the phone faces the windshield
 * (dash or vent mount, portrait or landscape), or, if the phone lies roughly flat, the top of
 * the phone points to the front of the car.
 */
class Calibration(val rest: Vec3, val forward: Vec3, val right: Vec3) {

    fun toCar(reading: Vec3): CarG {
        val a = reading - rest
        return CarG((a dot forward) / G, (a dot right) / G)
    }

    companion object {
        /** Null when [rest] can't be gravity alone, i.e. the car or phone was moving. */
        fun fromRest(rest: Vec3): Calibration? {
            val len = rest.length()
            if (len < 0.85f * G || len > 1.15f * G) return null
            val up = rest.normalized()
            fun flatten(v: Vec3) = v - up * (v dot up)
            val outOfBack = flatten(Vec3(0f, 0f, -1f))
            val forward = (if (outOfBack.length() > 0.5f) outOfBack else flatten(Vec3(0f, 1f, 0f))).normalized()
            val right = (forward cross up).normalized()
            return Calibration(rest, forward, right)
        }

        /** True when the samples are steady enough to calibrate from (engine idle is fine, driving isn't). */
        fun isSteady(samples: List<Vec3>): Boolean {
            if (samples.size < 10) return false
            val m = Vec3.mean(samples)
            val rms = sqrt(samples.sumOf { ((it - m) dot (it - m)).toDouble() } / samples.size)
            return rms < 0.35
        }
    }
}

/** How hard the car is braking, from the deceleration in g. */
enum class BrakeLevel(val label: String, val from: Float) {
    NONE("NONE", 0f),
    LIGHT("LIGHT", 0.10f),
    MODERATE("MODERATE", 0.25f),
    HARD("HARD", 0.40f),
    VERY_HARD("VERY HARD", 0.60f);

    companion object {
        fun of(brakeG: Float): BrakeLevel = entries.last { brakeG >= it.from }
    }
}

/** Exponential smoothing with a time constant, so it behaves the same at any sensor rate. */
class LowPass3(private val tauSec: Float) {
    private var value: Vec3? = null

    fun feed(v: Vec3, dtSec: Float): Vec3 {
        val prev = value
        val next = if (prev == null || dtSec <= 0f) v else {
            val a = 1f - exp(-dtSec / tauSec)
            prev + (v - prev) * a
        }
        value = next
        return next
    }

    fun reset() {
        value = null
    }
}

/** Counts how often a value goes over [trigger]; it has to drop below [rearm] before it counts again. */
class EventCounter(private val trigger: Float, private val rearm: Float) {
    var count = 0
        private set
    private var armed = true

    fun feed(value: Float): Boolean {
        if (armed && value >= trigger) {
            armed = false
            count++
            return true
        }
        if (!armed && value < rearm) armed = true
        return false
    }

    fun reset() {
        count = 0
        armed = true
    }
}

class TripStats {
    var distanceM = 0.0; private set
    var movingSec = 0.0; private set
    var topSpeedMps = 0f; private set
    var peakBrakeG = 0f; private set
    var peakAccelG = 0f; private set
    var peakTurnG = 0f; private set
    val hardBrakes = EventCounter(trigger = 0.40f, rearm = 0.20f)
    val hardLaunches = EventCounter(trigger = 0.35f, rearm = 0.15f)
    val hardTurns = EventCounter(trigger = 0.40f, rearm = 0.20f)

    val avgMovingSpeedMps: Float get() = if (movingSec > 0) (distanceM / movingSec).toFloat() else 0f

    fun feedG(g: CarG) {
        peakBrakeG = max(peakBrakeG, g.brake)
        peakAccelG = max(peakAccelG, g.lon)
        peakTurnG = max(peakTurnG, abs(g.lat))
        hardBrakes.feed(g.brake)
        hardLaunches.feed(g.lon)
        hardTurns.feed(abs(g.lat))
    }

    fun feedGps(speedMps: Float, distanceM: Float, movingSec: Float) {
        topSpeedMps = max(topSpeedMps, speedMps)
        this.distanceM += distanceM
        this.movingSec += movingSec
    }

    fun reset() {
        distanceM = 0.0; movingSec = 0.0; topSpeedMps = 0f
        peakBrakeG = 0f; peakAccelG = 0f; peakTurnG = 0f
        hardBrakes.reset(); hardLaunches.reset(); hardTurns.reset()
    }
}
