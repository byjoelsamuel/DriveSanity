package io.github.byjoelsamuel.drivesanity

import io.github.byjoelsamuel.drivesanity.demo.RawEvent
import io.github.byjoelsamuel.drivesanity.demo.RawPlayer
import io.github.byjoelsamuel.drivesanity.obd.CommandGuard
import io.github.byjoelsamuel.drivesanity.track.AccelTimer
import io.github.byjoelsamuel.drivesanity.track.FinishLine
import io.github.byjoelsamuel.drivesanity.track.LapTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TrackLogicTest {
    // A circle of 100 m radius, driven clockwise from the north, with the line on the north point
    // facing east (heading 90).
    private val lat0 = 40.0
    private val lon0 = -119.0
    private val mLat = 111_320.0
    private val mLon = 111_320.0 * cos(Math.toRadians(lat0))
    private val line = FinishLine("Test", lat0 + 100 / mLat, lon0, 90.0)

    private fun at(angleDeg: Double): Pair<Double, Double> {
        val a = Math.toRadians(angleDeg)
        return (lat0 + 100 * cos(a) / mLat) to (lon0 + 100 * sin(a) / mLon)
    }

    /** Drives [laps] laps at a constant [mps], a fix a second, starting a bit before the line. */
    private fun drive(t: LapTimer, laps: Int, mps: Double, startMs: Long = 0, fixEveryMs: Long = 1000): Long {
        val perDeg = 2 * Math.PI * 100 / 360.0
        var angle = -20.0
        var now = startMs
        val end = 360.0 * laps + 20
        while (angle < end) {
            val (la, lo) = at(angle)
            t.onFix(now, la, lo, mps.toFloat())
            angle += mps * (fixEveryMs / 1000.0) / perDeg
            now += fixEveryMs
        }
        return now
    }

    @Test
    fun timesLapsBetweenFixes() {
        val t = LapTimer(line)
        drive(t, laps = 3, mps = 20.0)
        assertEquals(3, t.laps.size)
        val expected = (2 * Math.PI * 100 / 20.0 * 1000).toLong() // 31,416 ms
        for (lap in t.laps) assertEquals(expected.toDouble(), lap.ms.toDouble(), 60.0) // far better than the one-second spacing
        assertEquals(4, t.lapNumber)
    }

    @Test
    fun firstCrossingOnlyStartsTheClock() {
        val t = LapTimer(line)
        drive(t, laps = 1, mps = 20.0)
        assertEquals(1, t.laps.size)
        assertNotNull(t.currentMs(40_000))
    }

    @Test
    fun crossingTheWrongWayDoesNotCount() {
        val t = LapTimer(line)
        // Anticlockwise past the line.
        var a = 20.0
        var now = 0L
        while (a > -20.0) {
            val (la, lo) = at(a)
            t.onFix(now, la, lo, 15f)
            a -= 5.0
            now += 1000
        }
        assertEquals(0, t.lapNumber)
    }

    @Test
    fun deltaAgainstTheBestLap() {
        val t = LapTimer(line)
        var now = drive(t, laps = 1, mps = 25.0)             // out lap + one lap at 25 m/s (best)
        assertNull(t.deltaMs.takeIf { t.laps.isEmpty() })
        // A slower lap: partway round it should read behind.
        val perDeg = 2 * Math.PI * 100 / 360.0
        var a = 20.0
        now += 0
        val start = now
        var behind: Long? = null
        while (a < 200.0) {
            val (la, lo) = at(a)
            t.onFix(now, la, lo, 18f)
            a += 18.0 / perDeg
            now += 1000
            behind = t.deltaMs
        }
        assertTrue("delta should be positive when slower, was $behind", (behind ?: 0) > 500)
        assertNotNull(start)
    }

    @Test
    fun aBounceOverTheLineIsIgnored() {
        val t = LapTimer(line, minLapMs = 15_000)
        val end = drive(t, laps = 1, mps = 20.0)
        val before = t.laps.size
        // Back over the line the wrong way and forward again within a couple of seconds.
        val (la1, lo1) = at(-3.0)
        val (la2, lo2) = at(3.0)
        t.onFix(end + 1_000, la1, lo1, 5f)
        t.onFix(end + 2_000, la2, lo2, 5f)
        assertEquals(before, t.laps.size)
    }

    @Test
    fun zeroToSixtyAndTheQuarterMile() {
        val a = AccelTimer()
        a.onSpeed(0, 0f)
        // Constant 4.5 m/s²: 60 mph (26.82 m/s) at 5.96 s; a quarter mile (402 m) at 13.37 s.
        var t = 0L
        while (t <= 16_000) {
            a.onSpeed(t, (4.5 * t / 1000.0).toFloat().coerceAtMost(80f))
            t += 100
        }
        val run = a.liveRun()
        assertNotNull(run)
        assertEquals(5960.0, run!!.to60Ms!!.toDouble(), 80.0)
        assertEquals(6173.0, run.to100kmhMs!!.toDouble(), 80.0)
        assertEquals(13_370.0, run.quarterMs!!.toDouble(), 120.0)
        // Stopping ends the run and keeps the result.
        a.onSpeed(t, 0f)
        assertNotNull(a.result)
        assertEquals(AccelTimer.State.WAITING, a.state)
    }

    @Test
    fun aRollingStartIsNotTimed() {
        val a = AccelTimer()
        a.onSpeed(0, 8f) // never stood still first
        a.onSpeed(100, 12f)
        assertEquals(AccelTimer.State.WAITING, a.state)
    }

    @Test
    fun rawLinesRoundTrip() {
        val events = listOf(
            RawEvent.Fix(1000, 40.123456, -119.654321, 12.5f, 90f, 3f),
            RawEvent.G(1100, 0.31f, -0.12f),
            RawEvent.Obd(1200, "rpm", 3250f),
            RawEvent.Obd(1300, "coolant", 88.5f),
        )
        val back = RawEvent.parse(events.joinToString("\n") { it.line() } + "\n# a comment\nnonsense\n")
        assertEquals(events, back)
    }

    @Test
    fun playerHandsOutEventsOnceAndLoops() {
        val events = listOf(RawEvent.Obd(0, "rpm", 1f), RawEvent.Obd(500, "rpm", 2f), RawEvent.Obd(1000, "rpm", 3f))
        val p = RawPlayer(events, pauseMs = 1000)
        var restarts = 0
        assertEquals(1, p.due(0) { restarts++ }.size)
        assertEquals(1, p.due(600) { restarts++ }.size)
        assertEquals(1, p.due(1000) { restarts++ }.size)
        assertEquals(0, p.due(1500) { restarts++ }.size)
        // The clip is 1 s plus a 1 s pause; at 2.1 s it starts again.
        assertEquals(1, p.due(2100) { restarts++ }.size)
        assertEquals(1, restarts)
    }

    @Test
    fun theGuardOnlyLetsReadsThrough() {
        for (ok in listOf("ATZ", "ATE0", "ATSP0", "ATRV", "0100", "010C", "03", "07", "0A", "0902", "0101")) {
            assertTrue(ok, CommandGuard.allowed(ok, advanced = false))
        }
        for (bad in listOf("04", "08", "10", "11", "14", "2E", "2F", "27", "3101", "ATLP", "ATCRA", "2261B1", "ATSH720")) {
            assertFalse(bad, CommandGuard.allowed(bad, advanced = false))
        }
        assertTrue(CommandGuard.allowed("2261B1", advanced = true))
        assertTrue(CommandGuard.allowed("ATSH720", advanced = true))
        // Even with advanced on, nothing that changes anything gets through.
        for (bad in listOf("04", "2E1234", "2F", "3101", "27", "10")) assertFalse(bad, CommandGuard.allowed(bad, advanced = true))
    }

    @Test
    fun loggingIsSlowUntilSomethingHappens() {
        val p = LogPolicy()
        assertTrue(p.due(0, 50f, 90f, 0.1f))               // the first row
        assertFalse(p.due(1_000, 52f, 91f, 0.1f))          // nothing changed
        assertFalse(p.due(4_000, 50f, 90f, 0.1f))
        assertTrue(p.due(5_100, 50f, 90f, 0.1f))           // five seconds
        assertTrue(p.due(6_200, 70f, 90f, 0.1f))           // speed jumped 20 km/h
        assertTrue(p.due(7_300, 70f, 120f, 0.1f))          // turned 30 degrees
        assertTrue(p.due(8_400, 70f, 120f, 0.5f))          // hard braking
        assertFalse(p.due(8_900, 70f, 120f, 0.6f))         // but not more than once a second
        p.fast = true
        assertTrue(p.due(9_500, 70f, 120f, 0.0f))          // lap timing: every second
    }
}

