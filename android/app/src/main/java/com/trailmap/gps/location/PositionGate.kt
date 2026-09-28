package com.trailmap.gps.location

import com.trailmap.gps.geo.GeoMath
import kotlin.math.hypot
import kotlin.math.sqrt

enum class PositioningRung { SEEK, HOLD, CHECK, COAST, RECOVER, UNKNOWN }

enum class IntegrityLevel { HIGH, MEDIUM, LOW, INVALID }

enum class PositionOrigin { VERIFIED_GNSS, COASTING, LAST_VERIFIED }

enum class WitnessVote { AGREE, DISAGREE, ABSTAIN }

data class GnssFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyM: Double,
    val speedMps: Float,
    val bearingDeg: Float,
    val timeMs: Long,
    val satellitesUsed: Int = 0
) {
    val structurallyValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            GeoMath.isValidLatitude(latitude) && GeoMath.isValidLongitude(longitude) &&
            !GeoMath.isNullIsland(latitude, longitude) &&
            accuracyM.isFinite() && accuracyM > 0.0
}

data class VerifiedAnchor(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timeMs: Long,
    val radiusM: Double
)

data class WitnessVotes(
    val motion: WitnessVote = WitnessVote.ABSTAIN,
    val barometer: WitnessVote = WitnessVote.ABSTAIN,
    val terrain: WitnessVote = WitnessVote.ABSTAIN,
    val route: WitnessVote = WitnessVote.ABSTAIN
)

data class CoastProposal(
    val latitude: Double,
    val longitude: Double,
    val steps: Int,
    val radiusM: Double,
    val stepsKnown: Boolean
)

data class PositionSnapshot(
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val altitude: Double = 0.0,
    val horizontalUncertaintyM: Double = Double.NaN,
    val verticalUncertaintyM: Double = Double.NaN,
    val speedMps: Float = 0f,
    val bearingDeg: Float = 0f,
    val timestampMs: Long = 0L,
    val ageMs: Long = 0L,
    val observed: Boolean = false,
    val origin: PositionOrigin = PositionOrigin.LAST_VERIFIED,
    val rung: PositioningRung = PositioningRung.SEEK,
    val integrity: IntegrityLevel = IntegrityLevel.INVALID,
    val reason: String = "Waiting for satellites",
    val anchor: VerifiedAnchor? = null,
    val verifiedAgeMs: Long = 0L,
    val recordable: Boolean = false
) {
    val hasPosition: Boolean get() = latitude.isFinite() && longitude.isFinite()

    fun toGpsUpdate(): GpsUpdate? {
        if (!hasPosition) return null
        val quality = when (integrity) {
            IntegrityLevel.HIGH -> if (horizontalUncertaintyM <= 15.0) LocationQuality.EXCELLENT else LocationQuality.GOOD
            IntegrityLevel.MEDIUM -> LocationQuality.GOOD
            IntegrityLevel.LOW -> LocationQuality.DEGRADED
            IntegrityLevel.INVALID -> LocationQuality.STALE
        }
        return GpsUpdate(
            latitude = latitude,
            longitude = longitude,
            elevation = altitude,
            accuracy = horizontalUncertaintyM.toFloat(),
            bearing = bearingDeg,
            speed = speedMps,
            timestamp = timestampMs,
            quality = quality,
            ageMs = ageMs,
            provider = "position",
            integrityLabel = integrity.name,
            positionReason = reason,
            observed = observed,
            verifiedAgeMs = verifiedAgeMs,
            recordable = recordable,
            // Styling only: tight/heading look when the fix is trusted. The mark itself always draws.
            showPrecisePuck = observed &&
                (integrity == IntegrityLevel.HIGH || integrity == IntegrityLevel.MEDIUM) &&
                horizontalUncertaintyM < 40.0
        )
    }
}

