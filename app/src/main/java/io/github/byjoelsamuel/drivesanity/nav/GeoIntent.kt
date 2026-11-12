package io.github.byjoelsamuel.drivesanity.nav

import io.github.byjoelsamuel.drivesanity.map.LatLon

/**
 * Where a "navigate to" request from Android Auto or the Assistant points. They arrive as geo: links:
 * `geo:47.6,-122.3` or `geo:47.6,-122.3?q=Pike+Place` (a spot), or `geo:0,0?q=coffee+near+me` (words to search for).
 */
sealed interface GeoTarget {
    data class At(val place: Place) : GeoTarget
    data class Search(val words: String) : GeoTarget

    companion object {
        private val COORDS = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")

        /** [spec] is what comes after "geo:" and before any "?"; [q] is the q parameter, if there is one. */
        fun parse(spec: String, q: String?): GeoTarget? {
            val label = q?.trim()?.takeIf { it.isNotEmpty() }
            // "q=lat,lng(Label)" is a spot with a name.
            label?.let { text ->
                COORDS.matchEntire(text)?.let { m ->
                    val at = point(m.groupValues[1], m.groupValues[2])
                    if (at != null) return At(Place(m.groupValues[3].ifBlank { "Destination" }, at))
                }
            }
            val parts = spec.split(',')
            val at = if (parts.size >= 2) point(parts[0], parts[1].substringBefore(';')) else null
            // 0,0 means "no coordinates, look at q".
            if (at != null && !(at.lat == 0.0 && at.lon == 0.0)) return At(Place(label ?: "Destination", at))
            return label?.let { Search(it) }
        }

        private fun point(lat: String, lon: String): LatLon? {
            val la = lat.trim().toDoubleOrNull() ?: return null
            val lo = lon.trim().toDoubleOrNull() ?: return null
            return if (la in -90.0..90.0 && lo in -180.0..180.0) LatLon(la, lo) else null
        }
    }
}
