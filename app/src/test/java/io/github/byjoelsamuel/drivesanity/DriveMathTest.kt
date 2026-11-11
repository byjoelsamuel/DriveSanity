package io.github.byjoelsamuel.drivesanity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMathTest {
    private val eps = 0.01f

    @Test
    fun `dash mount in portrait - braking reads as braking`() {
        // Upright, screen facing the driver: gravity's reaction is along +y.
        val cal = Calibration.fromRest(Vec3(0f, G, 0f))!!
        // Braking at 0.5 g pushes the reading toward the driver, which is +z (out of the screen).
        val g = cal.toCar(Vec3(0f, G, 0.5f * G))
        assertEquals(-0.5f, g.lon, eps)
        assertEquals(0f, g.lat, eps)
        assertEquals(0.5f, g.brake, eps)
        assertEquals(BrakeLevel.HARD, BrakeLevel.of(g.brake))
    }

    @Test
    fun `dash mount - turning right reads as right`() {
        val cal = Calibration.fromRest(Vec3(0f, G, 0f))!!
        // Turning right the car accelerates to the right, the phone's +x.
        val g = cal.toCar(Vec3(0.3f * G, G, 0f))
        assertEquals(0.3f, g.lat, eps)
        assertEquals(0f, g.lon, eps)
    }

    @Test
    fun `flat on the console with the top pointing forward`() {
        val cal = Calibration.fromRest(Vec3(0f, 0f, G))!!
        // Speeding up at 0.3 g: the car accelerates toward the top of the phone, +y.
        val g = cal.toCar(Vec3(0f, 0.3f * G, G))
        assertEquals(0.3f, g.lon, eps)
        assertEquals(0f, g.brake, eps)
    }

    @Test
    fun `tilted mount still finds forward`() {
        // Leaning back 30 degrees from upright.
        val rest = Vec3(0f, G * 0.866f, G * 0.5f)
        val cal = Calibration.fromRest(rest)!!
        assertEquals(0f, cal.forward dot rest.normalized(), eps) // forward is level
        assertTrue(cal.forward.z < 0f)                           // and out of the back
    }

    @Test
    fun `refuses to calibrate from a reading that isn't gravity`() {
        assertNull(Calibration.fromRest(Vec3(0f, G * 1.4f, 0f)))
        assertNotNull(Calibration.fromRest(Vec3(0f, G, 0f)))
    }

    @Test
    fun `steady check rejects a shaky phone`() {
        val still = List(50) { Vec3(0.01f * (it % 3), G, 0f) }
        val shaky = List(50) { Vec3(if (it % 2 == 0) 1f else -1f, G, 0f) }
        assertTrue(Calibration.isSteady(still))
        assertFalse(Calibration.isSteady(shaky))
    }

    @Test
    fun `brake levels`() {
        assertEquals(BrakeLevel.NONE, BrakeLevel.of(0.05f))
        assertEquals(BrakeLevel.LIGHT, BrakeLevel.of(0.15f))
        assertEquals(BrakeLevel.MODERATE, BrakeLevel.of(0.3f))
        assertEquals(BrakeLevel.HARD, BrakeLevel.of(0.45f))
        assertEquals(BrakeLevel.VERY_HARD, BrakeLevel.of(0.9f))
    }

    @Test
    fun `one long hard stop counts once`() {
        val c = EventCounter(trigger = 0.4f, rearm = 0.2f)
        listOf(0.1f, 0.45f, 0.5f, 0.38f, 0.42f, 0.1f, 0.5f).forEach { c.feed(it) }
        assertEquals(2, c.count)
    }

    @Test
    fun `smoothing settles on the input`() {
        val lp = LowPass3(tauSec = 0.2f)
        var out = Vec3.ZERO
        repeat(100) { out = lp.feed(Vec3(1f, 0f, 0f), 0.02f) }
        assertEquals(1f, out.x, eps)
    }

    @Test
    fun `rpm bar stays white low, yellow near the top, red from the redline`() {
        assertEquals(0f, Revs.heat(2000f), eps)
        assertEquals(0f, Revs.heat(3400f), eps)
        assertTrue(Revs.heat(4500f) in 0.1f..0.9f)   // on its way to yellow
        assertEquals(1f, Revs.heat(5500f), 0.05f)     // yellow
        assertTrue(Revs.heat(6000f) in 1.1f..1.9f)   // on its way to red
        assertEquals(2f, Revs.heat(6300f), eps)
        assertEquals(2f, Revs.heat(8000f), eps)
    }
}
