package io.github.byjoelsamuel.drivesanity.nav

import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.map.Geo
import io.github.byjoelsamuel.drivesanity.map.LatLon
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

/** A posted limit, in the units the sign uses. */
data class Limit(val value: Int, val mph: Boolean) {
    fun inMph() = if (mph) value else (value / 1.609344 / 5).roundToInt() * 5
    fun inKmh() = if (!mph) value else (value * 1.609344 / 5).roundToInt() * 5
}

/**
 * The speed limit of the road you're on, from OpenStreetMap's maxspeed tags via the public
 * Overpass API. Asked at most every 20 s, and only after moving 150 m, to keep the load light.
 * Coverage depends on the area: many roads have no limit mapped, and then nothing shows.
 */
object SpeedLimit {
    private const val OVERPASS = "https://overpass-api.de/api/interpreter"

    @Volatile var current: Limit? = null
        private set
    private var askedAt: LatLon? = null
    private var askedMs = 0L
    @Volatile private var busy = false

    fun onLocation(p: LatLon) {
        val now = SystemClock.elapsedRealtime()
        val prev = askedAt
        if (busy || now - askedMs < 20_000) return
        if (prev != null && Geo.distanceM(prev, p) < 150.0) return
        // Far from where we last asked: the old answer may be for another road.
        if (prev != null && Geo.distanceM(prev, p) > 600.0) current = null
        askedAt = p
        askedMs = now
        busy = true
        val q = "[out:json][timeout:10];way(around:25,${p.lat},${p.lon})[highway][maxspeed];out tags;"
        Http.io.execute {
            try {
                current = parse(String(Http.post(OVERPASS, "data=" + URLEncoder.encode(q, "UTF-8"))))
            } catch (_: Exception) {
                // Keep the last answer; try again next time.
            } finally {
                busy = false
            }
        }
    }

    /** The most common limit among the roads found (a junction can return a side street too). */
    fun parse(json: String): Limit? {
        val els = JSONObject(json).optJSONArray("elements") ?: return null
        val limits = (0 until els.length()).mapNotNull {
            els.getJSONObject(it).optJSONObject("tags")?.optString("maxspeed")?.let(::parseMaxspeed)
        }
        return limits.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /** "35 mph" → 35 mph, "50" → 50 km/h; "none", "signals", "RU:urban" → null. */
    fun parseMaxspeed(tag: String): Limit? {
        val m = Regex("""^\s*(\d{1,3})\s*(mph|km/h|kmh)?\s*$""", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return Limit(m.groupValues[1].toInt(), m.groupValues[2].equals("mph", ignoreCase = true))
    }
}
