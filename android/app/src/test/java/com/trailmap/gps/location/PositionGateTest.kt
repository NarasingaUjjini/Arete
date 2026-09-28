package com.trailmap.gps.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionGateTest {
    @Test
    fun openSkyStationaryStaysHigh() {
        val memories = PositionReplay.run(listOf(
            fixEvent(1_000, 37.0, -119.0, 6.0),
            fixEvent(5_000, 37.0, -119.0, 6.0),
            fixEvent(9_000, 37.00001, -119.0, 7.0)
        ))
        val last = memories.last()
        assertEquals(IntegrityLevel.HIGH, last.integrity)
        assertEquals(PositioningRung.HOLD, last.rung)
        assertTrue(last.radiusM <= 15.0)
    }

    @Test
    fun openSkyWalkingKeepsTheFix() {
        val memories = PositionReplay.run(listOf(
            fixEvent(1_000, 37.0, -119.0, 8.0),
            fixEvent(5_000, 37.00008, -119.0, 8.0),
            fixEvent(9_000, 37.00016, -119.0, 8.0)
        ))
        assertEquals(IntegrityLevel.HIGH, memories.last().integrity)
        assertTrue(memories.last().latitude > 37.0001)
    }

    @Test
    fun suddenJumpDoesNotMoveTheDotOrClaimHigh() {
        val memories = PositionReplay.run(listOf(
            fixEvent(1_000, 37.0, -119.0, 6.0),
            fixEvent(2_000, 37.02, -119.0, 6.0)
        ))
        assertEquals(37.0, memories.last().latitude, 0.0001)
        assertNotEquals(IntegrityLevel.HIGH, memories.last().integrity)
        assertEquals(PositioningRung.CHECK, memories.last().rung)
    }

    @Test
    fun staleGnssCoastsWithoutHighIntegrity() {
        val start = PositionReplay.run(listOf(fixEvent(1_000, 37.0, -119.0, 8.0))).last()
        val coast = PositionGate.reduce(
            start,
            25_000,
            null,
            coast = CoastProposal(37.0, -119.0, 0, 20.0, true)
        )
        assertEquals(PositioningRung.COAST, coast.rung)
        assertNotEquals(IntegrityLevel.HIGH, coast.integrity)
    }

    @Test
    fun publishedPositionAlwaysBecomesAGpsUpdate() {
        val anchored = PositionReplay.run(listOf(fixEvent(1_000, 37.0, -119.0, 8.0))).last()
        val coast = PositionGate.reduce(
            anchored,
            25_000,
            null,
            coast = CoastProposal(37.0, -119.0, 0, 20.0, true)
        )
        val coastUpdate = coast.snapshot(25_000).toGpsUpdate()
        assertTrue(coastUpdate != null)
        assertEquals(37.0, coastUpdate!!.latitude, 0.0001)
        assertTrue(!coastUpdate.showPrecisePuck)
        assertTrue(coastUpdate.accuracy.isFinite() && coastUpdate.accuracy > 0f)
    }

    @Test
    fun longOutageWithoutStepsBecomesUnknown() {
        val anchored = PositionReplay.run(listOf(fixEvent(1_000, 37.0, -119.0, 8.0))).last()
        val expired = PositionGate.reduce(
            anchored.copy(rung = PositioningRung.COAST, stepsKnown = false, radiusM = 40.0),
            1_000 + PositionGate.UNKNOWN_WITHOUT_STEPS_MS + 5_000,
            null,
            coast = CoastProposal(37.0, -119.0, 0, 40.0, false)
        )
        assertEquals(PositioningRung.UNKNOWN, expired.rung)
        assertEquals(IntegrityLevel.INVALID, expired.integrity)
        assertEquals("Movement estimate expired", expired.reason)
    }

    @Test
    fun recoveryNeedsTwoAgreeingFixes() {
        val anchored = PositionReplay.run(listOf(fixEvent(1_000, 37.0, -119.0, 8.0))).last()
        val coasting = anchored.copy(rung = PositioningRung.COAST, radiusM = 25.0, observed = false)
        val first = PositionGate.reduce(coasting, 40_000, sample(40_000, 37.00005, -119.0, 8.0))
        assertEquals(PositioningRung.RECOVER, first.rung)
        assertNotEquals(IntegrityLevel.HIGH, first.integrity)
        val second = PositionGate.reduce(first, 42_000, sample(42_000, 37.00006, -119.0, 8.0))
        assertEquals(PositioningRung.HOLD, second.rung)
        assertEquals(IntegrityLevel.HIGH, second.integrity)
    }

    @Test
    fun conflictingReturnDoesNotJump() {
        val anchored = PositionReplay.run(listOf(fixEvent(1_000, 37.0, -119.0, 8.0))).last()
            .copy(rung = PositioningRung.COAST, radiusM = 20.0)
        val back = PositionGate.reduce(anchored, 40_000, sample(40_000, 37.01, -119.0, 8.0))
        assertEquals(37.0, back.latitude, 0.0001)
        assertNotEquals(IntegrityLevel.HIGH, back.integrity)
        assertEquals("Returned GNSS conflicts with estimate", back.reason)
    }

    @Test
    fun routeDepartureDoesNotRejectGnss() {
        val memories = PositionReplay.run(listOf(
            fixEvent(1_000, 37.0, -119.0, 6.0),
            ReplayEvent(5_000, sample(5_000, 37.00002, -119.0, 6.0), WitnessVotes(route = WitnessVote.DISAGREE))
        ))
        assertEquals(IntegrityLevel.HIGH, memories.last().integrity)
        assertEquals(PositioningRung.HOLD, memories.last().rung)
        assertTrue(memories.last().latitude > 37.0)
    }

    @Test
    fun terrainDisagreementLowersIntegrityWithoutAPreciseClaim() {
        val memories = PositionReplay.run(listOf(
            fixEvent(1_000, 37.0, -119.0, 6.0),
            ReplayEvent(5_000, sample(5_000, 37.00002, -119.0, 6.0), WitnessVotes(terrain = WitnessVote.DISAGREE))
        ))
        assertEquals(IntegrityLevel.LOW, memories.last().integrity)
        assertEquals("Terrain disagrees", memories.last().reason)
    }

    @Test
    fun seekDoesNotInventACoordinate() {
        val after = PositionGate.reduce(GateMemory(), 5_000, null)
        assertTrue(!after.hasPublished())
        assertEquals(PositioningRung.SEEK, after.rung)
        assertEquals(IntegrityLevel.INVALID, after.integrity)
    }

    @Test
    fun seekStillShowsACoarseGpsFix() {
        val after = PositionGate.reduce(GateMemory(), 5_000, sample(5_000, 37.0, -119.0, 40.0))
        assertEquals(37.0, after.latitude, 0.0001)
        assertEquals(PositioningRung.SEEK, after.rung)
        assertEquals(IntegrityLevel.LOW, after.integrity)
        val update = after.snapshot(5_000).toGpsUpdate()
        assertTrue(update != null)
        assertTrue(!update!!.showPrecisePuck)
    }

    @Test
    fun gpsClockSkewDoesNotHideAFreshFix() {
        val after = PositionGate.reduce(GateMemory(), 5_000, sample(6_500, 37.0, -119.0, 8.0))
        assertEquals(PositioningRung.HOLD, after.rung)
        assertEquals(37.0, after.latitude, 0.0001)
    }

    @Test
    fun coastRadiusGrowsWithSteps() {
        val afterTen = PositionGate.coastRadius(8.0, 10, 20.0, true)
        val afterHundred = PositionGate.coastRadius(8.0, 100, 20.0, true)
        assertTrue(afterHundred > afterTen)
        assertTrue(afterTen > 8.0)
    }

    @Test
    fun missingStepsStillCoastsWithoutHighIntegrity() {
        val anchored = PositionReplay.run(listOf(fixEvent(1_000, 37.0, -119.0, 8.0))).last()
        val coast = PositionGate.reduce(
            anchored,
            25_000,
            null,
            coast = CoastProposal(37.0, -119.0, 0, 35.0, false)
        )
        assertEquals(PositioningRung.COAST, coast.rung)
        assertNotEquals(IntegrityLevel.HIGH, coast.integrity)
        assertEquals(37.0, coast.latitude, 0.0001)
    }

    @Test
    fun replayOfAJumpNeverMarksHighAgain() {
        PositionTraceLog.enabled = true
        PositionTraceLog.clear()
        val memories = PositionReplay.run(listOf(
            fixEvent(1_000, 37.0, -119.0, 5.0),
            fixEvent(3_000, 37.05, -119.0, 5.0),
            fixEvent(6_000, 37.08, -119.0, 5.0)
        ))
        assertTrue(memories.drop(1).none { it.integrity == IntegrityLevel.HIGH })
        assertTrue(PositionTraceLog.snapshot().any { it.integrity != IntegrityLevel.HIGH.name })
        PositionTraceLog.enabled = false
        PositionTraceLog.clear()
    }

    private fun fixEvent(time: Long, lat: Double, lon: Double, accuracy: Double) =
        ReplayEvent(time, sample(time, lat, lon, accuracy))

    private fun sample(time: Long, lat: Double, lon: Double, accuracy: Double) = GnssFix(
        latitude = lat,
        longitude = lon,
        altitude = 2000.0,
        accuracyM = accuracy,
        speedMps = 1f,
        bearingDeg = 0f,
        timeMs = time,
        satellitesUsed = 8
    )
}
