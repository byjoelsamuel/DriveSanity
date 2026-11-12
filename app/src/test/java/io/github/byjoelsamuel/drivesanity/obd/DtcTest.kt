package io.github.byjoelsamuel.drivesanity.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcTest {
    @Test
    fun `code letters and digits`() {
        assertEquals("P0133", Dtc.code(0x01, 0x33))
        assertEquals("C0035", Dtc.code(0x40, 0x35))
        assertEquals("B1234", Dtc.code(0x92, 0x34))
        assertEquals("U0100", Dtc.code(0xC1, 0x00))
        assertEquals("P2A00", Dtc.code(0x2A, 0x00))
    }

    @Test
    fun `one line reply on CAN has a count byte`() {
        assertEquals(listOf("P0133", "P0300"), Dtc.parse("430201330300", 0x43, hasCount = true))
    }

    @Test
    fun `multi-line reply is joined in order`() {
        val raw = "00A\r0:43040133030004\r1:200500\r"
        assertEquals(listOf("P0133", "P0300", "P0420", "P0500"), Dtc.parse(raw, 0x43, hasCount = true))
    }

    @Test
    fun `older buses have no count and pad with zeros`() {
        assertEquals(listOf("P0133"), Dtc.parse("43013300000000", 0x43, hasCount = false))
    }

    @Test
    fun `no codes, no data, and other services are ignored`() {
        assertTrue(Dtc.parse("430000000000", 0x43, hasCount = true).isEmpty())
        assertTrue(Dtc.parse("NO DATA", 0x43, hasCount = true).isEmpty())
        assertTrue(Dtc.parse("470201330000", 0x43, hasCount = true).isEmpty())
    }

    @Test
    fun `reads a VIN`() {
        val raw = "014\r0:490201575030\r1:5A5A5A39395A54\r2:53333932313234\r"
        assertEquals("WP0ZZZ99ZTS392124", Dtc.vin(raw))
        assertNull(Dtc.vin("NO DATA"))
    }

    @Test
    fun `describes known codes and falls back to the system`() {
        assertEquals("Random misfire", Dtc.describe("P0300"))
        assertEquals("Ignition or misfire", Dtc.describe("P0399"))
        assertEquals("Transmission", Dtc.describe("P0799"))
        assertEquals("Mazda-specific engine or transmission code", Dtc.describe("P1234"))
        assertEquals("Network: modules not talking", Dtc.describe("U0155"))
    }
}
