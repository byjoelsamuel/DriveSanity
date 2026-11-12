package io.github.byjoelsamuel.drivesanity.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Answers like an ELM327 clone would, echo and all. */
private class FakeElm(private val replies: Map<String, String>) : ObdTransport {
    val sent = mutableListOf<String>()
    private var pending = ""

    override fun send(text: String) {
        val cmd = text.trim()
        sent += cmd
        pending = (replies[cmd] ?: "?") + "\r\r"
    }

    override fun readReply(timeoutMs: Long): String = pending.also { pending = "" }
    override fun close() {}
}

class ElmTest {
    private val car = mapOf(
        "ATZ" to "ATZ\r\r\rELM327 v1.5",
        "ATE0" to "ATE0\rOK", "ATL0" to "OK", "ATS0" to "OK", "ATH0" to "OK", "ATSP0" to "OK",
        // Supports 01-07, 0B-10, 11, 13, 15, 1C, 1F, 20 -> RPM (0C), speed (0D), coolant (05), intake (0F), throttle (11).
        "0100" to "SEARCHING...\r41 00 BE 3F A8 13",
        // From 0x21: only 0x2F (fuel) and 0x40 (next range).
        "0120" to "4120 00 02 00 01",
        "0140" to "4140 40 00 00 00",
        "010C" to "410C1AF8",
        "0105" to "41057B",
        "012F" to "412F9E",
        "0111" to "NO DATA",
        "ATRV" to "12.6V",
    )

    @Test
    fun `starts up and finds what the car supports`() {
        val fake = FakeElm(car)
        val s = ObdSession(fake)
        assertEquals("ELM327 v1.5", s.start())
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATAT2", "0100", "0120", "0140"), fake.sent)
        for (pid in listOf(Pid.RPM, Pid.SPEED, Pid.COOLANT, Pid.INTAKE, Pid.THROTTLE, Pid.LOAD, Pid.FUEL)) {
            assertTrue("$pid", s.supports(pid))
        }
        assertTrue(0x42 in s.supported)
    }

    @Test
    fun `decodes readings`() {
        val s = ObdSession(FakeElm(car)).apply { start() }
        assertEquals(1726f, s.read(Pid.RPM)!!, 0.01f)        // (0x1A*256 + 0xF8) / 4
        assertEquals(83f, s.read(Pid.COOLANT)!!, 0.01f)      // 0x7B - 40
        assertEquals(61.96f, s.read(Pid.FUEL)!!, 0.01f)      // 0x9E * 100 / 255
        assertEquals(12.6f, s.volts()!!, 0.01f)
        assertNull(s.read(Pid.THROTTLE))                      // NO DATA
    }

    @Test
    fun `doesn't ask for what the car doesn't support`() {
        val fake = FakeElm(car - "0120" + ("0100" to "4100 BE 3E A8 12"))
        val s = ObdSession(fake).apply { start() }
        assertFalse(s.supports(Pid.FUEL))
        fake.sent.clear()
        assertNull(s.read(Pid.FUEL))
        assertTrue(fake.sent.isEmpty())
    }

    @Test(expected = IOException::class)
    fun `says so when the car doesn't answer`() {
        ObdSession(FakeElm(car + ("0100" to "SEARCHING...\rUNABLE TO CONNECT"))).start()
    }

    @Test
    fun `parsing edge cases`() {
        assertNull(Elm.mode01("NO DATA", 0x0C))
        assertNull(Elm.mode01("410C1A", 0x0D))                         // a different PID
        assertEquals(listOf(0x1A, 0xF8), Elm.mode01("41 0C 1A F8\r41 0C 1A F0", 0x0C)!!.toList()) // two ECUs: first wins
        assertTrue(Elm.isError("CAN ERROR"))
        assertTrue(Elm.isError("?"))
        assertFalse(Elm.isError("410C1AF8"))
        assertEquals(14.1f, Elm.volts("14.1V")!!, 0.01f)
        assertNull(Elm.volts("?"))
        assertEquals(setOf(0x01), Elm.supported("4100 80 00 00 00", 0x00))
    }
}
