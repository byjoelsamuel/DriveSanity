package io.github.byjoelsamuel.drivesanity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The car-side record and the "Car" lines in Phone check that read from it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarHealthTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clean() {
        ctx.getSharedPreferences("car_health", Context.MODE_PRIVATE).edit().clear().commit()
        CarHealth.clearError(ctx)
    }

    @Test
    fun agoReadsInPlainWords() {
        val now = 10_000_000_000L
        assertEquals("just now", CarHealth.ago(now - 20_000, now))
        assertEquals("1 minute ago", CarHealth.ago(now - 60_000, now))
        assertEquals("5 minutes ago", CarHealth.ago(now - 5 * 60_000, now))
        assertEquals("1 hour ago", CarHealth.ago(now - 3_600_000, now))
        assertEquals("3 hours ago", CarHealth.ago(now - 3 * 3_600_000, now))
        assertEquals("2 days ago", CarHealth.ago(now - 2 * 86_400_000, now))
        assertEquals("just now", CarHealth.ago(now + 5_000, now)) // a clock that moved back
    }

    @Test
    fun sessionsAreCounted() {
        assertEquals(0, CarHealth.read(ctx).sessions)
        CarHealth.sessionStarted(ctx, now = 1_000)
        CarHealth.sessionStarted(ctx, now = 2_000)
        val h = CarHealth.read(ctx)
        assertEquals(2, h.sessions)
        assertEquals(2_000L, h.lastSessionMs)
    }

    @Test
    fun theSameErrorRepeatingIsSavedOnceThenAgainAfterTenSeconds() {
        CarHealth.error(ctx, "Map screen", IllegalStateException("boom"), now = 1_000)
        assertEquals(1_000L, CarHealth.read(ctx).lastErrorMs)
        CarHealth.error(ctx, "Map screen", IllegalStateException("boom"), now = 1_033) // the next frame
        assertEquals(1_000L, CarHealth.read(ctx).lastErrorMs)
        CarHealth.error(ctx, "Map screen", IllegalStateException("boom"), now = 12_000)
        assertEquals(12_000L, CarHealth.read(ctx).lastErrorMs)
        CarHealth.error(ctx, "Music screen", IllegalStateException("other"), now = 12_010) // a different error is saved at once
        assertTrue(CarHealth.read(ctx).lastError!!.startsWith("Music screen"))
    }

    @Test
    fun theCarLinesFollowWhatHappened() {
        fun line(title: String) = PhoneCheck.carItems(ctx, now = 100_000).first { it.title == title }

        // Nothing yet: Android Auto hasn't opened the app, with a way to the steps.
        assertFalse(line("Car connection").ok)
        assertEquals(PhoneCheck.Fix.INSTALL_HELP, line("Car connection").fix)
        assertTrue(line("Car screen errors").ok)

        CarHealth.sessionStarted(ctx, now = 40_000)
        assertTrue(line("Car connection").ok)
        assertTrue(line("Car connection").detail.contains("1 time"))

        CarHealth.error(ctx, "Gauges screen", RuntimeException("bad frame"), now = 90_000)
        val err = PhoneCheck.carItems(ctx, now = 100_000).first { it.title == "Car screen error" }
        assertFalse(err.ok)
        assertTrue(err.detail.contains("Gauges screen"))
        assertEquals(PhoneCheck.Fix.CLEAR_CAR_ERROR, err.fix)

        PhoneCheck.fix(ctx, PhoneCheck.Fix.CLEAR_CAR_ERROR)
        assertNull(CarHealth.read(ctx).lastError)
        assertNotNull(PhoneCheck.carItems(ctx).firstOrNull { it.title == "Car screen errors" })
    }

    @Test
    fun updatesAreOfferedStraightToUnknownAutoOnlyWhenItIsInstalled() {
        assertFalse(PhoneCheck.unknownAutoInstalled(ctx))
        val pm = org.robolectric.Shadows.shadowOf(ctx.packageManager)
        val info = android.content.pm.PackageInfo().apply {
            packageName = PhoneCheck.UNKNOWN_AUTO
            versionName = "1.6"
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = PhoneCheck.UNKNOWN_AUTO }
        }
        pm.installPackage(info)
        assertTrue(PhoneCheck.unknownAutoInstalled(ctx))
        assertEquals("1.6", PhoneCheck.versionOf(ctx, PhoneCheck.UNKNOWN_AUTO))
        // With it installed, its line is fine whether or not the Play mark is there.
        assertTrue(PhoneCheck.carItems(ctx).first { it.title == "UnknownAuto" }.ok)
    }
}
