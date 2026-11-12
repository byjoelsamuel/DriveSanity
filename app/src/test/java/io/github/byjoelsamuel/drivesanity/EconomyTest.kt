package io.github.byjoelsamuel.drivesanity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    @Test
    fun `air flow becomes fuel use`() {
        val e = Economy()
        // 10 g/s of air at the ideal ratio is 0.68 g/s of petrol, about 3.3 litres an hour.
        e.update(0, 10f, 1f, 20f)
        assertEquals(3.29f, e.litresPerHour!!, 0.02f)
        // At 20 m/s (72 km/h) that's 4.6 litres per 100 km.
        assertEquals(4.57f, e.litresPer100Km!!, 0.03f)
    }

    @Test
    fun `trip average builds up over time`() {
        val e = Economy()
        for (i in 0..60) e.update(i * 1000L, 10f, 1f, 20f)
        assertEquals(4.57f, e.tripLitresPer100Km!!, 0.05f)
        assertEquals(0.0548, e.litres, 0.002)
        e.reset()
        assertNull(e.tripLitresPer100Km)
    }

    @Test
    fun `a richer mix burns more, coasting burns none, standing still has no per 100 km`() {
        val rich = Economy().apply { update(0, 10f, 0.85f, 20f) }
        assertTrue(rich.litresPerHour!! > 3.29f)
        val coasting = Economy().apply { update(0, 6f, 2.2f, 20f) }
        assertEquals(0f, coasting.litresPerHour!!, 0.001f)
        val idle = Economy().apply { update(0, 2.5f, 1f, 0f) }
        assertNull(idle.litresPer100Km)
        assertTrue(idle.litresPerHour!! > 0f)
    }

    @Test
    fun `no air flow reading means no estimate`() {
        val e = Economy().apply { update(0, 10f, 1f, 20f) }
        e.update(1000, null, 1f, 20f)
        assertNull(e.litresPerHour)
        assertNull(e.litresPer100Km)
    }

    @Test
    fun `reading it in miles per gallon or litres per 100 km`() {
        assertEquals(51.5f, Units.MPH.economy(4.57f), 0.1f)
        assertEquals("51 MPG", Units.MPH.economyText(4.57f, null))
        assertEquals("4.6 L/100", Units.KMH.economyText(4.57f, null))
        assertEquals("3.3 L/h", Units.KMH.economyText(null, 3.29f))
        assertEquals("0.87 gal/h", Units.MPH.economyText(null, 3.29f))
        assertEquals("–", Units.MPH.economyText(null, null))
    }
}
