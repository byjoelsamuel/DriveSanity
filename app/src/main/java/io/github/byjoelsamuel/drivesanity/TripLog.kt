package io.github.byjoelsamuel.drivesanity

import android.location.Location
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One recording: a CSV file with a row every 0.1 s. */
class TripLog private constructor(val file: File) {
    private val out = file.bufferedWriter()
    private val startMs = SystemClock.elapsedRealtime()

    init {
        out.write("time_s,lat,lon,gps_speed_kmh,gps_accuracy_m,gps_accel_g,long_g,lat_g,brake_g,brake_level\n")
    }

    fun write(g: CarG, fix: Location?, speedMps: Float?, gpsAccelG: Float?) {
        val t = (SystemClock.elapsedRealtime() - startMs) / 1000.0
        val row = listOf(
            "%.2f".format(Locale.US, t),
            fix?.let { "%.6f".format(Locale.US, it.latitude) } ?: "",
            fix?.let { "%.6f".format(Locale.US, it.longitude) } ?: "",
            speedMps?.let { "%.1f".format(Locale.US, it * 3.6f) } ?: "",
            fix?.let { "%.0f".format(Locale.US, it.accuracy) } ?: "",
            gpsAccelG?.let { "%.3f".format(Locale.US, it) } ?: "",
            "%.3f".format(Locale.US, g.lon),
            "%.3f".format(Locale.US, g.lat),
            "%.3f".format(Locale.US, g.brake),
            BrakeLevel.of(g.brake).name,
        )
        out.write(row.joinToString(","))
        out.write("\n")
    }

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
