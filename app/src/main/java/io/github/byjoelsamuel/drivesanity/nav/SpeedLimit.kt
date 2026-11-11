package io.github.byjoelsamuel.drivesanity.nav

import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.map.Geo
import io.github.byjoelsamuel.drivesanity.map.LatLon
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

/** A posted limit, in the units the sign uses. */
data class Limit(val value: Int, val mph: Boolean) {
    fun inMph() = if (mph) value else (value / 1.609344 / 5).roundToInt() * 5
    fun inKmh() = if (!mph) value else (value * 1.609344 / 5).roundToInt() * 5
}

/** A stretch of road with a mapped limit. */
data class Road(val limit: Limit, val points: List<LatLon>)

/**
 * The speed limit of the road you're on, from OpenStreetMap's maxspeed tags via the public
 * Overpass API. It downloads every road with a limit within 1.5 km in one go, then matches the
 * car to the nearest one going the same way on every GPS fix, so the sign changes as soon as
 * you turn. A new area is fetched when you get near the edge of the old one. Many roads have
 * no limit mapped; then nothing shows.
 */
object SpeedLimit {
    private const val OVERPASS = "https://overpass-api.de/api/interpreter"
    private const val RADIUS_M = 1500
    private const val REFETCH_FROM_M = 900.0

    @Volatile var current: Limit? = null
        private set
    @Volatile private var roads: List<Road> = emptyList()
    private var areaCenter: LatLon? = null
    private var askedMs = 0L
    @Volatile private var busy = false
    private var lastMatch: LatLon? = null

    fun onLocation(p: LatLon, headingDeg: Float?) {
        val center = areaCenter
        val now = SystemClock.elapsedRealtime()
        if (!busy && now - askedMs > 30_000 && (center == null || Geo.distanceM(center, p) > REFETCH_FROM_M)) fetch(p)

        val hit = match(roads, p, headingDeg)
        if (hit != null) {
            current = hit
            lastMatch = p
        } else if (lastMatch?.let { Geo.distanceM(it, p) > 80.0 } != false) {
            // Off every mapped road for a while (or never on one): don't show a stale limit.
            current = null
        }
    }

    private fun fetch(p: LatLon) {
        busy = true
        askedMs = SystemClock.elapsedRealtime()
        val q = "[out:json][timeout:20];way(around:$RADIUS_M,${p.lat},${p.lon})[highway][maxspeed];out tags geom;"
        Http.io.execute {
            try {
                roads = parseRoads(String(Http.post(OVERPASS, "data=" + URLEncoder.encode(q, "UTF-8"))))
                areaCenter = p
            } catch (_: Exception) {
                // Keep what we had; try again in 30 s.
            } finally {
                busy = false
            }
        }
    }

    fun parseRoads(json: String): List<Road> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        return (0 until els.length()).mapNotNull { i ->
            val e = els.getJSONObject(i)
            val limit = e.optJSONObject("tags")?.optString("maxspeed")?.let(::parseMaxspeed) ?: return@mapNotNull null
            val geom = e.optJSONArray("geometry") ?: return@mapNotNull null
            val pts = (0 until geom.length()).map { geom.getJSONObject(it).let { g -> LatLon(g.getDouble("lat"), g.getDouble("lon")) } }
            if (pts.size < 2) null else Road(limit, pts)
        }
    }

    /**
     * The limit of the road the car is on: the nearest segment within 20 m, and, when the
     * heading is known, one running the same way (either direction, within 35°), so a side
     * street at a junction doesn't win.
     */
    fun match(roads: List<Road>, p: LatLon, headingDeg: Float?): Limit? {
        var best: Limit? = null
        var bestD = 20.0
        for (road in roads) {
            for (i in 0 until road.points.size - 1) {
                val a = road.points[i]
                val b = road.points[i + 1]
                val d = Geo.distanceToSegmentM(p, a, b)
                if (d >= bestD) continue
                if (headingDeg != null) {
                    val diff = abs(((Geo.bearingDeg(a, b) - headingDeg) % 180 + 180) % 180)
                    if (minOf(diff, 180 - diff) > 35) continue
                }
                best = road.limit
                bestD = d
            }
        }
        return best
    }

    /** "35 mph" → 35 mph, "50" → 50 km/h; "none", "signals", "RU:urban" → null. */
    fun parseMaxspeed(tag: String): Limit? {
        val m = Regex("""^\s*(\d{1,3})\s*(mph|km/h|kmh)?\s*$""", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return Limit(m.groupValues[1].toInt(), m.groupValues[2].equals("mph", ignoreCase = true))
    }
}
