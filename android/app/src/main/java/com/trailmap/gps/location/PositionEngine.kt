package com.trailmap.gps.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.trailmap.gps.geo.GeoMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class PositionEngine(
    context: Context,
    private val locationEngine: LocationEngine,
    private val headingEngine: HeadingEngine
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var clockJob: Job? = null
    private var started = false
    private var memory = GateMemory()
    private var lastFix: GnssFix? = null
    private var motionOn = false
    private var rawOn = false
    private var stepBase: Int? = null
    private var stepCount = 0
    private var stepSensorPresent = false
    private var usingCounter = false
    private var pressureHpa: Float? = null
    private var anchorPressure: Float? = null
    private var anchorTerrain: Double? = null
    private var appliedSteps = 0
    private var coastLat = Double.NaN
    private var coastLon = Double.NaN
    private var frequencyBands = 0
    private var lastAnchoredMs = 0L

    var terrainMeters: ((Double, Double) -> Double?)? = null
    var routeDistanceMeters: ((Double, Double) -> Double?)? = null

    private val _snapshot = MutableStateFlow(PositionSnapshot())
    val snapshot: StateFlow<PositionSnapshot> = _snapshot.asStateFlow()

    private val measurementCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            if (Build.VERSION.SDK_INT < 26) return
            val bands = mutableSetOf<Int>()
            for (measurement in eventArgs.measurements) {
                if (!measurement.hasCarrierFrequencyHz()) continue
                val mhz = measurement.carrierFrequencyHz / 1_000_000.0
                bands += if (mhz >= 1_400.0) 1 else 5
            }
            frequencyBands = bands.size
        }
    }

    fun start() {
        if (started) return
        started = true
        collectJob = scope.launch {
            locationEngine.state.collect { onLocation(it) }
        }
        clockJob = scope.launch {
            while (isActive) {
                delay(1_000)
                locationEngine.tickAge()
                ingest(System.currentTimeMillis())
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        collectJob?.cancel()
        clockJob?.cancel()
        stopMotion()
        stopRaw()
        headingEngine.release(HOLDER)
        locationEngine.setDesiredInterval(null)
    }

    private fun onLocation(state: CurrentLocationState) {
        if (state.usable && state.provider.equals("gps", ignoreCase = true)) {
            lastFix = GnssFix(
                latitude = state.latitude,
                longitude = state.longitude,
                altitude = state.altitude,
                accuracyM = state.horizontalAccuracy.toDouble(),
                speedMps = state.speed,
                bearingDeg = state.bearing,
                timeMs = state.timestamp,
                satellitesUsed = state.satellitesUsed
            )
        }
        ingest(System.currentTimeMillis())
    }

    private fun ingest(now: Long) {
        val fix = lastFix?.takeIf { now - it.timeMs <= PositionGate.STALE_MS }
        val coast = if (fix == null && memory.anchor != null && memory.rung != PositioningRung.SEEK) coastProposal(now) else null
        val votes = if (memory.rung == PositioningRung.HOLD || memory.rung == PositioningRung.SEEK) {
            WitnessVotes()
        } else {
            witnessVotes(fix)
        }
        memory = PositionGate.reduce(memory, now, fix, votes, coast)
        if (memory.observed && memory.fixTimeMs != 0L && memory.fixTimeMs != lastAnchoredMs) {
            lastAnchoredMs = memory.fixTimeMs
            onAnchorAccepted()
        }
        syncSensors()
        applyInterval(now)
        _snapshot.value = memory.snapshot(now)
        if (PositionTraceLog.enabled && frequencyBands > 0 && memory.rung == PositioningRung.CHECK) {
            PositionTraceLog.record(
                PositionTraceEntry(now, memory.rung.name, memory.integrity.name, memory.radiusM, "bands=$frequencyBands", false)
            )
        }
    }

    private fun onAnchorAccepted() {
        anchorPressure = pressureHpa
        val anchor = memory.anchor
        anchorTerrain = anchor?.let { terrainMeters?.invoke(it.latitude, it.longitude) }
        stepBase = stepCount
        appliedSteps = 0
        coastLat = Double.NaN
        coastLon = Double.NaN
    }

    private fun coastProposal(now: Long): CoastProposal? {
        val anchor = memory.anchor ?: return null
        var lat = if (coastLat.isFinite()) coastLat else anchor.latitude
        var lon = if (coastLon.isFinite()) coastLon else anchor.longitude
        val heading = headingEngine.heading.value
        val steps = if (stepSensorPresent && stepBase != null) (stepCount - stepBase!!).coerceAtLeast(0) else 0
        if (stepSensorPresent && heading.ready && steps > appliedSteps) {
            var accepted = appliedSteps
            for (i in appliedSteps until steps) {
                val next = GeoMath.destination(lat, lon, heading.magneticDegrees, PositionGate.STRIDE_M)
                if (stepRejected(anchor, next.first, next.second)) break
                lat = next.first
                lon = next.second
                accepted = i + 1
            }
            appliedSteps = accepted
            coastLat = lat
            coastLon = lon
        }
        val elapsed = (now - anchor.timeMs) / 1000.0
        return CoastProposal(
            latitude = lat,
            longitude = lon,
            steps = appliedSteps,
            radiusM = PositionGate.coastRadius(anchor.radiusM, appliedSteps, elapsed, stepSensorPresent),
            stepsKnown = stepSensorPresent
        )
    }

    private fun stepRejected(anchor: VerifiedAnchor, lat: Double, lon: Double): Boolean {
        val baro = pressureDeltaMeters() ?: return false
        val terrain = terrainMeters?.invoke(lat, lon) ?: return false
        val base = anchorTerrain ?: return false
        return abs((terrain - base) - baro) > 30.0
    }

    private fun pressureDeltaMeters(): Double? {
        val now = pressureHpa ?: return null
        val base = anchorPressure ?: return null
        return (base - now) * 8.3
    }

    private fun witnessVotes(fix: GnssFix?): WitnessVotes {
        return WitnessVotes(
            motion = motionVote(fix),
            barometer = barometerVote(fix),
            terrain = terrainVote(fix),
            route = routeVote(fix)
        )
    }

    private fun motionVote(fix: GnssFix?): WitnessVote {
        if (fix == null || !stepSensorPresent || !coastLat.isFinite()) return WitnessVote.ABSTAIN
        val dist = GeoMath.haversineMeters(coastLat, coastLon, fix.latitude, fix.longitude)
        return when {
            dist > 40.0 -> WitnessVote.DISAGREE
            dist < 20.0 -> WitnessVote.AGREE
            else -> WitnessVote.ABSTAIN
        }
    }

    private fun barometerVote(fix: GnssFix?): WitnessVote {
        val delta = pressureDeltaMeters() ?: return WitnessVote.ABSTAIN
        val anchor = memory.anchor ?: return WitnessVote.ABSTAIN
        if (fix == null) return WitnessVote.ABSTAIN
        val expected = anchor.altitude + delta
        return if (abs(fix.altitude - expected) > 40.0) WitnessVote.DISAGREE else WitnessVote.AGREE
    }

    private fun terrainVote(fix: GnssFix?): WitnessVote {
        if (fix == null) return WitnessVote.ABSTAIN
        val terrain = terrainMeters?.invoke(fix.latitude, fix.longitude) ?: return WitnessVote.ABSTAIN
        return if (abs(fix.altitude - terrain) > 80.0) WitnessVote.DISAGREE else WitnessVote.AGREE
    }

    private fun routeVote(fix: GnssFix?): WitnessVote {
        if (fix == null) return WitnessVote.ABSTAIN
        val distance = routeDistanceMeters?.invoke(fix.latitude, fix.longitude) ?: return WitnessVote.ABSTAIN
        return when {
            distance < 40.0 -> WitnessVote.AGREE
            distance > 150.0 -> WitnessVote.DISAGREE
            else -> WitnessVote.ABSTAIN
        }
    }

    private fun syncSensors() {
        val motion = memory.rung == PositioningRung.CHECK ||
            memory.rung == PositioningRung.COAST ||
            memory.rung == PositioningRung.RECOVER
        if (motion) headingEngine.acquire(HOLDER) else headingEngine.release(HOLDER)
        if (motion && !motionOn) startMotion()
        if (!motion && motionOn) stopMotion()
        val raw = memory.rung == PositioningRung.CHECK
        if (raw && !rawOn) startRaw()
        if (!raw && rawOn) stopRaw()
    }

    private fun startMotion() {
        motionOn = true
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        when {
            counter != null -> {
                usingCounter = true
                stepSensorPresent = true
                sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL)
            }
            detector != null -> {
                usingCounter = false
                stepSensorPresent = true
                sensorManager.registerListener(this, detector, SensorManager.SENSOR_DELAY_NORMAL)
            }
            else -> stepSensorPresent = false
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepBase = null
        appliedSteps = 0
    }

    private fun stopMotion() {
        if (!motionOn && !stepSensorPresent) return
        motionOn = false
        stepSensorPresent = false
        sensorManager.unregisterListener(this)
    }

    @SuppressLint("MissingPermission")
    private fun startRaw() {
        if (Build.VERSION.SDK_INT < 24) return
        rawOn = true
        runCatching {
            locationManager.registerGnssMeasurementsCallback(measurementCallback, Handler(Looper.getMainLooper()))
        }
    }

    private fun stopRaw() {
        if (!rawOn) return
        rawOn = false
        frequencyBands = 0
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) locationManager.unregisterGnssMeasurementsCallback(measurementCallback)
        }
    }

    private fun applyInterval(now: Long) {
        val interval = when (memory.rung) {
            PositioningRung.HOLD -> null
            PositioningRung.SEEK -> if (now - memory.seekStartedMs > PositionGate.SEEK_MS) 20_000L else 1_000L
            PositioningRung.COAST, PositioningRung.UNKNOWN -> 20_000L
            else -> 1_000L
        }
        locationEngine.setDesiredInterval(interval)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values.firstOrNull()?.toInt() ?: return
                if (stepBase == null) stepBase = total
                stepCount = total
            }
            Sensor.TYPE_STEP_DETECTOR -> stepCount += 1
            Sensor.TYPE_PRESSURE -> pressureHpa = event.values.firstOrNull()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val HOLDER = "position"
    }
}
