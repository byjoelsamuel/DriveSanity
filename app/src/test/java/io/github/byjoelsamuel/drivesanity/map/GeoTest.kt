package io.github.byjoelsamuel.drivesanity.map

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun `mercator puts the origin in the middle of the world`() {
        assertEquals(128.0, Geo.worldX(0.0, 0), 1e-9)
        assertEquals(128.0, Geo.worldY(0.0, 0), 1e-9)
        assertEquals(0.0, Geo.worldX(-180.0, 3), 1e-9)
        // Zoom 1 doubles everything.
        assertEquals(2 * Geo.worldX(10.0, 4), Geo.worldX(10.0, 5), 1e-6)
        // North is up: higher latitude, smaller y.
        assert(Geo.worldY(50.0, 10) < Geo.worldY(40.0, 10))
    }

    @Test
    fun `distances`() {
        // One degree of latitude is about 111.2 km.
        assertEquals(111_195.0, Geo.distanceM(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 50.0)
        // A point 10 m north of the middle of an east-west segment.
        val a = LatLon(40.0, -75.001)
        val b = LatLon(40.0, -74.999)
        val p = LatLon(40.0 + 10 / 111_195.0, -75.0)
        assertEquals(10.0, Geo.distanceToSegmentM(p, a, b), 0.1)
        // Past the end, it's the distance to the end point.
        val q = LatLon(40.0, -74.998)
        assertEquals(Geo.distanceM(q, b), Geo.distanceToSegmentM(q, a, b), 0.5)
        assertEquals(10.0, Geo.distanceToLineM(p, listOf(a, b, LatLon(41.0, -74.999))), 0.1)
    }
}