/** The built-in demo drive has to do what the demo promises: three timed laps and a standing start. */
class DemoDriveTest {
    private val text = java.io.File("src/main/assets/demo.dsr").readText()
    private val events = RawEvent.parse(text)

    @Test
    fun hasAStartLineAndThreeLaps() {
        val l = text.lineSequence().first { it.startsWith("# line,") }.split(',')
        val line = FinishLine(l[1], l[2].toDouble(), l[3].toDouble(), l[4].toDouble())
        val timer = LapTimer(line)
        for (e in events.filterIsInstance<RawEvent.Fix>()) timer.onFix(e.t, e.lat, e.lon, e.speedMps)
        assertEquals(3, timer.laps.size)
        for (lap in timer.laps) assertTrue("lap ${lap.ms}", lap.ms in 45_000..60_000)
        assertTrue("laps should differ, so the delta has something to show", timer.laps.map { it.ms }.distinct().size == 3)
    }

    @Test
    fun hasAStandingStartWorthTiming() {
        val a = AccelTimer()
        for (e in events.filterIsInstance<RawEvent.Obd>().filter { it.name == "speed" }) a.onSpeed(e.t, e.value / 3.6f)
        val run = a.result ?: a.liveRun()
        assertNotNull(run)
        assertTrue("0-100 took ${run!!.to100kmhMs}", run.to100kmhMs in 5_500L..9_000L)
    }

