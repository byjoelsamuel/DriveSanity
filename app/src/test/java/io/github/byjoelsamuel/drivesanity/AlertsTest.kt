package io.github.byjoelsamuel.drivesanity

import io.github.byjoelsamuel.drivesanity.nav.Limit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    private val calm = Snapshot(20f, 4f, null, emptyList(), true, false, false, true,
        rpm = 2000f, coolantC = 90f, volts = 14.1f, fuelPct = 60f, tires = listOf(36f, 36f, 35f, 35f))

    @Test
    fun `nothing to say when all is well`() {
        assertTrue(Alerts.active(calm, Units.MPH, Limit(50, true)).isEmpty())
    }

    @Test
    fun `hot engine, low battery, low tire, low fuel and speeding, in that order`() {
        val bad = calm.copy(coolantC = 112f, volts = 11.4f, tires = listOf(36f, 24f, 35f, 35f), fuelPct = 9f)
        val texts = Alerts.active(bad, Units.MPH, Limit(25, true)).map { it.text }
        assertEquals(
            listOf("ENGINE HOT  234°F", "LOW BATTERY  11.4 V", "LOW TIRE FR  24 PSI", "LOW FUEL  9%", "OVER LIMIT  25"),
            texts,
        )
        assertTrue(Alerts.active(bad, Units.MPH, null).first().severe)
    }

    @Test
    fun `battery is judged against the engine running`() {
        // 12.2 V with the engine off is normal; with the engine running it means the alternator isn't charging.
        assertTrue(Alerts.active(calm.copy(rpm = null, volts = 12.2f), Units.MPH, null).isEmpty())
        assertEquals(1, Alerts.active(calm.copy(rpm = 1500f, volts = 12.2f), Units.MPH, null).size)
    }

    @Test
    fun `speeding has some room, in either unit`() {
        // 20 m/s is 44.7 mph, 72 km/h.
        assertTrue(Alerts.active(calm, Units.MPH, Limit(40, true)).isEmpty())   // 4.7 over
        assertEquals(1, Alerts.active(calm, Units.MPH, Limit(35, true)).size)    // 9.7 over
        assertTrue(Alerts.active(calm, Units.KMH, Limit(65, false)).isEmpty())   // 7 over
        assertEquals(1, Alerts.active(calm, Units.KMH, Limit(60, false)).size)   // 12 over
    }

    @Test
    fun `shift light levels follow the redline`() {
        assertEquals(0, Alerts.shiftLevel(null, 6300f))
        assertEquals(0, Alerts.shiftLevel(5000f, 6300f))
        assertEquals(1, Alerts.shiftLevel(5900f, 6300f))
        assertEquals(2, Alerts.shiftLevel(6250f, 6300f))
        assertEquals(0, Alerts.shiftLevel(5000f, 4800f).coerceAtMost(0))
        assertEquals(2, Alerts.shiftLevel(4800f, 4800f))
    }
}
