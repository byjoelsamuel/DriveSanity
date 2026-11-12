package io.github.byjoelsamuel.drivesanity

import android.os.Looper
import io.github.byjoelsamuel.drivesanity.demo.Demo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoFlowTest {
    @Test
    fun theDemoDrivesTheWholeApp() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(Demo.start(ctx))
        val looper = shadowOf(Looper.getMainLooper())
        var sawSpeed = false
        var maxLap = 0
        var bestSeen: Long? = null
        var deltaSeen = false
        var run: Boolean? = null
        repeat(190) { // three minutes of drive, a second at a time
            looper.idleFor(Duration.ofSeconds(1))
            val s = DriveEngine.snapshot()
            if ((s.speedMps ?: 0f) > 5f) sawSpeed = true
            s.lap?.let { l ->
                maxLap = maxOf(maxLap, l.number)
                l.bestMs?.let { bestSeen = it }
                if (l.deltaMs != null) deltaSeen = true
            }
            if (s.run?.to100Ms != null) run = true
        }
        val s = DriveEngine.snapshot()
        assertTrue("demo flag", s.demo)
        assertTrue("obd should look live", s.obdLive)
        assertTrue("saw it move", sawSpeed)
        assertTrue("lap number reached $maxLap", maxLap >= 3)
        assertNotNull(bestSeen)
        assertTrue("best lap $bestSeen", bestSeen!! in 45_000..60_000)
        assertTrue("delta against the best lap", deltaSeen)
        assertEquals("0-100 timed", true, run)
        Demo.stop()
        assertTrue(!DriveEngine.snapshot().demo)
    }
}
