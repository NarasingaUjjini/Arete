package com.trailmap.gps.location

object HeadingMath {
    fun normalize(degrees: Double): Double {
        if (!degrees.isFinite()) return 0.0
        return ((degrees % 360.0) + 360.0) % 360.0
    }

    fun delta(from: Double, to: Double): Double {
        val raw = normalize(to) - normalize(from)
        return when {
            raw > 180.0 -> raw - 360.0
            raw < -180.0 -> raw + 360.0
            else -> raw
        }
    }

    fun smooth(previous: Double, sample: Double, alpha: Double): Double {
        val weight = alpha.coerceIn(0.0, 1.0)
        return normalize(previous + delta(previous, sample) * weight)
    }
}
