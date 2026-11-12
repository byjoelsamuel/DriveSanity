package io.github.byjoelsamuel.drivesanity

import io.github.byjoelsamuel.drivesanity.obd.Obd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmootherTest {
    private fun Smoother.step(ms: Long, now: Long): Long {
        frame(now + ms * 1_000_000)
        return now + ms * 1_000_000
    }

    @Test
    fun firstReadingAppearsStraightAway() {
        val s = Smoother()
        s.frame(1_000_000_000)
        assertEquals(750f, s.ease("rpm", 750f, 0.07f)!!, 0f)
    }

    @Test
    fun movesTowardsTheTargetWithoutOvershooting() {
        val s = Smoother()
        var t = 1_000_000_000L
        s.frame(t)
        s.ease("rpm", 1000f, 0.1f)
        var last = 1000f
        repeat(20) {
            t = s.step(33, t)
            val v = s.ease("rpm", 3000f, 0.1f)!!
            assertTrue(v >= last && v <= 3000f)
            last = v
        }
        assertTrue(last > 2900f)
    }

    @Test
    fun takesTheSameTimeAtAnyFrameRate() {
        fun after(frameMs: Long): Float {
            val s = Smoother()
            var t = 1_000_000_000L
            s.frame(t)
            s.ease("v", 0f, 0.2f)
            var elapsed = 0L
            var v = 0f
            while (elapsed < 400) {
                t = s.step(frameMs, t)
                elapsed += frameMs
                v = s.ease("v", 100f, 0.2f)!!
            }
            return v
        }
        assertEquals(after(16), after(40), 2f)
    }

    @Test
    fun noReadingForgetsTheValue() {
        val s = Smoother()
        s.frame(1_000_000_000)
        s.ease("x", 10f, 0.1f)
        assertNull(s.ease("x", null, 0.1f))
        s.frame(1_033_000_000)
        assertEquals(500f, s.ease("x", 500f, 0.1f)!!, 0f) // starts there, doesn't slide in from 10
    }

    @Test
    fun valveRescaleMakesIdleZeroAndOpenHundred() {
        val r = Obd.Rescale()
        assertEquals(0f, r.scale(12f), 0f) // nothing seen yet
        r.see(12f)
        r.see(14f)
        assertEquals(0f, r.scale(12f), 0.01f)
        assertTrue(r.scale(14f) < 10f) // a small movement isn't blown up to full
        r.see(70f)
        assertEquals(100f, r.scale(70f), 0.5f)
        assertEquals(50f, r.scale(12f + (70f - 12f) / 2), 1f)
    }
}
