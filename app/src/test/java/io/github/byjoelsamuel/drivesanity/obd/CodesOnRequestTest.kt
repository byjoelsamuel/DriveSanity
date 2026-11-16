package io.github.byjoelsamuel.drivesanity.obd

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fault codes are only read and shown when someone asks, and go away when the screen that showed them does. */
class CodesOnRequestTest {
    @After
    fun reset() {
        Obd.setDemo(false)
        Obd.wantDiagnostics("car", false)
        Obd.clearCodes()
    }

    @Test
    fun nothingIsReadUntilAsked() {
        Obd.clearCodes()
        Obd.wantDiagnostics("car", true) // showing the OBD2 screen is not asking
        assertEquals(Obd.CodesState.NOT_ASKED, Obd.codesState)
        assertNull(Obd.dtcReport)
    }

    @Test
    fun askingStartsAReadAndTheDemoAnswersAtOnce() {
        Obd.clearCodes()
        Obd.requestReadCodes()
        assertEquals("a real car is now being asked", Obd.CodesState.READING, Obd.codesState)
        Obd.clearCodes()
        Obd.setDemo(true)
        Obd.requestReadCodes()
        assertEquals("the demo has no car to ask", Obd.CodesState.DONE, Obd.codesState)
        assertNull(Obd.dtcReport)
    }

    @Test
    fun leavingTheScreenClearsWhatWasShown() {
        Obd.wantDiagnostics("car", true)
        Obd.setDemo(true)
        Obd.requestReadCodes()
        assertEquals(Obd.CodesState.DONE, Obd.codesState)
        Obd.wantDiagnostics("car", false)
        assertEquals(Obd.CodesState.NOT_ASKED, Obd.codesState)
    }
}
