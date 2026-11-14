package io.github.byjoelsamuel.drivesanity.nav

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleNavTest {
    @Test
    fun distanceInTheTitleInstructionInTheText() {
        val g = GoogleNav.parse(listOf("250 ft", "Turn right onto Main St", "12 min (5.2 mi) · ETA 8:49 PM"))!!
        assertEquals("Turn right onto Main St", g.instruction)
        assertEquals("250 ft", g.toTurn)
        assertEquals("12 min", g.timeLeft)
        assertEquals("5.2 mi", g.distanceLeft)
        assertEquals("8:49 PM", g.eta)
        assertEquals(Arrow.RIGHT, g.arrow)
        assertEquals("5.2 mi · 12 min · ETA 8:49 PM", g.trip)
    }

    @Test
    fun instructionFirstThenTheTripLine() {
        val g = GoogleNav.parse(listOf("Turn left onto Elm St", "0.3 mi", "ETA 9:10 PM · 22 min · 8.4 mi"))!!
        assertEquals("Turn left onto Elm St", g.instruction)
        assertEquals("0.3 mi", g.toTurn)
        assertEquals("9:10 PM", g.eta)
        assertEquals("22 min", g.timeLeft)
        assertEquals("8.4 mi", g.distanceLeft)
        assertEquals(Arrow.LEFT, g.arrow)
    }

    @Test
    fun theDistanceCanSitInFrontOfTheInstruction() {
        val g = GoogleNav.parse(listOf("0.5 mi - Continue onto I-95 N", "1 h 5 min (60 mi) · 24-hour clock 21:05"))!!
        assertEquals("0.5 mi", g.toTurn)
        assertEquals("Continue onto I-95 N", g.instruction)
        assertEquals("1 h 5 min", g.timeLeft)
        assertEquals("60 mi", g.distanceLeft)
        assertEquals(Arrow.STRAIGHT, g.arrow)
    }

    @Test
    fun kilometresAndMultiLineBigText() {
        val g = GoogleNav.parse("In 400 m\nKeep left at the fork\n14 min · 7.1 km · 18:32".lines())!!
        assertEquals("Keep left at the fork", g.instruction)
        assertEquals("18:32", g.eta)
        assertEquals("14 min", g.timeLeft)
        assertEquals("7.1 km", g.distanceLeft)
        assertEquals(Arrow.SLIGHT_LEFT, g.arrow)
    }

    @Test
    fun nothingUsefulGivesNothing() {
        assertNull(GoogleNav.parse(emptyList()))
        assertNull(GoogleNav.parse(listOf("12 min", "ETA 8:49 PM")))
        assertNull(GoogleNav.parse(listOf("OK")))
    }

    @Test
    fun arrowsFollowTheWords() {
        assertEquals(Arrow.UTURN, GoogleNav.arrowFor("Make a U-turn"))
        assertEquals(Arrow.ARRIVE, GoogleNav.arrowFor("Arrive at your destination"))
        assertEquals(Arrow.SLIGHT_RIGHT, GoogleNav.arrowFor("Bear right onto Route 9"))
        assertEquals(Arrow.STRAIGHT, GoogleNav.arrowFor("Head north on Oak Ave"))
    }

    @Test
    fun readsARealNotificationsText() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val n = Notification.Builder(ctx, "nav")
            .setContentTitle("Turn right onto Main St")
            .setContentText("300 ft")
            .setSubText("9 min (3.1 mi) · ETA 7:15 AM")
            .build()
        val g = GoogleNav.fromNotification(n)
        assertNotNull(g)
        assertEquals("Turn right onto Main St", g!!.instruction)
        assertEquals("300 ft", g.toTurn)
        assertEquals("7:15 AM", g.eta)
    }
}