data class GateMemory(
    val rung: PositioningRung = PositioningRung.SEEK,
    val anchor: VerifiedAnchor? = null,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val altitude: Double = 0.0,
    val radiusM: Double = Double.NaN,
    val verticalM: Double = 30.0,
    val origin: PositionOrigin = PositionOrigin.LAST_VERIFIED,
    val integrity: IntegrityLevel = IntegrityLevel.INVALID,
    val reason: String = "Waiting for satellites",
    val observed: Boolean = false,
    val speedMps: Float = 0f,
    val bearingDeg: Float = 0f,
    val fixTimeMs: Long = 0L,
    val seekStartedMs: Long = 0L,
    val lastGnssMs: Long = 0L,
    val recoverStreak: Int = 0,
    val recoverLat: Double = Double.NaN,
    val recoverLon: Double = Double.NaN,
    val checkUntilMs: Long = 0L,
    val coastSteps: Int = 0,
    val stepsKnown: Boolean = false
) {
    fun hasPublished(): Boolean = latitude.isFinite() && longitude.isFinite()

    fun snapshot(now: Long): PositionSnapshot {
        val verifiedAge = anchor?.let { (now - it.timeMs).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        return PositionSnapshot(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            horizontalUncertaintyM = radiusM,
            verticalUncertaintyM = verticalM,
            speedMps = speedMps,
            bearingDeg = bearingDeg,
            timestampMs = if (observed) fixTimeMs else now,
            ageMs = if (fixTimeMs > 0L) (now - fixTimeMs).coerceAtLeast(0L) else 0L,
            observed = observed,
            origin = origin,
            rung = rung,
            integrity = integrity,
            reason = reason,
            anchor = anchor,
            verifiedAgeMs = if (anchor == null) Long.MAX_VALUE else verifiedAge,
            recordable = observed && integrity != IntegrityLevel.LOW && integrity != IntegrityLevel.INVALID
        )
    }
}

data class PositionTraceEntry(
    val nowMs: Long,
    val rung: String,
    val integrity: String,
    val radiusM: Double,
    val reason: String,
    val accepted: Boolean
)

object PositionTraceLog {
    @Volatile var enabled: Boolean = false
    private val entries = ArrayDeque<PositionTraceEntry>()

    fun record(entry: PositionTraceEntry) {
        if (!enabled) return
        synchronized(entries) {
            if (entries.size >= 200) entries.removeFirst()
            entries.addLast(entry)
        }
    }

    fun snapshot(): List<PositionTraceEntry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }
}

data class ReplayEvent(
    val nowMs: Long,
    val fix: GnssFix? = null,
    val votes: WitnessVotes = WitnessVotes(),
    val coast: CoastProposal? = null
)

object PositionReplay {
    fun run(events: List<ReplayEvent>, start: GateMemory = GateMemory()): List<GateMemory> {
        var memory = start
        return events.map { event ->
            memory = PositionGate.reduce(memory, event.nowMs, event.fix, event.votes, event.coast)
            memory
        }
    }
}

object PositionGate {
    const val STALE_MS = 15_000L
    const val SEEK_MS = 90_000L
    const val CHECK_MS = 20_000L
    const val UNKNOWN_RADIUS_M = 150.0
    const val UNKNOWN_WITHOUT_STEPS_MS = 180_000L
    const val UNKNOWN_MAX_GAP_MS = 20 * 60_000L
    const val MAX_SPEED_MPS = 55.0
    const val GATE_SIGMA = 3.0
    const val STRIDE_M = 0.75
    const val STEP_SIGMA_M = 0.45
    private const val TIME_DRIFT_WITH_STEPS = 0.2
    private const val TIME_DRIFT_WITHOUT_STEPS = 1.2

    fun coastRadius(anchorRadius: Double, steps: Int, elapsedSec: Double, stepsKnown: Boolean): Double {
        val stepTerm = if (stepsKnown) STEP_SIGMA_M * sqrt(steps.coerceAtLeast(0).toDouble()) else 0.0
        val timeTerm = (if (stepsKnown) TIME_DRIFT_WITH_STEPS else TIME_DRIFT_WITHOUT_STEPS) * elapsedSec.coerceAtLeast(0.0)
        return (anchorRadius.coerceAtLeast(5.0) + stepTerm + timeTerm).coerceAtMost(2_000.0)
    }

