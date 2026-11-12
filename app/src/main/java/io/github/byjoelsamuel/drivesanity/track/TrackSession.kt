package io.github.byjoelsamuel.drivesanity.track

import android.content.Context
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.Prefs
import org.json.JSONArray
import org.json.JSONObject

/** What the screens show of the lap timer. */
data class LapView(
    val number: Int,
    val currentMs: Long?,
    val lastMs: Long?,
    val bestMs: Long?,
    val deltaMs: Long?,
    val lapsDone: Int,
    val trackName: String,
)

/** What the screens show of the standing-start timer. */
data class RunView(
    val running: Boolean,
    val liveMs: Long?,
    val to60Ms: Long?,
    val to100Ms: Long?,
    val quarterMs: Long?,
    val trapKmh: Float?,
)

/**
 * Lap timing and standing-start timing for the current drive. Lap timing is armed with a start/finish
 * line (saved ones are kept under their names), and it records the drive at one row a second while it
 * runs, so the laps are in the history afterwards.
 */
object Track {
    @Volatile private var timer: LapTimer? = null
    private val accel = AccelTimer()
    private val lock = Any()

    /** Called with each finished lap, to be written next to the recording. */
    @Volatile var onLap: ((Lap, String) -> Unit)? = null

    val armed: Boolean get() = timer != null
    val lineName: String? get() = timer?.line?.name

    fun arm(line: FinishLine) {
        synchronized(lock) { timer = LapTimer(line) }
    }

    fun disarm() {
        synchronized(lock) { timer = null }
    }

    /** A fix from GPS (or the demo), on the elapsed-realtime clock. */
    fun onFix(tMs: Long, lat: Double, lon: Double, speedMps: Float, gpsSpeedForRuns: Boolean) {
        synchronized(lock) {
            val t = timer
            if (t != null) {
                val before = t.laps.size
                t.onFix(tMs, lat, lon, speedMps)
                if (t.laps.size > before) onLap?.invoke(t.laps.last(), t.line.name)
            }
            if (gpsSpeedForRuns) accel.onSpeed(tMs, speedMps)
        }
    }

    /** A speed reading from the car, as it arrives. */
    fun onSpeed(tMs: Long, mps: Float) {
        synchronized(lock) { accel.onSpeed(tMs, mps) }
    }

    fun lapView(nowMs: Long = SystemClock.elapsedRealtime()): LapView? = synchronized(lock) {
        val t = timer ?: return null
        LapView(t.lapNumber, t.currentMs(nowMs), t.last?.ms, t.best?.ms, t.deltaMs, t.laps.size, t.line.name)
    }

    fun laps(): List<Lap> = synchronized(lock) { timer?.laps.orEmpty().toList() }

    fun runView(nowMs: Long = SystemClock.elapsedRealtime()): RunView? = synchronized(lock) {
        val live = accel.liveRun()
        val shown = live ?: accel.result
        if (shown == null && accel.state != AccelTimer.State.READY) return null
        RunView(live != null, accel.liveMs(nowMs), shown?.to60Ms, shown?.to100kmhMs, shown?.quarterMs, shown?.quarterTrapKmh)
    }

    // --- saved lines ----------------------------------------------------------------------------

    fun saved(ctx: Context): List<FinishLine> = runCatching {
        val arr = JSONArray(Prefs.tracksJson(ctx))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FinishLine(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getDouble("heading"), o.optDouble("half", 25.0))
        }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, line: FinishLine) {
        val all = saved(ctx).filterNot { it.name == line.name } + line
        write(ctx, all)
    }

    fun delete(ctx: Context, name: String) = write(ctx, saved(ctx).filterNot { it.name == name })

    private fun write(ctx: Context, lines: List<FinishLine>) {
        val arr = JSONArray()
        for (l in lines) {
            arr.put(JSONObject().put("name", l.name).put("lat", l.lat).put("lon", l.lon).put("heading", l.headingDeg).put("half", l.halfWidthM))
        }
        Prefs.setTracksJson(ctx, arr.toString())
    }
}

/** "1:42.318", or "–" for nothing. */
fun lapText(ms: Long?): String {
    if (ms == null) return "–"
    val m = ms / 60_000
    val s = (ms % 60_000) / 1000.0
    return if (m > 0) "%d:%06.3f".format(java.util.Locale.US, m, s) else "%.3f".format(java.util.Locale.US, s)
}

/** "+0.42" or "−1.08": ahead of the best lap is negative. */
fun deltaText(ms: Long?): String {
    if (ms == null) return "–"
    val s = ms / 1000.0
    return (if (s < 0) "−" else "+") + "%.2f".format(java.util.Locale.US, kotlin.math.abs(s))
}
