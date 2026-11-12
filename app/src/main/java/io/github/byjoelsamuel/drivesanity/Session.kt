package io.github.byjoelsamuel.drivesanity

import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/** One row of a recording. */
data class SessionPoint(
    val t: Double,
    val lat: Double?,
    val lon: Double?,
    val kmh: Float?,
    val rpm: Float?,
    val throttle: Float?,
    val brakeG: Float,
    val accelG: Float,
    val latG: Float,
    val lap: Int?,
)

data class SessionLap(val track: String, val number: Int, val ms: Long, val topKmh: Float)

/** A recording read back from its CSV (and the laps file beside it), with the numbers worked out. */
class Session(val points: List<SessionPoint>, val laps: List<SessionLap>) {
    val durationS: Double get() = points.lastOrNull()?.t ?: 0.0

    /** Distance along the recorded path, in metres. Gaps of more than half a minute (a pause) aren't counted. */
    val distanceM: Double by lazy {
        var d = 0.0
        var prev: SessionPoint? = null
        for (p in points) {
            val a = prev
            if (a != null && a.lat != null && a.lon != null && p.lat != null && p.lon != null && p.t - a.t <= 30.0) {
                val mLat = 111_320.0
                val mLon = 111_320.0 * cos(Math.toRadians(a.lat))
                d += hypot((p.lat - a.lat) * mLat, (p.lon - a.lon) * mLon)
            }
            if (p.lat != null) prev = p
        }
        d
    }

    val topKmh: Float get() = points.maxOfOrNull { it.kmh ?: 0f } ?: 0f
    val maxRpm: Float get() = points.maxOfOrNull { it.rpm ?: 0f } ?: 0f
    val peakBrakeG: Float get() = points.maxOfOrNull { it.brakeG } ?: 0f
    val peakAccelG: Float get() = points.maxOfOrNull { it.accelG } ?: 0f
    val peakLatG: Float get() = points.maxOfOrNull { it.latG } ?: 0f

    /** Average speed while moving (over 3 km/h), in km/h. */
    val avgMovingKmh: Float by lazy {
        var time = 0.0
        var dist = 0.0
        for (i in 1 until points.size) {
            val dt = points[i].t - points[i - 1].t
            val v = points[i].kmh ?: continue
            if (v > 3f && dt in 0.0..30.0) {
                time += dt
                dist += v * dt
            }
        }
        if (time > 0) (dist / time).toFloat() else 0f
    }

    val bestLap: SessionLap? get() = laps.minByOrNull { it.ms }

    /**
     * The path without its first and last [trimM] metres, so a shared picture doesn't show where
     * the drive began and ended (home, usually). Empty if the drive is too short to hide the ends.
     */
    fun trimmedPath(trimM: Double): List<SessionPoint> {
        val pts = path
        if (pts.size < 2) return emptyList()
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val mLon = 111_320.0 * cos(Math.toRadians(a.lat!!))
            cum[i] = cum[i - 1] + hypot((b.lat!! - a.lat) * 111_320.0, (b.lon!! - a.lon!!) * mLon)
        }
        val total = cum.last()
        if (total < trimM * 3) return emptyList()
        return pts.filterIndexed { i, _ -> cum[i] >= trimM && total - cum[i] >= trimM }
    }

    /** Positions that have a place on the map, for drawing the path. */
    val path: List<SessionPoint> by lazy { points.filter { it.lat != null && it.lon != null } }

    companion object {
        fun read(file: File): Session {
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            val points = parse(lines)
            val lapsFile = File(file.path.removeSuffix(".csv") + ".laps")
            val laps = if (lapsFile.exists()) parseLaps(runCatching { lapsFile.readLines() }.getOrDefault(emptyList())) else emptyList()
            return Session(points, laps)
        }

        /** Reads either the current format or the older one (a row every tenth of a second, with different column names). */
        fun parse(lines: List<String>): List<SessionPoint> {
            val header = lines.firstOrNull()?.split(',') ?: return emptyList()
            fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
            val iT = col("time_s")
            val iLat = col("lat")
            val iLon = col("lon")
            val iKmh = col("speed_kmh", "gps_speed_kmh")
            val iRpm = col("rpm")
            val iThr = col("throttle_pct")
            val iBrake = col("peak_brake_g", "brake_g")
            val iAccel = col("peak_accel_g")
            val iLong = col("long_g")
            val iLatG = col("peak_lat_g", "lat_g")
            val iLap = col("lap")
            if (iT < 0) return emptyList()
            val out = ArrayList<SessionPoint>(lines.size)
            for (line in lines.drop(1)) {
                val f = line.split(',')
                fun num(i: Int): Float? = if (i in f.indices) f[i].toFloatOrNull() else null
                val t = f.getOrNull(iT)?.toDoubleOrNull() ?: continue
                val long = num(iLong)
                out.add(
                    SessionPoint(
                        t = t,
                        lat = if (iLat in f.indices) f[iLat].toDoubleOrNull() else null,
                        lon = if (iLon in f.indices) f[iLon].toDoubleOrNull() else null,
                        kmh = num(iKmh),
                        rpm = num(iRpm),
                        throttle = num(iThr),
                        brakeG = num(iBrake) ?: 0f,
                        accelG = num(iAccel) ?: max(0f, long ?: 0f),
                        latG = num(iLatG)?.let { abs(it) } ?: 0f,
                        lap = f.getOrNull(iLap)?.toIntOrNull(),
                    ),
                )
            }
            return out
        }

        fun parseLaps(lines: List<String>): List<SessionLap> = lines.mapNotNull { l ->
            val p = l.split(',')
            if (p.size < 4) return@mapNotNull null
            val n = p[1].toIntOrNull() ?: return@mapNotNull null
            val ms = p[2].toLongOrNull() ?: return@mapNotNull null
            SessionLap(p[0], n, ms, p[3].toFloatOrNull() ?: 0f)
        }
    }
}