    fun reduce(
        memory: GateMemory,
        now: Long,
        fix: GnssFix?,
        votes: WitnessVotes = WitnessVotes(),
        coast: CoastProposal? = null
    ): GateMemory {
        val seeded = if (memory.seekStartedMs == 0L) memory.copy(seekStartedMs = now) else memory
        val suspicious = fix != null && fix.structurallyValid && seeded.hasPublished() && impliedSpeed(seeded, fix) > MAX_SPEED_MPS
        val fresh = fix != null && fix.structurallyValid && !suspicious && freshEnough(now, fix)
        var next = if (!fresh && coast != null && seeded.anchor != null && seeded.rung != PositioningRung.SEEK) {
            seeded.copy(
                latitude = coast.latitude,
                longitude = coast.longitude,
                radiusM = coast.radiusM,
                coastSteps = coast.steps,
                stepsKnown = coast.stepsKnown,
                origin = PositionOrigin.COASTING,
                observed = false
            )
        } else {
            seeded
        }
        next = when (next.rung) {
            PositioningRung.SEEK -> onSeek(next, now, fix, fresh, votes)
            PositioningRung.HOLD -> onHold(next, now, fix, fresh, suspicious, votes)
            PositioningRung.CHECK -> onCheck(next, now, fix, fresh, votes)
            PositioningRung.COAST -> onCoast(next, now, fix, fresh, votes)
            PositioningRung.RECOVER -> onRecover(next, now, fix, fresh, votes)
            PositioningRung.UNKNOWN -> onUnknown(next, now, fix, fresh, votes)
        }
        PositionTraceLog.record(
            PositionTraceEntry(
                nowMs = now,
                rung = next.rung.name,
                integrity = next.integrity.name,
                radiusM = next.radiusM,
                reason = next.reason,
                accepted = next.observed && next.fixTimeMs == fix?.timeMs
            )
        )
        return next
    }

    private fun onSeek(memory: GateMemory, now: Long, fix: GnssFix?, fresh: Boolean, votes: WitnessVotes): GateMemory {
        if (fresh && fix != null && residualOk(memory, fix)) {
            if (coldStartOk(fix)) return accept(memory, fix, votes, "GNSS verified")
            return previewSeek(memory, fix, now)
        }
        if (now - memory.seekStartedMs > SEEK_MS && memory.anchor != null) {
            return publishCoast(memory, now, "GNSS unavailable")
        }
        val reason = if (now - memory.seekStartedMs > SEEK_MS) "Still acquiring from satellites" else "Waiting for satellites"
        return memory.copy(
            rung = PositioningRung.SEEK,
            latitude = if (memory.anchor != null) memory.latitude else Double.NaN,
            longitude = if (memory.anchor != null) memory.longitude else Double.NaN,
            integrity = IntegrityLevel.INVALID,
            reason = reason,
            observed = false
        )
    }

    private fun previewSeek(memory: GateMemory, fix: GnssFix, now: Long): GateMemory {
        return memory.copy(
            rung = PositioningRung.SEEK,
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitude = fix.altitude,
            radiusM = fix.accuracyM.coerceAtLeast(3.0),
            speedMps = fix.speedMps,
            bearingDeg = fix.bearingDeg,
            origin = PositionOrigin.VERIFIED_GNSS,
            integrity = IntegrityLevel.LOW,
            reason = "Acquiring from satellites",
            observed = true,
            fixTimeMs = fix.timeMs,
            lastGnssMs = now
        )
    }

    private fun onHold(
        memory: GateMemory,
        now: Long,
        fix: GnssFix?,
        fresh: Boolean,
        suspicious: Boolean,
        votes: WitnessVotes
    ): GateMemory {
        if (suspicious) return rejectJump(memory, now)
        if (!fresh) {
            val gap = if (memory.lastGnssMs > 0L) now - memory.lastGnssMs else Long.MAX_VALUE
            return if (gap > STALE_MS && memory.anchor != null) publishCoast(memory, now, "GNSS unavailable") else memory
        }
        val sample = fix ?: return memory
        if (!residualOk(memory, sample) || votes.motion == WitnessVote.DISAGREE) {
            return rejectJump(memory, now)
        }
        val reason = when {
            votes.terrain == WitnessVote.DISAGREE -> "Terrain disagrees"
            votes.barometer == WitnessVote.DISAGREE -> "Pressure disagrees"
            votes.motion == WitnessVote.AGREE -> "GNSS and motion agree"
            else -> "GNSS verified"
        }
        return accept(memory, sample, votes, reason)
    }

    private fun onCheck(memory: GateMemory, now: Long, fix: GnssFix?, fresh: Boolean, votes: WitnessVotes): GateMemory {
        if (fresh && fix != null && residualOk(memory, fix) && votes.motion != WitnessVote.DISAGREE) {
            return accept(memory, fix, votes, "GNSS verified after check")
        }
        if (now < memory.checkUntilMs) {
            return memory.copy(rung = PositioningRung.CHECK, integrity = IntegrityLevel.LOW, observed = false, reason = "Checking a suspect fix")
        }
        return if (memory.anchor != null) publishCoast(memory, now, "GNSS unavailable") else memory.copy(
            rung = PositioningRung.SEEK,
            integrity = IntegrityLevel.INVALID,
            reason = "Waiting for satellites",
            observed = false
        )
    }

