package io.github.byjoelsamuel.drivesanity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingNumberTest {
    private val sec = 1_000_000_000L

    @Test
    fun `counts up smoothly instead of jumping`() {
        val r = RollingNumber()
        assertNull(r.update(null, 0))
        assertEquals(30f, r.update(30f, sec)!!, 0.001f) // first reading shows straight away
        var t = sec
        repeat(2) { t += 100_000_000L; r.update(60f, t) }
        assertTrue("part way after 0.2 s: ${r.shown}", r.shown > 31f && r.shown < 55f)
        assertTrue(r.rate > 10f)
        repeat(40) { t += 100_000_000L; r.update(60f, t) }
        assertEquals(60f, r.shown, 0.05f)
        assertEquals(0f, r.rate, 1f)
    }

    @Test
    fun `counts down and resets without a reading`() {
        val r = RollingNumber()
        r.update(60f, sec)
        r.update(40f, 2 * sec)
        assertTrue(r.shown in 40f..60f && r.rate < 0f)
        assertNull(r.update(null, 3 * sec))
        assertEquals(10f, r.update(10f, 4 * sec)!!, 0.001f)
    }
}
