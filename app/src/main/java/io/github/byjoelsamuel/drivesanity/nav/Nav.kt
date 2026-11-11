package io.github.byjoelsamuel.drivesanity.nav

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.map.Geo
import io.github.byjoelsamuel.drivesanity.map.LatLon
import java.net.URLEncoder

/**
 * Turn-by-turn, kept simple: search an address with Nominatim, get a driving route from the
 * FOSSGIS OSRM server (both free, OpenStreetMap-based), then follow along with GPS: which
 * manoeuvre is next, how far to it, what's left. Goes off the route by more than 60 m and it
 * asks for a new one. Everything here runs on the main thread except the network calls.
 */
object Nav {
    private const val SEARCH = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q="
    private const val ROUTE = "https://routing.openstreetmap.de/routed-car/route/v1/driving/"

    private val main = Handler(Looper.getMainLooper())

    var destination: Place? = null
        private set
    var route: Route? = null
        private set
    /** Index of the next manoeuvre in the route's steps. */
    var next = 0
        private set
    var toNextM = 0.0
        private set
    var remainingM = 0.0
        private set
    var remainingS = 0.0
        private set
    /** A short message instead of directions: "Finding a route…", "Arrived", an error. */
    var message: String? = null
        private set

    private var offRoute = 0
    private var lastRouteAt = 0L
    private var lastPos: LatLon? = null

    val active get() = destination != null

    /** Clock time you'll get there, e.g. "8:52 PM", from the time left on the route. */
    fun arrivalTime(): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        .format(java.util.Date(System.currentTimeMillis() + (remainingS * 1000).toLong()))

    fun search(query: String, near: LatLon?, done: (Result<List<Place>>) -> Unit) {
        var url = SEARCH + URLEncoder.encode(query, "UTF-8")
        // Prefer results near the car, without excluding the rest.
        if (near != null) url += "&viewbox=${near.lon - 0.5},${near.lat + 0.5},${near.lon + 0.5},${near.lat - 0.5}"
        Http.io.execute {
            val r = runCatching { Osrm.parsePlaces(String(Http.get(url))) }
            main.post { done(r) }
        }
    }

    fun start(place: Place) {
        destination = place
        route = null
        message = "Finding a route…"
        lastPos?.let { requestRoute(it) }
    }

    fun stop() {
        destination = null
        route = null
        message = null
    }

    private fun requestRoute(from: LatLon) {
        val dest = destination ?: return
        lastRouteAt = SystemClock.elapsedRealtime()
        offRoute = 0
        val url = "$ROUTE${from.lon},${from.lat};${dest.at.lon},${dest.at.lat}?overview=full&geometries=geojson&steps=true"
        Http.io.execute {
            val r = runCatching { Osrm.parseRoute(String(Http.get(url))) }
            main.post {
                if (destination !== dest) return@post // cancelled or changed meanwhile
                r.onSuccess {
                    route = it
                    next = minOf(1, it.steps.size - 1) // step 0 is "head off" from where we are
                    message = null
                    lastPos?.let { p -> follow(p) }
                }.onFailure { message = "No route: ${it.message}" }
            }
        }
    }

    /** Called with every GPS fix. */
    fun onLocation(p: LatLon) {
        val first = lastPos == null
        lastPos = p
        if (destination != null && route == null && first) requestRoute(p)
        follow(p)
    }

    private fun follow(p: LatLon) {
        val r = route ?: return
        if (r.steps.isEmpty()) return
        // Within 25 m of the next manoeuvre means we're doing it: move on to the one after.
        while (next < r.steps.size - 1 && Geo.distanceM(p, r.steps[next].at) < 25.0) next++
        val last = r.steps.size - 1
        toNextM = Geo.distanceM(p, r.steps[next].at)
        if (next == last && toNextM < 30.0) {
            route = null
            destination = null
            message = "Arrived"
            main.postDelayed({ if (destination == null) message = null }, 10_000)
            return
        }
        remainingM = toNextM + (next until r.steps.size).sumOf { r.steps[it].distanceM }
        remainingS = (next until r.steps.size).sumOf { r.steps[it].durationS } +
            r.steps[maxOf(0, next - 1)].let { s -> if (s.durationS > 0) toNextM / (s.distanceM / s.durationS).coerceAtLeast(3.0) else 0.0 }

        offRoute = if (Geo.distanceToLineM(p, r.points) > 60.0) offRoute + 1 else 0
        if (offRoute >= 3 && SystemClock.elapsedRealtime() - lastRouteAt > 30_000) {
            message = "Rerouting…"
            requestRoute(p)
        }
    }
}