    private fun onCoast(memory: GateMemory, now: Long, fix: GnssFix?, fresh: Boolean, votes: WitnessVotes): GateMemory {
        val coasting = publishCoast(memory, now, "GNSS unavailable")
        if (expired(coasting, now)) return freezeUnknown(coasting)
        if (fresh && fix != null) return beginRecover(coasting, fix, votes)
        return coasting
    }

    private fun onRecover(memory: GateMemory, now: Long, fix: GnssFix?, fresh: Boolean, votes: WitnessVotes): GateMemory {
        if (!fresh || fix == null) {
            val gap = if (memory.lastGnssMs > 0L) now - memory.lastGnssMs else Long.MAX_VALUE
            return if (gap > STALE_MS) publishCoast(memory, now, "GNSS unavailable") else memory
        }
        if (!residualOk(memory, fix)) {
            return memory.copy(
                rung = PositioningRung.RECOVER,
                recoverStreak = 0,
                radiusM = (memory.radiusM.takeIf { it.isFinite() } ?: fix.accuracyM) + 15.0,
                integrity = IntegrityLevel.LOW,
                observed = false,
                reason = "Returned GNSS conflicts with estimate"
            )
        }
        val agreesWithPrevious = !memory.recoverLat.isFinite() || distance(memory.recoverLat, memory.recoverLon, fix) <= GATE_SIGMA * fix.accuracyM.coerceAtLeast(5.0)
        if (!agreesWithPrevious) {
            return memory.copy(
                rung = PositioningRung.RECOVER,
                recoverStreak = 1,
                recoverLat = fix.latitude,
                recoverLon = fix.longitude,
                lastGnssMs = fix.timeMs,
                integrity = IntegrityLevel.LOW,
                observed = false,
                reason = "Checking returned satellites"
            )
        }
        val streak = memory.recoverStreak + 1
        if (streak >= 2) return accept(memory, fix, votes, "GNSS verified after outage")
        return memory.copy(
            rung = PositioningRung.RECOVER,
            recoverStreak = streak,
            recoverLat = fix.latitude,
            recoverLon = fix.longitude,
            lastGnssMs = fix.timeMs,
            integrity = IntegrityLevel.LOW,
            observed = false,
            reason = "Checking returned satellites"
        )
    }

    private fun onUnknown(memory: GateMemory, now: Long, fix: GnssFix?, fresh: Boolean, votes: WitnessVotes): GateMemory {
        val frozen = freezeUnknown(memory)
        return if (fresh && fix != null) beginRecover(frozen, fix, votes) else frozen
    }

    private fun beginRecover(memory: GateMemory, fix: GnssFix, votes: WitnessVotes): GateMemory {
        if (!residualOk(memory, fix)) {
            return memory.copy(
                rung = PositioningRung.RECOVER,
                recoverStreak = 0,
                radiusM = (memory.radiusM.takeIf { it.isFinite() } ?: 50.0) + 15.0,
                integrity = IntegrityLevel.LOW,
                observed = false,
                reason = "Returned GNSS conflicts with estimate"
            )
        }
        return memory.copy(
            rung = PositioningRung.RECOVER,
            recoverStreak = 1,
            recoverLat = fix.latitude,
            recoverLon = fix.longitude,
            lastGnssMs = fix.timeMs,
            integrity = IntegrityLevel.LOW,
            observed = false,
            reason = "Checking returned satellites"
        )
    }

    private fun accept(memory: GateMemory, fix: GnssFix, votes: WitnessVotes, fallbackReason: String): GateMemory {
        val softDisagree = votes.terrain == WitnessVote.DISAGREE || votes.barometer == WitnessVote.DISAGREE
        val integrity = when {
            softDisagree || fix.accuracyM > 50.0 -> IntegrityLevel.LOW
            fix.accuracyM <= 15.0 -> IntegrityLevel.HIGH
            else -> IntegrityLevel.MEDIUM
        }
        val reason = when {
            votes.terrain == WitnessVote.DISAGREE -> "Terrain disagrees"
            votes.barometer == WitnessVote.DISAGREE -> "Pressure disagrees"
            votes.motion == WitnessVote.AGREE && votes.route != WitnessVote.DISAGREE -> "GNSS and motion agree"
            else -> fallbackReason
        }
        return memory.copy(
            rung = PositioningRung.HOLD,
            anchor = VerifiedAnchor(fix.latitude, fix.longitude, fix.altitude, fix.timeMs, fix.accuracyM.coerceAtLeast(3.0)),
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitude = fix.altitude,
            radiusM = fix.accuracyM.coerceAtLeast(3.0),
            verticalM = if (softDisagree) 45.0 else 12.0,
            origin = PositionOrigin.VERIFIED_GNSS,
            integrity = integrity,
            reason = reason,
            observed = true,
            speedMps = fix.speedMps,
            bearingDeg = fix.bearingDeg,
            fixTimeMs = fix.timeMs,
            lastGnssMs = fix.timeMs,
            recoverStreak = 0,
            recoverLat = Double.NaN,
            recoverLon = Double.NaN,
            checkUntilMs = 0L
        )
    }