    @Test
    fun staysUnderTheRedline() {
        val max = events.filterIsInstance<RawEvent.Obd>().filter { it.name == "rpm" }.maxOf { it.value }
        assertTrue("peak rpm $max", max <= 6600f)
    }
}

class SessionTest {
    private val newCsv = """
        time_s,lat,lon,speed_kmh,gps_accuracy_m,heading_deg,peak_brake_g,peak_accel_g,peak_lat_g,rpm,rpm_max,throttle_pct,load_pct,coolant_c,intake_c,fuel_pct,battery_v,lap
        0.0,40.000000,-119.000000,0.0,3,90,0.00,0.00,0.00,750,760,0,19,88,27,63,14.0,
        5.0,40.000000,-118.999000,36.0,3,90,0.05,0.42,0.10,3100,5200,40,45,88,27,63,14.0,1
        10.0,40.000000,-118.998000,72.0,3,90,0.62,0.30,0.55,4200,6100,60,70,88,27,63,14.0,1
    """.trimIndent().lines()

    private val oldCsv = """
        time_s,lat,lon,gps_speed_kmh,gps_accuracy_m,gps_accel_g,long_g,lat_g,brake_g,brake_level,rpm,obd_speed_kmh,throttle_pct,load_pct,coolant_c,intake_c,fuel_pct,battery_v
        0.00,40.0,-119.0,0.0,3,0.0,0.0,0.0,0.0,NONE,750,0,12.0,19.0,88,27,,14.0
        0.10,40.0,-118.999,36.0,3,0.1,0.30,-0.20,0.0,NONE,3100,36,40.0,45.0,88,27,,14.0
    """.trimIndent().lines()

    @Test
    fun readsTheCurrentFormat() {
        val pts = Session.parse(newCsv)
        assertEquals(3, pts.size)
        val s = Session(pts, emptyList())
        assertEquals(72f, s.topKmh, 0f)
        assertEquals(4200f, pts[2].rpm!!, 0f)
        assertEquals(0.62f, s.peakBrakeG, 0f)
        assertEquals(0.42f, s.peakAccelG, 0f)
        assertEquals(0.55f, s.peakLatG, 0f)
        assertEquals(10.0, s.durationS, 0.0)
        // 0.002 degrees of longitude at 40 N is about 170 m.
        assertEquals(170.0, s.distanceM, 8.0)
        assertEquals(1, pts[1].lap)
        assertEquals(null, pts[0].lap)
    }

    @Test
    fun stillReadsTheOlderFormat() {
        val pts = Session.parse(oldCsv)
        assertEquals(2, pts.size)
        assertEquals(36f, pts[1].kmh!!, 0f)
        assertEquals(0.30f, pts[1].accelG, 0f)
        assertEquals(0.20f, pts[1].latG, 0f)
    }

    @Test
    fun readsLaps() {
        val laps = Session.parseLaps(listOf("Demo loop,1,52600,96", "Demo loop,2,51480,101", "junk"))
        assertEquals(2, laps.size)
        assertEquals(51480L, Session(emptyList(), laps).bestLap!!.ms)
    }

    @Test
    fun anEmptyFileIsFine() {
        assertEquals(0, Session.parse(emptyList()).size)
        assertEquals(0.0, Session(emptyList(), emptyList()).distanceM, 0.0)
    }
}
