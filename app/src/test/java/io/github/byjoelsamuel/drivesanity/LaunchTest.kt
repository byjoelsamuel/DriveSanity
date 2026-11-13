package io.github.byjoelsamuel.drivesanity

import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.track.Launch
import io.github.byjoelsamuel.drivesanity.track.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The 0–60 screen's logic: what it says when, the optional 3-2-1, and a jump start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchTest {
    private val looper get() = shadowOf(android.os.Looper.getMainLooper())

    @Before
    fun clean() {
        Launch.reset()
        looper.idleFor(Duration.ofSeconds(60)) // past any earlier test's countdown
        Launch.reset()
    }

    private fun speed(mps: Float) = Launch.onSpeed(SystemClock.elapsedRealtime(), mps)

    @Test
    fun movingMeansCantArmUntilStopped() {
        speed(15f)
        assertEquals(Phase.MOVING, Launch.view().phase)
        looper.idleFor(Duration.ofMillis(200)); speed(0f)
        assertEquals(Phase.READY, Launch.view().phase)
    }

    @Test
    fun theCountdownGoesThreeTwoOneGo() {
        speed(0f)
        Launch.startCountdown()
        assertEquals(Phase.COUNTDOWN, Launch.view().phase)
        assertEquals(3, Launch.view().count)
        looper.idleFor(Duration.ofMillis(1100)); assertEquals(2, Launch.view().count)
        looper.idleFor(Duration.ofMillis(1000)); assertEquals(1, Launch.view().count)
        looper.idleFor(Duration.ofMillis(1000)); assertEquals(Phase.GO, Launch.view().phase)
        looper.idleFor(Duration.ofMillis(1200)); assertEquals(Phase.READY, Launch.view().phase) // it's over; the car decides when the clock starts
    }

    @Test
    fun theCountdownWontStartWhileMoving() {
        speed(12f)
        Launch.startCountdown()
        assertTrue(!Launch.countingDown)
    }

    @Test
    fun movingBeforeTheGoIsTooEarly() {
        speed(0f)
        Launch.startCountdown()
        looper.idleFor(Duration.ofMillis(1500))
        speed(3f)
        assertEquals(Phase.TOO_EARLY, Launch.view().phase)
        assertTrue(!Launch.countingDown)
        looper.idleFor(Duration.ofMillis(2600))
        assertTrue(Launch.view().phase != Phase.TOO_EARLY)
    }

    @Test
    fun aRunIsTimedFromTheCarMovingOffAndKeptAfterwards() {
        speed(0f)
        // 4.5 m/s squared for eight seconds, a reading every tenth.
        var v = 0f
        repeat(80) {
            looper.idleFor(Duration.ofMillis(100))
            v += 0.45f
            speed(v)
            if (it == 20) assertEquals(Phase.RUNNING, Launch.view().phase)
        }
        // Lift off and stop.
        repeat(40) { looper.idleFor(Duration.ofMillis(100)); v = (v - 1.2f).coerceAtLeast(0f); speed(v) }
        val r = Launch.view()
        assertNotNull(r.to60Ms)
        assertTrue("0-60 took ${r.to60Ms}", r.to60Ms!! in 5_000..7_000)
        assertNotNull(r.to100Ms)
        assertEquals(Phase.READY, r.phase) // stopped again, with the result still showing
        Launch.reset()
        assertNull(Launch.view().to60Ms)
    }
}
