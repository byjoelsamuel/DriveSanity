package io.github.byjoelsamuel.drivesanity

import io.github.byjoelsamuel.drivesanity.nav.GeoTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIntentTest {
    @Test
    fun aSpotGoesStraightThrough() {
        val t = GeoTarget.parse("47.6062,-122.3321", null) as GeoTarget.At
        assertEquals(47.6062, t.place.at.lat, 1e-9)
        assertEquals(-122.3321, t.place.at.lon, 1e-9)
        assertEquals("Destination", t.place.name)
    }

    @Test
    fun aSpotKeepsItsLabel() {
        val t = GeoTarget.parse("47.6,-122.3", "Pike Place Market") as GeoTarget.At
        assertEquals("Pike Place Market", t.place.name)
    }

    @Test
    fun zeroZeroMeansLookTheWordsUp() {
        val t = GeoTarget.parse("0,0", "coffee near me") as GeoTarget.Search
        assertEquals("coffee near me", t.words)
    }

    @Test
    fun coordinatesInsideTheQueryWithAName() {
        val t = GeoTarget.parse("0,0", "34.99,-106.61(Treasure)") as GeoTarget.At
        assertEquals("Treasure", t.place.name)
        assertEquals(34.99, t.place.at.lat, 1e-9)
    }

    @Test
    fun nonsenseIsIgnored() {
        assertNull(GeoTarget.parse("0,0", null))
        assertNull(GeoTarget.parse("", "  "))
        assertNull(GeoTarget.parse("200,300", null)) // not on Earth
    }

    @Test
    fun phoneMakersGetTheirOwnTips() {
        assertNotNull(PhoneCheck.brandTip("samsung"))
        assertNotNull(PhoneCheck.brandTip("OnePlus"))
        assertTrue(PhoneCheck.brandTip("OnePlus")!!.contains("Lock"))
        assertNull(PhoneCheck.brandTip("Google"))
    }
}
