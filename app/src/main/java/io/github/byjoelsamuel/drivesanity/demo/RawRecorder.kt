package io.github.byjoelsamuel.drivesanity.demo

import android.os.SystemClock
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a raw session (see [RawEvent]) while it's on, for support (a bug report with a recording of
 * exactly what the car said) or to make a demo. Readings are written only when they change.
 */
object RawRecorder {
    @Volatile private var out: BufferedWriter? = null
    @Volatile var file: File? = null
        private set
    private var startMs = 0L
    private var lastFlushMs = 0L
    private val last = HashMap<String, Float>()
    private var lastGMs = 0L

    val on: Boolean get() = out != null

    fun dir(filesDir: File) = File(filesDir, "raw")

    @Synchronized
    fun start(filesDir: File): File {
        stop()
        val f = File(dir(filesDir).apply { mkdirs() }, "raw-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".dsr")
        out = f.bufferedWriter().also { it.write("# DriveSanity raw session\n") }
        file = f
        startMs = SystemClock.elapsedRealtime()
        last.clear()
        return f
    }

    @Synchronized
    fun stop() {
        runCatching { out?.flush(); out?.close() }
        out = null
    }

    private fun now() = SystemClock.elapsedRealtime() - startMs

    @Synchronized
    private fun write(e: RawEvent) {
        val w = out ?: return
        w.write(e.line())
        w.write("\n")
        val t = SystemClock.elapsedRealtime()
        if (t - lastFlushMs > 3_000) {
            w.flush()
            lastFlushMs = t
        }
    }

    fun fix(lat: Double, lon: Double, speedMps: Float, bearing: Float, accuracy: Float) {
        if (out != null) write(RawEvent.Fix(now(), lat, lon, speedMps, bearing, accuracy))
    }

    fun g(lon: Float, lat: Float) {
        if (out == null) return
        val t = now()
        if (t - lastGMs < 100) return // ten a second is plenty
        lastGMs = t
        write(RawEvent.G(t, lon, lat))
    }

    fun obd(name: String, value: Float) {
        if (out == null) return
        // Only what changed, so a steady reading isn't written fifty times.
        if (last[name] == value) return
        last[name] = value
        write(RawEvent.Obd(now(), name, value))
    }
}
