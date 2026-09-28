package com.trailmap.gps.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HeadingReading(
    val magneticDegrees: Double = 0.0,
    val ready: Boolean = false
)

class HeadingEngine(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _heading = MutableStateFlow(HeadingReading())
    val heading: StateFlow<HeadingReading> = _heading.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val accelValues = FloatArray(3)
    private val magValues = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false
    private var filtered: Double? = null
    private var started = false
    private val holders = mutableSetOf<String>()

    fun acquire(holder: String) {
        val wasEmpty = holders.isEmpty()
        holders += holder
        if (wasEmpty) start()
    }

    fun release(holder: String) {
        holders -= holder
        if (holders.isEmpty()) stop()
    }

    fun start() {
        if (started) return
        started = true
        if (rotation != null) {
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_NORMAL)
            return
        }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        if (!started) return
        holders.clear()
        started = false
        sensorManager.unregisterListener(this)
        hasAccel = false
        hasMag = false
        filtered = null
        _heading.value = HeadingReading()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sample = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> azimuthFromRotation(event.values)
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelValues, 0, 3)
                hasAccel = true
                azimuthFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magValues, 0, 3)
                hasMag = true
                azimuthFromAccelMag()
            }
            else -> null
        } ?: return
        val next = filtered?.let { HeadingMath.smooth(it, sample, SMOOTH) } ?: sample
        val previous = filtered
        if (previous != null && kotlin.math.abs(HeadingMath.delta(previous, next)) < MIN_EMIT_DEG) {
            filtered = next
            return
        }
        filtered = next
        _heading.value = HeadingReading(magneticDegrees = next, ready = true)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun azimuthFromRotation(values: FloatArray): Double {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        return HeadingMath.normalize(Math.toDegrees(orientation[0].toDouble()))
    }

    private fun azimuthFromAccelMag(): Double? {
        if (!hasAccel || !hasMag) return null
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) return null
        SensorManager.getOrientation(rotationMatrix, orientation)
        return HeadingMath.normalize(Math.toDegrees(orientation[0].toDouble()))
    }

    companion object {
        private const val SMOOTH = 0.18
        private const val MIN_EMIT_DEG = 0.4
    }
}
