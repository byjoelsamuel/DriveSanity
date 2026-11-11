package io.github.byjoelsamuel.drivesanity.nav

import io.github.byjoelsamuel.drivesanity.map.LatLon
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class Arrow { STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, UTURN, ARRIVE }

/** One manoeuvre: where it happens and what to do there. [distanceM] runs from here to the next one. */
data class Step(
    val at: LatLon,
    val type: String,
    val modifier: String,
    val name: String,
    val exit: Int,
    val distanceM: Double,
    val durationS: Double,
) {
    val text: String get() = Osrm.instruction(type, modifier, name, exit)
    val arrow: Arrow get() = Osrm.arrow(type, modifier)
}

data class Route(val points: List<LatLon>, val steps: List<Step>, val distanceM: Double, val durationS: Double)

data class Place(val name: String, val at: LatLon)

/** Reading replies from OSRM (routes) and Nominatim (address search). */
object Osrm {
    fun parseRoute(json: String): Route {
        val o = JSONObject(json)
        if (o.optString("code") != "Ok") throw IOException(o.optString("message", o.optString("code", "No route")))
        val r = o.getJSONArray("routes").optJSONObject(0) ?: throw IOException("No route found")
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map { lonLat(coords.getJSONArray(it)) }
        val steps = mutableListOf<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val ss = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until ss.length()) {
                val s = ss.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                steps += Step(
                    at = lonLat(m.getJSONArray("location")),
                    type = m.optString("type"),
                    modifier = m.optString("modifier"),
                    name = s.optString("name").ifBlank { s.optString("ref") },
                    exit = m.optInt("exit", 0),
                    distanceM = s.optDouble("distance", 0.0),
                    durationS = s.optDouble("duration", 0.0),
                )
            }
        }
        return Route(points, steps, r.optDouble("distance", 0.0), r.optDouble("duration", 0.0))
    }

    fun parsePlaces(json: String): List<Place> {
        val a = JSONArray(json)
        return (0 until a.length()).map {
            val p = a.getJSONObject(it)
            Place(p.optString("display_name"), LatLon(p.getString("lat").toDouble(), p.getString("lon").toDouble()))
        }
    }

    private fun lonLat(a: JSONArray) = LatLon(a.getDouble(1), a.getDouble(0))

    fun instruction(type: String, modifier: String, name: String, exit: Int): String {
        val onto = if (name.isBlank()) "" else " onto $name"
        val side = when {
            modifier.contains("left") -> "left"
            modifier.contains("right") -> "right"
            else -> "straight"
        }
        return when (type) {
            "depart" -> if (name.isBlank()) "Head off" else "Head off on $name"
            "arrive" -> "Arrive at your destination"
            "roundabout", "rotary" -> if (exit > 0) "At the roundabout, take exit $exit$onto" else "Take the roundabout$onto"
            "merge" -> "Merge$onto"
            "on ramp" -> "Take the ramp$onto"
            "off ramp" -> "Take the exit$onto"
            "fork" -> "Keep $side$onto"
            else -> when (modifier) {
                "left" -> "Turn left$onto"
                "right" -> "Turn right$onto"
                "slight left" -> "Bear left$onto"
                "slight right" -> "Bear right$onto"
                "sharp left" -> "Sharp left$onto"
                "sharp right" -> "Sharp right$onto"
                "uturn" -> "Make a U-turn$onto"
                else -> "Continue$onto"
            }
        }
    }

    fun arrow(type: String, modifier: String): Arrow = when {
        type == "arrive" -> Arrow.ARRIVE
        modifier == "uturn" -> Arrow.UTURN
        modifier == "slight left" -> Arrow.SLIGHT_LEFT
        modifier == "slight right" -> Arrow.SLIGHT_RIGHT
        modifier.contains("left") -> Arrow.LEFT
        modifier.contains("right") -> Arrow.RIGHT
        else -> Arrow.STRAIGHT
    }
}
