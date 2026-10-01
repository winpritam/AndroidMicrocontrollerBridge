package com.example.service.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeDetector(
    private val context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val tag = "ShakeDetector"
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var lastShakeTimestamp: Long = 0
    private var isListening = false

    // Shake sensitivity threshold (2.3x Earth gravity)
    private val shakeThresholdGravity = 2.3f
    private val shakeSlopTimeMs = 1200

    fun start() {
        if (isListening) return
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                isListening = true
                Log.i(tag, "Accelerometer shake detector registered successfully.")
            } else {
                Log.w(tag, "No accelerometer hardware sensor available on this device.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting shake detector: ${e.message}")
        }
    }

    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
            isListening = false
            Log.i(tag, "Shake detector stopped.")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping shake detector: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // Total g-force calculation
            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            if (gForce > shakeThresholdGravity) {
                val now = System.currentTimeMillis()
                if (lastShakeTimestamp + shakeSlopTimeMs > now) {
                    return
                }
                lastShakeTimestamp = now
                Log.i(tag, "Phone shake detected! (gForce=$gForce)")
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
