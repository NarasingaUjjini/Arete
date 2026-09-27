package com.trailmap.gps.conditions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaylightTest {
    @Test
    fun sanFranciscoJuneSolsticeHasMorningSunriseUtc() {
        val hours = Daylight.solarEventHoursUtc(37.7749, -122.4194, 2024, 6, 21, 90.833, rising = true)
        assertTrue(hours != null)
        assertTrue("sunrise UTC hours $hours", hours!! in 11.5..14.5)
    }

    @Test
    fun polarNightReturnsNull() {
        val hours = Daylight.solarEventHoursUtc(80.0, 0.0, 2024, 12, 21, 90.833, rising = true)
        assertEquals(null, hours)
    }

    @Test
    fun nwsParsesWindRange() {
        assertEquals(12.5, NwsProvider.parseMph("10 to 15 mph")!!, 0.01)
    }

    @Test
    fun nearestAvalancheCenterIsOfficialUrl() {
        val rainier = AvalancheDirectory.nearest(46.85, -121.76)
        assertEquals("Northwest Avalanche Center", rainier.centerName)
        assertTrue(rainier.url.startsWith("https://"))
        assertTrue(rainier.note.contains("does not rate avalanche danger"))
    }
}
