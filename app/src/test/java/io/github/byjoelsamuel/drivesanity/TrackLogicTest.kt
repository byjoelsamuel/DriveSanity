package io.github.byjoelsamuel.drivesanity

import io.github.byjoelsamuel.drivesanity.demo.RawEvent
import io.github.byjoelsamuel.drivesanity.demo.RawPlayer
import io.github.byjoelsamuel.drivesanity.obd.CommandGuard
import io.github.byjoelsamuel.drivesanity.track.AccelTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TrackLogicTest {
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
    }
}

/** The built-in demo drive has to do what the demo promises: a standing start. */
class DemoDriveTest {
    private val text = java.io.File("src/main/assets/demo.dsr").readText()
    private val events = RawEvent.parse(text)

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
        time_s,lat,lon,speed_kmh,gps_accuracy_m,heading_deg,peak_brake_g,peak_accel_g,peak_lat_g,rpm,rpm_max,throttle_pct,load_pct,coolant_c,intake_c,fuel_pct,battery_v
        0.0,40.000000,-119.000000,0.0,3,90,0.00,0.00,0.00,750,760,0,19,88,27,63,14.0
        5.0,40.000000,-118.999000,36.0,3,90,0.05,0.42,0.10,3100,5200,40,45,88,27,63,14.0
        10.0,40.000000,-118.998000,72.0,3,90,0.62,0.30,0.55,4200,6100,60,70,88,27,63,14.0
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
        val s = Session(pts)
        assertEquals(72f, s.topKmh, 0f)
        assertEquals(4200f, pts[2].rpm!!, 0f)
        assertEquals(0.62f, s.peakBrakeG, 0f)
        assertEquals(0.42f, s.peakAccelG, 0f)
        assertEquals(0.55f, s.peakLatG, 0f)
        assertEquals(10.0, s.durationS, 0.0)
        // 0.002 degrees of longitude at 40 N is about 170 m.
        assertEquals(170.0, s.distanceM, 8.0)
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
    fun anEmptyFileIsFine() {
        assertEquals(0, Session.parse(emptyList()).size)
        assertEquals(0.0, Session(emptyList()).distanceM, 0.0)
    }
}

class ShareTrimTest {
    private fun line(lonSteps: Int): Session {
        // A straight line east, one point every 50 m, starting at "home".
        val mLon = 111_320.0 * Math.cos(Math.toRadians(40.0))
        val pts = (0..lonSteps).map {
            SessionPoint(it * 5.0, 40.0, -119.0 + it * 50.0 / mLon, 50f, null, null, 0f, 0f, 0f)
        }
        return Session(pts)
    }

    @org.junit.Test
    fun theEndsAreLeftOut() {
        val s = line(40) // 2 km
        val trimmed = s.trimmedPath(250.0)
        org.junit.Assert.assertEquals(s.path.size - 2 * 5 - 1 + 1, trimmed.size + 0) // 250 m is 5 points each end, the 250 m point itself kept
        org.junit.Assert.assertTrue(trimmed.first().t >= 25.0)
        org.junit.Assert.assertTrue(trimmed.last().t <= s.path.last().t - 25.0)
    }

    @org.junit.Test
    fun aShortDriveShowsNoPathAtAll() {
        org.junit.Assert.assertTrue(line(10).trimmedPath(250.0).isEmpty()) // 500 m: the ends would be everything
    }
}

class ShareConfigTest {
    @org.junit.Test
    fun aConfigSurvivesBeingSaved() {
        val c = ShareConfig(setOf(Block.TOP_SPEED, Block.PATH, Block.ZERO_60), 0.75f, PanelStyle.SOLID, Place.BOTTOM)
        val back = ShareConfig.decode(c.encode())
        org.junit.Assert.assertEquals(c, back)
    }

    @org.junit.Test
    fun rubbishFallsBackToTheDefault() {
        org.junit.Assert.assertEquals(ShareConfig(), ShareConfig.decode(null))
        org.junit.Assert.assertEquals(ShareConfig(), ShareConfig.decode("nonsense"))
        org.junit.Assert.assertEquals(ShareConfig(), ShareConfig.decode("A;B;C;D"))
    }

    @org.junit.Test
    fun aWholeGenreSwitchesOnAndOffTogether() {
        val c = ShareConfig().withGenre(Genre.PERFORMANCE, false)
        org.junit.Assert.assertTrue(Block.of(Genre.PERFORMANCE).none { it in c.blocks })
        org.junit.Assert.assertTrue(Block.TOP_SPEED in c.blocks)
        org.junit.Assert.assertTrue(Block.of(Genre.PERFORMANCE).all { it in c.withGenre(Genre.PERFORMANCE, true).blocks })
    }

    @org.junit.Test
    fun everyBlockHasAGenreAndTheGenresCoverThem() {
        org.junit.Assert.assertTrue(Block.entries.all { it.genre in Genre.entries })
        org.junit.Assert.assertEquals(Block.entries.size, Genre.entries.sumOf { Block.of(it).size })
    }

    @org.junit.Test
    fun aCardOnlyOffersWhatThereIsData_For() {
        val s = Session(
            listOf(
                SessionPoint(0.0, 40.0, -119.0, 50f, 3000f, 20f, 0.3f, 0.4f, 0.2f),
                SessionPoint(5.0, 40.0, -118.99, 80f, 5000f, 60f, 0.5f, 0.6f, 0.4f),
            ),
        )
        val d = CardData.ofSession(s, "Drive", "1 JAN", "Car", Units.MPH, hideEnds = false)
        org.junit.Assert.assertTrue(d.has(Block.TOP_SPEED))
        org.junit.Assert.assertTrue(d.has(Block.PATH))
                org.junit.Assert.assertFalse(d.has(Block.ZERO_60))  // no run
        org.junit.Assert.assertFalse(d.has(Block.DRIVES))   // only on a profile
    }

    @org.junit.Test
    fun theProfileAddsDrivesUp() {
        fun drive(km: Double, top: Float) = Session(
            listOf(
                SessionPoint(0.0, 40.0, -119.0, 0f, 2000f, null, 0.2f, 0.3f, 0.1f),
                SessionPoint(20.0, 40.0, -119.0 + km * 1000 / (111_320.0 * Math.cos(Math.toRadians(40.0))), top, 4000f, null, 0.5f, 0.2f, 0.3f),
            ),
        )
        val p = CardData.ofProfile(listOf(drive(10.0, 100f), drive(5.0, 140f)), "Car", Units.KMH, "1 JAN")
        org.junit.Assert.assertEquals(2, p.drives)
        org.junit.Assert.assertEquals(15_000.0, p.distanceM!!, 200.0)
        org.junit.Assert.assertEquals(40.0, p.durationS!!, 0.0)
        org.junit.Assert.assertEquals(140f, p.topKmh!!, 0f)
        org.junit.Assert.assertEquals(0.5f, p.brakeG!!, 0f)
        org.junit.Assert.assertTrue(p.has(Block.DRIVES))
        org.junit.Assert.assertFalse(p.has(Block.PATH)) // a profile is many drives, so there's no one path
    }
}
