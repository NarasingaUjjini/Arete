package com.trailmap.gps.terrain

/**
 * Rolling vertical speed from GPS altitude. Uses a 3-minute window so a single noisy
 * sample cannot produce a meaningless instant rate.
 */
class VerticalSpeedTracker(
    private val windowMs: Long = 180_000L,
    private val minSpanMs: Long = 45_000L
) {
    private data class Sample(val timeMs: Long, val elevationMeters: Double)

    private val samples = ArrayDeque<Sample>()

    fun add(timeMs: Long, elevationMeters: Double) {
        if (!elevationMeters.isFinite()) return
        samples.addLast(Sample(timeMs, elevationMeters))
        trim(timeMs)
    }

    fun metersPerHour(nowMs: Long = samples.lastOrNull()?.timeMs ?: 0L): Double? {
        trim(nowMs)
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val dt = last.timeMs - first.timeMs
        if (dt < minSpanMs) return null
        return (last.elevationMeters - first.elevationMeters) / (dt / 3_600_000.0)
    }

    private fun trim(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().timeMs > windowMs) {
            samples.removeFirst()
        }
    }
}
