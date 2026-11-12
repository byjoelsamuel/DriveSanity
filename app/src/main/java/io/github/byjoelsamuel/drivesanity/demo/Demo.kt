package io.github.byjoelsamuel.drivesanity.demo

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.obd.Obd
import java.io.File

/**
 * Demo mode: plays a recorded session (the one built into the app, or one of your own) through
 * the whole app in place of the real GPS, motion sensor and OBD2 adapter. Every screen, the lap
 * timer and the car screen run off it, so the app can be tried, shown or tested with no car.
 */
object Demo {
    const val HOLDER = "demo"

    @Volatile var active = false
        private set

    /** A short name for what's playing, for the screens. */
    @Volatile var name = ""
        private set

    /** The start/finish line that goes with the recording, if it has one (a "# line,name,lat,lon,heading" comment). */
    @Volatile var line: io.github.byjoelsamuel.drivesanity.track.FinishLine? = null
        private set

    private var player: RawPlayer? = null
    private var startMs = 0L
    private lateinit var app: Context
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val elapsed = SystemClock.elapsedRealtime() - startMs
            for (e in p.due(elapsed, restarted = { DriveEngine.demoRestarted() })) apply(e)
            handler.postDelayed(this, 25)
        }
    }

    /** Starts playing [file], or the built-in session if it's null. */
    fun start(context: Context, file: File? = null): Boolean {
        app = context.applicationContext
        val text = runCatching {
            file?.readText() ?: app.assets.open("demo.dsr").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return false
        val events = RawEvent.parse(text)
        if (events.isEmpty()) return false
        stop()
        line = text.lineSequence().firstOrNull { it.startsWith("# line,") }?.split(',')?.let { p ->
            runCatching {
                io.github.byjoelsamuel.drivesanity.track.FinishLine(p[1], p[2].toDouble(), p[3].toDouble(), p[4].toDouble())
            }.getOrNull()
        }
        player = RawPlayer(events)
        name = file?.name ?: "Built-in drive"
        active = true
        Obd.setDemo(true)
        DriveEngine.hold(app, HOLDER)
        DriveEngine.demoRestarted()
        startMs = SystemClock.elapsedRealtime()
        handler.post(tick)
        return true
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacks(tick)
        player = null
        Obd.setDemo(false)
        DriveEngine.demoStopped()
        DriveEngine.release(HOLDER)
    }

    private fun apply(e: RawEvent) {
        when (e) {
            is RawEvent.Obd -> Obd.inject(e.name, e.value)
            is RawEvent.G -> DriveEngine.demoG(e.lon, e.lat)
            is RawEvent.Fix -> {
                val loc = Location("demo").apply {
                    latitude = e.lat
                    longitude = e.lon
                    speed = e.speedMps
                    bearing = e.bearing
                    accuracy = e.accuracy
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    time = System.currentTimeMillis()
                }
                DriveEngine.onLocationChanged(loc)
            }
        }
    }
}
