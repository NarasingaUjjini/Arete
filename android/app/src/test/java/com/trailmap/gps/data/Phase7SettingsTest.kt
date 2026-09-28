package com.trailmap.gps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7SettingsTest {
    @Test
    fun profileSlotsAreUnique() {
        DataBarProfile.entries.filter { it != DataBarProfile.CUSTOM }.forEach { profile ->
            val (a, b, c) = DataBarMetric.slotsFor(profile)
            assertEquals(3, setOf(a, b, c).size)
        }
    }

    @Test
    fun customSlotsRejectDuplicates() {
        val unique = DataBarMetric.uniqueSlots(
            DataBarMetric.ELEVATION,
            DataBarMetric.ELEVATION,
            DataBarMetric.ELEVATION
        )
        assertEquals(DataBarMetric.ELEVATION, unique.first)
        assertNotEquals(unique.first, unique.second)
        assertNotEquals(unique.second, unique.third)
        assertNotEquals(unique.first, unique.third)
    }

    @Test
    fun mapChromeDefaultsToEdge() {
        assertEquals(MapChromeLayout.EDGE, MapChromeLayout.fromStored(null))
        assertEquals(3, MapChromeLayout.entries.size)
    }

    fun accentDoesNotReuseReservedSafetyHex() {
        val reserved = setOf("#FFB4AB", "#D4A017", "#E07A3D")
        AccentTheme.entries.forEach { theme ->
            assertTrue(theme.hex !in reserved)
        }
    }
}
