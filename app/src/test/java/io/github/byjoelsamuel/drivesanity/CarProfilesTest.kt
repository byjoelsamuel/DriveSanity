package io.github.byjoelsamuel.drivesanity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CarProfilesTest {
    @Test
    fun `ids are unique and unknown ids fall back to the default`() {
        assertEquals(CarProfiles.all.size, CarProfiles.all.map { it.id }.toSet().size)
        assertSame(CarProfiles.MAZDA3, CarProfiles.default)
        assertSame(CarProfiles.MAZDA3, CarProfiles.byId("nope"))
        assertSame(CarProfiles.MAZDA3, CarProfiles.byId(null))
        assertSame(CarProfiles.AMAZE_CVT, CarProfiles.byId("amaze-cvt"))
        assertEquals(CarProfiles.DEFAULT_ID, CarProfiles.default.id)
    }

    @Test
    fun `only the mazda has a known gear table and the cvt has no gears at all`() {
        assertEquals(6, CarProfiles.MAZDA3.gears!!.kmhPer1000Rpm.size)
        for (p in listOf(CarProfiles.ALCAZAR_PETROL, CarProfiles.ALCAZAR_DIESEL, CarProfiles.AMAZE, CarProfiles.AMAZE_CVT)) {
            assertNull(p.name, p.gears)
        }
        assertFalse(CarProfiles.AMAZE_CVT.hasGears)
        assertTrue(CarProfiles.AMAZE.hasGears && CarProfiles.ALCAZAR_PETROL.hasGears && CarProfiles.ALCAZAR_DIESEL.hasGears)
    }

    @Test
    fun `rev scale follows the redline`() {
        assertEquals(6300f to 8000f, CarProfiles.MAZDA3.revScale())
        assertEquals(4800f to 6000f, CarProfiles.ALCAZAR_DIESEL.revScale())
        assertEquals(6000f to 7000f, CarProfiles.ALCAZAR_PETROL.revScale())
        // Moving the redline up past the scale makes room for it.
        assertEquals(7000f to 8000f, CarProfiles.MAZDA3.revScale(7000f))
        assertEquals(7800f to 9000f, CarProfiles.MAZDA3.revScale(7800f))
    }

    @Test
    fun `the redline checked flag matches what was verified`() {
        assertTrue(CarProfiles.MAZDA3.redlineChecked)
        assertTrue(CarProfiles.all.filter { it !== CarProfiles.MAZDA3 }.none { it.redlineChecked })
    }

    @Test
    fun `gear text`() {
        val gear = Snapshot(null, null, null, emptyList(), false, false, false, false, gear = 4)
        assertEquals("4", gear.gearText)
        assertEquals("–", gear.copy(gear = null).gearText)
        assertEquals("CVT", gear.copy(gearless = true).gearText)
    }

    @Test
    fun `only the diesel is excluded from the fuel economy estimate`() {
        assertFalse(CarProfiles.ALCAZAR_DIESEL.petrol)
        assertTrue(CarProfiles.all.filter { it !== CarProfiles.ALCAZAR_DIESEL }.all { it.petrol })
    }

    @Test
    fun `the screen switcher goes round all five screens`() {
        assertEquals(5, Prefs.Layout.entries.size)
        assertEquals(Prefs.Layout.SPORT, Prefs.Layout.DIAG.next)
        assertEquals(Prefs.Layout.GAUGES, Prefs.Layout.SPORT.next)
    }
}
