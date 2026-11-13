package io.github.byjoelsamuel.drivesanity

import android.location.Location
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.obd.Obd
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One recording: a CSV file that opens in a spreadsheet. It gets a row every five seconds, plus one
 * whenever something happens (see [LogPolicy]). The hardest braking, acceleration and cornering and
 * the highest revs since the last row are written on each row, so the file stays small without losing
 * the moments that matter.
 */
class TripLog private constructor(val file: File) {
    private val out = file.bufferedWriter()
    private val startMs = SystemClock.elapsedRealtime()
    val policy = LogPolicy()

    private var peakBrake = 0f
    private var peakAccel = 0f
    private var peakLat = 0f
    private var peakRpm = 0f

    init {
        out.write("time_s,lat,lon,speed_kmh,gps_accuracy_m,heading_deg,peak_brake_g,peak_accel_g,peak_lat_g,rpm,rpm_max,throttle_pct,load_pct,coolant_c,intake_c,fuel_pct,battery_v\n")
    }

    /** Called with every motion sample: remembers the biggest values until the next row. */
    fun feed(g: CarG) {
        if (g.brake > peakBrake) peakBrake = g.brake
        if (g.lon > peakAccel) peakAccel = g.lon
        if (kotlin.math.abs(g.lat) > peakLat) peakLat = kotlin.math.abs(g.lat)
        Obd.rpm?.let { if (it > peakRpm) peakRpm = it }
    }

    fun write(fix: Location?, speedMps: Float?, headingDeg: Float?) {
        val t = (SystemClock.elapsedRealtime() - startMs) / 1000.0
        val row = listOf(
            "%.1f".format(Locale.US, t),
            fix?.let { "%.6f".format(Locale.US, it.latitude) } ?: "",
            fix?.let { "%.6f".format(Locale.US, it.longitude) } ?: "",
            speedMps?.let { "%.1f".format(Locale.US, it * 3.6f) } ?: "",
            fix?.let { "%.0f".format(Locale.US, it.accuracy) } ?: "",
            headingDeg?.let { "%.0f".format(Locale.US, it) } ?: "",
            "%.2f".format(Locale.US, peakBrake),
            "%.2f".format(Locale.US, peakAccel),
            "%.2f".format(Locale.US, peakLat),
            num(Obd.rpm, "%.0f"),
            if (peakRpm > 0f) "%.0f".format(Locale.US, peakRpm) else "",
            num(Obd.pedalPct, "%.0f"),
            num(Obd.loadPct, "%.0f"),
            num(Obd.coolantC, "%.0f"),
            num(Obd.intakeC, "%.0f"),
            num(Obd.fuelPct, "%.0f"),
            num(Obd.volts, "%.1f"),
        )
        out.write(row.joinToString(","))
        out.write("\n")
        peakBrake = 0f; peakAccel = 0f; peakLat = 0f; peakRpm = 0f
    }

    private fun num(v: Float?, fmt: String) = v?.let { fmt.format(Locale.US, it) } ?: ""

    fun close() = out.close()

    companion object {
        fun dir(filesDir: File) = File(filesDir, "trips")

        fun open(filesDir: File): TripLog {
            val dir = dir(filesDir).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            return TripLog(File(dir, "drive-$stamp.csv"))
        }

        fun newest(filesDir: File): File? =
            dir(filesDir).listFiles { f -> f.name.endsWith(".csv") }?.maxByOrNull { it.lastModified() }
    }
}