    private fun rejectJump(memory: GateMemory, now: Long): GateMemory = memory.copy(
        rung = PositioningRung.CHECK,
        checkUntilMs = now + CHECK_MS,
        radiusM = ((if (memory.radiusM.isFinite()) memory.radiusM else 20.0) + 20.0).coerceAtMost(400.0),
        integrity = IntegrityLevel.LOW,
        observed = false,
        origin = if (memory.anchor != null) PositionOrigin.COASTING else PositionOrigin.LAST_VERIFIED,
        reason = "GNSS jump rejected",
        recoverStreak = 0
    )

    private fun publishCoast(memory: GateMemory, now: Long, reason: String): GateMemory {
        val radius = memory.radiusM.takeIf { it.isFinite() } ?: memory.anchor?.radiusM ?: 30.0
        val integrity = if (radius >= 80.0) IntegrityLevel.LOW else IntegrityLevel.MEDIUM
        return memory.copy(
            rung = PositioningRung.COAST,
            integrity = integrity,
            reason = reason,
            observed = false,
            origin = PositionOrigin.COASTING,
            radiusM = radius
        )
    }

    private fun freezeUnknown(memory: GateMemory): GateMemory {
        val anchor = memory.anchor
        return memory.copy(
            rung = PositioningRung.UNKNOWN,
            latitude = anchor?.latitude ?: memory.latitude,
            longitude = anchor?.longitude ?: memory.longitude,
            altitude = anchor?.altitude ?: memory.altitude,
            radiusM = memory.radiusM.takeIf { it.isFinite() }?.coerceAtLeast(UNKNOWN_RADIUS_M) ?: UNKNOWN_RADIUS_M,
            origin = PositionOrigin.LAST_VERIFIED,
            integrity = IntegrityLevel.INVALID,
            observed = false,
            reason = "Movement estimate expired"
        )
    }

    private fun expired(memory: GateMemory, now: Long): Boolean {
        val gap = if (memory.lastGnssMs > 0L) now - memory.lastGnssMs else Long.MAX_VALUE
        val radius = memory.radiusM.takeIf { it.isFinite() } ?: 0.0
        return radius >= UNKNOWN_RADIUS_M ||
            (!memory.stepsKnown && gap > UNKNOWN_WITHOUT_STEPS_MS) ||
            gap > UNKNOWN_MAX_GAP_MS
    }

    private fun coldStartOk(fix: GnssFix): Boolean {
        if (fix.accuracyM > 25.0) return false
        if (fix.satellitesUsed in 1..3) return false
        return true
    }

    private fun freshEnough(now: Long, fix: GnssFix): Boolean {
        val age = now - fix.timeMs
        return age <= STALE_MS && age >= -30_000L
    }

    private fun residualOk(memory: GateMemory, fix: GnssFix): Boolean {
        if (!memory.hasPublished()) return true
        val dist = distance(memory.latitude, memory.longitude, fix)
        val sigma = hypot(
            memory.radiusM.takeIf { it.isFinite() }?.coerceAtLeast(5.0) ?: 8.0,
            fix.accuracyM.coerceAtLeast(3.0)
        )
        return dist <= GATE_SIGMA * sigma
    }

    private fun impliedSpeed(memory: GateMemory, fix: GnssFix): Double {
        if (!memory.hasPublished() || memory.fixTimeMs <= 0L) return 0.0
        val dt = (fix.timeMs - memory.fixTimeMs) / 1000.0
        if (dt < 0.2) return 0.0
        return distance(memory.latitude, memory.longitude, fix) / dt
    }

    private fun distance(lat: Double, lon: Double, fix: GnssFix): Double =
        GeoMath.haversineMeters(lat, lon, fix.latitude, fix.longitude)
}
