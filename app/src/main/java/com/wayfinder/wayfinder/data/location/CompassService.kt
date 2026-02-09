package com.wayfinder.wayfinder.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class CompassService(context: Context) : SensorEventListener {
    private var listener: CompassListener? = null
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var isAccelerometerSet = false
    private var isMagnetometerSet = false
    private var r = FloatArray(9)
    private var orientation = FloatArray(3)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var azimuth = 0f // Rotation around the Z axis

    interface CompassListener {
        fun onNewAzimuth(azimuth: Float)
    }

    fun start() {
        // Change SENSOR_DELAY_GAME to SENSOR_DELAY_NORMAL for a slower update rate
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }


    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setCompassListener(listener: CompassListener) {
        this.listener = listener
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            isAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            isMagnetometerSet = true
        }
        if (isAccelerometerSet && isMagnetometerSet) {
            SensorManager.getRotationMatrix(r, null, lastAccelerometer, lastMagnetometer)
            SensorManager.getOrientation(r, orientation)
            val azimuthInRadians = orientation[0]
            val azimuthInDegrees = (Math.toDegrees(azimuthInRadians.toDouble()).toFloat() + 360) % 360
            listener?.onNewAzimuth(azimuthInDegrees)
        }
    }
}
