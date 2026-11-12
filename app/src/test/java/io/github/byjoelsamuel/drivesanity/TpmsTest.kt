package io.github.byjoelsamuel.drivesanity

import org.junit.Assert.assertEquals
import org.junit.Test

class TpmsTest {
    @Test
    fun `units and text`() {
        assertEquals("36", Tpms.text(36.2f, Units.MPH))
        assertEquals("248", Tpms.text(36f, Units.KMH))
        assertEquals("–", Tpms.text(null, Units.MPH))
        assertEquals("", Tpms.digits(null, Units.KMH))
        assertEquals("PSI", Tpms.unit(Units.MPH))
        assertEquals("kPa", Tpms.unit(Units.KMH))
    }

    @Test
    fun `levels`() {
        assertEquals(0, Tpms.level(36f))
        assertEquals(1, Tpms.level(30f))
        assertEquals(1, Tpms.level(46f))
        assertEquals(2, Tpms.level(25f))
        assertEquals(0, Tpms.level(null))
    }
}
