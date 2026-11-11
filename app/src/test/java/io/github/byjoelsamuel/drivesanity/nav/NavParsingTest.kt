package io.github.byjoelsamuel.drivesanity.nav

import io.github.byjoelsamuel.drivesanity.map.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class NavParsingTest {
    @Test
    fun `bearing and offset`() {
        val a = LatLon(40.0, -75.0)
        assertEquals(0.0, io.github.byjoelsamuel.drivesanity.map.Geo.bearingDeg(a, LatLon(40.01, -75.0)), 0.01)
        assertEquals(90.0, io.github.byjoelsamuel.drivesanity.map.Geo.bearingDeg(a, LatLon(40.0, -74.99)), 0.1)
        val moved = io.github.byjoelsamuel.drivesanity.map.Geo.offset(a, 90.0, 100.0)
        assertEquals(100.0, io.github.byjoelsamuel.drivesanity.map.Geo.distanceM(a, moved), 0.5)
    }

    // Trimmed from a real OSRM reply's shape.
    private val route = """
        {"code":"Ok","routes":[{"distance":1520.4,"duration":184.2,
          "geometry":{"type":"LineString","coordinates":[[-75.0,40.0],[-75.0,40.005],[-74.99,40.005]]},
          "legs":[{"steps":[
            {"name":"Main Street","distance":556.0,"duration":60.0,"maneuver":{"type":"depart","modifier":"","location":[-75.0,40.0]}},
            {"name":"Oak Avenue","distance":850.0,"duration":110.0,"maneuver":{"type":"turn","modifier":"right","location":[-75.0,40.005]}},
            {"name":"","ref":"US 1","distance":114.4,"duration":14.2,"maneuver":{"type":"roundabout","modifier":"straight","exit":2,"location":[-74.995,40.005]}},
            {"name":"Oak Avenue","distance":0.0,"duration":0.0,"maneuver":{"type":"arrive","modifier":"right","location":[-74.99,40.005]}}
          ]}]}]}
    """.trimIndent()

    @Test
    fun `reads a route`() {
        val r = Osrm.parseRoute(route)
        assertEquals(3, r.points.size)
        assertEquals(LatLon(40.005, -75.0), r.points[1])
        assertEquals(4, r.steps.size)
        assertEquals(1520.4, r.distanceM, 0.01)
        assertEquals("Head off on Main Street", r.steps[0].text)
        assertEquals("Turn right onto Oak Avenue", r.steps[1].text)
        assertEquals(Arrow.RIGHT, r.steps[1].arrow)
        assertEquals("At the roundabout, take exit 2 onto US 1", r.steps[2].text)
        assertEquals(Arrow.ARRIVE, r.steps[3].arrow)
    }

    @Test(expected = IOException::class)
    fun `no route is an error`() {
        Osrm.parseRoute("""{"code":"NoRoute","message":"Impossible route between points"}""")
    }

    @Test
    fun `instructions for the common manoeuvres`() {
        assertEquals("Bear left onto Elm St", Osrm.instruction("turn", "slight left", "Elm St", 0))
        assertEquals("Keep right", Osrm.instruction("fork", "slight right", "", 0))
        assertEquals("Take the exit onto I-95", Osrm.instruction("off ramp", "right", "I-95", 0))
        assertEquals("Continue onto Pine Rd", Osrm.instruction("new name", "straight", "Pine Rd", 0))
        assertEquals("Make a U-turn", Osrm.instruction("continue", "uturn", "", 0))
    }

    @Test
    fun `reads search results`() {
        val places = Osrm.parsePlaces(
            """[{"display_name":"Springfield, Illinois, USA","lat":"39.7990","lon":"-89.6440"},
                {"display_name":"Springfield, Missouri, USA","lat":"37.2090","lon":"-93.2923"}]"""
        )
        assertEquals(2, places.size)
        assertEquals(LatLon(39.799, -89.644), places[0].at)
    }

    @Test
    fun `speed limit tags`() {
        assertEquals(Limit(35, mph = true), SpeedLimit.parseMaxspeed("35 mph"))
        assertEquals(Limit(50, mph = false), SpeedLimit.parseMaxspeed("50"))
        assertEquals(Limit(100, mph = false), SpeedLimit.parseMaxspeed("100 km/h"))
        assertNull(SpeedLimit.parseMaxspeed("none"))
        assertNull(SpeedLimit.parseMaxspeed("RU:urban"))
        assertEquals(55, Limit(90, mph = false).inMph())
        assertEquals(40, Limit(25, mph = true).inKmh())
    }

    @Test
    fun `matches the road the car is on`() {
        // A north-south avenue at 45 mph crossed by an east-west street at 25 mph.
        val json = """{"elements":[
            {"tags":{"maxspeed":"45 mph"},"geometry":[{"lat":40.000,"lon":-75.000},{"lat":40.010,"lon":-75.000}]},
            {"tags":{"maxspeed":"25 mph"},"geometry":[{"lat":40.005,"lon":-75.005},{"lat":40.005,"lon":-74.995}]},
            {"tags":{"maxspeed":"none"},"geometry":[{"lat":40.0,"lon":-75.1},{"lat":40.1,"lon":-75.1}]}]}"""
        val roads = SpeedLimit.parseRoads(json)
        assertEquals(2, roads.size)
        val junction = LatLon(40.005, -75.00005)
        // Same spot, two headings: the road you're driving along wins.
        assertEquals(Limit(45, mph = true), SpeedLimit.match(roads, junction, 2f))
        assertEquals(Limit(25, mph = true), SpeedLimit.match(roads, junction, 268f))
        // Southbound counts the same as northbound.
        assertEquals(Limit(45, mph = true), SpeedLimit.match(roads, junction, 181f))
        // Far from every mapped road: unknown.
        assertNull(SpeedLimit.match(roads, LatLon(40.02, -75.02), 0f))
    }
}
