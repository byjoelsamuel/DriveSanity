package io.github.byjoelsamuel.drivesanity.demo

import java.util.Locale

/**
 * A raw session: what the phone's GPS, its motion sensor and the OBD2 adapter reported, with the time
 * of each in milliseconds from the start. One event per line, so it can be written as it happens and
 * read back to drive the whole app with no car:
 *
 *     F,t,lat,lon,speed_mps,bearing_deg,accuracy_m   a GPS fix
 *     G,t,lon_g,lat_g                                 the car's g-force (forward, sideways)
 *     O,t,name,value                                  an OBD2 reading (rpm, speed, throttle, …)
 *
 * Lines starting with # are comments.
 */
sealed interface RawEvent {
    val t: Long

    data class Fix(override val t: Long, val lat: Double, val lon: Double, val speedMps: Float, val bearing: Float, val accuracy: Float) : RawEvent
    data class G(override val t: Long, val lon: Float, val lat: Float) : RawEvent
    data class Obd(override val t: Long, val name: String, val value: Float) : RawEvent

    fun line(): String = when (this) {
        is Fix -> String.format(Locale.US, "F,%d,%.6f,%.6f,%.2f,%.1f,%.0f", t, lat, lon, speedMps, bearing, accuracy)
        is G -> String.format(Locale.US, "G,%d,%.3f,%.3f", t, lon, lat)
        is Obd -> String.format(Locale.US, "O,%d,%s,%s", t, name, if (value % 1f == 0f) value.toLong().toString() else "%.2f".format(Locale.US, value))
    }

    companion object {
        fun parseLine(line: String): RawEvent? {
            val p = line.trim().split(',')
            if (p.size < 3 || line.startsWith("#")) return null
            return try {
                val t = p[1].toLong()
                when (p[0]) {
                    "F" -> if (p.size >= 7) Fix(t, p[2].toDouble(), p[3].toDouble(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat()) else null
                    "G" -> if (p.size >= 4) G(t, p[2].toFloat(), p[3].toFloat()) else null
                    "O" -> if (p.size >= 4) Obd(t, p[2], p[3].toFloat()) else null
                    else -> null
                }
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun parse(text: String): List<RawEvent> = text.lineSequence().mapNotNull(::parseLine).sortedBy { it.t }.toList()
    }
}

/** Plays a list of events back against a clock that loops. */
class RawPlayer(private val events: List<RawEvent>, private val pauseMs: Long = 3_000) {
    val durationMs: Long = events.lastOrNull()?.t ?: 0L
    private var next = 0
    private var lastHead = -1L

    /**
     * Everything that should have happened by [elapsedMs] since playing began and wasn't handed out yet.
     * At the end it waits [pauseMs] and starts over. [restarted] says the clip just wrapped around.
     */
    fun due(elapsedMs: Long, restarted: () -> Unit = {}): List<RawEvent> {
        if (events.isEmpty()) return emptyList()
        val cycle = durationMs + pauseMs
        val head = elapsedMs % cycle
        val out = ArrayList<RawEvent>()
        if (head < lastHead) {
            // Wrapped: finish the old pass, then start the new one.
            while (next < events.size) out.add(events[next++])
            next = 0
            restarted()
        }
        while (next < events.size && events[next].t <= head) out.add(events[next++])
        lastHead = head
        return out
    }
}
