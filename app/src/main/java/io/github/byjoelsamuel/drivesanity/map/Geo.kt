package io.github.byjoelsamuel.drivesanity.map

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Map maths: Web Mercator (how map tiles are laid out) and distances on the ground. */
object Geo {
    private const val EARTH_R = 6_371_000.0
    const val TILE = 256

    /** Position in "world pixels" at [zoom]: the whole world is 256 × 2^zoom pixels wide. */
    fun worldX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * TILE * (1 shl zoom)

    fun worldY(lat: Double, zoom: Int): Double {
        val s = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * TILE * (1 shl zoom)
    }

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_R * asin(sqrt(h))
    }

    /** Metres from [p] to the segment [a]–[b]. Treats the ground as flat around [p], fine at street scale. */
    fun distanceToSegmentM(p: LatLon, a: LatLon, b: LatLon): Double {
        val ky = EARTH_R * PI / 180
        val kx = ky * cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * ky
        val dx = (b.lon - p.lon) * kx - ax
        val dy = (b.lat - p.lat) * ky - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    /** Metres from [p] to the nearest point of a polyline. */
    fun distanceToLineM(p: LatLon, line: List<LatLon>): Double {
        if (line.size == 1) return distanceM(p, line[0])
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) best = minOf(best, distanceToSegmentM(p, line[i], line[i + 1]))
        return best
    }
}
